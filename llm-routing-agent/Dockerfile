# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY customer-service-agent/pom.xml ./customer-service-agent/
COPY llm-routing-agent ./llm-routing-agent/
RUN mvn clean package -pl llm-routing-agent -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /app/llm-routing-agent/target/*.jar app.jar
ENV TZ=Asia/Shanghai
ENTRYPOINT ["java", "-jar", "app.jar"]
