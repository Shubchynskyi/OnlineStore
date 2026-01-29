# CLAUDE.md
<!-- markdownlint-disable MD040 -->

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# OnlineStore - AI Agent Guidelines

## Language
**Respond in Russian** - all explanations, descriptions, assistance in Russian

---

## Rules for Claude Code

### Communication
1. **Respond in Russian** - all explanations, descriptions, assistance in Russian
2. **Code in English** - all variables, functions, classes, comments in code in English
3. **Documentation in English** - files, README, comments in files in English

### Code Quality

**Cleanliness and Readability:**
- ✅ Code must be **self-documenting** - variable and function names must be clear without comments
- ✅ Use **meaningful names** (productRepository instead of repo, orderService.calculateTotal instead of calc)
- ✅ Functions should be **small** (one responsibility = one function)
- ✅ Avoid **magic numbers** - use constants

**Single Responsibility Principle (SRP):**
- Each class/function should do **one thing and do it well**
- If a function does several things - break it into several functions
- If a class has several reasons to change - break it into several classes

**DRY (Don't Repeat Yourself):**
- 🚫 Do not repeat code - extract to a function/utility/helper
- 🚫 Do not copy-paste - create a reusable solution
- ✅ Use services, utilities, base classes

**Comments:**
- ❌ Minimize comments - good code doesn't need comments
- ❌ Do not describe WHAT the code does (it's visible from the code)
- ✅ Write comments ONLY in English
- ✅ Comments only to explain WHY or complex business logic

Example of a bad comment:
```java
// Increment counter
counter++;
```

Example of a good comment:
```java
// Skip first element due to API limitation that always returns a duplicate
items.stream().skip(1)...
```

---

## Project

**OnlineStore** - a scalable online store with an AI chatbot, flexible payment and delivery integrations.

### Key Features
- Modular monolith (capability to extract into microservices)
- Plugin Architecture for payments and shipping
- Event-driven architecture (RabbitMQ)
- Full Observability (Prometheus, Grafana, Jaeger, Loki)

---

## Tech Stack

### Backend
- **Java 25** (LTS)
- **Spring Boot 4.0**
- **Spring Cloud Gateway** (API Gateway)
- **PostgreSQL 17** (Primary/Replica)
- **Redis 7.4** (Cache + Sessions)
- **RabbitMQ 3.13** (Event Bus)
- **Elasticsearch 8** (Search)
- **Keycloak 26** (Auth)

### Frontend
- **Next.js 15** (React 19, Server Components) - Store Frontend
- **Angular 19** (Signals, Standalone) - Admin Panel
- **React Native 0.76+** - Mobile App

### DevOps
- **Docker** + **Docker Compose**
- **Kubernetes 1.31**
- **Prometheus** + **Grafana** (Monitoring)
- **Jaeger** (Tracing)
- **Loki** (Logs)

---

## Architecture

### Modular Monolith (Backend)
```
backend/
├── common/              # Shared entities, DTOs, utils
├── catalog-module/      # Products, Categories
├── orders-module/       # Orders, Cart
├── users-module/        # User profiles, Addresses
├── payments-module/     # Plugin providers (Card, PayPal, Bank Transfer, Crypto)
├── shipping-module/     # Plugin providers (DHL, DPD, GLS, FedEx, Nova Poshta)
├── notifications-module/# Email, Push, Telegram
├── search-module/       # Elasticsearch integration
└── application/         # Main Spring Boot app
```

### API Gateway
All requests pass through Spring Cloud Gateway (`:8080`):
- JWT Validation (Keycloak)
- Rate Limiting (Redis)
- Circuit Breaker (Resilience4j)
- Distributed Tracing (OpenTelemetry)

### Event-driven
Events via RabbitMQ:
- `orders.created` - new order
- `orders.status-changed` - status change
- `products.updated` - product update
- `payments.completed` - successful payment

### Database
- **Primary** (Write) - all writes
- **Replica** (Read) - reads for public API
- `@Transactional(readOnly = true)` automatically routes to Replica

---

## Code Style

### Java

```java
// Records for DTO
public record ProductDTO(
    Long id,
    String name,
    Money price,
    @JsonProperty("category_id") Long categoryId
) {}

// @Transactional(readOnly=true) for read operations
@Transactional(readOnly = true)
public ProductDTO findById(Long id) { ... }

// Structured logging
log.info("Order created: orderId={}, userId={}, amount={}",
    orderId, userId, amount);
```

### TypeScript (Next.js)

```typescript
// Server Components by default
// Add 'use client' only when necessary

// Zod for validation
const ProductSchema = z.object({
  name: z.string().min(1),
  price: z.number().positive(),
});

// Server Actions for mutations
async function createProduct(formData: FormData) {
  'use server';
  // ...
}
```

### Angular

```typescript
// Standalone Components
@Component({
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCardComponent {
  // Signals for reactivity
  product = input.required<Product>();

  // Computed signals
  formattedPrice = computed(() =>
    formatCurrency(this.product().price)
  );
}
```

---

## Commands

**IMPORTANT:** User prefers to run commands manually. Do NOT execute `task` commands unless explicitly instructed to do so in the user's prompt. User will run tests and applications themselves.

**Exception:** You MAY run tests and iterate on fixes ONLY when user explicitly requests it (e.g., "fix tests and keep running them until they pass").

### Infrastructure
```bash
task init            # Initialize project (copy .env, create directories)
task up              # Start Docker services
task down            # Stop Docker services
task logs            # Show logs
task restart         # Restart services
task clean           # Remove all Docker volumes and data
```

### Development
```bash
task backend-run     # Run backend (:8081)
task gateway-run     # Run API Gateway (:8080)
task bot-run         # Run Telegram Bot (:8082)
task admin-run       # Run Admin Panel (:4200)
task store-run       # Run Store Frontend (:3000)
```

### Database
```bash
task db-migrate      # Run Flyway migrations
task db-psql         # Connect to PostgreSQL (primary)
task db-reset        # Reset database (WARNING: deletes all data)
```

### Testing
```bash
task test            # All tests
task test-backend    # Backend tests
task test-e2e        # E2E tests

# Run single test (directly via Maven)
cd backend && ./mvnw test -Dtest=ProductServiceTest
cd backend && ./mvnw test -Dtest=ProductServiceTest#shouldCreateProduct
```

### Build
```bash
task build           # Build all services
task docker-build    # Build Docker images
```

### Monitoring
```bash
task monitoring      # Grafana (http://localhost:3001)
task tracing         # Jaeger (http://localhost:16686)
task prometheus      # Prometheus (http://localhost:9090)
```

### Service Access Points
| Service       | URL                        |
|---------------|----------------------------|
| API Gateway   | http://localhost:8080      |
| Backend       | http://localhost:8081      |
| Telegram Bot  | http://localhost:8082      |
| Store         | http://localhost:3000      |
| Admin Panel   | http://localhost:4200      |
| Keycloak      | http://localhost:8180      |
| RabbitMQ      | http://localhost:15672     |
| Elasticsearch | http://localhost:9200      |
| MinIO Console | http://localhost:9001      |
| Grafana       | http://localhost:3001      |
| Jaeger        | http://localhost:16686     |
| Prometheus    | http://localhost:9090      |
| Vault         | http://localhost:8200      |

---

## Project Structure

```
OnlineStore/
├── api-gateway/           # Spring Cloud Gateway (:8080)
├── backend/               # Modular Monolith (:8081)
├── telegram-bot/          # Telegram Bot (:8082)
├── store-frontend/        # Next.js 15 (:3000)
├── admin-panel/           # Angular 19 (:4200)
├── mobile-app/            # React Native
├── infrastructure/
│   ├── docker/            # Docker configs
│   └── k8s/               # Kubernetes manifests
└── docs/
    ├── getting-started.md # Step-by-step plan
    ├── quick-reference.md # Quick reference
    ├── architecture/
    │   ├── overview.md    # Diagrams
    │   └── decisions.md   # ADR
    ├── development/
    │   └── plan.md        # Project plan
    └── stages/            # Detailed stage plans
```

---

## Documentation

### Core Documents
- `docs/getting-started.md` - Step-by-step development plan
- `docs/quick-reference.md` - Quick cheat sheet
- `docs/architecture/overview.md` - Architecture diagrams
- `docs/architecture/decisions.md` - Architectural Decision Records (ADR)
- `docs/development/plan.md` - General project plan

### Development Stages
- `docs/stages/stage-0-infrastructure.md` - Infrastructure
- `docs/stages/stage-1-backend-core.md` - Backend Core
- `docs/stages/stage-2-telegram-bot.md` - Telegram Bot
- `docs/stages/stage-3-admin-panel.md` - Admin Panel (Angular)
- `docs/stages/stage-4-store-frontend.md` - Store Frontend (Next.js)
- `docs/stages/stage-5-mobile-app.md` - Mobile App
- `docs/stages/stage-6-testing.md` - Testing
- `docs/stages/stage-7-deploy.md` - Deployment

---

## Operating Rules

### General
1. Always read the file before editing
2. Follow the existing project code style
3. Do not create files unnecessarily
4. Use existing utilities and helpers

### Backend (Java)
1. Use Records for DTOs
2. Add `@Transactional(readOnly = true)` for read-only methods
3. Use Specification API for complex queries
4. Log structuredly with context (orderId, userId, etc.)

### Frontend (React/Angular)
1. Prefer Server Components (Next.js)
2. Use Signals (Angular)
3. Validate data via Zod
4. Use OnPush change detection (Angular)

### DevOps
1. Do not store secrets in Git
2. Use `.env` for local development
3. Check Docker Compose before commit

---

## Security

### Forbidden
- Committing `.env` files
- Storing secrets in code
- Using `@Transactional` without explicitly specifying readOnly

### Mandatory
- Validate all incoming data
- Use parameterized queries
- Check authorization at the service level
