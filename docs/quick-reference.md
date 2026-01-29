# ⚡ Quick Cheat Sheet
<!-- markdownlint-disable MD040 -->

Quick reference of commands and project stages for OnlineStore.

---

## 🎯 Current Stage

**Stage 0: Infrastructure** (1 week)

**Next steps:**
1. ✅ `task init` — create .env
2. ✅ Fill in .env
3. ✅ `task up` — start Docker
4. ⏳ Configure Keycloak
5. ⏳ Create API Gateway
6. ⏳ Configure PostgreSQL replication

**Details:** `docs/stages/stage-0-infrastructure.md`

---

## 🛠️ Main Commands

### Infrastructure
```bash
task init          # Project initialization
task up            # Start all services
task down          # Stop services
task restart       # Restart services
task logs          # Show logs
task clean         # Delete all data
```

### Services
```bash
task gateway-run   # API Gateway :8080
task backend-run   # Backend :8081
task bot-run       # Telegram Bot :8082
task admin-run     # Admin Panel :4200
task store-run     # Store Frontend :3000
```

### Monitoring
```bash
task monitoring    # Grafana → http://localhost:3001
task tracing       # Jaeger → http://localhost:16686
task prometheus    # Prometheus → http://localhost:9090
```

### Database
```bash
task db-migrate    # Apply migrations
task db-psql       # Connect to PostgreSQL
task db-reset      # Reset DB (DANGEROUS!)
```

### Testing
```bash
task test          # Backend tests (current)
task test-backend  # Backend tests
task test-e2e      # E2E tests

# Dependency audit
mvn org.owasp:dependency-check-maven:check
npm audit --audit-level=high
```

### Build
```bash
task build         # Build all services
task docker-build  # Build Docker images
```

---

## 🌐 Service Access

| Service | URL | Credentials |
|--------|-----|-------------|
| **API Gateway** | http://localhost:8080 | JWT token |
| **Backend API** | http://localhost:8081 | JWT token |
| **Store Frontend** | http://localhost:3000 | Keycloak |
| **Admin Panel** | http://localhost:4200 | Keycloak |
| **Keycloak** | http://localhost:8180 | admin / (from .env) |
| **RabbitMQ** | http://localhost:15672 | admin / (from .env) |
| **MinIO** | http://localhost:9001 | minioadmin / (from .env) |
| **Grafana** | http://localhost:3001 | admin / (from .env) |
| **Jaeger** | http://localhost:16686 | - |
| **Prometheus** | http://localhost:9090 | - |
| **Elasticsearch** | http://localhost:9200 | - |
| **Vault** | http://localhost:8200 | (from .env) |

---

## 📋 Project Stages

| # | Stage | Duration | File |
|---|-------|----------|------|
| 0 | Infrastructure | 1 week | [stage-0-infrastructure.md](stages/stage-0-infrastructure.md) |
| 1 | Backend Core | 4 weeks | [stage-1-backend-core.md](stages/stage-1-backend-core.md) |
| 2 | Telegram Bot | 2 weeks | [stage-2-telegram-bot.md](stages/stage-2-telegram-bot.md) |
| 3 | Admin Panel | 3 weeks | [stage-3-admin-panel.md](stages/stage-3-admin-panel.md) |
| 4 | Store Frontend | 3 weeks | [stage-4-store-frontend.md](stages/stage-4-store-frontend.md) |
| 5 | Mobile App | 2 weeks | [stage-5-mobile-app.md](stages/stage-5-mobile-app.md) |
| 6 | Testing | 2 weeks | [stage-6-testing.md](stages/stage-6-testing.md) |
| 7 | Deployment | 1 week | [stage-7-deploy.md](stages/stage-7-deploy.md) |

**Total:** ~18 weeks (~4.5 months)

---

## 🏗️ Project Structure

```
OnlineStore/
├── api-gateway/           # Spring Cloud Gateway :8080
├── backend/               # Modular Monolith :8081
│   ├── common/
│   ├── catalog-module/
│   ├── orders-module/
│   ├── users-module/
│   ├── payments-module/
│   ├── shipping-module/
│   ├── notifications-module/
│   ├── search-module/
│   └── application/
├── telegram-bot/          # Telegram Bot :8082
├── store-frontend/        # Next.js :3000
├── admin-panel/           # Angular :4200
├── mobile-app/            # React Native
├── infrastructure/
│   ├── docker/
│   ├── k8s/
│   └── nginx/
└── docs/
    ├── architecture/      # Diagrams and ADR
    ├── development/       # Project plan
    └── stages/            # Detailed stage plans
```

