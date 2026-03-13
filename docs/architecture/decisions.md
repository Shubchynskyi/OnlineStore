# 🏛️ Architecture Decision Records (ADR)

Document with key architectural decisions for the OnlineStore project.

---

## ADR-001: API Gateway (Spring Cloud Gateway)

**Status**: Accepted
**Date**: 2026-01-23
**Context**: A single entry point is needed for all clients (Web, Mobile, Telegram Bot, Admin Panel).

### Decision
Use **Spring Cloud Gateway** as the API Gateway for:
- Centralized request routing
- JWT validation and authentication
- Rate limiting and DDoS protection
- Request/Response transformation
- Load balancing between backend instances
- Circuit breaker integration
- Centralized logging

### Architecture
```
┌─────────────────────────────────────────────────────────────────┐
│                          Clients                                │
│  Web Store │ Admin Panel │ Mobile App │ Telegram Bot            │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│               Spring Cloud Gateway (:8080)                       │
├─────────────────────────────────────────────────────────────────┤
│ • JWT Validation (Keycloak)                                     │
│ • Rate Limiting (Redis-based)                                   │
│ • Circuit Breaker (Resilience4j)                                │
│ • Request Logging & Tracing                                     │
└──────────────────────────┬──────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────┐
│  Backend API    │ │ Telegram    │ │  Keycloak       │
│  (Modular       │ │ Bot Service │ │  (Auth)         │
│   Monolith)     │ │             │ │                 │
│  :8081-8083     │ │  :8082      │ │  :8180          │
└─────────────────┘ └─────────────┘ └─────────────────┘
```

### Routing Rules
```yaml
spring:
  cloud:
    gateway:
      routes:
        # Backend API
        - id: backend-api
          uri: lb://backend-service
          predicates:
            - Path=/api/v1/**
          filters:
            - name: CircuitBreaker
              args:
                name: backendCircuitBreaker
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        # Admin API (requires ADMIN role)
        - id: admin-api
          uri: lb://backend-service
          predicates:
            - Path=/api/admin/**
          filters:
            - name: AuthorizationFilter
              args:
                roles: ROLE_ADMIN,ROLE_SUPER_ADMIN
```

### Alternatives
- **Kong**: Heavier, requires a separate database
- **Nginx**: Reverse proxy only, no Spring Boot integration
- **Envoy**: More complex configuration

### Consequences
- ✅ Single point of security management
- ✅ Simplification of client logic
- ✅ Centralized monitoring
- ⚠️ Single point of failure (resolved via clustering)
- ⚠️ Additional latency ~5-10ms

---

## ADR-002: Message Broker (RabbitMQ)

**Status**: Accepted
**Date**: 2026-01-23
**Context**: Asynchronous communication between modules and event-driven architecture is needed.

### Decision
Use **RabbitMQ 3.13** (NOT Kafka).

### Rationale

| Criterion | RabbitMQ ✅ | Kafka |
|-----------|------------|-------|
| **Throughput** | 10K-50K msg/sec (sufficient) | 100K+ msg/sec (excessive) |
| **Routing** | Advanced routing (topic, headers) | Simple topic-based |
| **Priority Queues** | ✅ Yes | ❌ No |
| **Dead Letter Queue** | ✅ Native support | ⚠️ Requires custom logic |
| **Message TTL** | ✅ Yes | ❌ No |
| **Complexity** | Low | High |
| **Event Replay** | ❌ No | ✅ Yes |
| **Operational Cost** | Low | High (ZooKeeper/KRaft) |

### Use Cases in Project
```
Events:
  - orders.created        → Notifications, Payment processing
  - orders.status-changed → Telegram notifications, WebSocket broadcast
  - products.updated      → Cache invalidation, Search index update
  - payments.completed    → Order fulfillment
  - shipments.created     → Customer notifications
```

### Dead Letter Queue Strategy
```yaml
queues:
  - name: orders.created
    dlq: orders.created.dlq
    retry_limit: 3
    retry_delay: 5s, 30s, 5m
```

### Consequences
- ✅ Operational simplicity
- ✅ Rich routing patterns
- ✅ Built-in management UI
- ❌ No event replay (not critical for e-commerce)
- ❌ No horizontal scaling like Kafka (not critical for our scale)

