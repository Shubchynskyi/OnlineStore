# 📱 Stage 5: Mobile App (React Native)
<!-- markdownlint-disable MD040 -->

**Duration**: 2 weeks | **Stack**: React Native 0.76+, Expo, TypeScript

---

## 🏗️ Architecture

```
mobile-app/
├── app/                          # Expo Router
│   ├── (tabs)/
│   │   ├── index.tsx             # Home
│   │   ├── catalog.tsx
│   │   ├── cart.tsx
│   │   └── profile.tsx
│   ├── product/[id].tsx
│   ├── checkout/
│   └── _layout.tsx
├── components/
├── lib/
│   ├── api.ts
│   └── auth.ts
├── stores/                       # Zustand
└── hooks/
```

---

## ✅ Week 1: Core & Catalog

### 1.1 Setup
- [ ] `npx create-expo-app --template tabs`
- [ ] Expo Router configuration
- [ ] Auth (expo-auth-session + Keycloak)

### 1.2 Shared Code
- [ ] Reuse API client from store-frontend
- [ ] Reuse Zustand stores
- [ ] Shared types/DTOs

### 1.3 Home & Catalog
- [ ] Home screen (categories, featured)
- [ ] Catalog with filters
- [ ] Product details
- [ ] Image gallery

---

## ✅ Week 2: Cart, Checkout, Push

### 2.1 Cart & Checkout
- [ ] Cart screen
- [ ] Checkout flow (native)
- [ ] Payment (native card SDK or in-app browser for hosted checkout/PayPal)

Payment flow reference: [../architecture/payments-integration.md](../architecture/payments-integration.md).

### 2.2 Push Notifications
- [ ] expo-notifications setup
- [ ] Firebase Cloud Messaging integration
- [ ] Order status updates

### 2.3 Account
- [ ] Profile
- [ ] Order history
- [ ] Order tracking

---

## ✅ Definition of Done
- [ ] App runs on iOS and Android
- [ ] Full shopping flow works
- [ ] Push notifications work
- [ ] Ready for App Store / Google Play
