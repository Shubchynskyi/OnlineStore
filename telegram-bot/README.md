# Telegram Bot Service

Spring Boot 4 service that handles customer chat flows, AI assistant prompts, Redis-backed dialog state, and manager notifications for OnlineStore.

## Prerequisites

- Java 25
- Docker Desktop or another Docker runtime for Redis and RabbitMQ
- A valid Telegram bot token
- The backend service running on the URL configured by `BACKEND_API_BASE_URL`

## Local setup

1. Copy the root `.env.example` to `.env` and fill in at least the Telegram Bot section.
2. Start the shared infrastructure from the repository root:

```bash
docker compose up -d redis rabbitmq keycloak
```

3. Start the backend from the repository root:

```bash
task backend-run
```

4. Start the bot from the repository root:

```bash
task bot-run
```

On Windows you can also run the module directly:

```powershell
cd telegram-bot
.\mvnw.cmd spring-boot:run
```

## Required environment variables

The bot reads its configuration from the repository root `.env` file.

### Minimum for local polling mode

- `TELEGRAM_BOT_TOKEN` - required Telegram Bot API token
- `BACKEND_API_BASE_URL` - backend base URL, defaults to `http://localhost:8080`
- `REDIS_HOST` / `REDIS_PORT` - Redis connection for dialog state
- `RABBITMQ_HOST` / `RABBITMQ_PORT` - RabbitMQ connection for manager notifications

### Optional webhook mode

- `TELEGRAM_WEBHOOK_URL` - leave empty to keep long polling mode
- `TELEGRAM_WEBHOOK_PATH` - must match the path segment used in the webhook URL
- `TELEGRAM_WEBHOOK_SECRET_TOKEN` - required when webhook mode is enabled and must be at least 16 characters

### Optional manager actions

- `TELEGRAM_MANAGER_NOTIFICATIONS_ENABLED`
- `TELEGRAM_MANAGER_CHAT_ID` or `TELEGRAM_MANAGER_CHAT_IDS`
- `TELEGRAM_MANAGER_USER_ID` or `TELEGRAM_MANAGER_USER_IDS`
- `BACKEND_SERVICE_AUTH_ENABLED`
- `BACKEND_SERVICE_AUTH_CLIENT_ID`
- `BACKEND_SERVICE_AUTH_CLIENT_SECRET`
- `BACKEND_SERVICE_AUTH_TOKEN_URL`

### Optional AI assistant

- `TELEGRAM_AI_ASSISTANT_ENABLED`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`

See `../.env.example` for the full list.

## Testing

Run the module suite from the repository root:

```bash
task test-bot
```

Or run the Maven wrapper directly on Windows:

```powershell
cd telegram-bot
.\mvnw.cmd -q test
```

Contract tests included in the suite:

- `RedisUserSessionStoreContractTests` verifies real Redis serialization and TTL behavior.
- `ManagerNotificationsRabbitContractTests` verifies real RabbitMQ exchange, queue, binding, and listener delivery.

Both contract suites use Testcontainers and are skipped automatically when Docker is unavailable.

## Local validation checklist

1. Start the backend and bot with the variables above.
2. Send `/start` and confirm the inline main menu appears.
3. Send `/search`, enter a query, and confirm product results come from the backend.
4. Open `/catalog` and `/cart` to verify browsing and cart rendering.
5. If AI is enabled, send `/assistant` and confirm the reply references store products.
6. If manager notifications are enabled, trigger an order or low-stock event from the backend and confirm the manager chat receives it.

## Related documentation

- `../docs/stages/stage-2-telegram-bot.md`
- `../docs/getting-started.md`
- `../docs/quick-reference.md`
