# 🤖 Stage 2: Telegram Bot Service
<!-- markdownlint-disable MD040 -->

**Duration**: 2 weeks | **Stack**: Java 25, Spring Boot 4, TelegramBots 7.x, OpenAI API

---

## ✅ Week 1: Base Bot

### 1.1 Initialization
- [ ] Spring Boot + TelegramBots starter
- [ ] Configuration: token, webhook URL
- [ ] Redis for dialog state storage

### 1.2 State Management
```java
public enum UserState {
    MAIN_MENU, BROWSING_CATALOG, SEARCHING,
    ENTERING_ADDRESS, CONFIRMING_ORDER, CHATTING_WITH_AI
}
```

### 1.3 Commands
| Command | Description |
|---------|-------------|
| `/start` | Welcome + menu |
| `/catalog` | Categories |
| `/search` | Product search |
| `/order` | Order status |
| `/cart` | Shopping cart |

### 1.4 Inline Keyboards
- Main menu (Catalog, Search, Cart, Orders)
- Product card (Add to cart, Details)
- Checkout flow

### T-002 Implementation Notes
- Transport mode is configuration-driven: empty `TELEGRAM_WEBHOOK_URL` keeps the bot in long-polling mode, while a configured webhook URL switches the bot to webhook delivery.
- `TELEGRAM_WEBHOOK_PATH` must match the path segment used in `TELEGRAM_WEBHOOK_URL`; the bot validates this at startup before registering the webhook.
- Dialog state is now persisted per user in Redis with TTL controlled by `TELEGRAM_SESSION_TTL`.
- Core routing currently covers `/start`, `/catalog`, `/search`, `/cart`, `/order`, main-menu callback routes, and stateful text capture for search/order/address/AI placeholder flows.

---

## ✅ Week 2: AI Chat & Notifications

### 2.1 Backend API Client (Feign)
- [ ] ProductClient, OrderClient, SearchClient

### 2.2 AI Chat (OpenAI)
- [ ] System prompt with store context
- [ ] RAG: search for relevant products via Elasticsearch
- [ ] Dialog history in Redis

### 2.3 Manager Notifications
- [ ] RabbitMQ Listener for events:
  - `orders.created` → New order
  - `orders.status-changed` → Status change
  - `products.low-stock` → Product running out
- [ ] Manager channel/group
- [ ] Inline buttons: Accept order, Call customer

---

## ✅ Definition of Done
- [ ] Bot responds to commands
- [ ] AI chat works
- [ ] Managers receive notifications
- [ ] Rate limiting works
