# ⚙️ Stage 1: Backend Core
<!-- markdownlint-disable MD040 -->

**Duration**: 4 weeks  
**Stack**: Java 25, Spring Boot 4, PostgreSQL 17  
**Goal**: Create a modular monolith with core business logic.

---

## 🏗️ Backend Architecture

```
backend/
├── pom.xml                         # Parent POM (Maven Multi-module)
├── common/                         # Shared code
│   ├── src/main/java/.../common/
│   │   ├── entity/                 # Base entities
│   │   ├── dto/                    # Shared DTOs
│   │   ├── event/                  # Domain events
│   │   ├── exception/              # Custom exceptions
│   │   └── util/                   # Utilities
│   └── pom.xml
│
├── catalog-module/
├── orders-module/
├── users-module/
├── payments-module/
├── shipping-module/
├── notifications-module/
├── search-module/
│
└── application/                    # Main Spring Boot app
    ├── src/main/java/.../
    │   ├── Application.java        # @SpringBootApplication
    │   ├── config/                 # Global configs
    │   └── api/                    # REST Controllers
    └── pom.xml
```

---

## ✅ Week 1: Initialization and Common Module

### 1.1 Maven Multi-module Setup
- [ ] Create parent `pom.xml`:
  ```xml
  <parent>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-parent</artifactId>
      <version>4.0.0</version>
  </parent>
  
  <properties>
      <java.version>25</java.version>
  </properties>
  
  <modules>
      <module>common</module>
      <module>catalog-module</module>
      <module>orders-module</module>
      <module>users-module</module>
      <module>payments-module</module>
      <module>shipping-module</module>
      <module>notifications-module</module>
      <module>search-module</module>
      <module>application</module>
  </modules>
  ```
- [ ] Configure dependencies: Spring Data JPA, Security, Validation, Web
- [x] Add: Lombok, Testcontainers, and explicit Spring `*Mapper` classes as the standardized MapStruct replacement

### 1.2 Common Module
- [ ] **BaseEntity** (id, createdAt, updatedAt, version)
- [ ] **AuditableEntity** extends BaseEntity (createdBy, updatedBy)
- [ ] **PageResponse** — generic pagination DTO
- [ ] **ApiError** — standardized errors
- [ ] **DomainEvent** — base interface for events
- [ ] **OutboxEvent** — persistence model for reliable event publishing
- [ ] **OutboxPublisher** — background publisher for pending outbox events
- [ ] **Money** — Value Object for money (amount, currency)

### 1.3 Database Configuration
- [ ] Configure multiple datasources:
  ```java
  @Configuration
  public class DataSourceConfig {
      @Bean @Primary
      @ConfigurationProperties("spring.datasource.primary")
      public DataSource primaryDataSource() { ... }
      
      @Bean
      @ConfigurationProperties("spring.datasource.replica")
      public DataSource replicaDataSource() { ... }
  }
  ```
- [ ] Routing DataSource for read/write splitting
- [ ] Flyway for migrations
- [ ] Configure connection pooling (HikariCP)

### 1.4 Security Configuration
- [ ] OAuth2 Resource Server with Keycloak:
  ```java
  @Configuration
  @EnableMethodSecurity
  public class SecurityConfig {
      @Bean
      public SecurityFilterChain filterChain(HttpSecurity http) {
          return http
              .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
              .authorizeHttpRequests(auth -> auth
                  .requestMatchers("/api/v1/public/**").permitAll()
                  .requestMatchers("/api/admin/**").hasRole("ADMIN")
                  .anyRequest().authenticated()
              )
              .build();
      }
  }
  ```
- [ ] JWT Token Converter for roles from Keycloak
- [ ] CORS configuration

---

## ✅ Week 2: Users & Catalog Modules

### 2.1 Users Module
- [ ] **Entities**:
  | Entity | Fields |
  |--------|--------|
  | `User` | id, keycloakId, email, phone, status, roles |
  | `UserProfile` | firstName, lastName, avatar, birthDate |
  | `Address` | country, city, street, postalCode, isDefault |
  
