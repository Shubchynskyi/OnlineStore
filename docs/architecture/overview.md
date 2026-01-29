# 🏗️ Architecture Map

## High-Level Architecture

```mermaid
graph TB
    subgraph "👥 Clients"
        WebStore["🌐 Web Store<br/>(Next.js 15)"]
        MobileClient["📱 Mobile App<br/>(React Native)"]
        TgClient["🤖 Telegram Bot<br/>(AI Chat)"]
    end

    subgraph "👔 Managers/Admins"
        AdminPanel["💼 Admin Panel<br/>(Angular 19)"]
        TgManager["📢 Telegram<br/>(Notifications)"]
    end

    subgraph "🚪 API Gateway Layer"
        Gateway["Spring Cloud Gateway<br/>:8080"]
        Nginx["Nginx<br/>(Load Balancer/SSL)"]
    end

    subgraph "🔐 Auth"
        Keycloak["Keycloak 26"]
    end

    subgraph "⚙️ Backend (Java 25 / Spring Boot 4)"
        ModCatalog["📦 Catalog"]
        ModOrders["🛒 Orders"]
        ModUsers["👤 Users"]
        ModPayments["💳 Payments<br/>(Circuit Breaker)"]
        ModShipping["🚚 Shipping<br/>(Circuit Breaker)"]
        ModNotifications["🔔 Notifications"]
    end

    subgraph "🤖 Telegram Bot Service"
        BotCore["Bot Core"]
        AIChat["AI Engine"]
    end

    subgraph "📊 Data Layer"
        PostgresPrimary[("PostgreSQL 17<br/>Primary - Write")]
        PostgresReplica[("PostgreSQL 17<br/>Replica - Read")]
        Redis[("Redis 7.4<br/>Cache + Sessions")]
        Elastic[("Elasticsearch 8<br/>Search")]
    end

    subgraph "📨 Messaging"
        RabbitMQ[/"RabbitMQ 3.13<br/>Event Bus + DLQ"\]
    end

    subgraph "📊 Monitoring Stack"
        Prometheus["Prometheus<br/>(Metrics)"]
        Grafana["Grafana<br/>(Dashboards)"]
        Jaeger["Jaeger<br/>(Tracing)"]
        Loki["Loki<br/>(Logs)"]
    end

    WebStore --> Nginx
    MobileClient --> Nginx
    AdminPanel --> Nginx
    Nginx --> Gateway

    TgClient --> Gateway
    BotCore --> Gateway

    Gateway --> Keycloak
    Gateway --> ModCatalog
    Gateway --> ModOrders
    Gateway --> ModUsers

    ModCatalog --> PostgresPrimary
    ModCatalog --> PostgresReplica
    ModCatalog --> Redis
    ModOrders --> PostgresPrimary
    ModOrders --> RabbitMQ
    ModPayments --> RabbitMQ
    ModShipping --> RabbitMQ
    ModNotifications --> RabbitMQ

    BotCore --> AIChat
    RabbitMQ -.-> TgManager

    PostgresPrimary -.->|Streaming<br/>Replication| PostgresReplica
    RabbitMQ -.-> Elastic
    RabbitMQ -.-> ModNotifications

    Gateway -.->|Metrics| Prometheus
    ModCatalog -.->|Metrics| Prometheus
    ModOrders -.->|Metrics| Prometheus
    Prometheus --> Grafana
    Gateway -.->|Traces| Jaeger
    ModCatalog -.->|Traces| Jaeger
    Gateway -.->|Logs| Loki
```

---

## 🚪 API Gateway Architecture

### Spring Cloud Gateway

**Role**: Single entry point for all clients with centralized routing, authentication, and monitoring.

```mermaid
sequenceDiagram
    participant Client as Client (Web/Mobile/Bot)
    participant Gateway as Spring Cloud Gateway
    participant Keycloak as Keycloak
    participant Backend as Backend Service
    participant Redis as Redis

    Client->>Gateway: POST /api/v1/orders<br/>Authorization: Bearer token

    Gateway->>Redis: Check rate limit
    Redis-->>Gateway: OK (within limits)

    Gateway->>Keycloak: Validate JWT token
    Keycloak-->>Gateway: Token valid + roles

    Gateway->>Backend: Forward request<br/>+ trace-id header
    Backend-->>Gateway: Response + metrics

    Gateway->>Gateway: Log request (OpenTelemetry)
    Gateway-->>Client: Response
```

**Features:**
- ✅ **JWT Validation** via Keycloak
- ✅ **Rate Limiting** (Redis-based)
- ✅ **Circuit Breaker** for backend services
- ✅ **Request/Response Transformation**
- ✅ **Load Balancing** between instances
- ✅ **Distributed Tracing** (OpenTelemetry)
- ✅ **Centralized Logging**

---

## 💳 Plugin Architecture: Payments & Shipping

```mermaid
classDiagram
    class PaymentProvider {
        <<interface>>
        +getProviderCode() String
        +getSupportedCountries() Set~String~
        +createPayment(request) PaymentResult
        +confirmPayment(paymentId) PaymentResult
        +refund(paymentId, amount) RefundResult
        +verifyWebhook(payload, signature) boolean
    }

    class ShippingProvider {
        <<interface>>
        +getProviderCode() String
        +getSupportedCountries() Set~String~
        +calculateRates(request) List~ShippingRate~
        +createShipment(order, rate) Shipment
        +track(trackingNumber) TrackingInfo
        +cancelShipment(shipmentId) void
    }

    PaymentProvider <|.. CardPaymentProvider
    PaymentProvider <|.. PayPalPaymentProvider
    PaymentProvider <|.. BankTransferPaymentProvider
    PaymentProvider <|.. CryptoPaymentProvider

    ShippingProvider <|.. DhlProvider
    ShippingProvider <|.. DpdProvider
    ShippingProvider <|.. GlsProvider
    ShippingProvider <|.. NovaPoshtaProvider
    ShippingProvider <|.. FedExProvider

    class ProviderRegistry {
        -providers Map
        +getProvider(code) Provider
        +getProviderForCountry(country) Provider
        +getEnabledProviders() List
    }

    ProviderRegistry --> PaymentProvider
    ProviderRegistry --> ShippingProvider
```

