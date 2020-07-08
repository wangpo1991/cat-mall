1、下载镜像

```shell
docker pull redis:5.0.7
```

2、批量创建一批redis【batchInitRedisCluster.sh】

```shell
for port in $(seq 7001 7006);  \
do \
mkdir -p /mydata/redis/node-${port}/conf
touch /mydata/redis/node-${port}/conf/redis.conf
cat << EOF > /mydata/redis/node-${port}/conf/redis.conf
port ${port}
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
cluster-announce-ip 10.4.11.106
cluster-announce-port ${port}
cluster-announce-bus-port 1${port}
appendonly yes
EOF
docker run -p ${port}:${port} -p 1${port}:1${port} --name redis-${port}  \
-v /mydata/redis/node-${port}/data:/data \
-v /mydata/redis/node-${port}/conf/redis.conf:/etc/redis/redis.conf \
-d redis:5.0.7 redis-server /etc/redis/redis.conf; \
done
```

3、利用上述节点组建集群

```shell
#进去7001容器
docker exec -it redis-7001 bash 
#利用客户端执行生成集群
redis-cli  --cluster create 10.4.11.106:7001 10.4.11.106:7002 10.4.11.106:7003 10.4.11.106:7004 10.4.11.106:7005 10.4.11.106:7006 --cluster-replicas 1
```

4、查询集群的节点信息

```shell
#进去其中一个容器
docker exec -it redis-7001 bash 
#利用客户端连接
redis-cli -c -h 10.4.11.106 -p 7001
#查询集群信息
cluster info 
#查询节点信息【可以看到节点的集群信息，谁主谁从】

cluster nodes
e51c05ccf34f00459ee28ca175095ae63cc1590b 10.4.11.106:7006@17006 slave 741fd2bccbf34823748bd4fd6d47e697dd37fc69 0 1594202275000 6 connected
1c0cde76446b6553e476e4d736609c3325ad408d 10.4.11.106:7001@17001 myself,master - 0 1594202276000 1 connected 0-5460
9a697f48f888d0087b231f8a0c394e99b4113e5b 10.4.11.106:7004@17004 slave 927ce7bdd22682d1bfae843cea298c91a82c9571 0 1594202276000 4 connected
728c775a5c37b1ade4d512aec88c340cbb5a28c0 10.4.11.106:7005@17005 slave 1c0cde76446b6553e476e4d736609c3325ad408d 0 1594202275522 5 connected
927ce7bdd22682d1bfae843cea298c91a82c9571 10.4.11.106:7003@17003 master - 0 1594202276127 3 connected 10923-16383
741fd2bccbf34823748bd4fd6d47e697dd37fc69 10.4.11.106:7002@17002 master - 0 1594202276933 2 connected 5461-10922
#故意关闭主节点master 7002，看看此处信息是否有变化
cluster nodes
10.4.11.106:7003> cluster nodes
e51c05ccf34f00459ee28ca175095ae63cc1590b 10.4.11.106:7006@17006 master - 0 1594202610097 7 connected 5461-10922
728c775a5c37b1ade4d512aec88c340cbb5a28c0 10.4.11.106:7005@17005 slave 1c0cde76446b6553e476e4d736609c3325ad408d 0 1594202609000 5 connected
9a697f48f888d0087b231f8a0c394e99b4113e5b 10.4.11.106:7004@17004 slave 927ce7bdd22682d1bfae843cea298c91a82c9571 0 1594202609085 4 connected
741fd2bccbf34823748bd4fd6d47e697dd37fc69 10.4.11.106:7002@17002 master,fail - 1594202598959 1594202597548 2 disconnected
927ce7bdd22682d1bfae843cea298c91a82c9571 10.4.11.106:7003@17003 myself,master - 0 1594202607000 3 connected 10923-16383
1c0cde76446b6553e476e4d736609c3325ad408d 10.4.11.106:7001@17001 master - 0 1594202608580 1 connected 0-5460
我们发现cluster的master节点挂掉了，然后
10.4.11.106:7003> cluster  nodes
e51c05ccf34f00459ee28ca175095ae63cc1590b 10.4.11.106:7006@17006 master - 0 1594202747716 7 connected 5461-10922
728c775a5c37b1ade4d512aec88c340cbb5a28c0 10.4.11.106:7005@17005 slave 1c0cde76446b6553e476e4d736609c3325ad408d 0 1594202746706 5 connected
9a697f48f888d0087b231f8a0c394e99b4113e5b 10.4.11.106:7004@17004 slave 927ce7bdd22682d1bfae843cea298c91a82c9571 0 1594202747512 4 connected
741fd2bccbf34823748bd4fd6d47e697dd37fc69 10.4.11.106:7002@17002 master,fail - 1594202598959 1594202597548 2 disconnected
927ce7bdd22682d1bfae843cea298c91a82c9571 10.4.11.106:7003@17003 myself,master - 0 1594202746000 3 connected 10923-16383
1c0cde76446b6553e476e4d736609c3325ad408d 10.4.11.106:7001@17001 master - 0 1594202747513 1 connected 0-5460
我们发现7006成为新的master
恢复我们的7002节点
再看下效果
10.4.11.106:7003> cluster  nodes
e51c05ccf34f00459ee28ca175095ae63cc1590b 10.4.11.106:7006@17006 master - 0 1594202829000 7 connected 5461-10922
728c775a5c37b1ade4d512aec88c340cbb5a28c0 10.4.11.106:7005@17005 slave 1c0cde76446b6553e476e4d736609c3325ad408d 0 1594202827482 5 connected
9a697f48f888d0087b231f8a0c394e99b4113e5b 10.4.11.106:7004@17004 slave 927ce7bdd22682d1bfae843cea298c91a82c9571 0 1594202827986 4 connected
741fd2bccbf34823748bd4fd6d47e697dd37fc69 10.4.11.106:7002@17002 slave e51c05ccf34f00459ee28ca175095ae63cc1590b 0 1594202828000 7 connected
927ce7bdd22682d1bfae843cea298c91a82c9571 10.4.11.106:7003@17003 myself,master - 0 1594202827000 3 connected 10923-16383
1c0cde76446b6553e476e4d736609c3325ad408d 10.4.11.106:7001@17001 master - 0 1594202829501 1 connected 0-5460
7006成为新的master，恢复的7002成为了slave

```