- [ ] **Repository**: UserRepository with @Query for frequent operations
- [ ] **Service**: UserService
  - `findByKeycloakId()`
  - `syncFromKeycloak()` — synchronization on first login
  - `updateProfile()`
  - `addAddress()`, `removeAddress()`
- [ ] **API**: `/api/v1/users`
  | Method | Endpoint | Description |
  |--------|----------|-------------|
  | GET | `/me` | Current user |
  | PUT | `/me` | Update profile |
  | GET | `/me/addresses` | Address list |
  | POST | `/me/addresses` | Add address |
  
### 2.2 Catalog Module
- [ ] **Entities**:
  | Entity | Fields |
  |--------|--------|
  | `Category` | id, name, slug, parentId, sortOrder, image |
  | `Product` | id, name, slug, description, categoryId, status |
  | `ProductVariant` | id, productId, sku, price, stock, attributes (JSONB) |
  | `ProductImage` | id, productId, url, sortOrder, isMain |
  | `ProductAttribute` | id, productId, name, value (JSONB) |
  
- [ ] **JSONB for dynamic attributes**:
  ```java
  @Type(JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> attributes;
  // {"color": "red", "size": "XL", "material": "cotton"}
  ```
- [ ] **Repository**: ProductRepository
  - Specification API for filtering
  - `@EntityGraph` for loading optimization
- [ ] **Service**: ProductService, CategoryService
- [ ] **Authorization**: admin mutations (`create/update`) must be enforced at service layer (`@PreAuthorize`), not only at controller routes
- [ ] **Caching**: Redis for catalog
  ```java
  @Cacheable(value = "products", key = "#id")
  public ProductDTO findById(Long id) { ... }
  
  @CacheEvict(value = "products", key = "#id")
  public void update(Long id, ProductDTO dto) { ... }
  ```
- [ ] **Media Storage (MinIO/S3)**:
  - S3-compatible client for product images
  - Admin API: `POST /api/admin/media/uploads` -> pre-signed upload URL
  - Admin API: `POST /api/admin/products/{id}/images` -> attach image metadata
- [ ] **Events**: `ProductCreatedEvent`, `ProductUpdatedEvent`
- [ ] **API**:
  | Method | Endpoint | Description |
  |--------|----------|-------------|
  | GET | `/api/v1/public/catalog/categories` | Category tree |
  | GET | `/api/v1/public/catalog/categories/{slug}` | Category + products |
  | GET | `/api/v1/public/catalog/products` | List with filters |
  | GET | `/api/v1/public/catalog/products/{slug}` | Product details |

---

## ✅ Week 3: Orders & Payments Modules

### 3.1 Orders Module
- [ ] **Entities**:
| Entity | Fields |
|--------|--------|
| `Order` | id, userId, status, totalAmount, shippingAddress |
| `OrderItem` | id, orderId, productVariantId, quantity, price |
| `OrderStatus` | PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED |
| `Cart` | id, userId, totalAmount, currency, updatedAt |
| `CartItem` | id, cartId, productVariantId, quantity, price |
  
- [ ] **State Machine** for statuses:
  ```java
  @Configuration
  @EnableStateMachineFactory
  public class OrderStateMachineConfig 
      extends StateMachineConfigurerAdapter<OrderStatus, OrderEvent> {
      // PENDING -> PAID (on PAYMENT_RECEIVED)
      // PAID -> PROCESSING (on MANAGER_CONFIRM)
      // PROCESSING -> SHIPPED (on SHIPMENT_CREATED)
      // SHIPPED -> DELIVERED (on DELIVERY_CONFIRMED)
  }
  ```
- [ ] **Service**: OrderService
  - `createOrderFromCart()` — snapshot cart + reserve stock
  - `getCart()`, `addItem()`, `updateItemQuantity()`, `removeItem()`, `clearCart()`
  - `updateStatus()` — via State Machine
  - `cancelOrder()`
- [ ] **Authorization**: order status transition operations must enforce admin role checks at service layer
- [ ] **Events**: `OrderCreatedEvent`, `OrderStatusChangedEvent`
- [ ] **Outbox**: publish order events via outbox table
- [ ] **API**: `/api/v1/orders`
- [ ] **API (Cart)**:
  | Method | Endpoint | Description |
  |--------|----------|-------------|
  | GET | `/api/v1/cart` | Current cart |
  | POST | `/api/v1/cart/items` | Add item |
  | PATCH | `/api/v1/cart/items/{id}` | Update quantity |
  | DELETE | `/api/v1/cart/items/{id}` | Remove item |

