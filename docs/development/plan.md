# 📋 Implementation Plan for Online Store

## 🎯 Project Overview

**Goal**: Create a scalable online store with an AI chatbot, flexible payment and delivery integration.

---

## MVP Scope (end of Stage 1)

**Included**:
- Catalog browsing (categories, product list, product details)
- Order creation and basic order lifecycle
- One payment provider in sandbox with webhook confirmation
- Keycloak-based authentication and RBAC
- Admin operations via backend API (no UI)
- Core observability: health, metrics, logs, basic traces

**Deferred**:
- Admin Panel UI, Telegram bot, mobile app
- Advanced search, recommendations, AI chat
- Full set of payment and shipping providers
- Full monitoring stack, alerting, and dashboards

---

## 🏗️ Project Structure (Monorepo)

```
OnlineStore/
├── api-gateway/                # Spring Cloud Gateway (Entry point)
│   ├── pom.xml
│   └── src/main/java/...
│
├── backend/                    # Java 25 Spring Boot 4 (Modular Monolith)
│   ├── pom.xml                 # Parent POM
│   ├── common/                 # Shared entities, DTOs, utils
│   ├── catalog-module/
│   ├── orders-module/
│   ├── users-module/
│   ├── payments-module/        # Plugin architecture for providers
│   ├── shipping-module/        # Plugin architecture for carriers
│   ├── notifications-module/
│   ├── search-module/
│   └── application/            # Main Spring Boot app, assembles all modules
│
├── telegram-bot/               # Separate Spring Boot app
│
├── store-frontend/             # Next.js 15 (React 19)
├── admin-panel/                # Angular 19
├── mobile-app/                 # React Native
│
├── infrastructure/
│   ├── docker/
│   │   ├── prometheus/         # Prometheus config
│   │   ├── grafana/            # Grafana dashboards
│   │   └── nginx/
│   └── k8s/
│
├── docs/                       # Documentation
│   ├── stages/                 # Detailed stage plans
│   └── architecture/decisions.md # ADR document
│
└── docker-compose.yml          # Development environment
```

### ❓ How to Run?

| Component | Development | Production |
|-----------|-------------|------------|
| **API Gateway** | `task gateway-run` → `:8080` | Separate Pod (Load Balanced) |
| **Backend (Monolith)** | `task backend-run` → `:8081` | Single JAR file → Kubernetes Pod(s) |
| **Telegram Bot** | `task bot-run` → `:8082` | Separate Pod |
| **Admin Panel** | `task admin-run` → `:4200` | Static build → Nginx |
| **Store Frontend** | `task store-run` → `:3000` | `npm run build` → Node.js/Nginx |
| **Mobile App** | `npx expo start` → device/emulator | App Store / Google Play |

**Modular monolith = one JAR, but the code is divided into modules.** All modules are compiled together and run as a single application. This provides:
- ✅ Ease of deployment (one artifact)
- ✅ ACID transactions between modules
- ✅ Easy to extract a module into a microservice later

---

## 🔧 Technology Stack (2025)

| Component | Technology | Version |
|-----------|------------|--------|
| **Language** | Java | 25 (LTS) |
| **Backend Framework** | Spring Boot | 4.0 |
| **API Gateway** | Spring Cloud Gateway | 4.2 |
| **Database** | PostgreSQL | 17 |
| **Cache** | Redis | 7.4 |
| **Search** | Elasticsearch | 8.x |
| **Messaging** | RabbitMQ | 3.13 |
| **Auth** | Keycloak | 26 |
| **Store Frontend** | Next.js | 15 (React 19, Server Components) |
| **Admin Panel** | Angular | 19 (Signals, Standalone) |
| **Mobile** | React Native | 0.76+ (New Architecture) |
| **Telegram Bot** | TelegramBots | 7.x |
| **AI** | OpenAI API / Claude API | Latest |
| **Circuit Breaker** | Resilience4j | 2.2 |
| **Tracing** | OpenTelemetry + Jaeger | Latest |
| **Monitoring** | Prometheus + Grafana | Latest |
| **Logs** | Loki | Latest |
| **Container** | Docker | 27.x |
| **Orchestration** | Kubernetes | 1.31 |

---

## 🚪 API Gateway (Spring Cloud Gateway)

### Why an API Gateway?

**Single entry point** for all clients with centralized processing:

