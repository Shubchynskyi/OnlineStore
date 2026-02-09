# 🏗️ Stage 0: Infrastructure
<!-- markdownlint-disable MD040 -->

**Duration**: 1 week  
**Goal**: Prepare the development environment and basic infrastructure.

---

## ✅ Checklist

### 0.1 Docker Compose Environment
- [ ] Create `docker-compose.yml` with all services
- [ ] PostgreSQL 17 Primary (port 5432)
- [ ] PostgreSQL 17 Replica (port 5433) with streaming replication
- [ ] PostgreSQL exporter (Prometheus)
- [ ] Redis 7.4 (port 6379)
- [ ] Redis exporter (Prometheus)
- [ ] RabbitMQ 3.13 (ports 5672, 15672 management)
- [ ] RabbitMQ Prometheus plugin (port 15692)
- [ ] Elasticsearch 8.x (ports 9200, 9300)
- [ ] Elasticsearch exporter (Prometheus)
- [ ] Keycloak 26 (port 8180)
- [ ] MinIO (ports 9000, 9001 console)
- [ ] Jaeger (ports 16686 UI, 14250 collector) — Distributed Tracing
- [ ] OpenTelemetry Collector (ports 4317 gRPC, 4318 HTTP, 8889 metrics) — optional OTLP hub (logs to Loki, traces to Jaeger)
- [ ] Prometheus (port 9090) — Metrics
- [ ] Grafana (port 3001) — Dashboards
- [ ] Loki (port 3100) — Log aggregation
- [ ] Vault (port 8200, dev mode) — Secrets management (optional for dev)
- [ ] Configure volumes for persistence
- [ ] Create `.env.example` with environment variables

### 0.2 PostgreSQL Replication Setup
- [x] Use `infrastructure/docker/postgres/primary.conf` and `infrastructure/docker/postgres/pg_hba.conf`
- [x] Configure `postgresql.conf` for Primary:
  ```
  wal_level = replica
  max_wal_senders = 5
  wal_keep_size = 1GB
  ```
- [x] Create replication user (via `infrastructure/docker/postgres/init/01-create-replication-user.sh`)
- [x] Configure `pg_hba.conf` for replication
- [x] If you change `POSTGRES_REPLICATION_USER`, update `pg_hba.conf` accordingly (documented in pg_hba.conf)
- [x] Replica bootstrap is handled by `infrastructure/docker/postgres/replica-entrypoint.sh`
- [x] Configure Replica as standby (with replication slot `replica1_slot`)
- [x] Test replication lag (use `task db-replication-status` or `task db-replication-test`)

### 0.3 Keycloak Configuration
- [ ] Create Realm: `online-store`
- [ ] Configure Clients:
  | Client ID | Type | Auth Flow |
  |-----------|------|-----------|
  | `store-web` | Public | Authorization Code + PKCE |
  | `admin-panel` | Public | Authorization Code + PKCE |
  | `mobile-app` | Public | Authorization Code + PKCE |
  | `backend-service` | Confidential | Client Credentials |
  | `telegram-bot` | Confidential | Client Credentials |
- [ ] Create Roles:
  - `ROLE_CLIENT` — regular customer
  - `ROLE_MANAGER` — manager (orders, products)
  - `ROLE_ADMIN` — administrator (+ users)
  - `ROLE_SUPER_ADMIN` — super admin (+ settings)
- [ ] Create test users for each role
- [ ] Ensure Keycloak database exists (created by init scripts)
- [ ] Create directory: `infrastructure/keycloak`
- [ ] Export realm config for reproducibility

### 0.4 Repository Structure
- [ ] Initialize monorepo structure:
  ```
  mkdir -p backend/{common,catalog-module,orders-module,users-module,payments-module,shipping-module,notifications-module,search-module,application}
  mkdir -p telegram-bot
  mkdir -p store-frontend
  mkdir -p admin-panel
  mkdir -p mobile-app
  mkdir -p infrastructure/{docker,k8s,nginx,keycloak}
  mkdir -p docs/stages
  ```
- [ ] Configure `.gitignore`
- [ ] Configure `.editorconfig`
- [ ] README.md with startup instructions

### 0.5 CI/CD Pipeline (GitHub Actions)
- [ ] `.github/workflows/backend.yml`:
  - Build & Test on push
  - SonarQube analysis
  - Docker image build
- [ ] `.github/workflows/frontend.yml`:
  - Lint & Test
  - Build
- [ ] `.github/workflows/deploy.yml`:
  - Deploy to staging on merge to `develop`
  - Deploy to production on release tag

### 0.6 API Gateway Setup

#### Project Creation
- [ ] Go to https://start.spring.io
- [ ] Select: Maven, Java 25, Spring Boot 4.0
- [ ] Add dependencies:
  - Spring Cloud Gateway
  - Spring Boot Actuator
  - Spring Security OAuth2 Resource Server
  - Spring Data Redis Reactive
  - Resilience4j
  - OpenTelemetry
