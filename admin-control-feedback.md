Admin Control Feedback and Improvement Ideas

Current project context
-----------------------
Your admin plan already has a strong foundation:

- Keycloak is the identity provider.
- The realm is `practice`.
- The frontend client is `payment-web`.
- The `ADMIN` realm role already exists.
- API Gateway and backend services validate Keycloak JWTs.
- Product reads are public.
- Users, orders, and payments require authentication.
- Product creation already requires `ROLE_ADMIN`.
- `GET /users/me` creates an application profile for a new Keycloak subject.
- Payment-service uses a private service token for internal order updates.
- `/internal/**` should remain private and unavailable through the public gateway.
- Bakong secrets must never be exposed to the frontend.

Recommended admin menu
----------------------

Admin Dashboard
├── Overview
├── Users
├── Products
├── Orders
├── Payments
├── Audit Logs
├── System
└── Settings


1. Overview Dashboard
---------------------

Keep the dashboard useful and operational, not only decorative.

Recommended cards:

- Revenue Today
- Revenue This Week
- Orders Today
- Pending Payment Orders
- Paid Orders
- Cancelled Orders
- Verified Payments
- Unconfirmed Payments
- Mismatched Payments
- Expired Payments
- Total Users
- Active Products

Example:

Revenue Today        125,000 KHR
Revenue This Week    1,840,000 KHR

Orders Today         24
Pending Payment       5
Paid                 18
Cancelled             1

Payment Issues        3
Users                120
Products              35

Make dashboard cards clickable.

Examples:

Payment Issues
→ Open Payments page filtered by MISMATCH + UNCONFIRMED

Pending Orders
→ Open Orders page filtered by PENDING_PAYMENT

Also show:

- Recent orders
- Recent payment verifications
- Payment failures
- Service-health warnings
- Bakong configuration warning when required config is missing

The Overview page should be read-only.


2. Users
--------

Recommended features:

- Search by name
- Search by email
- Search by application user ID
- Search by Keycloak subject
- View profile details
- View account creation time
- View order count
- View order history
- Show whether the profile is linked to Keycloak

Important:

Do not automatically connect an old database user to a Keycloak account by matching email.

Keycloak identity and application profile are separate concepts.

For the first version, keep Users mostly read-only.

Later you can add:

- Enable account
- Disable account

But define clearly whether the action affects:

- Keycloak account
- Application profile
- Both

Backend authorization must decide access, not only the frontend.


3. Products
-----------

Recommended features:

- Search products
- Filter by status
- Create product
- Edit product name
- Edit KHR price
- Activate product
- Deactivate product
- View created date
- View updated date
- View whether existing orders reference the product

Recommended product status:

public enum ProductStatus {
    ACTIVE,
    INACTIVE
}

Prefer deactivation instead of hard delete.

Reason:

Historical orders may still reference the product.

Example:

Products

Search...                         [+ Add Product]

ID   Product       Price        Status       Updated
----------------------------------------------------
1    Notebook      5,500 KHR    ACTIVE       26 Sep
2    Keyboard     45,000 KHR    ACTIVE       25 Sep
3    Old Mouse    12,000 KHR    INACTIVE     20 Sep

Use BigDecimal for money.

Product prices must always be validated by the backend.


4. Orders
---------

Recommended features:

- Search by order ID
- Search by user ID
- Filter by order status
- Filter by creation date
- View user information
- View product information
- View quantity
- View saved unit price
- View total
- View currency
- View order status
- Open related payment
- View payment status

Important rule:

Admin must NOT have a button like:

[Mark Paid]

Only payment-service verification should move:

PENDING_PAYMENT
→ PAID

Optional future admin action:

PENDING_PAYMENT
→ CANCELLED

But only allow cancellation when payment is not VERIFIED.

Backend must always validate again before cancelling.


5. Payments
-----------

This should be one of the strongest admin pages because Bakong payment handling is important.

Recommended features:

- Search by payment ID
- Search by order ID
- Search by MD5 identifier
- Filter by payment status
- Filter by amount
- Filter by currency
- Filter by creation date
- Filter by expiry date
- View related order
- View payment amount
- View currency
- View status
- View created time
- View expiry time
- View paid time
- View Bakong transaction hash where permitted
- View from account where permitted
- View to account where permitted
- View mismatch reason
- Retry normal payment verification

Statuses:

PENDING
VERIFIED
UNCONFIRMED
MISMATCH
EXPIRED