### 3.2 Payments Module (Plugin Architecture)
Payment flow, webhooks, and status model: [../architecture/payments-integration.md](../architecture/payments-integration.md).

- [ ] **Interface**:
  ```java
  public interface PaymentProvider {
      String getProviderCode();
      Set<String> getSupportedCountries();
      PaymentResult createPayment(PaymentRequest request);
      PaymentResult confirmPayment(String paymentId, String idempotencyKey);
      RefundResult refund(String paymentId, Money amount, String idempotencyKey);
      boolean verifyWebhook(String payload, String signature, String timestamp);
  }
  ```
- [ ] **Implementations**:
  - [x] `CardPaymentProvider`
  - [ ] `PayPalPaymentProvider`
  - [x] `BankTransferPaymentProvider`
  - [x] `CryptoPaymentProvider` (optional)
- [ ] **PaymentProviderRegistry**:
  ```java
  @Component
  public class PaymentProviderRegistry {
      private final Map<String, PaymentProvider> providers;
      
      public PaymentProvider getProvider(String code) { ... }
      public PaymentProvider getProviderForCountry(String countryCode) { ... }
  }
  ```
- [ ] **Entities**: `Payment`, `PaymentStatus`, `PaymentProviderConfig`, `PaymentMutation`
- [ ] **Webhook Controller**: `/api/webhooks/payments/{provider}`
- [ ] **Create Payment Contract**: `POST /api/v1/payments` requires `orderId`, `providerCode`, `amount`, `currency`, `returnUrl`, and `idempotencyKey`
- [ ] **Confirm Payment Contract**: `POST /api/v1/payments/{id}/confirm` requires `idempotencyKey` and validates current-user order ownership before provider capture
- [ ] **Refund Contract**: `POST /api/admin/payments/{id}/refund` requires `amount`, `currency`, `idempotencyKey`, and admin/manager authorization
- [ ] **Idempotency**: persist create idempotency on `payments` and confirm/refund idempotency in durable `payment_mutations`
- [ ] **Webhook Security**: require signature + `X-Webhook-Timestamp` freshness window, and persist replay guard with unique `(provider_code, event_id)` constraint
- [ ] **Payment Integrity**: server-side order ownership and amount/currency validation before provider call
- [ ] **Outbox**: publish `payments.authorized`, `payments.completed`, `payments.failed`, and `payments.refunded` via outbox table (while preserving legacy `payment.completed`)

### 3.3 Shipping Module (Plugin Architecture)
- [x] **Interface**:
  ```java
  public interface ShippingProvider {
      String getProviderCode();
      Set<String> getSupportedCountries();
      List<ShippingRate> calculateRates(ShippingRequest request);
      Shipment createShipment(ShippingRequest request, ShippingRate selectedRate);
      TrackingInfo track(String trackingNumber);
      void cancelShipment(String shipmentId);
  }
  ```
- [x] **Implementations**:
  - [x] `DhlEuropeShippingProvider`
  - [ ] `DpdShippingProvider`
  - [ ] `GlsShippingProvider`
  - [ ] `FedExShippingProvider` (optional)
  - [x] `NovaPoshtaShippingProvider` (Ukraine)
  - [x] `StubShippingProvider` (disabled by default, available for local fallback/testing)
- [x] **ShippingProviderRegistry** — similar to PaymentProviderRegistry, backed by persisted `shipping_provider_configs`
- [x] **Entities**: `Shipment`, `ShipmentStatus`, `TrackingEvent`
- [x] **Admin Configuration**: enable/disable providers and supported countries via admin API for UI integration
- [x] **Access Control**: shipping operations must enforce order ownership at service layer

---

## ✅ Week 4: Search, Notifications, Integration

