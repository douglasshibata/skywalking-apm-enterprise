# Multi-stage Dockerfile to build and package order-service with SkyWalking Agent
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
RUN mvn dependency:go-offline -B -pl order-service

COPY order-service/src order-service/src
RUN mvn package -DskipTests -pl order-service

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Download official SkyWalking Java Agent v9.1.0 for runtime instrumentation
RUN apk add --no-cache curl && \
    mkdir -p /skywalking/agent && \
    curl -sfL https://archive.apache.org/dist/skywalking/java-agent/9.1.0/apache-skywalking-java-agent-9.1.0.tgz | tar -xz -C /skywalking/agent --strip-components=1

COPY --from=build /app/order-service/target/order-service-1.0.0.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
