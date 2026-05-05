# C4 Code Level: Discovery Service (Eureka)

## Overview

- **Name**: Discovery Service (Eureka Server)
- **Description**: Netflix Eureka service registry for microservice discovery. All services register here and discover each other via the Eureka server.
- **Location**: `D:\dev\stealing-from-paradise\backend\discovery-service`
- **Language**: Java 25 + Spring Boot 4.0.4
- **Purpose**: Provides service registration and discovery for the FlashSale microservice ecosystem. Every backend service (identity, product, order, payment, notification, flashsale, search) registers with this Eureka server upon startup and uses it to locate peers.

## Code Elements

### Annotation-Based Configuration (No Explicit Beans)

The discovery-service is a minimal Spring Boot application. All configuration is achieved through annotations and `application.yml` properties. There are no custom service classes, repositories, controllers, or data transfer objects.

#### Main Application Class

- `DiscoveryServiceApplication` (line 9)
  - **Description**: Entry point for the Eureka Server. The `@EnableEurekaServer` annotation activates the Netflix Eureka server auto-configuration, which registers the built-in Eureka REST endpoints (`/eureka/apps`, `/eureka/v2/apps`, etc.) for service registration, heartbeats, and discovery queries.
  - **Location**: `D:\dev\stealing-from-paradise\backend\discovery-service\src\main\java\com\flashsale\discoveryservice\DiscoveryServiceApplication.java`
  - **Method**:
    - `main(String[] args): void` -- Bootstraps the Spring context via `SpringApplication.run(DiscoveryServiceApplication.class, args)`.
  - **Annotations**: `@SpringBootApplication`, `@EnableEurekaServer`

#### Test Class

- `DiscoveryServiceApplicationTests` (line 7)
  - **Description**: Verifies that the Spring application context loads successfully. A smoke test that fails if the Eureka server auto-configuration encounters errors (e.g., port conflicts, missing dependencies).
  - **Location**: `D:\dev\stealing-from-paradise\backend\discovery-service\src\test\java\com\flashsale\discoveryservice\DiscoveryServiceApplicationTests.java`
  - **Method**:
    - `contextLoads(): void` -- Empty test method; the `@SpringBootTest` annotation itself validates context loading.
  - **Annotations**: `@SpringBootTest`

### Configuration (`application.yml`)

**Location**: `D:\dev\stealing-from-paradise\backend\discovery-service\src\main\resources\application.yml`

Key properties:

| Property | Value | Description |
|---|---|---|
| `server.port` | `${SERVER_PORT:8761}` | Listen port (default 8761, overridable via env) |
| `spring.application.name` | `discovery-service` | Service name for Eureka dashboard identification |
| `eureka.instance.hostname` | `discovery-service` | Container hostname |
| `eureka.instance.prefer-ip-address` | `true` | Register with IP rather than hostname |
| `eureka.client.register-with-eureka` | `false` | This server does not register with itself |
| `eureka.client.fetch-registry` | `false` | This server does not fetch the registry from itself |
| `eureka.client.service-url.defaultZone` | `http://discovery-service:8761/eureka/` | Peer URL (self-referencing in single-node mode) |
| `eureka.server.peer-node-read-timeout-ms` | `30000` | Peer communication read timeout |
| `eureka.server.peer-node-connect-timeout-ms` | `10000` | Peer communication connect timeout |

### Configuration (`application-prod.yml`)

**Location**: `D:\dev\stealing-from-paradise\backend\discovery-service\src\main\resources\application-prod.yml`

| Property | Value |
|---|---|
| `logging.level.root` | `INFO` |
| `logging.level.com.netflix.eureka` | `INFO` |

## Dependencies

### Internal Dependencies

None. The discovery-service has no dependencies on other FlashSale modules. It is a root-level infrastructure service.

### External Dependencies (from `pom.xml`)

| Artifact | Group ID | Purpose |
|---|---|---|
| `spring-cloud-starter-netflix-eureka-server` | `org.springframework.cloud` | Netflix Eureka server runtime -- provides `/eureka/` REST API, registry data structures, peer replication, self-preservation mode, and the Eureka dashboard at `/`. |
| `spring-boot-starter-actuator` | `org.springframework.boot` | Health check and monitoring endpoints (`/actuator/health`, `/actuator/info`) used by orchestration and monitoring tooling. |

### Build Plugin

| Plugin | Purpose |
|---|---|
| `spring-boot-maven-plugin` | Packages the application as a fat JAR with embedded Tomcat. |

## Relationships

The discovery-service exposes the Eureka REST API consumed by all other FlashSale microservices. The diagram below shows the code-level structure of the service itself.

```mermaid
---
title: Code Diagram for Discovery Service (Eureka Server)
---
classDiagram
    namespace DiscoveryService {
        class DiscoveryServiceApplication {
            <<Spring Boot Application>>
            +main(String[] args) void
        }
        class DiscoveryServiceApplicationTests {
            <<Test>>
            +contextLoads() void
        }
        class application_yml {
            <<Configuration>>
            +server.port = ${SERVER_PORT:8761}
            +eureka.client.register-with-eureka = false
            +eureka.client.fetch-registry = false
        }
        class application_prod_yml {
            <<Configuration>>
            +logging.level.root = INFO
            +logging.level.com.netflix.eureka = INFO
        }
    }

    note for DiscoveryServiceApplication "Annotated with @SpringBootApplication\nand @EnableEurekaServer.\nActivates Netflix Eureka\nauto-configuration."

    DiscoveryServiceApplication --> application_yml : reads
    DiscoveryServiceApplication --> application_prod_yml : reads (prod profile)
    DiscoveryServiceApplicationTests --> DiscoveryServiceApplication : verifies context loads
```

## System-Level Endpoints (Exposed by Eureka Server)

The Eureka server auto-configuration registers these REST endpoints (not defined in application code -- provided by the Netflix library):

| Endpoint | Method | Description |
|---|---|---|
| `/eureka/apps` | GET | Get all registered applications |
| `/eureka/apps/{appName}` | GET | Get a specific application and its instances |
| `/eureka/apps/{appName}/{instanceId}` | GET | Get a specific instance |
| `/eureka/apps/{appName}` | POST | Register a new instance |
| `/eureka/apps/{appName}/{instanceId}` | PUT | Send heartbeat/renew lease |
| `/eureka/apps/{appName}/{instanceId}` | DELETE | Deregister an instance |
| `/eureka/v2/apps` | * | XML/JSON versioned API mirror |
| `/actuator/health` | GET | Health check endpoint (from actuator) |
| `/actuator/info` | GET | Application info endpoint (from actuator) |

## Notes

- This is a **single-node Eureka server** configuration. For production high availability, a multi-node peer-aware setup would require additional `eureka.client.service-url.defaultZone` entries pointing to peer Eureka URLs and removing the `register-with-eureka: false` / `fetch-registry: false` flags.
- Self-preservation mode (enabled by default) prevents the server from evicting instances during network partitions. In development, setting `eureka.server.enable-self-preservation=false` is common but is not configured here.
- The default Eureka dashboard is accessible at `http://discovery-service:8761/` and shows all registered services, their status (UP/DOWN), and IP addresses.
- The service does **not** use `spring-cloud-starter-bootstrap`, so it relies on standard Spring property sources rather than `bootstrap.yml`.
