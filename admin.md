# Admin Control Plan

This document describes the planned admin-control work for the current Spring
microservices practice project. It is a plan only. No application code,
database schema, API, or frontend behavior is changed by this document.

## Current foundation

- Keycloak is the identity provider and issues OpenID Connect access tokens.
- The realm is `practice` and the frontend client is `payment-web`.
- The `ADMIN` realm role already exists.
- The API gateway and services validate Keycloak JWTs.
- Product reads are public.
- User profiles, orders, and payments require authentication.
- Creating products and legacy user records requires `ROLE_ADMIN`.
- `GET /users/me` creates a profile for a new Keycloak subject.
- Existing database users and orders are intentionally separate from new
  Keycloak accounts.
- Payment-service uses a private service token for the internal order update.

## Admin frontend baseline

The separate admin frontend is located at:

```text
/home/seyha/web/admin-payment
```

It is currently a Next.js 16.3.6 starter application with React and Tailwind
support. Its current page is still the generated Next.js welcome screen. It
does not yet contain:

- Keycloak authentication or an OIDC client
- An API client or gateway proxy configuration
- An admin route or protected layout
- Dashboard cards
- Users, products, orders, or payments screens
- Audit-log or system-health screens
- Shared admin types, loading states, or error handling

Therefore the admin implementation should begin with the application shell and
authentication foundation before adding the website-control tables. The admin
frontend must call the API gateway on port `8080`; it must not call individual
Spring services or PostgreSQL directly.

## First backend slice implemented

The existing services now expose the first role-protected admin slice:

```text
GET  /admin/users
GET  /admin/users/{id}
GET  /admin/users/stats
GET  /admin/products
GET  /admin/products/{id}
POST /admin/products
PATCH /admin/products/{id}
PATCH /admin/products/{id}/status
GET  /admin/products/stats
GET  /admin/orders
GET  /admin/orders/{id}
GET  /admin/orders/stats
GET  /admin/payments
GET  /admin/payments/{id}
POST /admin/payments/{id}/retry-verification
GET  /admin/payments/stats
```

Each route is routed through the API gateway and protected in the owning
service with `hasRole('ADMIN')`. Payment responses use the existing safe DTO and
do not expose QR text, MD5, Bakong credentials, or service tokens. These routes
are intentionally read-only for users, orders, and payments; there is no manual
payment verification or manual order-payment transition.

## Admin goals

The admin area should allow an authorized administrator to:

1. See a dashboard summary.
2. Manage products.
3. Review users and their application profiles.
4. Review orders and payment states.
5. Investigate failed, mismatched, expired, or unconfirmed payments.
6. Use safe, auditable actions without exposing Bakong credentials.

The admin area must never become a way to bypass Keycloak authorization or the
payment-service/order-service service boundary.

## Website admin menu

The first admin release should look like a practical control panel for this
website:

```text
Admin Dashboard
├── Overview
├── Users
├── Products
├── Orders
├── Payments
└── Settings
```

### Overview

Show a small set of useful cards and recent activity:

- Total registered application users
- Total active products
- Orders created today and this week
- Orders by `PENDING_PAYMENT`, `PAID`, and `CANCELLED`
- Payments by `PENDING`, `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, and `EXPIRED`
- Recent orders and recent payment verification results
- A warning when payment configuration or a service is unavailable

The overview is read-only. It must not expose Bakong tokens or private service
credentials.

### Users

The user page should let an admin:

- Search by name, email, application user ID, or Keycloak subject
- View profile details and account creation time
- See how many orders belong to the application profile
- Open the user's order history
- See whether the profile is linked to a Keycloak account
- Disable or re-enable a Keycloak account only after the account-management
  contract is explicitly defined

The page must distinguish a Keycloak identity from an application profile. An
old database user must not be linked automatically by matching email.

### Products

The product page should let an admin:

- Search and filter products
- Create a product
- Edit product name and KHR price
- Mark a product active or inactive
- Review when a product was created or changed
- See whether existing orders refer to the product

Do not hard-delete a product that is referenced by an order unless retention
behavior is defined. Prefer inactive products so historical orders remain
readable. Product price validation must use positive `BigDecimal` KHR values.

### Orders

The order page should let an admin:

- Search by order ID or user ID
- Filter by order status
- Filter by creation date
- View user, product, quantity, saved unit price, total, currency, and status
- Open the related payment record when one exists
- See whether payment is waiting, verified, mismatched, or expired

The admin may inspect orders but must not directly change an order to `PAID`.
The existing payment verification flow remains the only source of a successful
payment transition.

### Payments

The payment page should let an admin:

- Search by payment ID, order ID, or MD5 identifier without displaying secrets
- Filter by payment status
- Filter by amount, currency, and creation or expiry date
- View amount, KHR currency, order relationship, status, expiry, paid time,
  Bakong hash, and account IDs where already permitted by the safe payment DTO
- See a clear reason for `UNCONFIRMED`, `MISMATCH`, or `EXPIRED`
- Open the related order
- Retry a normal verification request only if that action is explicitly needed
  and still follows payment-service rules

An admin must not manually set `VERIFIED`, `PAID`, or a Bakong transaction hash.
Admin support should investigate and retry the normal verification flow rather
than bypassing it.

### Settings

The settings page should be limited to safe operational information:

- Current service and Eureka registration status
- Keycloak realm and frontend client name
- Payment currency (`KHR`)
- QR lifetime (`5 minutes`)
- Bakong configuration state: configured or missing, never the secret value

Do not add forms that edit Bakong tokens, internal service tokens, or production
credentials in the browser.

## Proposed architecture

```text
Admin browser
    |
    | OIDC access token with ADMIN realm role
    v
Next.js admin routes and components
    |
    | API gateway only
    v
Gateway authorization
    |
    +--> user-service
    +--> product-service
    +--> order-service
    +--> payment-service