5、图形补充

5.1 cluster info 

![avatar](https://user-images.githubusercontent.com/62863976/86913497-57563280-c151-11ea-8da2-ff9170da1cb7.png)

![image-20200708181115665](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708181115665.png)

5.2 cluster nodes 

![avatar](https://user-images.githubusercontent.com/62863976/86913515-5cb37d00-c151-11ea-9bc4-d13d208bf164.png)

![image-20200708181245599](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708181245599.png)

5.3 故障演示

![avatar](https://user-images.githubusercontent.com/62863976/86914415-e7e14280-c152-11ea-9fda-9342eba08a0e.png)

![image-20200708181332211](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708181332211.png)

![avatar](https://user-images.githubusercontent.com/62863976/86913595-781e8800-c151-11ea-8eb3-084936e81a69.png)

![image-20200708181405996](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708181405996.png)

![avatar](https://user-images.githubusercontent.com/62863976/86913610-7ce33c00-c151-11ea-9c3a-db6af9b94bbc.png)

![image-20200708181457861](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708181457861.png)

![avatar](https://user-images.githubusercontent.com/62863976/86913625-8076c300-c151-11ea-9086-99eb52000d87.png)

![image-20200708182032832](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708182032832.png)

![avatar](https://user-images.githubusercontent.com/62863976/86913535-6341f480-c151-11ea-8fae-6252da8b8857.png)

![image-20200708182757301](/Users/wangpo/Library/Application Support/typora-user-images/image-20200708182757301.png)


