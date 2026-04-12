# Flash Sale Backend Configuration Manifest

## 📋 Table of Contents
1. [Environment Variables (.env)](#1-environment-variables)
2. [Docker Compose Configuration](#2-docker-compose-configuration)
3. [Application Configuration (application.yml)](#3-application-configuration)
4. [Maven Project Configuration (pom.xml)](#4-maven-project-configuration)

---

## 1. Environment Variables

### .env (Production Configuration)
```dotenv
# ==============================================================================
# HỆ THỐNG FLASH SALE E-COMMERCE - PRODUCTION ENVIRONMENT CONFIGURATION
# CẢNH BÁO: KHÔNG BAO GIỜ COMMIT FILE NÀY LÊN GITHUB PUBLIC (Add vào .gitignore)
# ==============================================================================

# [1] GLOBAL SETTINGS & IMAGE VERSIONS
COMPOSE_PROJECT_NAME=flashsale_prod
SPRING_PROFILES_ACTIVE=prod
TZ=Asia/Ho_Chi_Minh

# Phiên bản hạ tầng (Ghim cứng version, không dùng 'latest' trên Prod)
POSTGRES_VER=15.4-alpine
MONGO_VER=6.0.8
REDIS_VER=7.2.1-alpine
KAFKA_VER=7.4.0
ELASTIC_VER=8.10.2

# [2] HOST PORT MAPPING
PORT_NGINX_HTTP=80
PORT_NGINX_HTTPS=443
PORT_API_GATEWAY=8080
PORT_FRONTEND_CUSTOMER=3000
PORT_SELLER_CENTER=3001
PORT_ADMIN_PORTAL=3002

# [3] SECRETS & CREDENTIALS
JWT_SECRET=bXktc3VwZXItc2VjcmV0LWtleS1mb3ItZmxhc2hzYWxlLXByb2R1Y3Rpb24tMjAyNA==
JWT_EXPIRATION_MS=86400000 # 24 hours
JWT_REFRESH_EXPIRATION_MS=604800000 # 7 days

# PostgreSQL Credentials
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres123!

# MongoDB Credentials
MONGO_INITDB_ROOT_USERNAME=fs_mongo_admin
MONGO_INITDB_ROOT_PASSWORD=S3cr3t_M0ng0_P@ssw0rd!2024

# Redis Password
REDIS_PASSWORD=

# MinIO (Object Storage)
MINIO_ACCESS_KEY=fs_storage_admin
MINIO_SECRET_KEY=S3cr3t_M1n1o_P@ssw0rd!2024

# [4] DATABASE NAMES
DB_NAME_IDENTITY=fs_identity_prod
DB_NAME_ORDER=fs_order_prod
DB_NAME_PAYMENT=fs_payment_prod
DB_NAME_PROMOTION=fs_promo_prod
DB_NAME_WORKER=fs_worker_prod
DB_NAME_PRODUCT=fs_product_prod
DB_NAME_NOTIFICATION=fs_noti_prod
DB_NAME_FLASHSALE=fs_flashsale_prod

# [5] INFRASTRUCTURE ROUTING
DB_HOST=postgres
MONGO_HOST=mongo
REDIS_HOST=redis
KAFKA_SERVER=kafka:29092
AXON_SERVER=axonserver:8124
ELASTIC_URI=http://elasticsearch:9200
MINIO_URL=http://minio:9000

# Service Discovery
EUREKA_URI=http://discovery-service:8761/eureka/

# [6] EXTERNAL 3RD-PARTY INTEGRATIONS
VNPAY_TMN_CODE=YOUR_VNPAY_TMN_CODE
VNPAY_HASH_SECRET=YOUR_VNPAY_HASH_SECRET_HERE
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://api.flashsale.com/v1/payment/vnpay-return

# Email Service
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=YOUR_SENDGRID_API_KEY
MAIL_FROM=no-reply@flashsale.com

# [7] RESOURCE TUNING
ES_JAVA_OPTS="-Xms2g -Xmx2g"
JVM_OPTS_GATEWAY="-Xms512m -Xmx512m -XX:+UseG1GC"
JVM_OPTS_FLASHSALE="-Xms2g -Xmx2g -XX:+UseZGC -XX:MaxGCPauseMillis=10"
JVM_OPTS_ORDER="-Xms1g -Xmx1g -XX:+UseG1GC"
JVM_OPTS_COMMON="-Xms256m -Xmx512m -XX:+UseG1GC"

# Frontend configuration
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws/user
VITE_API_URL=http://localhost:8080/api/v1
```

---

## 2. Docker Compose Configuration

### 2.1 Backend Docker Compose (backend/docker-compose.yml)

**Networks:**
- `flashsale-net` (bridge driver)

**Volumes:**
- postgres_data
- mongo_data
- redis_data
- elastic_data
- minio_data
- kafka_data
- zookeeper_data
- axon_data

#### Infrastructure Services

**PostgreSQL (fs-postgres)**
- Image: postgres:15-alpine
- Port: 5432
- Environment: POSTGRES_USER, POSTGRES_PASSWORD
- Health Check: pg_isready interval 30s

**MongoDB (fs-mongo)**
- Image: mongo:6.0
- Port: 27017
- Health Check: mongosh ping interval 30s

**Redis (fs-redis)**
- Image: redis:alpine
- Port: 6379
- Command: redis-server --appendonly yes
- Health Check: redis-cli ping interval 30s

**Elasticsearch (fs-elastic)**
- Image: elasticsearch:8.10.2
- Port: 9200
- Environment:
  - discovery.type=single-node
  - xpack.security.enabled=false
  - ES_JAVA_OPTS=-Xms512m -Xmx512m

**MinIO (fs-minio)**
- Image: minio/minio:latest
- Ports: 9000 (API), 9001 (Console)
- Environment: MINIO_ROOT_USER, MINIO_ROOT_PASSWORD
- Command: server /data --console-address ":9001"

**Zookeeper (fs-zookeeper)**
- Image: confluentinc/cp-zookeeper:7.4.0
- Port: 2181
- Environment:
  - ZOOKEEPER_CLIENT_PORT: 2181
  - ZOOKEEPER_TICK_TIME: 2000

**Kafka (fs-kafka)**
- Image: confluentinc/cp-kafka:7.4.0
- Port: 9092
- Depends on: zookeeper
- Environment:
  - KAFKA_BROKER_ID: 1
  - KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
  - KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
  - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
  - KAFKA_AUTO_CREATE_TOPICS_ENABLE: false
  - KAFKA_LOG_RETENTION_HOURS: 168

**Axon Server (fs-axon)**
- Image: axoniq/axonserver:latest
- Ports: 8024 (gRPC), 8124 (Server)
- Environment:
  - AXONIQ_AXONSERVER_STANDALONE=true
  - AXONIQ_AXONSERVER_DEVMODE_ENABLED=true
- Health Check: curl to /actuator/health interval 30s

#### Microservices

**Discovery Service (fs-discovery)**
- Port: 8761
- Depends on: None
- Health Check: /actuator/health

**API Gateway (fs-gateway)**
- Port: 8080
- Environment: EUREKA_URI, REDIS_HOST
- Depends on: redis, discovery-service

**Identity Domain (fs-identity)**
- Service Type: identity-domain
- Environment: EUREKA_URI, DB_HOST, DB_NAME_IDENTITY, POSTGRES_USER, POSTGRES_PASSWORD, AXON_SERVER
- Depends on: postgres, axonserver, discovery-service

**Product Domain (fs-product)**
- Service Type: product-domain
- Environment: EUREKA_URI, MONGO_HOST, PRODUCT_DB_NAME, KAFKA_SERVER, AXON_SERVER, MINIO_URL
- Depends on: mongo, kafka, axonserver, discovery-service

**Order Domain (fs-order)**
- Service Type: order-domain
- Environment: EUREKA_URI, DB_HOST, DB_NAME_ORDER, POSTGRES_USER, POSTGRES_PASSWORD, KAFKA_SERVER, AXON_SERVER
- Depends on: postgres, axonserver, discovery-service

**Payment Domain (fs-payment)**
- Service Type: payment-domain
- Environment: EUREKA_URI, DB_HOST, DB_NAME_PAYMENT, POSTGRES_USER, POSTGRES_PASSWORD, AXON_SERVER
- Depends on: postgres, axonserver, discovery-service

**Flash Sale Service (fs-flashsale)**
- Context: flashsale-domain
- Environment: EUREKA_URI, REDIS_HOST, KAFKA_SERVER
- Depends on: redis, kafka, discovery-service

**Search Service (fs-search)**
- Environment: EUREKA_URI, ELASTIC_URI, KAFKA_SERVER
- Depends on: elasticsearch, kafka, discovery-service

**Notification Service (fs-notification)**
- Environment: EUREKA_URI, MONGO_HOST, KAFKA_SERVER
- Depends on: mongo, kafka, discovery-service

**Worker Service (fs-worker)**
- Environment: EUREKA_URI, DB_HOST, DB_NAME_WORKER, POSTGRES_USER, POSTGRES_PASSWORD, KAFKA_SERVER, AXON_SERVER
- Depends on: postgres, kafka, axonserver, discovery-service

**Cart Service (fs-cart)**
- Environment: EUREKA_URI, REDIS_HOST, REDIS_PASSWORD
- Depends on: redis, discovery-service

### 2.2 Infrastructure Docker Compose (infra/docker-compose.yml)

Same infrastructure services as backend compose but standalone for separate orchestration.

---

## 3. Application Configuration

### 3.1 Discovery Service
```yaml
server:
  port: 8761

spring:
  application:
    name: discovery-service

eureka:
  instance:
    hostname: discovery-service
    prefer-ip-address: true
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      defaultZone: http://discovery-service:8761/eureka/
```

### 3.2 API Gateway
```yaml
server:
  port: 8080
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      routes:
        - id: flashsale-route
          uri: lb://flashsale-service
          predicates:
            - Path=/api/v1/flash-sale/**
        - id: order-domain-route
          uri: lb://order-domain
          predicates:
            - Path=/api/v1/orders/**
        - id: product-domain-route
          uri: lb://product-domain
          predicates:
            - Path=/api/v1/products/**
        - id: identity-domain-route
          uri: lb://identity-domain
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:123456}
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.3 Identity Service
```yaml
server:
  port: 8081
spring:
  application:
    name: identity-domain
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME_IDENTITY:fs_identity_prod}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        show_sql: false
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
jwt:
  secret: ${JWT_SECRET:super_secret_jwt_key_for_flashsale_project_that_is_very_long}
  expiration: ${JWT_EXPIRATION_MS:86400000}
```

### 3.4 Product Service
```yaml
server:
  port: 8090
spring:
  application:
    name: product-domain
  kafka:
    bootstrap-servers: ${KAFKA_SERVER:localhost:9092}
  mongodb:
    uri: mongodb://${MONGO_INITDB_ROOT_USERNAME:root}:${MONGO_INITDB_ROOT_PASSWORD:root}@${MONGO_HOST:localhost}:27017/${DB_NAME_PRODUCT:fs_product_prod}?authSource=admin
minio:
  url: ${MINIO_URL:http://localhost:9000}
  access-key: ${MINIO_ROOT_USER:minioadmin}
  secret-key: ${MINIO_ROOT_PASSWORD:minioadmin}
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.5 Order Service (with Axon)
```yaml
server:
  port: 8083
spring:
  application:
    name: order-domain
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME_ORDER:fs_order_prod}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        show_sql: false
  kafka:
    bootstrap-servers: ${KAFKA_SERVER:localhost:9092}
axon:
  axonserver:
    servers: ${AXON_SERVER:localhost:8124}
  eventhandling:
    poolsize: 10
  serializer:
    general: xstream
  repository:
    snapshot-filter: 10
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.6 Payment Service (with Axon)
```yaml
server:
  port: 8082
spring:
  application:
    name: payment-domain
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME_PAYMENT:fs_payment_prod}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        show_sql: false
axon:
  axonserver:
    servers: ${AXON_SERVER:localhost:8124}
  eventhandling:
    poolsize: 10
  serializer:
    general: xstream
  repository:
    snapshot-filter: 10
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.7 Flash Sale Service
```yaml
server:
  port: 8085
spring:
  application:
    name: flashsale-service
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME_FLASHSALE:flashsale_db}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres123!}
    pool:
      initial-size: 5
      max-size: 20
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD:123456}
  kafka:
    bootstrap-servers: ${KAFKA_SERVER:localhost:9092}
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.8 Search Service
```yaml
server:
  port: 8086
spring:
  application:
    name: search-service
  elasticsearch:
    uris: ${ELASTIC_URI:http://localhost:9200}
  kafka:
    bootstrap-servers: ${KAFKA_SERVER:localhost:9092}
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.9 Notification Service
```yaml
server:
  port: 8087
spring:
  application:
    name: notification-service
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  kafka:
    bootstrap-servers: ${KAFKA_SERVER:localhost:9092}
  mongodb:
    uri: mongodb://${MONGO_INITDB_ROOT_USERNAME:root}:${MONGO_INITDB_ROOT_PASSWORD:root}@${MONGO_HOST:localhost}:27017/${DB_NAME_NOTIFICATION:fs_noti_prod}?authSource=admin
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.10 Worker Service
```yaml
server:
  port: 8089
spring:
  application:
    name: worker-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME_WORKER:fs_worker_prod}
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
eureka:
  instance:
    prefer-ip-address: true
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka/}
```

### 3.11 Cart Service
```yaml
server:
  port: 8089
  servlet:
    context-path: /api/cart

spring:
  application:
    name: cart-service
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
  actuator:
    metrics:
      export:
        prometheus:
          enabled: true
  endpoints:
    web:
      exposure:
        include: health,info,prometheus

eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka}
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
```

---

## 4. Maven Project Configuration

### 4.1 Parent POM (backend/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.flashsale</groupId>
    <artifactId>flashsale-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>FlashSale Parent</name>
    <description>He thong Microservices Flash Sale (Java 25 &amp; Spring Boot)</description>

    <modules>
        <module>discovery-service</module>
        <module>cart-service</module>
        <module>api-gateway</module>
        <module>flashsale-service</module>
        <module>search-service</module>
        <module>worker-service</module>
        <module>notification-service</module>
        <module>identity-service</module>
        <module>product-service</module>
        <module>order-service</module>
        <module>payment-service</module>
    </modules>

    <properties>
        <java.version>25</java.version>
        <spring-boot.version>4.0.4</spring-boot.version>
        <spring-cloud.version>2025.1.1</spring-cloud.version>
        <spring-boot-maven-plugin.version>4.0.4</spring-boot-maven-plugin.version>
        <axon.version>4.13.0</axon.version>
        <grpc.version>1.70.0</grpc.version>
        <netty.version>4.1.112.Final</netty.version>
        <grpc-spring-boot.version>3.1.0.RELEASE</grpc-spring-boot.version>
        <protobuf.version>4.33.1</protobuf.version>
        <lombok.version>1.18.40</lombok.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot & Cloud -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Net.devh gRPC Starters -->
            <dependency>
                <groupId>net.devh</groupId>
                <artifactId>grpc-server-spring-boot-starter</artifactId>
                <version>${grpc-spring-boot.version}</version>
            </dependency>
            <dependency>
                <groupId>net.devh</groupId>
                <artifactId>grpc-client-spring-boot-starter</artifactId>
                <version>${grpc-spring-boot.version}</version>
            </dependency>

            <!-- gRPC & Netty BOM -->
            <dependency>
                <groupId>io.grpc</groupId>
                <artifactId>grpc-bom</artifactId>
                <version>${grpc.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.google.protobuf</groupId>
                <artifactId>protobuf-bom</artifactId>
                <version>${protobuf.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>io.netty</groupId>
                <artifactId>netty-bom</artifactId>
                <version>${netty.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Axon BOM -->
            <dependency>
                <groupId>org.axonframework</groupId>
                <artifactId>axon-bom</artifactId>
                <version>${axon.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>${java.version}</source>
                    <target>${java.version}</target>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo.maven.apache.org/maven2</url>
        </repository>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>spring-snapshots</id>
            <name>Spring Snapshots</name>
            <url>https://repo.spring.io/snapshot</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>

    <pluginRepositories>
        <pluginRepository>
            <id>central</id>
            <url>https://repo.maven.apache.org/maven2</url>
        </pluginRepository>
        <pluginRepository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </pluginRepository>
        <pluginRepository>
            <id>spring-snapshots</id>
            <name>Spring Snapshots</name>
            <url>https://repo.spring.io/snapshot</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </pluginRepository>
    </pluginRepositories>
</project>
```

### 4.2 Discovery Service (discovery-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>discovery-service</artifactId>
    <name>Discovery Service (Eureka)</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>

</project>
```

### 4.3 API Gateway (api-gateway/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>API Gateway</name>
    <description>Cổng API tập trung - Chạy trên mô hình Reactive (WebFlux)</description>

    <dependencies>
        <!-- 1. SPRING CLOUD GATEWAY: Lõi xử lý routing chuyên dụng cho WebFlux -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
        </dependency>

        <!-- 2. SERVICE DISCOVERY: Tìm kiếm địa chỉ các Microservice từ Eureka -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- 3. REDIS REACTIVE: Rate Limiting -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- 4. MONITORING: Giám sát tình trạng gateway -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- 5. SECURITY: Để Gateway check JWT ban đầu -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Thư viện test cho môi trường WebFlux -->
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.4 Identity Service (identity-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>identity-service</artifactId>
    <name>Identity Domain</name>

    <dependencies>
        <!-- Spring Web để chạy độc lập -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.5 Product Service (product-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>product-service</artifactId>
    <name>Product Domain</name>

    <dependencies>
        <!-- Catalog lưu bằng MongoDB -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>

        <!-- Tương tác MinIO -->
        <dependency>
            <groupId>io.minio</groupId>
            <artifactId>minio</artifactId>
            <version>8.5.7</version>
        </dependency>

        <!-- Đẩy dữ liệu sang Kafka để đồng bộ Search -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.6 Order Service (order-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>order-service</artifactId>
    <name>Order Domain (Saga and Event Store)</name>

    <dependencies>
        <dependency>
            <groupId>com.flashsale</groupId>
            <artifactId>order-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>

        <!-- Spring Web để chạy độc lập -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Axon: Trái tim xử lý Aggregate & Saga -->
        <dependency>
            <groupId>org.axonframework</groupId>
            <artifactId>axon-spring-boot-starter</artifactId>
        </dependency>

        <!-- Kafka: Hứng đơn hàng từ FlashSale Svc -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <!-- Ghi dữ liệu vào PostgreSQL -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- gRPC (Cung cấp và gọi sang các service khác như Payment) -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.7 Payment Service (payment-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>payment-service</artifactId>
    <name>Payment Service</name>

    <dependencies>
        <dependency>
            <groupId>com.flashsale</groupId>
            <artifactId>payment-api</artifactId>
            <version>1.0.0-SNAPSHOT</version>
        </dependency>

        <!-- Axon Framework -->
        <dependency>
            <groupId>org.axonframework</groupId>
            <artifactId>axon-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-server-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.8 Flash Sale Service (flashsale-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>flashsale-service</artifactId>
    <name>Flash Sale Service (Core)</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- Redis thực thi Lua Script -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
        <!-- Kafka đẩy đơn hàng đi -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <!-- gRPC Client -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.9 Search Service (search-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>search-service</artifactId>
    <name>Search Service (Elasticsearch)</name>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Elasticsearch để tìm kiếm Full-text -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
        </dependency>

        <!-- Kafka Consumer -->
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.10 Notification Service (notification-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>notification-service</artifactId>
    <name>Notification Service (Reactive)</name>
    <description>Dịch vụ đẩy thông báo thời gian thực sử dụng Spring WebFlux &amp; Reactive Stack</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.11 Worker Service (worker-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>worker-service</artifactId>
    <description>Cronjobs, DLQ Retry, Hủy đơn quá hạn</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Thư viện lập lịch Quartz -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-quartz</artifactId>
        </dependency>

        <!-- Khóa phân tán ShedLock -->
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-spring</artifactId>
            <version>5.13.0</version>
        </dependency>
        <dependency>
            <groupId>net.javacrumbs.shedlock</groupId>
            <artifactId>shedlock-provider-jdbc-template</artifactId>
            <version>5.13.0</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.12 Cart Service (cart-service/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.flashsale</groupId>
        <artifactId>flashsale-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>cart-service</artifactId>
    <name>Cart Service</name>

    <dependencies>
        <!-- 1. Web & Virtual Threads (Imperative Model) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- 2. Redis: Lưu trữ giỏ hàng tốc độ cao -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- 3. Validation: Kiểm tra dữ liệu đầu vào -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- 4. gRPC Client: Gọi Identity/Product Service -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
        </dependency>

        <!-- 5. Cloud Discovery: Đăng ký với Eureka -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>

        <!-- 6. Observability -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot-maven-plugin.version}</version>
            </plugin>
        </plugins>
    </build>
</project>
```

### 4.13 Common Library (common-lib/pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.5</version>
        <relativePath/>
    </parent>
    <groupId>com.flashsale</groupId>
    <artifactId>common-lib</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>common-lib</name>
    <description>common-lib</description>
    
    <properties>
        <java.version>25</java.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```

---

## Summary Table

| Service | Port | Database | Key Technologies |
|---------|------|----------|------------------|
| Discovery Service | 8761 | None | Eureka |
| API Gateway | 8080 | None | Spring Cloud Gateway, Redis |
| Identity Domain | 8081 | PostgreSQL | Spring Data JPA, gRPC |
| Product Domain | 8090 | MongoDB | MongoDB, MinIO, Kafka |
| Order Domain | 8083 | PostgreSQL | Axon Framework, Kafka, gRPC |
| Payment Domain | 8082 | PostgreSQL | Axon Framework, gRPC |
| Flash Sale Service | 8085 | PostgreSQL (R2DBC) | WebFlux, Redis, Kafka |
| Search Service | 8086 | Elasticsearch | Spring Data Elasticsearch, Kafka |
| Notification Service | 8087 | MongoDB | WebFlux, MongoDB Reactive, Redis |
| Worker Service | 8089 | PostgreSQL | Quartz, ShedLock |
| Cart Service | 8089 | Redis | Redis, gRPC |

---

## Key Technologies Summary

### Framework Versions
- **Java**: 25
- **Spring Boot**: 4.0.4
- **Spring Cloud**: 2025.1.1
- **Axon Framework**: 4.13.0
- **gRPC**: 1.70.0
- **Protobuf**: 4.33.1
- **Netty**: 4.1.112.Final

### Infrastructure
- **PostgreSQL**: 15-alpine
- **MongoDB**: 6.0
- **Redis**: alpine
- **Elasticsearch**: 8.10.2
- **Kafka**: 7.4.0 (with Zookeeper)
- **MinIO**: latest
- **Axon Server**: latest

### Libraries
- Spring Data JPA
- Spring Data MongoDB (Reactive & Sync)
- Spring Data Elasticsearch
- Spring Data Redis (Reactive & Sync)
- Spring Kafka
- Spring WebFlux
- Spring Cloud Gateway
- Lombok
- MinIO Java SDK
- gRPC Spring Boot Starter
- Quartz Scheduler
- ShedLock

---

Generated: 2026-04-12