---

## 📚 Key Documents

| Document | Purpose |
|----------|---------|
| [../README.md](../README.md) | Project overview |
| [getting-started.md](getting-started.md) | **Step-by-step plan** |
| [development/plan.md](development/plan.md) | General plan and stack |
| [architecture/overview.md](architecture/overview.md) | Architecture diagrams |
| [architecture/decisions.md](architecture/decisions.md) | Architectural decisions |

---

## 🔑 Environment Variables (.env)

### Required for start:
```bash
POSTGRES_PASSWORD=your_secure_password
KEYCLOAK_ADMIN_PASSWORD=your_secure_password
POSTGRES_REPLICATION_USER=replicator
POSTGRES_REPLICATION_PASSWORD=your_secure_password
```

### For Telegram bot:
```bash
TELEGRAM_BOT_TOKEN=123456:ABC-DEF...
TELEGRAM_MANAGER_CHAT_ID=-100123456789
```

### For AI chat:
```bash
OPENAI_API_KEY=sk-...
AI_PROVIDER=openai
```

### For payments:
```bash
PAYMENTS_CARD_API_KEY=sk_test_...
PAYMENTS_CARD_WEBHOOK_SECRET=whsec_...
PAYPAL_CLIENT_ID=...
PAYPAL_CLIENT_SECRET=...
BANK_TRANSFER_IBAN=...
```

**Full list:** `.env.example`

---

## 🧪 Testing API Gateway

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Public API (no auth)
```bash
curl http://localhost:8080/api/v1/public/products
```

### Get token from Keycloak
```bash
TOKEN=$(curl -X POST http://localhost:8180/realms/online-store/protocol/openid-connect/token \
  -d "client_id=store-web" \
  -d "grant_type=password" \
  -d "username=testclient" \
  -d "password=password" \
  | jq -r '.access_token')
```

### Authenticated Request
```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/orders
```

### Rate Limiting Test
```bash
# Should hit rate limit after 100+ requests
for i in {1..150}; do
  curl http://localhost:8080/api/v1/public/products
done
```

---

## 🐛 Troubleshooting

### Docker services not starting
```bash
# Check status
docker compose ps

# View logs of a specific service
docker compose logs postgres-primary

# Restart
task restart
```

### Keycloak unavailable
```bash
# Check logs
docker compose logs keycloak

# Wait 1-2 minutes after start
# Keycloak takes a while to start
```

### API Gateway not starting
```bash
# Ensure Redis is running
docker compose ps redis

# Check application.yml
cat api-gateway/src/main/resources/application.yml

# Run with debug logs
cd api-gateway
./mvnw spring-boot:run -Dspring-boot.run.arguments=--logging.level.org.springframework.cloud.gateway=DEBUG
```

### PostgreSQL replication not working
```bash
# Check replication status
docker exec -it store-postgres-primary psql -U store -d online_store \
  -c "SELECT * FROM pg_stat_replication;"

# Should have one row with state=streaming
```

---

## 💡 Best Practices

### Git Workflow
```bash
# Create feature branch
git checkout -b feature/my-feature

# Commit with conventional commits
git commit -m "feat(catalog): add product search"
git commit -m "fix(orders): resolve payment validation"
git commit -m "docs: update API documentation"

# Push
git push origin feature/my-feature

# Create Pull Request on GitHub
```

### Database Migrations
```bash
# Run new migrations
cd backend
./mvnw flyway:migrate

# Naming: V{version}__{description}.sql
# Example: V001__create_users_table.sql
```

### Logging
```java
// Structured logging with trace context
log.info("Order created: orderId={}, userId={}, amount={}",
    orderId, userId, amount);
```

---

## 🆘 Get Help

1. **Documentation:** Read `docs/` for details
2. **GitHub Issues:** Create an issue for bug reports
3. **Team:** Contact the tech lead

---

**This cheat sheet should always be at hand!** 📌
