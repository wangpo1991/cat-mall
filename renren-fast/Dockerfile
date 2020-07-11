FROM java:8
EXPOSE 8080

VOLUME /tmp
ADD target/*.jar  /app.jar
RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Xms256m","-Xms256m","-jar","/app.jar", "--spring.profiles.active=prod"]
FROM java:8
EXPOSE 8080