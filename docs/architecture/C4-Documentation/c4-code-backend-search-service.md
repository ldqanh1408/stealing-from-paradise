# C4 Code Level: Search Service

## Overview

- **Name**: Search Service
- **Description**: Full-text search service using Elasticsearch, consuming Kafka events to index products and provide search capabilities. Follows a consumer-only Kafka pattern (no producers).
- **Location**: `D:\dev\stealing-from-paradise\backend\search-service\`
- **Language**: Java 25 + Spring Boot 4.0.4
- **Purpose**: Full-text product search with Elasticsearch indexing, synchronized from product-service via Kafka events. Provides multi-field search (name, description, tags) with category and seller filtering.

## Code Elements

### Application Entry Point

#### `SearchServiceApplication`

- Description: Spring Boot application entry point. Enables service discovery via Eureka and Elasticsearch repository scanning.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\SearchServiceApplication.java`
- Annotations:
  - `@SpringBootApplication(scanBasePackages = {"com.flashsale"})` -- scans all `com.flashsale` packages including common-lib
  - `@EnableDiscoveryClient` -- registers with Eureka for service discovery
  - `@EnableElasticsearchRepositories(basePackages = "com.flashsale.searchservice.domain.repository")` -- enables Spring Data Elasticsearch repositories
- Methods:
  - `main(String[] args): void` -- launches Spring Boot application
- Dependencies: Spring Boot, Spring Cloud Netflix Eureka Client, Spring Data Elasticsearch

---

### Configuration Classes

#### `ElasticsearchConfig`

- Description: Elasticsearch index initialization. On startup, creates the `products` index with mapping derived from `SearchProduct` annotations if it does not already exist. Gracefully handles Elasticsearch unavailability at startup (logs warning, does not block service start).
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\config\ElasticsearchConfig.java`
- Annotations:
  - `@Configuration`
  - `@Slf4j` (Lombok)
  - `@RequiredArgsConstructor` (Lombok)
- Beans:
  - `initializeElasticsearchIndex(ElasticsearchOperations): CommandLineRunner` -- creates `products` index and applies mapping at startup
    - Parameters:
      - `elasticsearchOperations: ElasticsearchOperations` -- Spring Data Elasticsearch template
    - Behavior:
      - Checks if `products` index exists via `IndexOperations`
      - If not found, creates index and applies `SearchProduct` mapping
      - On exception, logs warning and continues (non-blocking)
- Dependencies:
  - Internal: `SearchProduct` (domain model)
  - External: `ElasticsearchOperations` (Spring Data Elasticsearch)

---

#### `KafkaConfig`

- Description: Kafka consumer configuration for the search service. Configures a batch-acknowledgment consumer with 3 concurrent listeners, earliest offset reset, and manual commit (auto-commit disabled). Missing topics are non-fatal.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\config\KafkaConfig.java`
- Annotations:
  - `@Configuration`
  - `@EnableKafka`
- Fields:
  - `bootstrapServers: String` -- injected from `spring.kafka.bootstrap-servers`
  - `groupId: String` -- injected from `spring.kafka.consumer.group-id` (defaults to `${spring.application.name}-group`)
- Beans:
  - `consumerFactory(): ConsumerFactory<String, String>` -- creates `DefaultKafkaConsumerFactory` with:
    - Key/value deserializer: `StringDeserializer`
    - `AUTO_OFFSET_RESET_CONFIG`: earliest
    - `ENABLE_AUTO_COMMIT_CONFIG`: false
    - `MAX_POLL_RECORDS_CONFIG`: 100
  - `kafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String>` -- creates factory with:
    - Concurrency: 3
    - Ack mode: BATCH
    - Missing topics fatal: false
- Dependencies:
  - External: Apache Kafka (org.apache.kafka.clients.consumer), Spring Kafka

---

#### `SecurityConfig`

