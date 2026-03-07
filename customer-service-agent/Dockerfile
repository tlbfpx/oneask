# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY llm-routing-agent/pom.xml ./llm-routing-agent/
COPY customer-service-agent ./customer-service-agent/
RUN mvn clean package -pl customer-service-agent -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-jammy

# Install Python 3 and requests library
RUN apt-get update && \
    apt-get install -y python3 python3-requests && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/customer-service-agent/target/*.jar app.jar
ENV TZ=Asia/Shanghai
ENV PYTHON_PATH=python3

ENTRYPOINT ["java", "-jar", "app.jar"]
