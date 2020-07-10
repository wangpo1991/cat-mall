1、下载镜像

```
docker pull qbanxiaoli/fastdfs
```

2、启动fastDFS



```
docker run -d --restart=always --privileged=true --net=host --name=fastdfs -e IP=10.4.11.106 -e WEB_PORT=9999 -v ${HOME}/fastdfs:/var/local/fdfs qbanxiaoli/fastdfs
#说明
IP 后面是你的服务器公网ip或者虚拟机的IP，-e WEB_PORT=80 指定nginx端口 端口最好是80

```

3、测试fastdfs

```
#进入容器
docker exec -it fastdfs /bin/bash
#创建文本
echo "Hello FastDFS!">index.html
#利用指令上传文件
fdfs_test /etc/fdfs/client.conf upload index.html
```

4、基于Springboot整合fastdfs不再赘述详情见代码库

5、下载jmeter文件

[JMeter 下载链接](http://jmeter.apache.org/download_jmeter.cgi)

6、解压文件

```
tar -xzvf apache-jmeter-5.3.tgz 
```

7、准备执行

```
cd apache-jmeter-5.3
cd bin/
sh jmeter
 
```

8、按照步骤执行即可

创建线程组

![image](https://user-images.githubusercontent.com/62863976/87127931-5e9c4e00-c2c1-11ea-9ebc-f104fd184530.png)

设置线程组

![image](https://user-images.githubusercontent.com/62863976/87128053-8db2bf80-c2c1-11ea-8d6d-5b7f60377ffa.png)

创建HTTP请求

![image](https://user-images.githubusercontent.com/62863976/87128202-cfdc0100-c2c1-11ea-8981-c4a2a96c3578.png)

设置请求参数1

![image](https://user-images.githubusercontent.com/62863976/87128478-424ce100-c2c2-11ea-9661-00f8111293c8.png)

设置上传参数FilesUpload

![image](https://user-images.githubusercontent.com/62863976/87128666-99eb4c80-c2c2-11ea-980f-4c3afc253331.png)

![image](https://user-images.githubusercontent.com/62863976/87129044-475e6000-c2c3-11ea-8bee-b66d549daa0c.png)

选择一个结果观察树

![image](https://user-images.githubusercontent.com/62863976/87130134-023b2d80-c2c5-11ea-8fc1-01f3ce317f75.png)

以上参数均设置为完毕，保证服务已经启动，我们进行下一步测试

当你首次创建HTTPRequest的时候会让你保存一个jmx文件，记住他的存储位置

选择此处进行脚本执行

```
./jmeter -n -t  HTTP\ Request.jmx 

```

响应结果集

![image](https://user-images.githubusercontent.com/62863976/87130662-d66c7780-c2c5-11ea-8db7-54b9a38ecc61.png)

执行脚本压测生成报告

```
./jmeter -n -t  HTTP\ Request.jmx  -Jconcurrent_number=20 -Jduration=120 -Jcycles=-1 -l report.jtl -e -o  /Users/wangpo/Desktop/software/apache-jmeter-5.3/reports/02/
```

在目录/Users/wangpo/Desktop/software/apache-jmeter-5.3/reports/02/下打开，可以看到index.html打开可以看到报告

![image](https://user-images.githubusercontent.com/62863976/87131081-76c29c00-c2c6-11ea-8e90-f2a84ffab128.png)

至此利用Jmeter压测上传文件功能完毕



参考资料

[1、Mac 安装 JMeter，JMeter 下载，JMeter Http 压力测试](https://www.sojson.com/blog/264.html)

[2、docker+fastdfs+springboot一键式搭建分布式文件服务器](https://blog.csdn.net/qq_37759106/article/details/82981023?utm_medium=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-2.nonecase&depth_1-utm_source=distribute.pc_relevant.none-task-blog-BlogCommendFromMachineLearnPai2-2.nonecase)

