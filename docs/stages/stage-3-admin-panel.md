# 💼 Stage 3: Admin Panel (Angular 19)
<!-- markdownlint-disable MD040 -->

**Duration**: 3 weeks | **Stack**: Angular 19, Signals, Standalone Components, Angular Material

---

## 🏗️ Architecture

```
admin-panel/
├── src/app/
│   ├── app.component.ts          # Standalone root
│   ├── app.config.ts             # provideRouter, provideHttpClient
│   ├── app.routes.ts             # Lazy-loaded routes
│   ├── core/
│   │   ├── auth/                 # Keycloak OIDC
│   │   ├── interceptors/         # Auth, Error handling
│   │   └── guards/               # Role-based guards
│   ├── shared/
│   │   ├── ui/                   # Reusable UI components
│   │   └── pipes/                # Format pipes
│   └── features/
│       ├── dashboard/
│       ├── catalog/
│       ├── orders/
│       ├── users/
│       ├── payments/
│       └── settings/
```

---

## ✅ Week 1: Core Setup

### 1.1 Initialization
- [ ] `ng new admin-panel --standalone --style=scss --routing`
- [ ] Angular Material 19 + Custom theme
- [ ] Keycloak integration (`angular-auth-oidc-client`)

### 1.2 Core Module
- [ ] Auth Service (Keycloak)
- [ ] HTTP Interceptors (JWT, Error handler)
- [ ] Role Guards (`canMatch`)

### 1.3 Shared UI (Signals-based)
```typescript
// Example Signal-based component
@Component({
  selector: 'app-data-table',
  standalone: true,
  template: `
    <table>
      @for (item of items(); track item.id) {
        <tr>...</tr>
      }
    </table>
    @if (loading()) { <mat-spinner /> }
  `
})
export class DataTableComponent<T> {
  items = input.required<T[]>();
  loading = input(false);
  rowClick = output<T>();
}
```

---

## ✅ Week 2: Catalog & Orders

### 2.1 Dashboard
- [ ] Widgets: Orders today, Revenue, Low stock alerts
- [ ] Charts (ngx-charts)
- [ ] Real-time updates via WebSocket

### 2.2 Catalog Management
- [ ] Categories CRUD (tree view, drag-drop)
- [ ] Products CRUD
  - JSONB attributes editor
  - Image upload (drag-drop, MinIO)
  - Variants management
- [ ] Bulk import/export CSV

### 2.3 Orders Management
- [ ] Orders list with filters
- [ ] Order details + status change
- [ ] Print invoice/shipping label

---

## ✅ Week 3: Users, Payments, Settings

### 3.1 Users Management
- [ ] User list with search
- [ ] Role assignment
- [ ] Block/unblock

### 3.2 Payment Providers Settings
- [ ] Enable/disable providers
- [ ] Configure API keys
- [ ] View transactions

Payment configuration reference: [../architecture/payments-integration.md](../architecture/payments-integration.md).

### 3.3 Shipping Providers Settings
- [ ] Enable/disable per country
- [ ] Configure credentials
- [ ] Default provider per region

### 3.4 General Settings
- [ ] Store info (name, logo)
- [ ] Email templates
- [ ] Telegram bot config

---

## ✅ Definition of Done
- [ ] Login via Keycloak works
- [ ] All CRUD operations work
- [ ] Real-time updates via WebSocket
- [ ] Responsive design (mobile-friendly)