---

## ADR-003: Distributed Tracing (OpenTelemetry)

**Status**: Accepted
**Date**: 2026-01-23
**Context**: Need the ability to track a request through API Gateway → Backend → RabbitMQ → Elasticsearch.

### Decision
Use **OpenTelemetry** + **Jaeger** for distributed tracing.

### Integration
```java
// Spring Boot Auto-configuration
dependencies {
    implementation 'io.opentelemetry:opentelemetry-api'
    implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter'
}
```

### Trace Flow Example
```
TraceID: 7f8a9b2c3d4e5f6g
│
├─ Span: API Gateway [POST /api/v1/orders] (10ms)
│  │
│  └─ Span: Backend Service [OrderService.createOrder] (85ms)
│     │
│     ├─ Span: PostgreSQL [INSERT INTO orders] (15ms)
│     │
│     ├─ Span: RabbitMQ [Publish orders.created] (5ms)
│     │
│     └─ Span: Redis [Cache update] (3ms)
│
└─ Span: Notification Service [Email sending] (150ms)
```

### Export
```yaml
otel:
  exporter:
    jaeger:
      endpoint: http://jaeger:14250
  traces:
    sampler:
      probability: 1.0  # 1.0 in dev, 0.1 in prod
```

### Consequences
- ✅ Visualization of the full request flow
- ✅ Fast diagnostics of bottlenecks
- ✅ Industry standard (vendor-neutral)
- ⚠️ Overhead ~1-2% CPU (with 10% sampling)

---

## ADR-004: Circuit Breaker (Resilience4j)

**Status**: Accepted
**Date**: 2026-01-23
**Context**: Payment and Shipping providers may be unavailable. The system must not hang.

### Decision
Use **Resilience4j** for Circuit Breaker, Retry, and Timeout patterns.

### Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      card:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 3

      dhl:
        slidingWindowSize: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 30s

  retry:
    instances:
      payment:
        maxAttempts: 3
        waitDuration: 1s
        retryExceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException
```

### Usage
```java
@Service
public class PaymentService {

    @CircuitBreaker(name = "card", fallbackMethod = "fallbackPayment")
    @Retry(name = "payment")
    @TimeLimiter(name = "payment")
    public PaymentResult createPayment(PaymentRequest request) {
        return cardClient.createCharge(request);
    }

    private PaymentResult fallbackPayment(PaymentRequest request, Exception e) {
        log.error("Card provider unavailable, using fallback", e);
        // Switch to another provider or queue for retry
        return PaymentResult.pending("Provider temporarily unavailable");
    }
}
```

### Consequences
- ✅ Graceful degradation
- ✅ Fast fail when a provider is unavailable
- ✅ Automatic recovery
- ⚠️ Need to think through fallback strategies

Related: [payments-integration.md](payments-integration.md).

---

## ADR-005: Database Read/Write Splitting

**Status**: Accepted
**Date**: 2026-01-23
**Context**: PostgreSQL Primary/Replica for read scaling.

### Decision
Use **RoutingDataSource** for automatic request routing.

### Implementation
```java
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {

        var routing = new RoutingDataSource();

        Map<Object, Object> dataSources = new HashMap<>();
        dataSources.put(DataSourceType.PRIMARY, primary);
        dataSources.put(DataSourceType.REPLICA, replica);

        routing.setTargetDataSources(dataSources);
        routing.setDefaultTargetDataSource(primary);

        return routing;
    }
}

public class RoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? DataSourceType.REPLICA
            : DataSourceType.PRIMARY;
    }
}
```

### Usage
```java
@Service
public class ProductService {

    @Transactional(readOnly = true)  // → Routes to REPLICA
    public ProductDTO findById(Long id) { ... }

