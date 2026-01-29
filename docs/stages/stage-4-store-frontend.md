# 🛒 Stage 4: Store Frontend (Next.js 15)
<!-- markdownlint-disable MD040 -->

**Duration**: 3 weeks | **Stack**: Next.js 15, React 19, Server Components, Tailwind CSS

---

## 🏗️ Architecture

```
store-frontend/
├── app/
│   ├── layout.tsx                # Root layout
│   ├── page.tsx                  # Home page (SSR)
│   ├── (shop)/
│   │   ├── catalog/
│   │   │   ├── page.tsx          # Catalog (SSR)
│   │   │   └── [category]/
│   │   ├── product/[slug]/
│   │   ├── cart/
│   │   └── checkout/
│   ├── (account)/
│   │   ├── profile/
│   │   └── orders/
│   └── api/                      # API routes (if needed)
├── components/
│   ├── ui/                       # shadcn/ui components
│   ├── catalog/
│   ├── cart/
│   └── checkout/
├── lib/
│   ├── api.ts                    # Backend API client
│   ├── auth.ts                   # NextAuth config
│   └── utils.ts
└── hooks/
```

---

## ✅ Week 1: Core & Catalog

### 1.1 Initialization
- [ ] `npx create-next-app@latest --typescript --tailwind --app`
- [ ] shadcn/ui setup
- [ ] NextAuth.js with Keycloak provider

### 1.2 API Client
```typescript
// lib/api.ts
const api = {
  catalog: {
    getCategories: () => fetch(`${API_URL}/public/catalog/categories`).then(r => r.json()),
    getProducts: (params: SearchParams) => fetch(`${API_URL}/public/search/products?${qs(params)}`),
    getProduct: (slug: string) => fetch(`${API_URL}/public/products/${slug}`),
  },
  // ...
}
```

### 1.3 Home Page (SSR)
- [ ] Hero section
- [ ] Categories grid
- [ ] Featured products
- [ ] SEO meta tags

### 1.4 Catalog Pages
- [ ] Category page with filters (Server Component)
- [ ] Product card component
- [ ] Pagination
- [ ] Faceted search (price, attributes)

---

## ✅ Week 2: Product & Cart

### 2.1 Product Page
- [ ] Image gallery (zoom, swipe)
- [ ] Variant selector
- [ ] Add to cart
- [ ] Related products
- [ ] Reviews section

### 2.2 Cart (Client Component)
```typescript
// Zustand store for cart
const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],
      addItem: (product, variant, quantity) => { /* logic */ },
      removeItem: (variantId) => { /* logic */ },
      updateQuantity: (variantId, quantity) => { /* logic */ },
      syncWithServer: async () => { /* logic */ },
    }),
    { name: 'cart-storage' }
  )
)
```

### 2.3 Cart Page
- [ ] Cart items list
- [ ] Quantity controls
- [ ] Price calculation
- [ ] Proceed to checkout

---

## ✅ Week 3: Checkout & Account

### 3.1 Checkout Flow (Multi-step)
1. **Address** — Shipping address form
2. **Shipping** — Select carrier (API call to get rates)
3. **Payment** — Select payment method
4. **Review** — Confirm order
5. **Complete** — Order confirmation

### 3.2 Payment Integration
- [ ] Card SDK or hosted checkout
- [ ] PayPal checkout
- [ ] Bank transfer flow (SEPA/IBAN)
- [ ] Payment status confirmation after redirect (backend webhook + client polling)

Payment flow reference: [../architecture/payments-integration.md](../architecture/payments-integration.md).

### 3.3 Account Pages
- [ ] Profile settings
- [ ] Order history
- [ ] Order tracking (real-time via polling/WebSocket)
- [ ] Saved addresses

---

## 🎨 UI/UX Best Practices

- [ ] Skeleton loaders for data fetching
- [ ] Optimistic updates for cart
- [ ] Image optimization with `next/image`
- [ ] Lazy loading for below-fold content
- [ ] Mobile-first responsive design

---

## ✅ Definition of Done
- [ ] SSR pages have good Lighthouse score (>90)
- [ ] Full checkout flow works
- [ ] Cart persists across sessions
- [ ] Auth via Keycloak works
- [ ] Mobile responsive
