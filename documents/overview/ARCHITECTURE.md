## System Architecture
Service: platform

### Service Registry

| ID | Service | Port | Database | Pattern | Responsibility |
|----|---------|------|----------|---------|----------------|
| SVC-001 | api-gateway | 8080 | — | Spring Cloud Gateway | JWT RS256 validation, routing, rate limiting |
| SVC-002 | discovery-service | 8761 | — | Eureka | Service registry & health |
| SVC-003 | identity-service | 8081 | PostgreSQL | JPA | Auth, JWT, users, addresses |
| SVC-004 | payment-service | 8082 | PostgreSQL + Axon | CQRS/ES | Stripe Connect, multi-vendor splits |
| SVC-005 | order-service | 8083 | PostgreSQL + Axon | CQRS/ES + Saga | Checkout, order lifecycle, RTS |
| SVC-006 | flashsale-service | 8085 | PostgreSQL | CQRS/ES | Flash sale sessions, price promotion |
| SVC-007 | product-service | 8090 | PostgreSQL | Traditional | Catalog, variants, cart, images (MinIO) |
| SVC-008 | search-service | 8091 | Elasticsearch | Traditional | Full-text search, VN text analysis |
| SVC-009 | notification-service | 8092 | MongoDB | Traditional | SSE real-time notifications |
| SVC-010 | ai-chat-service | 8093 | MongoDB | Traditional | AI chat, tool calls, human-in-the-loop |
| SVC-011 | refund-service | 8094 | PostgreSQL | JPA | Admin refund approval, buyer requests, RTS automatic refunds |

### Infrastructure Map

| Component | Port | Used By | Purpose |
|-----------|------|---------|---------|
| PostgreSQL | 5432 | identity, payment, order, flashsale, product, refund | Primary relational store |
| MongoDB | 27017 | notification, ai-chat | Document store |
| Redis | 6379 | identity, api-gateway | Session cache, JWT blocklist |
| Elasticsearch | 9200 | search | Full-text product index |
| MinIO | 9000/9001 | product | Object storage (product images) |
| Kafka | 9092 | all services | Async event streaming (58 Kafka topics: 44 event + 14 request-reply) |
| Axon Server | 8024/8124 | payment, order, flashsale | Event store + command bus |

### Frontend Apps

| App | Port | Stack |
|-----|------|-------|
| Customer | 3000 | React 19 + Vite + TypeScript |
| Seller | 3001 | React 19 + Vite + TypeScript |
| Admin | 3002 | React 19 + Vite + TypeScript |

### Technology Stack

| Layer | Technologies |
|-------|-------------|
| Backend | Java 25 LTS, Spring Boot 4.0.4, Spring Cloud 2025.1.1, Axon Framework 4.13.0 |
| Databases | PostgreSQL 15.4, MongoDB 6.0, Redis 7.2, Elasticsearch 8.10 |
| Messaging | Kafka 7.4.0, Axon Server |
| Frontend | React 19, Vite 6.0, TypeScript, Tailwind CSS, Zustand, React Query |
| DevOps | Docker, Docker Compose, Nginx, Eureka |

### Key Features

- Multi-vendor marketplace with 3 roles (Customer, Seller, Admin)
- Flash sales with price promotion and session scheduling
- Stripe Connect for multi-vendor payments with automatic transfers
- Real-time SSE notifications
- Full-text search with Elasticsearch (Vietnamese text analysis)
- Return To Sender (RTS) refund workflow
- AI Chat Support (multi-turn, tool calls, human-in-the-loop)
- 15 scheduled cronjobs (1 implemented, 14 post-MVP)
- 58 Kafka topics (44 event + 14 request-reply)
- Axon CQRS/ES for Order, Payment, Flashsale services

---

## AI Chat Service -- Technical Architecture

> Source: `docs/services/ai-chat-service/01_technical_module.md`

### AI Orchestrator Layers

```
Frontend / Chat UI
        |  (JWT + message)
API Gateway
        |
AI Orchestrator (Spring AI)     <->  PageIndex (vector search)
        |  (JWT delegation)
Core Services (Order, Product, Account...)
```