    @Transactional(readOnly = false)  // → Routes to PRIMARY
    public void updateProduct(Long id, ProductDTO dto) { ... }
}
```

### Replication Lag Handling
```java
// Read-after-write: force PRIMARY
@Transactional(readOnly = false)
public ProductDTO updateAndReturn(Long id, ProductDTO dto) {
    productRepository.save(entity);
    return productRepository.findById(id).get();  // Reads from PRIMARY
}
```

### Consequences
- ✅ Scaling of read operations
- ✅ Offloading of the Primary database
- ⚠️ Replication lag ~100ms (eventual consistency)
- ⚠️ Read-after-write may get stale data

---

## ADR-006: Cache Invalidation Strategy

**Status**: Accepted
**Date**: 2026-01-23
**Context**: Redis cache must be invalidated when data is updated on all backend instances.

### Decision
Use **Cache-Aside Pattern** + **RabbitMQ for distributed invalidation**.

### Architecture
```
┌──────────────┐
│  Backend #1  │──┐
└──────────────┘  │
                  ├──→ RabbitMQ (cache.invalidation)
┌──────────────┐  │           ↓
│  Backend #2  │──┘      ┌─────────┐
└──────────────┘         │  Redis  │
                         └─────────┘
```

### Implementation
```java
@Service
public class ProductService {

    @CacheEvict(value = "products", key = "#id")
    public void updateProduct(Long id, ProductDTO dto) {
        productRepository.save(entity);

        // Publish cache invalidation event
        rabbitTemplate.convertAndSend(
            "cache.invalidation",
            new CacheInvalidationEvent("products", id)
        );
    }

    @RabbitListener(queues = "cache.invalidation")
    public void handleCacheInvalidation(CacheInvalidationEvent event) {
        cacheManager.getCache(event.getCacheName()).evict(event.getKey());
    }
}
```

### Cache TTL Strategy
```yaml
spring:
  cache:
    redis:
      time-to-live: 1h  # Default TTL
    cache-names:
      - products:3600      # 1 hour
      - categories:86400   # 24 hours
      - users:1800         # 30 minutes
```

### Consequences
- ✅ Consistency across instances
- ✅ Automatic expiration (TTL)
- ⚠️ Network overhead for invalidation events

---

## ADR-007: Secrets Management (HashiCorp Vault)

**Status**: Accepted
**Date**: 2026-01-23
**Context**: API keys, DB passwords, JWT secrets must not be stored in Git.

### Decision
- **Development**: `.env` files (Git ignored)
- **Staging/Production**: **Kubernetes Secrets** + **External Secrets Operator** → **Vault**

### Production Architecture
```
┌─────────────────────────────────────────┐
│        HashiCorp Vault                   │
│  ├─ payments/card/api-key                │
│  ├─ postgres/password                    │
│  └─ jwt/secret                           │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│   External Secrets Operator (K8s)       │
│   Syncs Vault → Kubernetes Secrets      │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│   Spring Boot Application               │
│   Reads from Kubernetes Secrets         │
└─────────────────────────────────────────┘
```

### Development Setup
```bash
# .env (NOT in Git)
PAYMENTS_CARD_API_KEY=sk_test_...
POSTGRES_PASSWORD=dev_password
JWT_SECRET=dev_secret_key
```

```yaml
# docker-compose.yml
services:
  backend:
    env_file: .env
```

### Production Setup
```yaml
# k8s/external-secret.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: backend-secrets
spec:
  secretStoreRef:
    name: vault-backend
    kind: SecretStore
  target:
    name: backend-secrets
  data:
    - secretKey: PAYMENTS_CARD_API_KEY
      remoteRef:
        key: secret/data/payments/card
        property: api_key
```

### Consequences
- ✅ Centralized secrets management
- ✅ Secret rotation without redeployment
- ✅ Audit log of secret access
- ⚠️ Requires Vault infrastructure

---

## ADR-008: Token Refresh Strategy

**Status**: Accepted
**Date**: 2026-01-23
**Context**: JWT tokens expire. A mechanism for updating without re-login is needed.

### Decision
**Access Token (short-lived) + Refresh Token (long-lived)** pattern.

### Keycloak Configuration
```yaml
Access Token Lifespan: 5 minutes
Refresh Token Lifespan: 7 days
Refresh Token Max Reuse: 0 (rotation)
```

### Flow
```
1. Login:
   POST /auth/token
   ← { accessToken, refreshToken, expiresIn: 300 }

2. API Request:
   GET /api/products
   Authorization: Bearer {accessToken}

