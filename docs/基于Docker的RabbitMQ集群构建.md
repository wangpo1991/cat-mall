1、拉取镜像

```
docker pull rabbitmq:management
```

2、创建文件目录

```
mkdir  -p /mydata/rabbitmq/rabbitmq01

mkdir  -p /mydata/rabbitmq/rabbitmq02

mkdir  -p /mydata/rabbitmq/rabbitmq03

```

3、创建节点

```
--rabbitmq01
docker run -d --hostname rabbitmq01  --name rabbitmq01 -v /mydata/rabbitmq/rabbitmq01:/var/lib/rabbitmq  -p 15672:15672 -p 5672:5672 -e RABBITMQ_ERLANG_COOKIE='monitor' rabbitmq:management
--rabbitmq02
docker run -d --hostname rabbitmq02  --name rabbitmq02 -v /mydata/rabbitmq/rabbitmq02:/var/lib/rabbitmq  -p 15673:15672 -p 5673:5672 -e RABBITMQ_ERLANG_COOKIE='monitor' --link rabbitmq01:rabbitmq01  rabbitmq:management
--rabbitmq03
docker run -d --hostname rabbitmq03  --name rabbitmq03 -v /mydata/rabbitmq/rabbitmq03:/var/lib/rabbitmq  -p 15674:15672 -p 5674:5672 -e RABBITMQ_ERLANG_COOKIE='monitor' --link rabbitmq01:rabbitmq01  --link rabbitmq02:rabbitmq02 rabbitmq:management
```

4、设置集群节点

```
--rabbitmq01
#进入容器
docker exec -it rabbitmq01 /bin/bash
#关闭rabbitmq
rabbitmqctl stop_app
#重置rabbitmq
rabbitmqctl reset
#启动rabbitmq
rabbitmqctl start_app
--rabbitmq02
#进入容器
docker exec -it rabbitmq02 /bin/bash
#关闭rabbitmq
rabbitmqctl stop_app
#重置rabbitmq
rabbitmqctl reset
#加入集群rabbitmq01
rabbitmqctl join_cluster --ram rabbit@rabbitmq01
#启动rabbitmq
rabbitmqctl start_app
--rabbitmq03
#进入容器
docker exec -it rabbitmq03 /bin/bash
#关闭rabbitmq
rabbitmqctl stop_app
#重置rabbitmq
rabbitmqctl reset
#加入集群rabbitmq01
rabbitmqctl join_cluster --ram rabbit@rabbitmq01
#启动rabbitmq
rabbitmqctl start_app
--rabbitmq01
#进入rabbitmq01
docker exec -it rabbitmq01 /bin/bash
#设置集群策略
rabbitmqctl set_policy -p / ha "^" '{"ha-mode":"all","ha-sync-mode":"automatic"}'
#查看集群策略
rabbitmqctl  list_policies -p /

```

效果参考【默认账号密码均为guest】

通过[链接](http://10.4.11.106:15673/#/)访问，端口采用集群任意一个节点的端口就行均可

![image](https://user-images.githubusercontent.com/62863976/86989687-8c07cf80-c1cd-11ea-937d-7427ce617244.png)

参考资料

[谷粒商城创建Rabbitmq集群](https://www.cnblogs.com/dalianpai/p/13197018.html)