# OnlineStore

Scalable e-commerce platform with modular architecture.

## Tech Stack (Planned)

### Backend
- **Java 25** (LTS)
- **Spring Boot 4.0**
- **Spring Cloud Gateway** - API Gateway
- **PostgreSQL 17** - Primary/Replica setup
- **Redis 7.4** - Cache & Sessions
- **RabbitMQ 3.13** - Event Bus
- **Elasticsearch 8** - Search
- **Keycloak 26** - Authentication

### Frontend
- **Next.js 15** (React 19) - Store Frontend
- **Angular 19** (Signals) - Admin Panel
- **React Native 0.76+** - Mobile App

### DevOps
- **Docker** + **Docker Compose**
- **Kubernetes 1.31**
- **Prometheus** + **Grafana** - Monitoring
- **Jaeger** - Distributed Tracing
- **Loki** - Log Aggregation

## Quick Start

```bash
# Initialize project
task init

# Start infrastructure services
task up

# Access services
# Grafana:    http://localhost:3001
# RabbitMQ:   http://localhost:15672
# Keycloak:   http://localhost:8180
```

## Documentation

See [docs/](docs/) for detailed documentation.

## License

MIT License. See LICENSE.