Example detail page:

Payment #184

Order:
ORD-184

Status:
MISMATCH

Expected:
Amount      11,000 KHR
Account     the configured receiving Bakong account

Bakong transaction:
Amount      10,000 KHR
Currency    KHR
Hash        ...
Paid At     ...

Problem:
AMOUNT_MISMATCH

[Retry Verification]

Never add:

[Mark Verified]

Never add:

[Mark Order Paid]

Admin should investigate and retry the normal verification flow instead of bypassing payment-service rules.


6. Audit Logs
-------------

This is the main feature I recommend adding to your current plan.

Create a dedicated Audit Logs page.

Purpose:

Track who changed what, when, and on which record.

Suggested table:

admin_audit_log
----------------
id
admin_subject
action
entity_type
entity_id
old_value
new_value
ip_address
created_at

Suggested audit actions:

PRODUCT_CREATED
PRODUCT_UPDATED
PRODUCT_DEACTIVATED
USER_DISABLED
USER_ENABLED
PAYMENT_VERIFY_RETRY
ORDER_CANCELLED

Example:

2026-09-26 15:42
SEYHA THEOUN
PRODUCT_UPDATE
Product #14

Old:
price = 5500

New:
price = 6500

Audit logs should be read-only from the admin UI.

Do not allow admins to delete their own audit history.


7. System Page
--------------

I recommend making System a separate page instead of putting everything inside Settings.

Example:

System Health

Discovery Service       UP
API Gateway             UP
User Service            UP
Product Service         UP
Order Service           UP
Payment Service         UP
PostgreSQL              UP
Bakong API              Available

Bakong Configuration

Account ID              Configured
Token                   Configured
Currency                KHR
QR Lifetime             5 minutes

Never show the actual token.

Good:

Token: Configured

Bad:

Token: eyJhbGciOi...

The System page can also show:

- Eureka registration state
- Backend service health
- Gateway health
- Database connectivity state
- Bakong API state
- Current application version
- Current environment such as development or production


8. Settings
-----------

Keep Settings limited to safe business/application settings.

Possible settings:

- Default currency
- QR lifetime display
- Website name
- Support email
- Maintenance message
- Feature toggles that are safe for admin use

Do NOT provide browser forms for:

- Bakong token
- Internal service token
- Keycloak client secret
- Database password
- Production credentials

Those should remain environment/server secrets.


9. Authorization
----------------

Keep the current `ADMIN` role for now.

Do not add many roles yet.

Current:

ADMIN

is enough.

Later, only if a real permission boundary is needed, you can introduce:

ADMIN
SUPPORT_ADMIN
CATALOG_ADMIN

Example:

ADMIN
- Full application administration

SUPPORT_ADMIN
- Users
- Orders
- Payments read/retry

CATALOG_ADMIN
- Product management

But do not add this complexity yet.

Important backend rules:

- Anonymous admin request → 401
- Authenticated non-admin → 403
- Valid ADMIN → allowed
- `/internal/**` → never routed publicly
- Backend must verify ADMIN role
- Frontend `isAdmin` is only for navigation/UI
- Never trust frontend authorization alone


10. Recommended Admin UI Layout
-------------------------------

Suggested structure:

┌─────────────────────────────────────────────────────────┐
│ Logo        Admin Console             🔔   Admin ▼       │
├───────────────┬─────────────────────────────────────────┤
│               │                                         │
│ Dashboard     │  Overview                               │
│ Users         │                                         │
│ Products      │  [Revenue] [Orders] [Payments] [Users] │
│ Orders        │                                         │
│ Payments      │  Revenue Chart                          │
│               │                                         │
│ Audit Logs    │  Recent Orders                          │
│ System        │                                         │
│ Settings      │  Payment Issues                         │
│               │                                         │
└───────────────┴─────────────────────────────────────────┘


11. Recommended Frontend Stack
------------------------------

For the admin frontend:

- Next.js
- Tailwind CSS
- shadcn/ui
- RTK Query
- Keycloak
- Recharts
- Lucide icons

Use Motion only for small transitions.

Avoid heavy animation because admin screens should prioritize:

- readability
- speed
- tables
- filters
- operations
- status visibility


12. Recommended Tables
----------------------

Every admin table should support:

- Loading state
- Empty state
- Error state
- 401 state
- 403 state
- 404 state
- 503 state
- Retry action
- Pagination
- Search
- Server-side filters when data becomes large

