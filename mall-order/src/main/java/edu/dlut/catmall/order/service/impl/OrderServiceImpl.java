package edu.dlut.catmall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import edu.dlut.catmall.order.entity.OrderItemEntity;
import edu.dlut.catmall.order.enume.OrderStatusEnum;
import edu.dlut.catmall.order.feign.CartFeign;
import edu.dlut.catmall.order.feign.MemberFeign;
import edu.dlut.catmall.order.feign.ProductFeign;
import edu.dlut.catmall.order.feign.WareFeign;
import edu.dlut.catmall.order.interceptor.LoginUserInterceptor;
import edu.dlut.catmall.order.service.OrderItemService;
import edu.dlut.catmall.order.to.OrderCreateTO;
import edu.dlut.catmall.order.vo.*;
import edu.dlut.common.constant.OrderConstant;
import edu.dlut.common.to.SkuHasStockVO;
import edu.dlut.common.utils.R;
import edu.dlut.common.vo.MemberResponseVO;
import org.aspectj.weaver.ast.Or;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import edu.dlut.common.utils.PageUtils;
import edu.dlut.common.utils.Query;

import edu.dlut.catmall.order.dao.OrderDao;
import edu.dlut.catmall.order.entity.OrderEntity;
import edu.dlut.catmall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import javax.annotation.Resource;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    private ThreadLocal<OrderSubmitVO> confirmVOThreadLocal = new ThreadLocal<>();

    @Resource
    private MemberFeign memberFeign;

    @Resource
    private CartFeign cartFeign;

    @Resource
    private WareFeign wareFeign;

    @Resource
    private ProductFeign productFeign;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private OrderItemService orderItemService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVO confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVO orderConfirmVO = new OrderConfirmVO();
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> getAddressedTask = CompletableFuture.runAsync(() -> {
            // 1 远程查询会员的所有列表
            RequestContextHolder.setRequestAttributes(requestAttributes);
            List<MemberAddressVO> addresses = memberFeign.getAddresses(memberResponseVO.getId());
            orderConfirmVO.setAddresses(addresses);
        }, threadPoolExecutor);

        CompletableFuture<Void> getCartItemsTask = CompletableFuture.runAsync(() -> {
            // 2 远程查询购物车所有选中的购物项
            RequestContextHolder.setRequestAttributes(requestAttributes);
            List<OrderItemVO> currentUserCartItems = cartFeign.getCurrentUserCartItems();
            orderConfirmVO.setItems(currentUserCartItems);
        }, threadPoolExecutor).thenRunAsync(() -> {
            List<OrderItemVO> items = orderConfirmVO.getItems();
            List<Long> collect = items.stream().map(o -> o.getSkuId()).collect(Collectors.toList());
            R hasStock = wareFeign.getSkusHasStock(collect);
            List<SkuHasStockVO> data = hasStock.getData(new TypeReference<List<SkuHasStockVO>>() {});
            if (!CollectionUtils.isEmpty(data)) {
                Map<Long, Boolean> map = data.stream().collect(Collectors.toMap(SkuHasStockVO::getSkuId, SkuHasStockVO::getHasStock));
                orderConfirmVO.setStocks(map);
            }
        }, threadPoolExecutor);

        // 3 查询用户积分
        orderConfirmVO.setIntegration(memberResponseVO.getIntegration());

        // 其他数据在 Bean 中自动计算

        // 防重令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId(), token, 30, TimeUnit.MINUTES);
        orderConfirmVO.setOrderToken(token);

        CompletableFuture.allOf(getAddressedTask, getCartItemsTask).get();
        return orderConfirmVO;
    }

    @Transactional
    @Override
    public SubmitOrderResponseVO submitOrder(OrderSubmitVO submitVO) {
        confirmVOThreadLocal.set(submitVO);
        SubmitOrderResponseVO responseVO = new SubmitOrderResponseVO();
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        String orderToken = submitVO.getOrderToken();
        // 原子性操作验证和删除令牌
        Long result = stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVO.getId()), orderToken);
        if (result == 0) {
            // 令牌验证失败
            responseVO.setCode(1);
            return responseVO;
        } else {
            // 验证成功 下单 去创建订单 验证令牌 核算价格 锁定库存
            OrderCreateTO orderCreateTO = createOrder();
            // 验价
            BigDecimal payAmount = orderCreateTO.getOrderEntity().getPayAmount();
            BigDecimal payPrice = submitVO.getPayPrice();
            if (Math.abs(payAmount.subtract(payPrice).doubleValue()) < 0.01) {
                // 金额对比
                // 保存订单
                saveOrder(orderCreateTO);
                // 锁定库存 只要有异常就回滚数据
                WareSkuLockVO lockVO = new WareSkuLockVO();
                lockVO.setOrderSn(orderCreateTO.getOrderEntity().getOrderSn());
                List<OrderItemVO> locks = orderCreateTO.getOrderItems().stream().map(o -> {
                    OrderItemVO itemVO = new OrderItemVO();
                    itemVO.setSkuId(o.getSkuId());
                    itemVO.setCount(o.getSkuQuantity());
                    itemVO.setTitle(o.getSkuName());
                    return itemVO;
                }).collect(Collectors.toList());
                lockVO.setLocks(locks);
                R r = wareFeign.lockOrder(lockVO);
                if (r.getCode() == 0) {
                    // 锁定成功
                } else {
                    // 锁定失败
                }
            } else {
                responseVO.setCode(2);
                return  responseVO;
            }
        }
        return responseVO;
    }

    private void saveOrder(OrderCreateTO orderCreateTO) {
        OrderEntity orderEntity = orderCreateTO.getOrderEntity();
        orderEntity.setModifyTime(new Date());
        this.save(orderEntity);
        List<OrderItemEntity> orderItems = orderCreateTO.getOrderItems();
        orderItemService.saveBatch(orderItems);
    }

    private OrderCreateTO createOrder() {
        OrderCreateTO orderCreateTO = new OrderCreateTO();
        String orderSn = IdWorker.getTimeId();
        OrderEntity orderEntity = buildOrder(orderSn);

        // 获取所有的订单项
        List<OrderItemEntity> orderItemEntities = buildOrderItems(orderSn);

        // 计算所有的价格 积分
        computePrice(orderEntity, orderItemEntities);

        return orderCreateTO;
    }

    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {
        BigDecimal total = new BigDecimal("0.0");
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");

        BigDecimal gift = new BigDecimal("0.0");
        BigDecimal growth = new BigDecimal("0.0");
        // 订单的总额，叠加每一个订单项的总额信息。
        for (OrderItemEntity entity : orderItemEntities) {
            coupon = coupon.add(entity.getCouponAmount());
            integration = integration.add(entity.getIntegrationAmount());
            promotion = promotion.add(entity.getPromotionAmount());
            total = total.add(entity.getRealAmount());

            gift = gift.add(new BigDecimal(entity.getGiftIntegration().toString()));
            growth = growth.add(new BigDecimal(entity.getGiftGrowth().toString()));
        }
        // 订单价格相关
        orderEntity.setTotalAmount(total);
        // 应付金额
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(integration);
        orderEntity.setCouponAmount(coupon);

        // 设置积分信息
        orderEntity.setIntegration(gift.intValue());
        orderEntity.setGrowth(growth.intValue());

        orderEntity.setDeleteStatus(0);
    }

    /**
     * 构建所有订单项数据
     * @return
     * @param orderSn
     */
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        List<OrderItemVO> currentUserCartItems = cartFeign.getCurrentUserCartItems();
        if (!CollectionUtils.isEmpty(currentUserCartItems)) {
            List<OrderItemEntity> itemEntities = currentUserCartItems.stream().map(cartItem -> {
                OrderItemEntity orderItemEntity = buildOrderItem(cartItem);
                orderItemEntity.setOrderSn(orderSn);
                return orderItemEntity;
            }).collect(Collectors.toList());
            return itemEntities;
        }
        return null;
    }

    /**
     * 构建每一个订单项数据
     * @param cartItem
     * @return
     */
    private OrderItemEntity buildOrderItem(OrderItemVO cartItem) {
        OrderItemEntity orderItemEntity = new OrderItemEntity();
        // 1 订单信息 订单号
        // 2 SPU信息
        Long skuId = cartItem.getSkuId();
        R r = productFeign.getSpuInfoBySkuId(skuId);
        SpuInfoVO data = r.getData(new TypeReference<SpuInfoVO>() {});
        orderItemEntity.setSpuId(data.getId());
        orderItemEntity.setSpuBrand(data.getBrandId().toString());
        orderItemEntity.setSpuName(data.getSpuName());
        orderItemEntity.setCategoryId(data.getCatalogId());

        // 3 SKU信息
        orderItemEntity.setSkuId(cartItem.getSkuId());
        orderItemEntity.setSkuName(cartItem.getTitle());
        orderItemEntity.setSkuPic(cartItem.getImage());
        orderItemEntity.setSkuPrice(cartItem.getPrice());
        String skuAttrs = StringUtils.collectionToDelimitedString(cartItem.getSkuAttrs(), ";");
        orderItemEntity.setSkuAttrsVals(skuAttrs);
        orderItemEntity.setSkuQuantity(cartItem.getCount());
        // 4 优惠信息 [不做]

        // 5 积分信息
        orderItemEntity.setGiftGrowth(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount().toString())).intValue());
        orderItemEntity.setGiftIntegration(cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount().toString())).intValue());

        // 6 订单的价格信息
        orderItemEntity.setPromotionAmount(new BigDecimal("0"));
        orderItemEntity.setIntegrationAmount(new BigDecimal("0"));
        orderItemEntity.setCouponAmount(new BigDecimal("0"));
        // 当前订单项的实际金额
        BigDecimal origin = orderItemEntity.getSkuPrice().multiply(new BigDecimal(orderItemEntity.getSkuQuantity().toString()));
        // 总额减去各种优惠后的价格
        BigDecimal subtract = origin.subtract(orderItemEntity.getCouponAmount()).subtract(orderItemEntity.getIntegrationAmount()).subtract(orderItemEntity.getPromotionAmount());
        orderItemEntity.setRealAmount(subtract);
        return orderItemEntity;
    }

    private OrderEntity buildOrder(String orderSn) {
        MemberResponseVO memberResponseVO = LoginUserInterceptor.loginUser.get();
        OrderEntity orderEntity = new OrderEntity();
        // 创建订单号
        orderEntity.setOrderSn(orderSn);
        orderEntity.setMemberId(memberResponseVO.getId());

        OrderSubmitVO orderSubmitVO = confirmVOThreadLocal.get();

        // 获取收货地址信息
        R fare = wareFeign.getFare(orderSubmitVO.getAddrId());
        FareVO fareResponse = fare.getData(new TypeReference<FareVO>() {});

        // 设置运费信息
        orderEntity.setFreightAmount(fareResponse.getFare());
        // 设置收货人信息
        orderEntity.setReceiverCity(fareResponse.getMemberAddressVO().getCity());
        orderEntity.setReceiverDetailAddress(fareResponse.getMemberAddressVO().getDetailAddress());
        orderEntity.setReceiverName(fareResponse.getMemberAddressVO().getName());
        orderEntity.setReceiverPhone(fareResponse.getMemberAddressVO().getPhone());
        orderEntity.setReceiverPostCode(fareResponse.getMemberAddressVO().getPostCode());
        orderEntity.setReceiverProvince(fareResponse.getMemberAddressVO().getProvince());
        orderEntity.setReceiverRegion(fareResponse.getMemberAddressVO().getRegion());

        // 设置订单的状态信息
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        orderEntity.setAutoConfirmDay(7);


        return orderEntity;
    }

}