- Description: Security configuration for the search service. Permits all requests but registers the `JwtTokenDecoderFilter` from common-lib to decode `X-User-*` headers (set by the API gateway) into the Spring Security context. Stateless session management. CSRF disabled.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\config\SecurityConfig.java`
- Annotations:
  - `@Configuration`
  - `@EnableWebSecurity`
- Field:
  - `jwtTokenDecoderFilter: JwtTokenDecoderFilter` -- injected from common-lib
- Beans:
  - `jwtTokenDecoderFilterRegistration(JwtTokenDecoderFilter): FilterRegistrationBean<JwtTokenDecoderFilter>` -- disables the default servlet registration for the filter (managed by `SecurityFilterChain` instead)
  - `securityFilterChain(HttpSecurity): SecurityFilterChain` -- configures HTTP security:
    - CSRF disabled
    - Headers: frame-options deny, referrer-policy strict-origin-when-cross-origin
    - Session management: stateless
    - HTTP basic, form login, anonymous: all disabled
    - All requests: `permitAll()`
    - Filter: `JwtTokenDecoderFilter` registered before `UsernamePasswordAuthenticationFilter`
- Dependencies:
  - Internal (common-lib): `JwtTokenDecoderFilter`
  - External: Spring Security

---

### Domain Model

#### `SearchProduct`

- Description: Elasticsearch document representing a product indexed for full-text search. Denormalized from product-service data, synchronized via Kafka events. Uses Spring Data Elasticsearch annotations for index mapping.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\domain\model\SearchProduct.java`
- Annotations:
  - `@Document(indexName = "products", versionType = Document.VersionType.EXTERNAL, createIndex = false)` -- maps to `products` index; index creation is handled by `ElasticsearchConfig`
  - `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` (Lombok)
- Fields:

| Field | Type | Elasticsearch Type | Description |
|---|---|---|---|
| `id` | `String` | `_id` | MongoDB ObjectId as document ID |
| `name` | `String` | `Keyword` | Product name |
| `description` | `String` | `Text` (standard analyzer) + `Keyword` sub-field | Product description with multi-field mapping for both full-text and exact match |
| `sellerId` | `Long` | `Long` | Seller identifier |
| `sellerName` | `String` | `Keyword` | Denormalized seller display name |
| `categoryId` | `String` | `Keyword` | Category reference (FK to categories collection) |
| `categoryName` | `String` | `Keyword` | Denormalized category name |
| `priceMin` | `Double` | `Double` | Minimum product variant price |
| `priceMax` | `Double` | `Double` | Maximum product variant price |
| `stockAvailable` | `Integer` | `Integer` | Available stock count |
| `isFlash` | `Boolean` | `Boolean` | Whether the product is in a flash sale |
| `status` | `String` | `Keyword` | Product status: PENDING, APPROVED, REJECTED |
| `images` | `List<String>` | `Keyword` | Product image URLs |
| `attributes` | `List<Map<String, Object>>` | `Nested` | Product variant attributes (nested object) |
| `tags` | `List<String>` | `Keyword` | Search tags to boost relevance |
| `createdAt` | `LocalDateTime` | `Date` | Creation timestamp |
| `updatedAt` | `LocalDateTime` | `Date` | Last update timestamp |

- Dependencies:
  - External: Spring Data Elasticsearch annotations, `LocalDateTime` (java.time)

---

### Repository

#### `SearchProductRepository`

- Description: Spring Data Elasticsearch repository interface for `SearchProduct` documents. Provides derived query methods and custom `@Query` annotations for full-text multi-match search with field boosting and combined boolean queries.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\domain\repository\SearchProductRepository.java`
- Interface: `ElasticsearchRepository<SearchProduct, String>`
- Methods:
  - `searchByKeyword(String keyword): List<SearchProduct>`
    - Query: `{"multi_match": {"query": "?0", "fields": ["name", "description^2", "tags^1.5"]}}`
    - Description: Full-text search across `name`, `description` (boosted 2x), and `tags` (boosted 1.5x)
  - `findByCategoryId(String categoryId): List<SearchProduct>`
    - Description: Filter products by exact category match
  - `findBySellerId(Long sellerId): List<SearchProduct>`
    - Description: Filter products by seller
  - `findByStatus(String status): List<SearchProduct>`
    - Description: Filter products by status (e.g., APPROVED)
  - `findByIsFlashTrue(): List<SearchProduct>`
    - Description: Find all flash sale products
  - `searchByCategoryAndKeyword(String keyword, String categoryId): List<SearchProduct>`
    - Query: `{"bool": {"must": [{"multi_match": {"query": "?0", "fields": ["name", "description"]}}], "filter": [{"term": {"category_id": "?1"}}, {"term": {"status": "APPROVED"}}]}}`
    - Description: Combined search with keyword and category filter, restricted to APPROVED products only
- Dependencies:
  - Internal: `SearchProduct`
  - External: Spring Data Elasticsearch `ElasticsearchRepository`, `@Query`

---

### Service Layer

#### `SearchService`

- Description: Core service for product search. Currently has a Kafka consumer listener for `product.approved` events (indexing logic is TODO) and a stub search method. Marked as `@Service` for dependency injection.
- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\java\com\flashsale\searchservice\service\SearchService.java`
- Annotations:
  - `@Service`
  - `@RequiredArgsConstructor` (Lombok)
  - `@Slf4j` (Lombok)
