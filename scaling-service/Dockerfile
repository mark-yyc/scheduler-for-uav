FROM openjdk:8-jdk-alpine

WORKDIR /app/
EXPOSE 8080

COPY ./target/auto-scaling-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java","-jar","app.jar"]