3. Access Token Expired (401):
   Client intercepts 401
   → POST /auth/refresh
     { refreshToken }
   ← { accessToken (new), refreshToken (new, rotated) }

4. Retry Original Request:
   GET /api/products
   Authorization: Bearer {new_accessToken}
```

### Client Implementation (JavaScript)
```typescript
// Axios interceptor
axios.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      const { accessToken } = await authService.refreshToken();
      originalRequest.headers.Authorization = "Bearer " + accessToken;

      return axios(originalRequest);
    }

    return Promise.reject(error);
  }
);
```

### Consequences
- ✅ UX: seamless token refresh
- ✅ Security: short-lived access tokens
- ✅ Refresh token rotation prevents replay attacks
- ⚠️ Need to store refresh token safely (httpOnly cookie)

---

## ADR-009: Flyway PostgreSQL Extension Provisioning Strategy

**Status**: Accepted
**Date**: 2026-03-13
**Context**: Flyway migrations currently require PostgreSQL extensions `uuid-ossp` and `pg_trgm`. Local Docker development can install them on the primary database, but managed or restricted environments may not allow the application role to execute `CREATE EXTENSION`.

### Decision
Keep extension provisioning in versioned Flyway migrations with idempotent `CREATE EXTENSION IF NOT EXISTS` statements, while treating extension privileges as an explicit deployment prerequisite.

### Operational Model
- Local Docker and self-managed PostgreSQL environments should run Flyway with a role that can install `uuid-ossp` and `pg_trgm`.
- Managed or restricted PostgreSQL environments must pre-provision `uuid-ossp` and `pg_trgm` before the first application deployment if the Flyway role lacks extension-install privileges.
- Primary and replica PostgreSQL images must ship the same extension binaries so WAL replay remains valid after extension creation on the primary node.
- Missing extension support must fail fast during migration. Do not add runtime fallbacks or silently disable UUID or trigram-backed capabilities.
- Browser-facing services must use explicit CORS allowlists. `onlinestore.security.cors.allowed-origins`, `allowed-methods`, and `allowed-headers` are the configuration source of truth, with `CORS_ALLOWED_ORIGINS`, `CORS_ALLOWED_METHODS`, and `CORS_ALLOWED_HEADERS` as environment overrides.

### Consequences
- ✅ Deterministic database bootstrap remains inside Flyway-managed schema history.
- ✅ Platform responsibilities are clear: extensions are either installed by the Flyway role or pre-provisioned before rollout.
- ✅ Replica safety is explicit because extension binaries must exist on every PostgreSQL node.
- ⚠️ First deployment into privilege-restricted environments requires DBA/platform coordination.
- ⚠️ Misconfigured extension privileges now surface as deployment failures instead of degraded runtime behavior.

---

## Decisions Summary

| ADR | Decision | Status | Priority |
|-----|----------|--------|-----------|
| ADR-001 | Spring Cloud Gateway | ✅ Accepted | 🔴 Critical |
| ADR-002 | RabbitMQ (not Kafka) | ✅ Accepted | 🔴 Critical |
| ADR-003 | OpenTelemetry + Jaeger | ✅ Accepted | 🟡 High |
| ADR-004 | Resilience4j Circuit Breaker | ✅ Accepted | 🔴 Critical |
| ADR-005 | Database Read/Write Splitting | ✅ Accepted | 🟡 High |
| ADR-006 | Cache Invalidation via RabbitMQ | ✅ Accepted | 🟡 High |
| ADR-007 | Vault for Secrets | ✅ Accepted | 🟡 High |
| ADR-008 | Refresh Token Strategy | ✅ Accepted | 🟢 Medium |
| ADR-009 | Flyway PostgreSQL Extension Provisioning Strategy | ✅ Accepted | 🟡 High |

---

## Future Decisions (TODO)

- [ ] **ADR-010**: Deployment Strategy (Rolling / Blue-Green / Canary)
- [ ] **ADR-011**: Contract Testing (Pact / Spring Cloud Contract)
- [ ] **ADR-012**: Feature Flags (LaunchDarkly / Unleash)
- [ ] **ADR-013**: Multi-tenancy Strategy (if marketplace is planned)