```

Use the existing gateway and REST communication style. Do not add Kafka,
RabbitMQ, Redis, a second identity provider, or a separate admin database for
this phase.

## Authorization model

### Keycloak roles

- Keep `ADMIN` as the realm role for administrators.
- Keep ordinary registered users without `ADMIN`.
- Do not trust a frontend-only `isAdmin` flag. Every admin endpoint must check
  the JWT role in the backend.
- Keep the Keycloak admin console separate from the application admin UI.
- Decide later whether a narrower role such as `CATALOG_ADMIN` or
  `SUPPORT_ADMIN` is needed. Do not add it until a real permission boundary is
  identified.

### Backend rules

- Gateway rejects unauthenticated admin requests with `401`.
- Services reject authenticated non-admin requests with `403`.
- Service-to-service calls continue using Eureka and REST.
- `/internal/**` remains unavailable through the public gateway.
- Bakong tokens, internal service tokens, and transaction credentials never
  appear in admin responses, browser storage, Swagger examples, or logs.

## Admin API plan

All responses keep the existing envelope:

```json
{
  "status": "success",
  "message": "...",
  "data": {}
}
```

### Dashboard

Prefer a small read-only aggregation endpoint after the individual service
contracts are stable:

```http
GET /admin/dashboard
```

The response may contain counts such as total users, products, orders by order
status, and payments by payment status. It must not contain passwords, JWTs,
Bakong tokens, QR secrets, or full private transaction details.

If aggregation would create unnecessary coupling, the frontend can initially
load separate admin read endpoints and combine the counts locally.

### User administration

Possible endpoints:

```http
GET   /admin/users
GET   /admin/users/{id}
PATCH /admin/users/{id}/status
```

First define what a user status means. Keycloak account enable/disable and the
application profile are different concerns. The first implementation should
read users safely before adding disable or delete actions.

Do not allow an admin action to silently link an old database user to a new
Keycloak subject by email. Add an explicit migration workflow later if that is
required.

### Product administration

Current product administration requires `ADMIN` and now supports:

```http
GET    /admin/products
POST   /admin/products
PATCH  /admin/products/{id}
PATCH  /admin/products/{id}/status
GET    /admin/products/stats
```

Products are deactivated with the status endpoint; hard deletion is not
available. Existing orders keep their saved unit price and product reference.

Validate prices as positive KHR `BigDecimal` values. The frontend must never be
the source of truth for money or authorization.

### Order administration

Current read-only endpoints:

```http
GET /admin/orders
GET /admin/orders/{id}
GET /admin/orders/stats
```

Support filtering by order status, date, user ID, and order ID. Admin reads may
see order ownership and product references, but must preserve the existing
database-per-service model.

Do not add a public admin endpoint that directly changes an order to `PAID`.
Only successful payment verification may use the internal payment transition.

### Payment administration

Possible endpoints:

```http
GET /admin/payments
GET /admin/payments/{paymentId}
```

Allow filtering by `PENDING`, `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, and
`EXPIRED`. Return safe payment information only: payment ID, order ID, amount,
currency, status, timestamps, and an existing transaction hash if the API
design already exposes it.

Do not add an admin action that marks a payment `VERIFIED` manually. If a
support workflow is needed, design a separate audited dispute or review state
instead of changing the verified payment state.

## Frontend admin plan

### Route and layout

Add a protected route such as `/admin`. On load:

1. Initialize Keycloak with the existing PKCE flow.
2. Refresh the access token when needed.
3. Check the `ADMIN` role from the token for navigation only.
4. Let the backend make the final authorization decision.
5. Show a clear `403` state for signed-in non-admin users.

Use the existing frontend design system and API proxy. Keep admin screens
separate from customer checkout screens.

### First screens

Implement in this order:

1. Admin dashboard summary.
2. Product table with create/edit and safe validation.
3. Orders table with status filters and detail view.
4. Payments table with verification state filters.
5. User profile table.

Every table needs loading, empty, error, unauthorized, and retry states. Use
pagination or server-side filters before the dataset becomes large.

### Sensitive actions

- Require a confirmation step for product deactivation and any account status
  change.
- Never display access tokens or service credentials.
- Never place Bakong credentials in `localStorage`, query parameters, or API
  responses.
- Avoid destructive delete actions until retention and order-history behavior
  are defined.

## Data and migration plan

1. Keep Keycloak as the source of authentication and realm roles.
2. Keep user-service as the source of application profile data.
3. Add `createdBySubject` or equivalent audit fields only when an admin write
   endpoint is introduced.
4. Add database migrations rather than relying on an implicit schema change.
5. Preserve existing orders and payment records.
6. Do not merge old users with Keycloak users automatically.

## Testing plan

### Security tests

- Anonymous admin request returns `401`.
- Authenticated non-admin request returns `403`.
- Admin request with a valid `ADMIN` role succeeds.
- Expired or invalid JWT returns `401`.
- Gateway does not route `/internal/**`.
- Internal order payment requires the service token.

### API tests

- Admin product create, update, and list behavior.
- Product price validation with KHR `BigDecimal`.
- Admin order and payment filters.
- Safe payment response fields.
- Existing customer order ownership remains enforced.
- Existing records are not automatically linked by matching email.

### Frontend tests

- Non-admin users do not see admin navigation.
- Direct `/admin` navigation shows the unauthorized state for non-admins.
- Admin refresh and token renewal work after the access token expires.
- Tables handle empty, loading, `401`, `403`, `404`, and `503` responses.
- No credential values appear in rendered markup, browser storage, or logs.

## Delivery phases

### Phase 1: Read-only admin foundation

- Confirm Keycloak `ADMIN` role assignment.
- Add backend role-protected read endpoints.
- Add `/admin` route and dashboard shell.
- Add security and response-contract tests.

### Phase 2: Product management

- Add admin product list, edit, and safe deactivation.
- Decide soft-delete behavior before changing the product schema.
- Add audit information and tests.

### Phase 3: Operations views

- Add order and payment tables with filters.
- Add payment detail and failure investigation views.
- Keep payment state transitions owned by payment-service verification.

### Phase 4: User support

- Add safe profile lookup.
- Define Keycloak account disable behavior.
- Design an explicit migration flow if old records must be linked later.

## Acceptance criteria

- Only an authenticated Keycloak user with `ADMIN` can access admin APIs.
- Non-admin customers can still browse public products and use their own
  checkout flow.
- Admin cannot manually mark a payment verified or bypass order payment rules.
- Existing users, orders, and payments remain intact.
- All APIs use the standard success and error envelopes.
- No secret or token is exposed to the frontend, Swagger, logs, or responses.
- The complete backend test suite and frontend lint, typecheck, and build pass.