### 4.1 Search Module (Elasticsearch)
- [ ] **Index Mapping**:
  ```json
  {
    "mappings": {
      "properties": {
        "name": { "type": "text", "analyzer": "russian" },
        "description": { "type": "text" },
        "category": { "type": "keyword" },
        "price": { "type": "float" },
        "attributes": { "type": "object" },
        "inStock": { "type": "boolean" },
        "createdAt": { "type": "date" }
      }
    }
  }
  ```
- [ ] **SearchService**:
  - Full-text search
  - Faceted filtering (by category, price, attributes)
  - Autocomplete suggestions
- [ ] **RabbitMQ Listener** for synchronization:
  ```java
  @RabbitListener(queues = "product.events")
  public void handleProductEvent(ProductEvent event) {
      switch (event.getType()) {
          case CREATED, UPDATED -> elasticSearchService.index(event.getProduct());
          case DELETED -> elasticSearchService.delete(event.getProductId());
      }
  }
  ```
- [ ] **API**:
  | Method | Endpoint | Description |
  |--------|----------|-------------|
  | GET | `/api/v1/public/search/products?q=...&category=...&price_min=...` | Search |
  | GET | `/api/v1/public/search/suggest?q=...` | Autocomplete |

### 4.2 Notifications Module
- [x] **NotificationService** interface with `DefaultNotificationService`
- [x] **Channels**:
  - [x] `EmailNotificationChannel`
  - [ ] `SmsNotificationChannel` (Twilio / optional; intentionally left out of Stage 1 closure scope)
  - [x] `PushNotificationChannel` (Firebase-compatible adapter boundary, local stub delivery)
- [x] **Templates**: email bodies are rendered through the shared notification template service
- [x] **RabbitMQ Listener**:
  - `OrderNotificationListener` consumes `order.notification` and delegates `order.created` / `order.status-changed` events into the notification service contract

### 4.3 WebSocket for Real-time Updates
- [x] STOMP configuration:
  ```java
  @Configuration
  @EnableWebSocketMessageBroker
  public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
      @Override
      public void configureMessageBroker(MessageBrokerRegistry config) {
          config.enableSimpleBroker("/topic");
          config.setApplicationDestinationPrefixes("/app");
      }
  }
  ```
- [x] Topics:
  - `/topic/products/{id}` — product updates
  - `/topic/orders/{id}` — order status updates with minimal realtime payloads and owner-checked subscriptions
- [x] Broadcast on changes via RabbitMQ using dedicated `product.realtime` / `order.realtime` listeners in the application module
- [x] Broker guard blocks direct client `SEND` frames to `/topic/**`; clients publish only via `/app/**`

### 4.4 API Documentation
- [ ] SpringDoc OpenAPI (Swagger UI)
- [ ] API versioning via headers or path
- [ ] Rate Limiting (Bucket4j or Spring Cloud Gateway)

---

## 📋 Best Practices (Java 2025)

### Records for DTOs
```java
public record ProductDTO(
    Long id,
    String name,
    String slug,
    BigDecimal price,
    List<String> images
) {}
```

### Pattern Matching
```java
public String getStatusMessage(OrderStatus status) {
    return switch (status) {
        case PENDING -> "Awaiting payment";
        case PAID -> "Paid";
        case SHIPPED -> "In transit";
        case DELIVERED -> "Delivered";
        case CANCELLED -> "Cancelled";
    };
}
```

### Virtual Threads (Project Loom)
```java
@Bean
public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
    return protocolHandler -> {
        protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    };
}
```

### Structured Concurrency
```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var productFuture = scope.fork(() -> productService.findById(id));
    var reviewsFuture = scope.fork(() -> reviewService.findByProductId(id));
    
    scope.join().throwIfFailed();
    
    return new ProductDetailDTO(productFuture.get(), reviewsFuture.get());
}
```

---

## ✅ Definition of Done

- [x] All modules compile and tests pass
- [x] API documentation is available at `/swagger-ui.html`
- [x] Test coverage > 70% (`backend` reactor enforces aggregate JaCoCo line coverage during `verify`)
- [x] Docker image builds: `./mvnw spring-boot:build-image` (covered by the existing `backend-ci` docker job on `main`)
- [x] Health check endpoint: `/actuator/health`
- [x] Metrics endpoint: `/actuator/prometheus` (ADMIN/OPS)
