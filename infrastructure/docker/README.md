# Docker Infrastructure Configuration

This directory contains configuration files for Docker-based infrastructure services.

## Directory Structure

```
docker/
├── postgres/
│   ├── primary.conf            # Primary DB config
│   ├── replica.conf            # Replica DB config
│   ├── pg_hba.conf             # Auth + replication rules
│   └── init/
│       ├── 01-create-replication-user.sh
│       └── 02-create-keycloak-db.sh
├── prometheus/
│   └── prometheus.yml          # Prometheus scrape configuration
├── grafana/
│   ├── datasources/
│   │   └── datasources.yml     # Auto-provisioned datasources
│   └── dashboards/
│       ├── dashboards.yml      # Dashboard provisioning config
│       ├── spring-boot-dashboard.json
│       ├── postgresql-dashboard.json
│       ├── rabbitmq-dashboard.json
│       └── redis-dashboard.json
├── otel-collector/
│   └── config.yml              # OpenTelemetry Collector configuration
└── README.md
```

## Prometheus Configuration

The `prometheus/prometheus.yml` file configures:

- **Scrape targets:**
  - Spring Boot applications (Backend, API Gateway, Telegram Bot)
  - Infrastructure exporters (PostgreSQL, Redis, Elasticsearch)
  - RabbitMQ Prometheus plugin (:15692)
  - Monitoring tools (Grafana, Jaeger)

- **Scrape intervals:**
  - Default: 15s
  - Spring Boot apps: 10s (higher frequency for application metrics)

- **Service discovery:**
  - Uses `host.docker.internal` for Spring Boot apps running outside Docker
  - Uses service names for containerized services

## Grafana Configuration

### Datasources

Auto-configured datasources:
- **Prometheus** (default) - Metrics from all services
- **Loki** - Log aggregation
- **Jaeger** - Distributed tracing
- **Redis** - Requires `redis-datasource` plugin

### Dashboards

Four pre-configured dashboards:

1. **Spring Boot Applications**
   - JVM memory usage
   - HTTP request rate
   - CPU usage
   - Active threads
   - HTTP response times (p95)

2. **PostgreSQL Monitoring**
   - Active connections
   - Transaction rate (commits/rollbacks)
   - Replication lag (Primary → Replica)
   - Cache hit ratio
   - Database size

3. **RabbitMQ Events**
   - Message publish rate
   - Message consume rate
   - Queue depth
   - Connections/Channels/Consumers
   - Dead Letter Queue messages

4. **Redis Cache**
   - Memory usage
   - Hit rate
   - Commands per second
   - Connected clients
   - Keys by database
   - Evicted keys

## OpenTelemetry Collector

The collector is configured to:

- Receive OTLP data (gRPC/HTTP) from applications
- Export traces to Jaeger
- Export logs to Loki
- Expose OTLP metrics for Prometheus scraping
- Tail Docker container logs for development

**Note:** The collector (and Loki) are optional in early development. If you do not ship OTLP data or tail logs, you can still inspect logs with `docker logs`.

### Docker Desktop log path

When using Docker Desktop on Windows, container logs live inside the Linux VM.
Set `DOCKER_LOGS_PATH` in `.env` to a valid WSL path (for example, `//wsl$/docker-desktop-data/data/docker/containers`)
so the `filelog` receiver can read container logs. If the path is not set or invalid, Loki will stay empty and the collector may log warnings.

## Usage

### Starting Services

```bash
# Start all infrastructure services
task up

# Or directly with Docker Compose
docker compose up -d
```

### Accessing Grafana

1. Open http://localhost:3001
2. Login with `admin / (from .env)`
3. Datasources are auto-configured
4. Dashboards are auto-loaded

### Accessing Prometheus

1. Open http://localhost:9090
2. Query metrics directly or view targets

## Adding Custom Dashboards

To add a new dashboard:

1. Create a JSON file in `grafana/dashboards/`
2. Use the Grafana dashboard JSON format
3. Restart Grafana: `docker compose restart grafana`

Or create dashboards via Grafana UI and export as JSON.

## Metrics Endpoints

Spring Boot applications must expose Prometheus metrics:

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## Troubleshooting

### Metrics not appearing

1. Check if service is running: `docker compose ps`
2. Check Prometheus targets: http://localhost:9090/targets
3. Verify Spring Boot actuator is exposed: `curl http://localhost:8081/actuator/prometheus`

### Grafana dashboards not loading

1. Check datasources: Grafana → Configuration → Data sources
2. Verify volume mounts in `docker-compose.yml`
3. Check Grafana logs: `docker compose logs grafana`

## Notes

- Prometheus data is stored in a Docker volume (persistent across restarts)
- Grafana configurations are auto-loaded on container start
- To reset all data: `docker compose down -v`
- Loki receives logs via OpenTelemetry Collector (file tail + OTLP). If you are not using the collector, Loki can be disabled.
