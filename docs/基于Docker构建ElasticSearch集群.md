1、下载镜像

```shell
docker pull  elasticsearch:7.4.2
```

2、创建路由网络

```shell
docker network create  --driver bridge --subnet=172.19.1.0/16 --gateway=172.19.0.1 mynet
```

3、设置句柄大小限制

```shell
sysctl -w vm.max_map_count=262144
```

4、创建节点

```shell
#创建master节点
for port in $(seq 1 3); \
do \
mkdir -p /mydata/elasticsearch/master-${port}/config
mkdir -p /mydata/elasticsearch/master-${port}/data
chmod -R 777 /mydata/elasticsearch/master-${port}
cat << EOF > /mydata/elasticsearch/master-${port}/config/elasticsearch.yml
cluster.name: my-es  #集群的名称，同一个集群该值必须设置成相同的
node.name: es-master-${port}  #该节点的名字
node.master: true  #该节点有机会成为master节点
node.data: false #该节点可以存储数据
network.host: 0.0.0.0
http.host: 0.0.0.0   #所有http均可访问
http.port: 920${port}
transport.tcp.port: 930${port}
discovery.zen.ping_timeout: 10s #设置集群中自动发现其他节点时ping连接的超时时间
discovery.seed_hosts: ["172.19.1.21:9301","172.19.1.22:9302","172.19.1.23:9303"]
cluster.initial_master_nodes: ["172.19.1.21"] #新集群初始时的候选主节点，es7的新增配置
EOF
docker run --name elasticsearch-node-${port} \
-p 920${port}:920${port} -p 930${port}:930${port} \
--network=mynet --ip 172.19.1.2${port} \
-e ES_JAVA_OPTS="-Xms300m -Xmx300m"  \
-v /mydata/elasticsearch/master-${port}/config/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml  \
-v /mydata/elasticsearch/master-${port}/data:/usr/share/elasticsearch/data  \
-v /mydata/elasticsearch/master-${port}/plugins:/usr/share/elasticsearch/plugins  \
-d elasticsearch:7.4.2
done

#创建slave节点

for port in $(seq 4 6); \
do \
mkdir -p /mydata/elasticsearch/node-${port}/config
mkdir -p /mydata/elasticsearch/node-${port}/data
chmod -R 777 /mydata/elasticsearch/node-${port}
cat << EOF > /mydata/elasticsearch/node-${port}/config/elasticsearch.yml
cluster.name: my-es  #集群的名称，同一个集群该值必须设置成相同的
node.name: es-node-${port}  #该节点的名字
node.master: false  #该节点有机会成为master节点
node.data: true #该节点可以存储数据
network.host: 0.0.0.0
http.host: 0.0.0.0   #所有http均可访问
http.port: 920${port}
transport.tcp.port: 930${port}
discovery.zen.ping_timeout: 10s #设置集群中自动发现其他节点时ping连接的超时时间
discovery.seed_hosts: ["172.19.1.21:9301","172.19.1.22:9302","172.19.1.23:9303"]
cluster.initial_master_nodes: ["172.19.1.21"] #新集群初始时的候选主节点，es7的新增配置
EOF
docker run --name elasticsearch-node-${port} \
-p 920${port}:920${port} -p 930${port}:930${port} \
--network=mynet --ip 172.19.1.2${port} \
-e ES_JAVA_OPTS="-Xms300m -Xmx300m"  \
-v /mydata/elasticsearch/node-${port}/config/elasticsearch.yml:/usr/share/elasticsearch/config/elasticsearch.yml  \
-v /mydata/elasticsearch/node-${port}/data:/usr/share/elasticsearch/data  \
-v /mydata/elasticsearch/node-${port}/plugins:/usr/share/elasticsearch/plugins  \
-d elasticsearch:7.4.2
done

```

5、查看效果

![image](https://user-images.githubusercontent.com/62863976/86994843-0c343200-c1da-11ea-9e1c-48e58706f6a5.png)

附属部分

6、安装docker版本的kibana

​	下载镜像【注意版本需要与要链接的ES版本保持一致】

```shell
docker pull kibana:7.4.2
```

执行安装并指向上述集群

```shell
--link 
--link <name or id>:alias
其中，name和id是源容器的name和id，alias是源容器在link下的别名。
--network指向上游的ES集群网络
```

```shell
--利用docker的dns映射
sudo docker run -p 5601:5601 --name kibana --network=mynet  --link elasticsearch-node-1:elasticsearch-node-1 -e ELASTICSEARCH_HOSTS=http://elasticsearch-node-1:9201 -d kibana:7.4.2
--或者利用传统链接
sudo docker run -p 5601:5601 --name kibana  -e ELASTICSEARCH_HOSTS=http://10.4.11.106:9201 -d kibana:7.4.2
```

查看效果



![image](https://user-images.githubusercontent.com/62863976/87009041-3516f000-c1f7-11ea-8807-e48007bed635.png)



![image](https://user-images.githubusercontent.com/62863976/87009059-3ea05800-c1f7-11ea-8be1-0bdc35bff0d0.png)



参考资料

1、[max virtual memory areas vm.max_map_count [65530\] is too low, increase to at least [262144]](https://www.cnblogs.com/yidiandhappy/p/7714489.html)

2、[谷粒商城创建ES集群](https://www.cnblogs.com/dalianpai/p/13202348.html)

