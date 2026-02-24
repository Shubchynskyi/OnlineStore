# 🚀 Stage 7: Deployment
<!-- markdownlint-disable MD040 -->

**Duration**: 1 week | **Goal**: Production-ready deployment

---

## ✅ Infrastructure

### Kubernetes / Docker Swarm
- [ ] Namespace: `online-store`
- [ ] Deployments: api-gateway, backend, telegram-bot, store-frontend, admin-panel
- [ ] Services: ClusterIP for internal, LoadBalancer for external
- [ ] ConfigMaps & Secrets
- [ ] HPA (Horizontal Pod Autoscaler)

### Ingress (Nginx)
- [ ] SSL termination (Let's Encrypt / cert-manager)
- [ ] Routing:
  - `api.store.com` → api-gateway
  - `store.com` → store-frontend
  - `admin.store.com` → admin-panel

### CI/CD Release Pipeline
- [ ] Build Docker images for deployable services (`api-gateway`, `backend`, `telegram-bot`, `store-frontend`, `admin-panel`)
- [ ] Push images to container registry (GHCR/Docker Hub/private registry)
- [ ] Tag images by commit SHA and release tag
- [ ] Deploy to staging on merge to `develop`
- [ ] Deploy to production on release tag

---

## ✅ Monitoring

### Observability Stack
- [ ] Prometheus + Grafana (metrics)
- [ ] Loki (logs)
- [ ] Jaeger (tracing)
- [ ] Alertmanager → Telegram

### Dashboards
- [ ] System metrics (CPU, RAM, Network)
- [ ] Application metrics (requests, latency, errors)
- [ ] Business metrics (orders, revenue)

---

## ✅ Backup & DR

- [ ] PostgreSQL backup (pg_dump, WAL archiving)
- [ ] Point-in-time recovery tested
- [ ] MinIO backup (cross-region replication)
- [ ] Disaster recovery plan documented

---

## ✅ Definition of Done
- [ ] All services running in K8s
- [ ] SSL configured
- [ ] Monitoring dashboards live
- [ ] Backup tested
- [ ] Runbook documented
