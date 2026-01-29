# OnlineStore - Documentation

Index of all project documentation.

---

## Quick Start

| Document | Description |
|----------|-------------|
| [getting-started.md](getting-started.md) | Step-by-step development plan with checklists |
| [quick-reference.md](quick-reference.md) | Quick command and link reference |

---

## Architecture

| Document | Description |
|----------|-------------|
| [architecture/overview.md](architecture/overview.md) | Architecture diagrams (Mermaid) |
| [architecture/decisions.md](architecture/decisions.md) | Architecture Decision Records (ADR) |

---

## Development

| Document | Description |
|----------|-------------|
| [development/plan.md](development/plan.md) | General project plan and technology stack |

---

## Development Stages

| Stage | Name | Document |
|-------|------|----------|
| 0 | Infrastructure | [stages/stage-0-infrastructure.md](stages/stage-0-infrastructure.md) |
| 1 | Backend Core | [stages/stage-1-backend-core.md](stages/stage-1-backend-core.md) |
| 2 | Telegram Bot | [stages/stage-2-telegram-bot.md](stages/stage-2-telegram-bot.md) |
| 3 | Admin Panel | [stages/stage-3-admin-panel.md](stages/stage-3-admin-panel.md) |
| 4 | Store Frontend | [stages/stage-4-store-frontend.md](stages/stage-4-store-frontend.md) |
| 5 | Mobile App | [stages/stage-5-mobile-app.md](stages/stage-5-mobile-app.md) |
| 6 | Testing | [stages/stage-6-testing.md](stages/stage-6-testing.md) |
| 7 | Deployment | [stages/stage-7-deploy.md](stages/stage-7-deploy.md) |

---

## AI Instructions

Rules for AI assistants are defined in [../CLAUDE.md](../CLAUDE.md).

---

## Documentation Structure

```
docs/
├── README.md                 # This file (index)
├── getting-started.md        # Step-by-step plan
├── quick-reference.md        # Quick reference
│
├── architecture/
│   ├── overview.md           # Diagrams
│   └── decisions.md          # ADR
│
├── development/
│   └── plan.md               # Project plan
│
└── stages/
    ├── stage-0-infrastructure.md
    ├── stage-1-backend-core.md
    ├── stage-2-telegram-bot.md
    ├── stage-3-admin-panel.md
    ├── stage-4-store-frontend.md
    ├── stage-5-mobile-app.md
    ├── stage-6-testing.md
    └── stage-7-deploy.md
```

---

## Main Commands

```bash
# Infrastructure
task up              # Start Docker
task down            # Stop Docker
task logs            # Logs

# Development
task backend-run     # Backend (:8081)
task store-run       # Store Frontend (:3000)
task admin-run       # Admin Panel (:4200)

# Monitoring
task monitoring      # Grafana (:3001)
task tracing         # Jaeger (:16686)

# Testing
task test            # Backend tests (current)
```

---

## Navigation

- [Back to Project README](../README.md)
- [Rules for AI](../CLAUDE.md)
