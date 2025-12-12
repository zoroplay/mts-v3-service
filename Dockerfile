# syntax=docker/dockerfile:1

#
# Build stage
#
FROM maven:3.9.6-eclipse-temurin-17 AS build
RUN apt-get update && apt-get install -y protobuf-compiler && rm -rf /var/lib/apt/lists/*
COPY src /home/app
RUN mvn -f /home/app/pom.xml clean package

#
# Package stage
#
FROM eclipse-temurin:17-jdk-alpine
COPY --from=build /home/app/bet-gateway/target/*.jar /usr/local/lib/mts-service.jar
COPY .env* /usr/local/lib/
RUN mkdir -p /gaming/logs/mts/sdk/traffic

WORKDIR /usr/local/lib

# http port
EXPOSE 8080

# system port
EXPOSE 8082

# grpc port
EXPOSE 82

CMD ["java","-jar","mts-service.jar"]