Payment flow details: [payments-integration.md](payments-integration.md).

---

## 📊 Monitoring & Observability Stack

### Components
Note: Alertmanager is part of the production stack and is not included in the local docker-compose by default.

```mermaid
graph LR
    subgraph "Application Layer"
        Gateway[API Gateway]
        Backend[Backend Services]
        Bot[Telegram Bot]
    end

    subgraph "Monitoring Stack"
        Prom[Prometheus<br/>Metrics Collection]
        Grafana[Grafana<br/>Visualization]
        Jaeger[Jaeger<br/>Distributed Tracing]
        Loki[Loki<br/>Log Aggregation]
        Alert[Alertmanager<br/>Alerts]
    end

    subgraph "Notifications"
        TG[Telegram]
        Email[Email]
    end

    Gateway -->|Metrics| Prom
    Backend -->|Metrics| Prom
    Bot -->|Metrics| Prom

    Gateway -->|Traces| Jaeger
    Backend -->|Traces| Jaeger

    Gateway -->|Logs| Loki
    Backend -->|Logs| Loki

    Prom --> Grafana
    Prom --> Alert
    Jaeger --> Grafana
    Loki --> Grafana

    Alert --> TG
    Alert --> Email
```

### Metrics (Prometheus + Grafana)

**System Metrics:**
- CPU, Memory, Disk usage
- Network I/O
- JVM heap, garbage collection

**Business Metrics:**
- Orders per minute
- Revenue per hour
- Payment success rate
- API response times (p50, p95, p99)
- Error rates

**Dashboards:**
1. **System Overview** - CPU, RAM, Disk
2. **API Performance** - Request rate, latency, errors
3. **Business Metrics** - Orders, revenue, conversion
4. **Database** - Query performance, connection pool
5. **Cache** - Hit rate, memory usage

### Distributed Tracing (OpenTelemetry + Jaeger)

```
TraceID: abc123...

Span: API Gateway [POST /api/v1/orders] - 150ms
  ├─ Span: Auth validation - 10ms
  ├─ Span: Rate limit check - 2ms
  └─ Span: Backend [OrderService.createOrder] - 130ms
      ├─ Span: DB [INSERT orders] - 15ms
      ├─ Span: DB [INSERT order_items] - 20ms
      ├─ Span: RabbitMQ [Publish event] - 5ms
      └─ Span: Redis [Cache update] - 3ms
```

### Log Aggregation (Loki)

**Structured Logging:**
```json
{
  "timestamp": "2026-01-23T12:34:56Z",
  "level": "INFO",
  "service": "backend-api",
  "traceId": "abc123...",
  "userId": "user_456",
  "message": "Order created",
  "orderId": "ord_789"
}
```

**Log Queries:**
```logql
# Errors in last hour
{service="backend-api"} |= "ERROR" | json

# Slow queries
{service="backend-api"} | json | duration > 1s

# Orders by user
{service="backend-api"} |= "Order created" | json | userId="user_456"
```

### Alerting (Alertmanager)

**Critical Alerts (Telegram + PagerDuty):**
- Service down (5 minutes)
- Error rate > 5%
- Response time p95 > 2s
- Database connection pool exhausted
- Payment provider unavailable

**Warning Alerts (Telegram):**
- CPU > 80% for 10 minutes
- Disk usage > 85%
- Low stock for products
- Failed RabbitMQ message > 10

---

## 📊 Database Replication

```mermaid
sequenceDiagram
    participant Admin as Admin Panel
    participant API as Backend API
    participant Primary as PostgreSQL Primary
    participant RMQ as RabbitMQ
    participant Replica as PostgreSQL Replica
    participant Cache as Redis
    participant ES as Elasticsearch

    Admin->>API: PUT /products/{id}
    API->>Primary: UPDATE product
    Primary-->>API: OK
    API->>RMQ: ProductChangedEvent
    
    par Sync
        Primary->>Replica: WAL Streaming (~100ms)
    and Cache Invalidation
        RMQ->>Cache: DEL product:{id}
    and Search Update
        RMQ->>ES: Index product
    end
```

---

## 📁 Project Structure (Monorepo)

```
OnlineStore/
├── backend/                    # Java 25 Spring Boot 4
│   ├── common/
│   ├── catalog-module/
│   ├── orders-module/
│   ├── payments-module/        # Plugin providers
│   ├── shipping-module/        # Plugin providers
│   └── application/            # Main app
├── telegram-bot/               # Separate Spring Boot app
├── store-frontend/             # Next.js 15
├── admin-panel/                # Angular 19
├── mobile-app/                 # React Native
├── infrastructure/
│   ├── docker/
│   └── k8s/
└── docs/stages/                # Detailed plans
```

**Run:**
- Backend: single JAR (`java -jar backend.jar`)
- Frontend: separate dev servers / static builds
- Telegram Bot: separate JAR