Recommended examples:

Users:
- ID
- Name
- Email
- Keycloak linked
- Orders
- Created At

Products:
- ID
- Name
- Price
- Status
- Updated At

Orders:
- ID
- User
- Product
- Amount
- Status
- Created At

Payments:
- ID
- Order
- Amount
- Status
- Created
- Expires
- Paid At


13. Security Recommendations
----------------------------

Keep these rules strict:

- Never store Bakong token in localStorage
- Never expose access token in API response
- Never expose service token in API response
- Never put credentials in query parameters
- Never print secrets in logs
- Never show secret values in Swagger examples
- Never allow frontend-only role checks to protect backend APIs
- Never expose `/internal/**` through API Gateway
- Never let admin manually mark payment VERIFIED
- Never let admin directly mark order PAID

Sensitive actions should require confirmation.

Examples:

Deactivate Product?
Cancel Order?
Disable User?

For destructive actions, show a confirmation dialog.


14. Recommended Backend API Shape
---------------------------------

Dashboard:

GET /admin/dashboard

Users:

GET /admin/users
GET /admin/users/{id}

Later:

PATCH /admin/users/{id}/status

Products:

GET   /admin/products
POST  /admin/products
PATCH /admin/products/{id}

Prefer:

PATCH /admin/products/{id}/status

instead of hard delete.

Orders:

GET /admin/orders
GET /admin/orders/{id}

Optional later:

POST /admin/orders/{id}/cancel

Payments:

GET  /admin/payments
GET  /admin/payments/{paymentId}
POST /admin/payments/{paymentId}/retry-verification

Audit:

GET /admin/audit-logs

System:

GET /admin/system/health
GET /admin/system/configuration-status


15. Standard Response Format
----------------------------

Continue using the project's current response format.

Success:

{
  "status": "success",
  "message": "...",
  "data": {}
}

Error:

{
  "status": "error",
  "message": "...",
  "details": {}
}


16. Recommended Development Phases
----------------------------------

Phase 1 - Admin Foundation

- Admin layout
- Keycloak ADMIN protection
- Dashboard
- Read-only Users
- Read-only Orders
- Read-only Payments
- Security tests

Phase 2 - Product Management

- Product create
- Product edit
- Product deactivate
- Product validation
- Audit Logs

Phase 3 - Operations

- Payment investigation
- Retry payment verification
- Order detail
- Payment detail
- System Health

Phase 4 - User Support

- User account lookup
- Enable/disable account
- Order cancellation
- Advanced search
- Advanced filtering
- Export if needed

Phase 5 - Future

- Granular roles
- Analytics
- Notifications
- Inventory
- Refund workflow
- Advanced reports


17. Features I Would Not Add Yet
--------------------------------

Do not add unnecessary complexity yet:

- Kafka
- RabbitMQ
- Redis
- Another identity provider
- Separate admin database
- Complex permission engine
- Many admin roles
- Manual payment state editing

Keep using:

Admin Browser
    ↓
Next.js
    ↓
API Gateway
    ↓
Spring services
    ↓
Service-specific database

And keep Keycloak as the identity provider.


18. Final Recommended Admin Menu
--------------------------------

Admin Dashboard
├── Overview
│   ├── Revenue
│   ├── Orders
│   ├── Payments
│   ├── Users
│   └── Recent Activity
│
├── Users
│   ├── User List
│   └── User Detail
│
├── Products
│   ├── Product List
│   ├── Add Product
│   └── Edit Product
│
├── Orders
│   ├── Order List
│   └── Order Detail
│
├── Payments
│   ├── Payment List
│   ├── Payment Detail
│   └── Payment Investigation
│
├── Audit Logs
│
├── System
│   ├── Service Health
│   ├── Eureka Status
│   └── Bakong Configuration Status
│
└── Settings


Main feedback
-------------

Your current admin plan is already well designed.

The most important additions I recommend are:

1. Audit Logs
2. Dedicated System Health page
3. Strong Payment Investigation page
4. Safe order cancellation for unpaid orders
5. Clickable dashboard cards
6. Better operational status visibility
7. Product deactivation instead of hard delete
8. Keep all final authorization decisions in the backend

The most important architectural rule should remain:

Admin can observe, manage, retry, deactivate, and investigate.

Admin must NOT bypass:

- Keycloak authorization
- payment-service verification
- order payment rules
- private internal APIs
- Bakong security boundaries
