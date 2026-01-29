# 🚀 Step-by-Step Development Plan

**Single execution plan for the OnlineStore project from start to finish.**

---

## 📋 Stages Overview

| # | Stage                                             | Duration | Status    |
|---|---------------------------------------------------|----------|-----------|
| 0 | [Infrastructure](#stage-0-infrastructure-1-week)  | 1 week   | ⏳ Current |
| 1 | [Backend Core](#stage-1-backend-core-4-weeks)     | 4 weeks  | ⏳ Pending |
| 2 | [Telegram Bot](#stage-2-telegram-bot-2-weeks)     | 2 weeks  | ⏳ Pending |
| 3 | [Admin Panel](#stage-3-admin-panel-3-weeks)       | 3 weeks  | ⏳ Pending |
| 4 | [Store Frontend](#stage-4-store-frontend-3-weeks) | 3 weeks  | ⏳ Pending |
| 5 | [Mobile App](#stage-5-mobile-app-2-weeks)         | 2 weeks  | ⏳ Pending |
| 6 | [Testing](#stage-6-testing-2-weeks)               | 2 weeks  | ⏳ Pending |
| 7 | [Deployment](#stage-7-deploy-1-week)              | 1 week   | ⏳ Pending |

**Total: ~18 weeks (~4.5 months)**

---

## ⚡ Quick Start (5 minutes)

```bash
# 1. Clone the repository (if not already done)
cd OnlineStore

# 2. Initialize the project
task init

# 3. Fill in required variables in .env
# Edit .env with your preferred editor
# POSTGRES_PASSWORD=...
# POSTGRES_REPLICATION_PASSWORD=...
# KEYCLOAK_ADMIN_PASSWORD=...
# TELEGRAM_BOT_TOKEN=...
# OPENAI_API_KEY=...

# 4. Start infrastructure
task up

# 5. Check status
docker compose ps

# 6. Open monitoring
task monitoring  # Grafana: http://localhost:3001
```

---

## MVP Target (end of Stage 1)

**Purpose**: Deliver a usable backend with a thin client flow.

**Scope**:
- Catalog: categories, product list, product details
- Orders: create order from cart, basic stock validation
- Payments: one provider in sandbox, webhook confirmation, order status update
- Auth: Keycloak login for store, RBAC for admin/manager roles
- Admin via API: product and order management endpoints
- Observability: health, metrics, logs, basic traces

**Out of scope until later stages**:
- Admin UI, Telegram bot, mobile app
- Advanced search and recommendations
- Full shipping provider matrix
- Full monitoring stack and alerting

---

## Stage 0: Infrastructure (1 week)

**Goal**: Prepare the development environment and basic infrastructure.

### Day 1–2: Docker Compose & Base Services

**Checklist:**
- [ ] Ensure Docker, Docker Compose, Java 25, Node.js 22+ are installed
- [ ] Run `task init` to create the `.env` file
- [ ] Fill `.env` with mandatory values
- [ ] Run `task up` to start all Docker services
- [ ] Check that all services are running: `docker compose ps`
- [ ] Open and verify accessibility:
  - Keycloak: http://localhost:8180
  - RabbitMQ: http://localhost:15672 (from .env)
  - Grafana: http://localhost:3001 (from .env)
  - Jaeger: http://localhost:16686
  - Prometheus: http://localhost:9090

**Commands:**
```bash
task up
task logs
docker compose ps
```

### Day 3: Keycloak Setup

**Checklist:**
- [ ] Open Keycloak Admin Console: http://localhost:8180
- [ ] Log in as admin (password from `.env`)
- [ ] Create a new Realm: `online-store`
- [ ] Create Clients:
  - `store-web` (Public, Authorization Code + PKCE)
  - `admin-panel` (Public, Authorization Code + PKCE)
  - `mobile-app` (Public, Authorization Code + PKCE)
  - `backend-service` (Confidential, Client Credentials)
  - `telegram-bot` (Confidential, Client Credentials)
- [ ] Create Roles:
  - `ROLE_CLIENT`
  - `ROLE_MANAGER`
  - `ROLE_ADMIN`
  - `ROLE_SUPER_ADMIN`
- [ ] Create test users:
  - `testclient` with role ROLE_CLIENT
  - `testmanager` with role ROLE_MANAGER
  - `testadmin` with role ROLE_ADMIN
- [ ] Export realm config: Realm Settings → Export
- [ ] Save to `infrastructure/keycloak/realm-export.json`

**Documentation:** [Keycloak Getting Started](https://www.keycloak.org/getting-started)

### Day 4–5: API Gateway (Spring Cloud Gateway)

**Checklist:**
- [ ] Create directory: `mkdir api-gateway && cd api-gateway`
- [ ] Go to https://start.spring.io
- [ ] Configure project:
  - Project: Maven
  - Language: Java
  - Spring Boot: 4.0.x
  - Java: 25
  - Dependencies: Spring Cloud Gateway, Actuator, OAuth2 Resource Server, Redis Reactive, Resilience
- [ ] Download and extract into `api-gateway/`
- [ ] Create `application.yml` with routes (see `docs/stages/stage-0-infrastructure.md` section 0.6)
- [ ] Create `SecurityConfig.java` for JWT validation
- [ ] Create `CustomJwtAuthenticationConverter.java` for roles
- [ ] Create `FallbackController.java` for Circuit Breaker
- [ ] Run: `./mvnw spring-boot:run`
- [ ] Check health: `curl http://localhost:8080/actuator/health`
- [ ] Test JWT authentication with a token from Keycloak

**Port:** `:8080`

**Detailed instructions:** `docs/stages/stage-0-infrastructure.md` (section 0.6)

### Day 6–7: PostgreSQL Replication Setup

**Checklist:**
- [ ] Configure `postgresql.conf` for Primary (WAL replication)
- [ ] Create a replication user in Primary
- [ ] Configure `pg_hba.conf` for replication
- [ ] Configure Replica as standby
- [ ] Verify replication:
  ```bash
  docker exec -it store-postgres-primary psql -U store -d online_store \
    -c "SELECT * FROM pg_stat_replication;"
  ```
- [ ] Ensure lag < 100ms

**Detailed instructions:** `docs/stages/stage-0-infrastructure.md` (section 0.2)

### ✅ Definition of Done (Stage 0)
- [ ] All Docker services are up and running
- [ ] Keycloak is configured with realm, clients, roles, users
- [ ] API Gateway is running and proxying requests
- [ ] JWT authentication works
- [ ] PostgreSQL replication works
- [ ] Grafana shows dashboards (even if empty)
- [ ] Jaeger is ready to receive traces

---

## Stage 1: Backend Core (4 weeks)

**Goal**: Create a modular monolith with core business logic.

### Week 1: Initialization and Common Module

**Checklist:**
- [ ] Create parent `pom.xml` for Maven multi-module project
- [ ] Create `common/` module with base entities:
  - `BaseEntity` (id, createdAt, updatedAt, version)
  - `AuditableEntity` (createdBy, updatedBy)
  - `PageResponse<T>` (generic pagination)
  - `ApiError` (standardized errors)
  - `Money` (Value Object: amount, currency)
- [ ] Configure Flyway for migrations
- [ ] Configure multiple DataSources (Primary/Replica)
- [ ] Configure RoutingDataSource for read/write splitting
- [ ] Configure Security with OAuth2 Resource Server

**Detailed instructions:** `docs/stages/stage-1-backend-core.md` (Week 1)

### Week 2: Users & Catalog Modules

**Users Module:**
- [ ] Entities: User, UserProfile, Address
- [ ] Repository, Service, REST Controller
- [ ] API endpoints: `/api/v1/users/me`, `/api/v1/users/me/addresses`

**Catalog Module:**
- [ ] Entities: Category, Product, ProductVariant, ProductImage
- [ ] JSONB for dynamic attributes
- [ ] Repository with Specification API for filtering
- [ ] Redis caching for catalog
- [ ] Events: ProductCreatedEvent, ProductUpdatedEvent
- [ ] API endpoints: `/api/v1/public/catalog/categories`, `/api/v1/public/products`

**Detailed instructions:** `docs/stages/stage-1-backend-core.md` (Week 2)

### Week 3: Orders & Payments Modules

**Orders Module:**
- [ ] Entities: Order, OrderItem, OrderStatus
- [ ] State Machine for order statuses
- [ ] Service: createOrder(), updateStatus(), cancelOrder()
- [ ] Events: OrderCreatedEvent, OrderStatusChangedEvent
- [ ] API endpoints: `/api/v1/orders`

**Payments Module (Plugin Architecture):**
- [ ] Interface: `PaymentProvider`
- [ ] Implementations: CardPaymentProvider, PayPalPaymentProvider, BankTransferPaymentProvider, CryptoPaymentProvider (optional)
- [ ] PaymentProviderRegistry
- [ ] Entities: Payment, PaymentStatus
- [ ] Webhook Controller: `/api/webhooks/payments/{provider}`

Payment flow, webhooks, and status model: [architecture/payments-integration.md](architecture/payments-integration.md).

**Detailed instructions:** `docs/stages/stage-1-backend-core.md` (Week 3)

### Week 4: Search, Notifications, Integration

**Search Module (Elasticsearch):**
- [ ] Index mapping for products
- [ ] SearchService: full-text search, faceted filtering, autocomplete
- [ ] RabbitMQ Listener for product synchronization
- [ ] API endpoints: `/api/v1/public/search/products`, `/api/v1/public/search/suggest`

**Notifications Module:**
- [ ] NotificationService interface
- [ ] Channels: Email, SMS (optional), Push
- [ ] Templates for email (Thymeleaf/Mustache)
- [ ] RabbitMQ Listener for events

**WebSocket:**
- [ ] STOMP configuration
- [ ] Topics: `/topic/products/{id}`, `/topic/orders/{id}`

**Detailed instructions:** `docs/stages/stage-1-backend-core.md` (Week 4)

### ✅ Definition of Done (Stage 1)
- [ ] Backend compiles: `./mvnw clean package`
- [ ] All tests pass: `./mvnw test`
- [ ] API documentation is available: http://localhost:8081/swagger-ui.html
- [ ] Test coverage > 70%
- [ ] Health check works: `/actuator/health`
- [ ] Metrics are exported to Prometheus

---

## Stage 2: Telegram Bot (2 weeks)

**Goal**: Create a Telegram bot for customers and manager notifications.

### Week 1: Base Bot

**Checklist:**
- [ ] Create Spring Boot project `telegram-bot/`
- [ ] Add TelegramBots 7.x dependency
- [ ] Configure bot token in `.env`
- [ ] State Management (Redis): UserState enum
- [ ] Commands: `/start`, `/catalog`, `/search`, `/order`, `/cart`
- [ ] Inline Keyboards: main menu, product card

**Detailed instructions:** `docs/stages/stage-2-telegram-bot.md` (Week 1)

### Week 2: AI Chat & Notifications

**Checklist:**
- [ ] Feign Client for Backend API
- [ ] Integration with OpenAI API
- [ ] System prompt with store context
- [ ] RAG: search for relevant products via Elasticsearch
- [ ] Dialog history in Redis
- [ ] RabbitMQ Listener for manager notifications:
  - `orders.created` → New order
  - `orders.status-changed` → Status change
  - `products.low-stock` → Product running out

**Detailed instructions:** `docs/stages/stage-2-telegram-bot.md` (Week 2)

### ✅ Definition of Done (Stage 2)
- [ ] Bot responds to commands
- [ ] AI chat works and gives relevant answers
- [ ] Managers receive notifications in Telegram
- [ ] Rate limiting works

---

## Stage 3: Admin Panel (3 weeks)

**Goal**: Create an administrator panel for store management.
**Stack**: Angular 19

### Week 1: Core Setup

**Checklist:**
- [ ] Create project: `ng new admin-panel --standalone --style=scss`
- [ ] Install Angular Material 19
- [ ] Configure Keycloak integration (`angular-auth-oidc-client`)
- [ ] Create Auth Service, HTTP Interceptors, Route Guards
- [ ] Create shared UI components (Signals-based)

**Detailed instructions:** `docs/stages/stage-3-admin-panel.md` (Week 1)

### Week 2: Catalog & Orders

**Checklist:**
- [ ] Dashboard with widgets: Orders today, Revenue, Low stock
- [ ] Catalog Management:
  - Categories CRUD (tree view, drag-drop)
  - Products CRUD (JSONB attributes editor, image upload)
  - Bulk import/export CSV
- [ ] Orders Management:
  - Orders list with filters
  - Order details and status change
  - Print invoice

**Detailed instructions:** `docs/stages/stage-3-admin-panel.md` (Week 2)

### Week 3: Users, Payments, Settings

**Checklist:**
- [ ] Users Management (list, search, role assignment, block/unblock)
- [ ] Payment Providers Settings (enable/disable, API keys)
- [ ] Shipping Providers Settings (enable/disable per country)
- [ ] General Settings (store info, email templates, Telegram config)

**Detailed instructions:** `docs/stages/stage-3-admin-panel.md` (Week 3)

### ✅ Definition of Done (Stage 3)
- [ ] Login via Keycloak works
- [ ] All CRUD operations work
- [ ] Real-time updates via WebSocket
- [ ] Responsive design

---

## Stage 4: Store Frontend (3 weeks)

**Goal**: Create a public store for customers.
**Stack**: Next.js 15 (React 19)

### Week 1: Core & Catalog

**Checklist:**
- [ ] Create project: `npx create-next-app@latest`
- [ ] Install shadcn/ui, Tailwind CSS
- [ ] Configure NextAuth.js with Keycloak
- [ ] Create an API client for Backend
- [ ] Home Page (SSR): Hero, Categories, Featured products
- [ ] Catalog Pages: Category page, Product card, Pagination, Filters

**Detailed instructions:** `docs/stages/stage-4-store-frontend.md` (Week 1)

### Week 2: Product & Cart

**Checklist:**
- [ ] Product Page: Image gallery, Variant selector, Add to cart, Reviews
- [ ] Cart (Zustand store): addItem, removeItem, updateQuantity, syncWithServer
- [ ] Cart Page: Items list, Quantity controls, Price calculation

**Detailed instructions:** `docs/stages/stage-4-store-frontend.md` (Week 2)

### Week 3: Checkout & Account

**Checklist:**
- [ ] Checkout Flow (Multi-step): Address → Shipping → Payment → Review → Complete
- [ ] Payment Integration: Card SDK or hosted checkout, PayPal, bank transfer (SEPA/IBAN), crypto (optional)
- [ ] Account Pages: Profile, Order history, Order tracking, Addresses

Payment integration details: [architecture/payments-integration.md](architecture/payments-integration.md).

**Detailed instructions:** `docs/stages/stage-4-store-frontend.md` (Week 3)

### ✅ Definition of Done (Stage 4)
- [ ] Lighthouse score > 90
- [ ] Full checkout flow works
- [ ] Cart persists across sessions
- [ ] Auth works
- [ ] Mobile responsive

---

## Stage 5: Mobile App (2 weeks)

**Goal**: Create a mobile app for iOS and Android.
**Stack**: React Native

### Week 1: Core & Catalog

**Checklist:**
- [ ] Create project: `npx create-expo-app`
- [ ] Configure Expo Router
- [ ] Configure Auth (expo-auth-session + Keycloak)
- [ ] Reuse API client and Zustand stores from Store Frontend
- [ ] Home & Catalog screens
- [ ] Product details screen

**Detailed instructions:** `docs/stages/stage-5-mobile-app.md` (Week 1)

### Week 2: Cart, Checkout, Push

**Checklist:**
- [ ] Cart & Checkout screens
- [ ] Payment (in-app browser)
- [ ] Push Notifications (expo-notifications + Firebase)
- [ ] Account screens (Profile, Orders, Tracking)

Payment integration details: [architecture/payments-integration.md](architecture/payments-integration.md).

**Detailed instructions:** `docs/stages/stage-5-mobile-app.md` (Week 2)

### ✅ Definition of Done (Stage 5)
- [ ] App runs on iOS and Android
- [ ] Full shopping flow works
- [ ] Push notifications work

---

## Stage 6: Testing (2 weeks)

**Goal**: Ensure quality and reliability.

### Week 1: Backend & Integration Tests

**Checklist:**
- [ ] Unit Tests (JUnit 5 + Mockito): Service layer, Validation
- [ ] Integration Tests (Testcontainers): Repository, RabbitMQ, Elasticsearch
- [ ] API Tests (REST Assured): All endpoints, Auth, Error handling
- [ ] Coverage > 80%

**Detailed instructions:** `docs/stages/stage-6-testing.md` (Week 1)

### Week 2: Frontend & E2E

**Checklist:**
- [ ] Frontend Unit Tests (Jest + Testing Library)
- [ ] E2E Tests (Playwright): Checkout flow, Admin CRUD, Auth
- [ ] Performance Tests (k6): Load testing
- [ ] Security: OWASP ZAP scan, Dependency audit

**Detailed instructions:** `docs/stages/stage-6-testing.md` (Week 2)

### ✅ Definition of Done (Stage 6)
- [ ] Backend coverage > 80%
- [ ] E2E tests pass
- [ ] No critical security issues
- [ ] Performance baseline established

---

## Stage 7: Deploy (1 week)

**Goal**: Production-ready deployment.

### Tasks

**Checklist:**
- [ ] Kubernetes / Docker Swarm setup
- [ ] Deployments for all services
- [ ] Ingress (Nginx) with SSL (Let's Encrypt)
- [ ] ConfigMaps & Secrets
- [ ] HPA (Horizontal Pod Autoscaler)
- [ ] Monitoring: Prometheus + Grafana dashboards
- [ ] Alerting: Alertmanager → Telegram
- [ ] Backup: PostgreSQL (pg_dump + WAL archiving), MinIO
- [ ] CI/CD: GitHub Actions pipelines

**Detailed instructions:** `docs/stages/stage-7-deploy.md`

### ✅ Definition of Done (Stage 7)
- [ ] All services are running in K8s
- [ ] SSL configured
- [ ] Monitoring dashboards live
- [ ] Backup tested
- [ ] Runbook documented

---

## 📚 Useful Resources

### Project Documentation
- [development/plan.md](development/plan.md) — General plan and technology stack
- [architecture/overview.md](architecture/overview.md) — Architecture diagrams
- [architecture/decisions.md](architecture/decisions.md) — Architecture Decision Records (ADR)
- [architecture/payments-integration.md](architecture/payments-integration.md) — Payment integration flow and status model

### Detailed Stage Plans
- [Stage 0: Infrastructure](stages/stage-0-infrastructure.md)
- [Stage 1: Backend Core](stages/stage-1-backend-core.md)
- [Stage 2: Telegram Bot](stages/stage-2-telegram-bot.md)
- [Stage 3: Admin Panel](stages/stage-3-admin-panel.md)
- [Stage 4: Store Frontend](stages/stage-4-store-frontend.md)
- [Stage 5: Mobile App](stages/stage-5-mobile-app.md)
- [Stage 6: Testing](stages/stage-6-testing.md)
- [Stage 7: Deploy](stages/stage-7-deploy.md)

### Development Commands
```bash
task help           # Show all commands
task up             # Start infrastructure
task down           # Stop infrastructure
task logs            # Show logs
task monitoring     # Open Grafana
task tracing        # Open Jaeger
task test           # Run backend tests (current)
```
