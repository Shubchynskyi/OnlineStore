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

### T-003 Implementation Notes
- The bot now uses a dedicated backend integration layer built on Spring `RestClient` for catalog, search, cart, and orders APIs, with shared timeout, retry, and backend-error decoding behavior.
- Public flows already hit the backend directly: `/catalog` and the catalog main-menu callback load live category previews, while free-text search requests query `/api/v1/public/search/products` and return result summaries.
- Backend gateway settings are configurable via `BACKEND_API_BASE_URL`, `BACKEND_API_CONNECT_TIMEOUT`, `BACKEND_API_READ_TIMEOUT`, `BACKEND_API_RETRY_*`, and preview-size properties under `telegram.bot.backend-api.*`.
- Protected cart and order operations are intentionally separated from service-account auth: they require a customer bearer token resolved from the bot session attribute `backendAccessToken`, so the bot does not impersonate customers with client-credentials tokens.
- Optional service-to-service authentication for public backend calls can be enabled via `BACKEND_SERVICE_AUTH_*` and uses Keycloak client credentials without changing the customer-auth boundary.
- Service-auth token responses are now rejected unless Keycloak returns a JWT-shaped `access_token` with a positive `expires_in`, preventing invalid tokens from being cached and replayed across backend calls.
- User-facing bot replies now surface sanitized backend-failure messages, while detailed backend status/error/path diagnostics remain in server logs for troubleshooting.

### T-004 Implementation Notes
- `/start`, `/catalog`, and `/search` now drive inline-first customer UX while preserving dialog-state consistency in the Redis-backed session.
- Catalog browsing now supports pageable category navigation, per-category product cards, product detail views, and callback-driven return paths to category pages.
- Search flow now collects a free-text query, renders pageable product cards, opens product detail views, and keeps the active query/page in session attributes for callback pagination.
- Empty search results reuse the existing search integration for suggestion hints while detailed cards and detail views are rendered from public catalog product data.

### T-005 Implementation Notes
- `/cart` now opens a dedicated inline cart view with item-by-item quantity controls, remove actions, refresh, and a checkout entry point.
- Product detail views in both catalog browsing and search now expose add-to-cart buttons for active in-stock variants and redirect customers into the cart flow after a successful add.
- Checkout now supports saved-address selection plus guided step-by-step address capture in Telegram, then renders a confirmation screen before placing the order through the existing orders API.
- Successful checkout transitions the dialog into order-tracking mode with the created order id, while `/order` remains the follow-up status lookup path for numeric order ids.
- Cart and checkout callbacks now recover gracefully when backend data changes mid-conversation by refreshing the latest cart/address state instead of leaving the dialog stuck on stale data.

### T-006 Implementation Notes
- The bot now exposes an `/assistant` command and inline main-menu entry that open a dedicated AI chat flow for product discovery questions.
- Assistant requests are sent to OpenAI through a dedicated `RestClient` integration configured by `TELEGRAM_AI_ASSISTANT_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`, timeout settings, and bounded retry controls.
- Each assistant turn builds a store-aware prompt that combines live public search results with catalog product data so recommendations stay grounded in current OnlineStore inventory context.
- Conversation history and assistant token counters are stored inside the existing Redis-backed user session, survive route changes, and can be reset with the in-chat `Clear chat` action.
- The per-user interaction lock is now configurable via `TELEGRAM_INTERACTION_LOCK_TTL` and defaults to `4m` so Redis-backed assistant session updates remain serialized across the slower search + catalog + AI request path, including configured retry windows.
- Token/cost exposure is controlled with bounded input length, capped completion tokens, trimmed conversation history, and a per-session token budget that stops further OpenAI calls until the user clears the chat.
- When OpenAI is disabled or temporarily unavailable, the bot returns a sanitized fallback message without corrupting the dialog state and still directs users toward `/catalog` and `/search`.

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