| Layer | Role |
|-------|------|
| Layer 1 -- Frontend | Send message + JWT, render SSE stream, display Product Card / Order Card |
| Layer 2 -- AI Orchestrator | Validate JWT, rate limit, manage ChatClient, dispatch Tool calls |
| Layer 3 -- PageIndex | Vector search for products and system features |
| Layer 4 -- Core Services | Existing system, AI calls via existing APIs, no modification |
| Layer 5 -- Security | JWT validation, Rate limiting, Human-in-the-loop (Level 3) |

### Tool Risk Classification

| Level | Action Type | Requirement | Example Tools |
|-------|------------|-------------|---------------|
| Level 1 | Read general info | No special auth | `searchProducts`, `searchFaq` |
| Level 2 | Read personal data | Valid JWT required | `getOrderDetail`, `getUserProfile` |
| Level 3 | Modify/delete data | JWT + Human confirmation | `cancelOrder`, `deleteAccount` |

### PageIndex -- 1 Billion Product Pipeline

```
1 billion products
      |  ANN vector search (~50ms)
Top-100 candidates
      |  Business filter (in_stock, active, correct category)
Top-20 results
      |  AI rerank + intent match
3-5 products displayed in chat
```

### Results Per Intent

| Intent | Example Query | PageIndex top-K | Display | Batch "See More" |
|--------|---------------|-----------------|---------|-------------------|
| Vague | "Find me a shirt" | 50 | 3 (after clarify) | 3 |
| Specific | "White t-shirt M < 200k" | 20 | 3-5 | 5 |
| Compare | "A vs B which is better" | 10 | 2-3 | None |

> Cache buffer: Store 20 products in Redis TTL 10 min. "See More" pops from Redis, no PageIndex re-query.

### Spring AI Configuration

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.1
          max-tokens: 2048

pageindex:
  api-key: ${PAGEINDEX_API_KEY}
  index-id:
    product: ${PAGEINDEX_PRODUCT_INDEX}
    feature: ${PAGEINDEX_FEATURE_INDEX}
```

### System Prompt Template

```
You are [BOT NAME], virtual assistant of [COMPANY NAME].

## Core Rules
1. NEVER fabricate information. If unknown -> "I don't have information about this issue".
2. ALWAYS use Tools to look up instead of answering from general knowledge.
3. DO NOT perform data-modifying actions without confirmation.
4. If question out of scope -> politely decline.

## Style
- Short, friendly, professional

## User Context
- userId: {userId} | Name: {userName} | Time: {currentTime}
```

### Tool Definition Checklist

| Element | Required? |
|---------|-----------|
| When to use | Mandatory |
| When NOT to use | Mandatory |
| Example trigger phrases | Recommended |
| Confirmation warning | Mandatory for Level 3 |
| Parameter format | Recommended |

### Project Structure

```
com.yourcompany.ai
+-- config/
|   +-- SpringAiConfig.java          # ChatClient bean, model config
|   +-- PageIndexConfig.java         # PageIndex client bean
|   +-- SecurityConfig.java          # JWT filter, rate limit
+-- controller/
|   +-- ChatController.java          # POST /api/ai/chat (SSE)
|   +-- SessionController.java       # POST/DELETE /api/ai/sessions
|   +-- ConfirmController.java       # POST /api/ai/confirm
+-- service/
|   +-- ChatService.java             # Orchestration logic
|   +-- PageIndexService.java        # Vector search wrapper
|   +-- ConfirmationService.java     # Human-in-the-loop
+-- tools/
|   +-- ProductSearchTool.java       # @Tool risk_level=1
|   +-- OrderQueryTool.java          # @Tool risk_level=2
|   +-- SystemActionTool.java        # @Tool risk_level=3
+-- model/
    +-- ChatRequest.java
    +-- ChatResponse.java