```
┌─────────────────────────────────────────────────────────────────┐
│                    Clients (Web, Mobile, Bot)                   │
└───────────────────────────────┬─────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│             Spring Cloud Gateway (:8080)                         │
├─────────────────────────────────────────────────────────────────┤
│ ✅ JWT Validation (Keycloak)                                    │
│ ✅ Rate Limiting (Redis-based, 100 req/min per user)            │
│ ✅ Circuit Breaker (Resilience4j)                               │
│ ✅ Request Logging & Distributed Tracing                        │
│ ✅ Load Balancing (round-robin between instances)                │
│ ✅ CORS & Security Headers                                      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         ▼                      ▼                      ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│  Backend API    │   │  Telegram Bot   │   │   Keycloak      │
│  (Modular       │   │   Service       │   │   (Auth)        │
│   Monolith)     │   │   :8082         │   │   :8180         │
│  :8081-8083     │   │                 │   │                 │
└─────────────────┘   └─────────────────┘   └─────────────────┘
```

### Routing Examples

| Client Request | Gateway Routes To | Requires Auth? |
|----------------|-------------------|----------------|
| `GET /api/v1/public/products` | Backend :8081 | ❌ Public |
| `POST /api/v1/orders` | Backend :8081 | ✅ Yes (JWT) |
| `GET /api/admin/users` | Backend :8081 | ✅ Yes (ROLE_ADMIN) |
| `POST /api/webhooks/payments/{provider}` | Backend :8081 | ❌ Webhook signature |

### Benefits

- ✅ **Security**: JWT validation on the Gateway and backend (defense in depth)
- ✅ **Scalability**: Load balancing automatically distributes the load
- ✅ **Resilience**: Circuit breaker protects against cascading failures
- ✅ **Observability**: Centralized monitoring and tracing
- ✅ **Client Simplicity**: One endpoint instead of several

---

## 💳 Flexible Integration: Payments and Shipping

Details for payment flow, webhooks, and status model: [../architecture/payments-integration.md](../architecture/payments-integration.md).

### Plugin Architecture

For payments and shipping, we use the **Strategy Pattern + Plugin System**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PaymentProvider Interface                    │
├─────────────────────────────────────────────────────────────────┤
│ + getProviderCode(): String  // "card", "paypal", "bank_transfer", "crypto" │
│ + getSupportedCountries(): Set<String>                          │
│ + createPayment(request): PaymentResult                         │
│ + confirmPayment(paymentId): PaymentResult                      │
│ + refund(paymentId, amount): RefundResult                       │
│ + verifyWebhook(payload, signature): boolean                    │
└─────────────────────────────────────────────────────────────────┘
         ▲              ▲              ▲              ▲
         │              │              │              │
   ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
   │   Card    │  │  PayPal   │  │ Bank Transfer │  │  Crypto  │
   │  Provider │  │  Provider │  │   Provider   │  │ Provider │
   └───────────┘  └───────────┘  └───────────┘  └───────────┘

┌─────────────────────────────────────────────────────────────────┐
│                   ShippingProvider Interface                    │
├─────────────────────────────────────────────────────────────────┤
│ + getProviderCode(): String  // "dhl", "dpd", "gls", "fedex", "novaposhta" │
│ + getSupportedCountries(): Set<String>                          │
│ + calculateRates(request): List<ShippingRate>                   │
│ + createShipment(order, rate): Shipment                         │
│ + track(trackingNumber): TrackingInfo                           │
│ + cancelShipment(shipmentId): void                              │
└─────────────────────────────────────────────────────────────────┘
         ▲              ▲              ▲              ▲
         │              │              │              │
   ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
   │    DHL    │  │    DPD    │  │    GLS   │  │   FedEx   │
   │  Provider │  │  Provider │  │  Provider │  │  Provider │
   └───────────┘  └───────────┘  └───────────┘  └───────────┘