- Fields:
  - `objectMapper: ObjectMapper` -- Jackson JSON mapper for deserializing Kafka messages
- Methods:
  - `onProductApproved(String message): void`
    - Description: Kafka listener for `product.approved` topic. Deserializes the JSON message into `ProductApprovedPayload` and logs the product ID. Indexing to Elasticsearch is a TODO placeholder.
    - Annotations: `@KafkaListener(topics = KafkaTopics.PRODUCT_APPROVED, groupId = "search-service-group")`
    - Parameters:
      - `message: String` -- raw JSON payload from Kafka
    - Error handling: Catches all exceptions, logs error message and stack trace
  - `search(String query, int page, int size): void`
    - Description: Stub method for querying Elasticsearch. Currently only logs the query string. Implementation is a TODO placeholder.
    - Parameters:
      - `query: String` -- search query text
      - `page: int` -- page number for pagination
      - `size: int` -- page size for pagination
- Dependencies:
  - Internal (common-lib): `KafkaTopics.PRODUCT_APPROVED`, `ProductApprovedPayload`
  - Internal: none yet (TODO: `SearchProductRepository`)
  - External: Jackson `ObjectMapper`, Spring Kafka `@KafkaListener`

---

### Resources / Configuration

#### `application.yml`

- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\resources\application.yml`
- Key settings:
  - Port: `8091`
  - Application name: `search-service`
  - Virtual threads enabled: `true`
  - Elasticsearch URIs: `${ELASTIC_URI:http://localhost:9200}`
  - Elasticsearch repository type: `none` (no auto-index creation)
  - Kafka bootstrap servers: `${KAFKA_SERVER:localhost:9092}`
  - Kafka consumer group ID: `${spring.application.name}-group`
  - Kafka consumer: auto-offset-reset=earliest, enable-auto-commit=false
  - Eureka: enabled, registers with `http://localhost:8761/eureka/`

#### `application-prod.yml`

- Location: `D:\dev\stealing-from-paradise\backend\search-service\src\main\resources\application-prod.yml`
- Key settings:
  - Log levels: root=INFO, com.flashsale=INFO, elasticsearch/kafka packages=WARN
  - Dev data: disabled

---

## Dependencies

### Internal Dependencies

| Dependency | Artifact | Usage |
|---|---|---|
| common-lib | `com.flashsale:common-lib:0.0.1-SNAPSHOT` | `KafkaTopics`, `ProductApprovedPayload`, `JwtTokenDecoderFilter` |

### External Dependencies

| Dependency | Version / Type | Usage |
|---|---|---|
| Spring Boot Starter Web | Spring Boot 4.0.4 | REST API support |
| Spring Boot Starter Data Elasticsearch | Spring Boot 4.0.4 | Elasticsearch client, repositories, document mapping |
| Spring Kafka | Spring Kafka | Kafka consumer for event-driven indexing |
| Spring Cloud Starter Netflix Eureka Client | Spring Cloud | Service registration and discovery |
| Lombok | provided | `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor` |
| Jackson | bundled with Spring Boot | JSON deserialization of Kafka payloads |
| Elasticsearch | 8.x (compatible) | Full-text search engine |

### Runtime Dependencies

| Service / System | Purpose |
|---|---|
| Elasticsearch (port 9200) | Index storage and full-text search |
| Kafka (port 9092) | Event ingestion for product indexing |
| Eureka (port 8761) | Service registration and discovery |

---

## Relationships

### Code Element Diagram

The search service follows a layered architecture with a consumer-driven event flow. The diagram below shows all code elements and their dependencies.

```mermaid
---
title: Code Diagram for Search Service Component
---
classDiagram
    namespace SearchService {
        class SearchServiceApplication {
            <<Application>>
            +main(String[] args) void
        }

        class ElasticsearchConfig {
            <<Configuration>>
            +initializeElasticsearchIndex(ElasticsearchOperations) CommandLineRunner
        }

        class KafkaConfig {
            <<Configuration>>
            +consumerFactory() ConsumerFactory~String, String~
            +kafkaListenerContainerFactory() ConcurrentKafkaListenerContainerFactory~String, String~
        }

        class SecurityConfig {
            <<Configuration>>
            +jwtTokenDecoderFilterRegistration(JwtTokenDecoderFilter) FilterRegistrationBean
            +securityFilterChain(HttpSecurity) SecurityFilterChain
        }

        class SearchProduct {
            <<Document>>
            +String id
            +String name
            +String description
            +Long sellerId
            +String sellerName
            +String categoryId
            +String categoryName
            +Double priceMin
            +Double priceMax
            +Integer stockAvailable
            +Boolean isFlash
            +String status
            +List~String~ images
            +List~Map~ attributes
            +List~String~ tags
            +LocalDateTime createdAt
            +LocalDateTime updatedAt
        }

        class SearchProductRepository {
            <<interface>> <<ElasticsearchRepository>>
            +searchByKeyword(String) List~SearchProduct~
            +findByCategoryId(String) List~SearchProduct~
            +findBySellerId(Long) List~SearchProduct~
            +findByStatus(String) List~SearchProduct~
            +findByIsFlashTrue() List~SearchProduct~
            +searchByCategoryAndKeyword(String, String) List~SearchProduct~
        }

        class SearchService {
            <<Service>>
            -ObjectMapper objectMapper
            +onProductApproved(String) void
            +search(String, int, int) void
        }
    }

    namespace common_lib {
        class KafkaTopics {
            +PRODUCT_APPROVED
            +PRODUCT_CREATED
            +PRODUCT_UPDATED
            +PRODUCT_DELETED
        }

        class ProductApprovedPayload {
            +String productId
            +String sellerId
            +String productName
            +String categoryId
        }

        class JwtTokenDecoderFilter {
            <<Filter>>
        }
    }

    SearchServiceApplication --> SearchService : scans package
    SearchServiceApplication --> ElasticsearchConfig : configures
    SearchServiceApplication --> KafkaConfig : configures
    SearchServiceApplication --> SecurityConfig : configures

    ElasticsearchConfig --> SearchProduct : creates index from
    ElasticsearchConfig --> ElasticsearchOperations : uses

    KafkaConfig --> ConsumerConfig : creates
    KafkaConfig --> DefaultKafkaConsumerFactory : creates

    SecurityConfig --> JwtTokenDecoderFilter : registers before
    SecurityConfig --> HttpSecurity : configures

    SearchProductRepository --> SearchProduct : manages

    SearchService --> SearchProductRepository : will use (TODO)
    SearchService --> KafkaTopics : listens on PRODUCT_APPROVED
    SearchService --> ProductApprovedPayload : deserializes
    SearchService --> ObjectMapper : uses
```

### Data Flow Diagram

The data flow from Kafka event ingestion to Elasticsearch indexing and query serving.

```mermaid
---
title: Data Flow for Search Service
---
flowchart LR
    subgraph Kafka
        A[product.approved topic]
    end

    subgraph SearchService
        direction TB
        B[SearchService.onProductApproved]
        C[SearchService.search]
        D[SearchProductRepository]
        E[(Elasticsearch<br/>products index)]
    end

    subgraph External
        F[Product Service]
        G[API Gateway / Client]
    end

    F -->|publishes| A
    A -->|consumes| B
    B -->|indexes (TODO)| E
    G -->|HTTP search request| C
    C -->|queries| D
    D -->|reads/writes| E
```

---

## Notes

- **Consumer-only pattern**: The search service only consumes Kafka events; it does not produce any events. This aligns with its role as a read-model / projection service.
- **TODO placeholders**: Both the Elasticsearch indexing in `onProductApproved` and the search query execution in `search()` have placeholder implementations. The repository layer (`SearchProductRepository`) is fully defined with Elasticsearch query annotations, but the service layer has not yet wired it in.
- **Denormalized data model**: `SearchProduct` stores denormalized fields (`sellerName`, `categoryName`) to avoid join queries at search time. These are kept in sync via Kafka events from the source services.
- **Index lifecycle**: The `ElasticsearchConfig` creates the index on first startup. The `createIndex = false` attribute on `@Document` prevents Spring Data from auto-creating the index (the config class handles this explicitly).
- **Security model**: All HTTP endpoints are `permitAll()` because search is a public-facing capability. The `JwtTokenDecoderFilter` is still registered to populate the security context for any authenticated requests (e.g., personalized search results in the future).
- **Virtual threads**: Enabled via `spring.threads.virtual.enabled: true`. The service uses blocking I/O (Elasticsearch and Kafka clients), which benefits from virtual thread offloading in Spring MVC.
