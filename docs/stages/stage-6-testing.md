# 🧪 Stage 6: Testing
<!-- markdownlint-disable MD040 -->

**Duration**: 2 weeks | **Goal**: Ensure quality and reliability

---

## ✅ Week 1: Backend & Integration Tests

### 1.1 Unit Tests (JUnit 5 + Mockito)
- [ ] Service layer tests
- [ ] Validation tests
- [ ] Coverage > 80%

### 1.2 Integration Tests (Testcontainers)
- [ ] Repository tests with real PostgreSQL
- [ ] RabbitMQ integration tests
- [ ] Elasticsearch tests

### 1.3 API Tests (REST Assured)
- [ ] All endpoints covered
- [ ] Auth scenarios
- [ ] Error handling

---

## ✅ Week 2: Frontend & E2E

### 2.1 Frontend Unit Tests
- [ ] Angular: Jest + Testing Library
- [ ] Next.js: Jest + React Testing Library
- [ ] Component tests

### 2.2 E2E Tests (Playwright)
- [ ] Full checkout flow
- [ ] Admin panel CRUD
- [ ] Auth flows

### 2.3 Performance Tests
- [ ] Load testing (k6)
- [ ] Database query analysis
- [ ] Cache hit rate

### 2.4 Security
- [ ] OWASP ZAP scan
- [ ] Dependency audit:
  - JVM: OWASP Dependency-Check (`mvn org.owasp:dependency-check-maven:check`)
  - JS: `npm audit --audit-level=high`
  - Fail build on critical/high findings

---

## ✅ Definition of Done
- [ ] Backend coverage > 80%
- [ ] E2E tests pass
- [ ] No critical security issues
- [ ] Performance baseline established