```

### Configuration via Admin Panel

```yaml
# Example configuration in DB
providers:
  payments:
    - code: card
      enabled: true
      countries: [DE, AT, NL, PL, UA]
      config:
        apiKey: ${PAYMENTS_CARD_API_KEY}
        webhookSecret: ${PAYMENTS_CARD_WEBHOOK_SECRET}
    - code: paypal
      enabled: true
      countries: [DE, AT, NL, PL, UA]
      config:
        clientId: ${PAYPAL_CLIENT_ID}
        clientSecret: ${PAYPAL_CLIENT_SECRET}
        webhookId: ${PAYPAL_WEBHOOK_ID}
    - code: bank_transfer
      enabled: true
      countries: [DE, AT, NL]
      config:
        iban: ${BANK_TRANSFER_IBAN}
        beneficiaryName: ${BANK_TRANSFER_BENEFICIARY}
    - code: crypto
      enabled: false
      countries: [DE, AT, UA]
      config:
        apiKey: ${CRYPTO_API_KEY}
        webhookSecret: ${CRYPTO_WEBHOOK_SECRET}
  
  shipping:
    - code: dhl
      enabled: true
      countries: [DE, FR, NL, GB]
    - code: dpd
      enabled: true
      countries: [DE, FR, PL]
    - code: gls
      enabled: true
      countries: [DE, NL, BE]
    - code: fedex
      enabled: false
      countries: [US, CA, GB, DE]
    - code: novaposhta
      enabled: false
      countries: [UA]
```

---

## 📅 Development Stages

Each stage has a detailed plan in the `docs/stages/` folder:

| Stage | Name | Duration | Detailed Plan |
|-------|------|----------|---------------|
| 0 | Infrastructure | 1 week | [stage-0-infrastructure.md](../stages/stage-0-infrastructure.md) |
| 1 | Backend Core | 4 weeks | [stage-1-backend-core.md](../stages/stage-1-backend-core.md) |
| 2 | Telegram Bot | 2 weeks | [stage-2-telegram-bot.md](../stages/stage-2-telegram-bot.md) |
| 3 | Admin Panel | 3 weeks | [stage-3-admin-panel.md](../stages/stage-3-admin-panel.md) |
| 4 | Store Frontend | 3 weeks | [stage-4-store-frontend.md](../stages/stage-4-store-frontend.md) |
| 5 | Mobile App | 2 weeks | [stage-5-mobile-app.md](../stages/stage-5-mobile-app.md) |
| 6 | Testing | 2 weeks | [stage-6-testing.md](../stages/stage-6-testing.md) |
| 7 | Deployment | 1 week | [stage-7-deploy.md](../stages/stage-7-deploy.md) |

**Total: ~18 weeks (~4.5 months)**

---

## 📊 Data Architecture

### Replication

```
┌─────────────────┐                    ┌─────────────────┐
│  PostgreSQL 17  │   Streaming        │  PostgreSQL 17  │
│    (Primary)    │   Replication      │    (Replica)    │
│  ───────────────│───────────────────►│  ───────────────│
│  Admin: R/W     │   ~10-100ms lag    │  Store: R       │
└────────┬────────┘                    └─────────────────┘
         │ 
         │ Domain events (Outbox)
         ▼
┌─────────────────┐
│    RabbitMQ     │
│   Event Bus     │
└────────┬────────┘
         │
    ┌────┴────┬──────────────┐
    ▼         ▼              ▼
┌───────┐ ┌────────┐ ┌─────────────┐
│ Redis │ │Elastic │ │ WebSocket   │
│ Cache │ │ Index  │ │ Broadcast   │
└───────┘ └────────┘ └─────────────┘
```

---

## 🛡️ Security

- **Authentication**: Keycloak (OAuth2 / OIDC)
- **Authorization**: RBAC (Role-Based Access Control)
- **API Security**: JWT validation on gateway and backend, rate limiting
- **Data**: Encryption at rest, TLS in transit
- **Secrets**: HashiCorp Vault / K8s Secrets

---

## What's Next?

1. **Study architectural decisions**: [../architecture/decisions.md](../architecture/decisions.md)
2. **Review detailed plans** in `../stages/`
3. **Start with infrastructure**: [Stage 0: Infrastructure](../stages/stage-0-infrastructure.md)
4. **Follow the checklists** in each plan

---

## Important Documents

| Document | Description |
|----------|-------------|
| [../architecture/decisions.md](../architecture/decisions.md) | Architectural decisions (ADR) with rationale |
| [../architecture/overview.md](../architecture/overview.md) | Visual architecture diagram with diagrams |
| [../architecture/payments-integration.md](../architecture/payments-integration.md) | Payment integration flow and status model |
| [../stages/](../stages/) | Detailed plans for each development stage |