```

### Redis Keys

| Key | TTL | Purpose |
|-----|-----|---------|
| `rate:{userId}` | 60s | Rate limit counter (20 req/min) |
| `tool:rate:{userId}` | 60s | Tool call rate limit (10/min) |
| `ctx:{sessionId}` | 30 min | Cache 20 recent messages, avoid DB query per request |
| `pending:{confirmId}` | 5 min | Fast lookup for confirm button (< 5ms) |
| `buf:{sessionId}` | 10 min | Buffer 20 products from PageIndex for "See More" |
| `tool:cache:{hash}` | 60s | Cache Level 1 tool results |

### Elasticsearch -- Product Search

**Index:** `skus` -- SKU-first with field collapsing by `product_id`

| Parameter | Value | Reason |
|-----------|-------|--------|
| `max_result_window` | 10,000 | ES hard limit |
| Page size | 40 products/page | Max 250 pages |
| `track_total_hits` | 10,000 | Count up to 10k then stop, show "10,000+ products" |
| Tiebreaker | `sort_id: asc` | Mandatory with all sort options for stable ordering |

### Vietnamese Text Analysis

| Problem | Solution |
|---------|----------|
| No-diacritic typing: "ao thun" | `asciifolding` filter with `preserve_original: true` |
| Spelling errors: "ao thunn" | `fuzziness: AUTO` in query |
| Synonyms | Synonym filter with file `synonyms/vi_product.txt` |
| Recommended plugin | `elasticsearch-plugin install analysis-icu` |

### Database: MongoDB Collection Schemas

**chat_sessions**
```json
{
  "_id": "ObjectId",
  "user_id": "Long",
  "status": "String (ACTIVE | CLOSED | EXPIRED)",
  "context_summary": "String",
  "created_at": "Date",
  "updated_at": "Date",
  "closed_at": "Date"
}
```

**chat_messages**
```json
{
  "_id": "ObjectId",
  "session_id": "ObjectId (ref: chat_sessions)",
  "role": "String (USER | ASSISTANT | TOOL_CALL | TOOL_RESULT)",
  "content": "String",
  "tool_name": "String (TOOL_CALL/TOOL_RESULT only)",
  "sequence_no": "Int (compound unique with session_id)",
  "tokens_used": "Int (ASSISTANT only)",
  "created_at": "Date"
}
```

**pending_confirmations**
```json
{
  "_id": "ObjectId",
  "session_id": "ObjectId (ref: chat_sessions)",
  "user_id": "Long",
  "tool_name": "String",
  "tool_arguments": "Object",
  "summary": "String",
  "status": "String (PENDING | CONFIRMED | REJECTED | EXPIRED)",
  "action": "String (CANCEL_ORDER | UPDATE_PROFILE | DELETE_ACCOUNT | CUSTOM)",
  "expires_at": "Date (TTL index: 5 min)",
  "created_at": "Date",
  "resolved_at": "Date"
}
```

**tool_call_logs**
```json
{
  "_id": "ObjectId",
  "session_id": "ObjectId (ref: chat_sessions)",
  "message_id": "ObjectId (ref: chat_messages)",
  "user_id": "Long",
  "tool_name": "String",
  "arguments": "Object",
  "result": "Object",
  "status": "String (SUCCESS | FAILED | BLOCKED | TIMEOUT)",
  "latency_ms": "Int",
  "error_code": "String",
  "error_message": "String",
  "created_at": "Date"
}
```

### Message Flow: 4 Records per Turn

```
#1  role=USER        -> "Where is order ORD-2024-00892?"
#2  role=TOOL_CALL   -> {"name":"getOrderDetail","args":{...}}
#3  role=TOOL_RESULT -> {"status":"SHIPPED","eta":"2026-05-05"}
#4  role=ASSISTANT   -> "Order is being delivered, estimated 05/05..."
```

`sequence_no` is mandatory -- timestamp not reliable (multiple records at same ms).

### SSE Streaming Events

| Event Type | When | Frontend Action |
|------------|------|-----------------|
| `delta` | LLM generates token | Append text to chat bubble |
| `tool_start` | AI begins tool call | Show "Fetching data..." |
| `tool_done` | Tool completed | Hide status indicator |
| `products` | Search results returned | Render Product Card grid |
| `order` | Order lookup result | Render Order Card with timeline |
| `confirmation_required` | Level 3 action pending | Render [Confirm] / [Cancel] buttons |
| `done` | Stream complete | Show final state, tokensUsed |
| `error` | Error during processing | Show error message, close stream |

### Rate Limiting

| Endpoint | Limit | Window | Redis Key |
|----------|-------|--------|-----------|
| POST /chat | 20 req/min/user | 60s | `rate:{userId}` |
| Tool calls | 10 req/min/user | 60s | `tool:rate:{userId}` |
| POST /confirm | 10 req/min/user | 60s | -- |
| All others | 60 req/min/user | 60s | -- |

Rate limit exceeded -> HTTP 429 + `X-RateLimit-Reset` header.