- [ ] Generate project in `api-gateway/`

#### pom.xml (key dependencies)
```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

#### application.yml
- [ ] Configure routes:
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: public-api
            uri: http://localhost:8081
            predicates:
              - Path=/api/v1/public/**
            filters:
              - name: RequestRateLimiter
                args:
                  redis-rate-limiter.replenishRate: 100
              - name: CircuitBreaker
                args:
                  name: backend
          - id: api
            uri: http://localhost:8081
            predicates:
              - Path=/api/v1/**
            filters:
              - name: RequestRateLimiter
                args:
                  redis-rate-limiter.replenishRate: 100
              - name: CircuitBreaker
                args:
                  name: backend
          - id: admin-api
            uri: http://localhost:8081
            predicates:
              - Path=/api/admin/**
  ```
- [ ] Configure CORS
- [ ] Configure Redis for rate limiting
- [ ] Configure OAuth2 Resource Server (Keycloak)

#### Security Configuration
- [ ] Create `SecurityConfig.java`:
  ```java
  @Configuration
  @EnableWebFluxSecurity
  public class SecurityConfig {
      @Bean
      public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
          return http
              .csrf(csrf -> csrf.disable())
              .authorizeExchange(exchanges -> exchanges
                  .pathMatchers("/api/v1/public/**").permitAll()
                  .pathMatchers("/api/admin/**").hasRole("ADMIN")
                  .anyExchange().authenticated()
              )
              .oauth2ResourceServer(oauth2 -> oauth2.jwt())
              .build();
      }
  }
  ```

#### JWT Authentication Converter
- [ ] Create `CustomJwtAuthenticationConverter.java` to extract roles from Keycloak

#### Fallback Controller
- [ ] Create `/fallback` endpoint for Circuit Breaker

#### Testing
- [ ] Run: `./mvnw spring-boot:run`
- [ ] Check health: `curl http://localhost:8080/actuator/health`
- [ ] Test rate limiting (150+ consecutive requests)

### 0.7 Monitoring Stack Configuration
- [ ] **Prometheus** — collect metrics from:
  - API Gateway
  - Backend services
  - PostgreSQL exporter
  - Redis exporter
  - RabbitMQ Prometheus plugin
  - Elasticsearch exporter
- [ ] **Grafana** — create dashboards:
  - System Overview (CPU, RAM, Disk)
  - API Performance (RPS, latency, errors)
  - Database (queries, connections)
  - Business metrics (orders, revenue)
- [ ] **Jaeger** — distributed tracing for:
  - API Gateway → Backend → Database
  - Event flow via RabbitMQ
- [ ] **Loki** — log aggregation
- [ ] **Alertmanager** — alerts to Telegram (optional, not included in dev docker-compose)

**Note:** OpenTelemetry Collector and Loki are optional for early development. If you do not ship logs via OTLP or file tailing, you can still inspect logs using `docker logs`.

### 0.8 Development Tools
- [ ] Taskfile with commands (see `Taskfile.yml`)
- [ ] VS Code workspace settings
- [ ] IntelliJ IDEA run configurations

---

## 📁 Files to Create

- `docker-compose.yml` (root)
- `infrastructure/docker/postgres/primary.conf`
- `infrastructure/docker/postgres/replica.conf`
- `infrastructure/docker/postgres/pg_hba.conf`
- `infrastructure/docker/postgres/replica-entrypoint.sh`
- `infrastructure/docker/postgres/init/01-create-replication-user.sh`
- `infrastructure/docker/postgres/init/02-create-keycloak-db.sh`

---

## ✅ Definition of Done

**Basic Infrastructure:**
- [ ] `docker compose up` starts all services without errors
- [ ] Keycloak is available at http://localhost:8180
- [ ] Able to log in with a test user
- [ ] PostgreSQL replication is working (check via `pg_stat_replication`)
- [ ] RabbitMQ management UI is available at http://localhost:15672
- [ ] Elasticsearch responds at http://localhost:9200
- [ ] MinIO console is available at http://localhost:9001

**Monitoring Stack:**
- [ ] Jaeger UI is available at http://localhost:16686
- [ ] Prometheus UI is available at http://localhost:9090
- [ ] Grafana is available at http://localhost:3001 (from .env)
- [ ] Loki is available on :3100 (log shipping requires a separate agent)
- [ ] Vault UI is available at http://localhost:8200 (dev mode)

**API Gateway:**
- [ ] API Gateway starts on port :8080
- [ ] JWT validation with Keycloak works
- [ ] Rate limiting works (verify via curl)
- [ ] Traces are sent to Jaeger
- [ ] Metrics are exported to Prometheus
