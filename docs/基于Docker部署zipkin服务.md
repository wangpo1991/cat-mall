1、下载镜像[支持内存和ES的版本，不支持kafka、rabbitmq]

```
docker pull openzipkin/zipkin-slim
```

2、启动容器

```shell
docker run -d --name=zipkin --link elasticsearch-node-1:elasticsearch-node-1 -e STORAGE_TYPE=elasticsearch -e ES_HOSTS=elasticsearch-node-1:9201 --network=mynet  -p 9411:9411 openzipkin/zipkin-slim
```

3、此处的es指向前文的基于Docker构建的ES集群

4、效果



参考资料

[1、GitHub-openzipkin-docker-zipkin](https://github.com/openzipkin-attic/docker-zipkin/blob/master/docker-compose-elasticsearch.yml)

2、[docker-zipkin](https://hub.docker.com/r/openzipkin/zipkin)

3、[docker exec 报错，怎么回事？](https://segmentfault.com/q/1010000008150884)

4、[docker --link如何理解](https://www.jianshu.com/p/21d66ca6115e)

