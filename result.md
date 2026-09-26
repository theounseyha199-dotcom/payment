# Backend project review

Reviewed on 2026-09-26. This note describes the code currently in this repository. It does not describe the separate Next.js customer or admin projects.

## Project at a glance

The active backend has six separate Gradle projects using Java 21, Spring Boot 4.1.1, Spring Cloud 2025.1.2, Eureka, Spring Cloud Gateway MVC, Spring Security resource servers, JPA, and PostgreSQL. `run-all.sh` starts PostgreSQL and Keycloak with Docker Compose, then starts the six Java services locally. The root `src/` application is an older starter app and is not started by this script.

| Service | Port | Database | Responsibility |
| --- | ---: | --- | --- |
| discovery-service | 8761 | — | Eureka registry |
| api-gateway | 8080 | — | Public routes and combined Swagger UI |
| user-service | 8081 | userdb | Application profiles and admin user queries |
| product-service | 8082 | productdb | Products, status, and product audit records |
| order-service | 8083 | orderdb | Orders and private payment status update |
| payment-service | 8084 | paymentdb | Individual KHQR, Bakong verification, payment audit records |

PostgreSQL listens locally on port 55432. Keycloak listens locally on port 8180, using realm `practice` and browser client `payment-web`. The realm defines `USER` and `ADMIN`; `USER` belongs to the default role, while `ADMIN` must be assigned to an account. Each data service owns its own database. Service calls use `RestClient` with Eureka service names.

## API and authorization

Call the application through `http://localhost:8080`. The gateway routes `/users/**`, `/products/**`, `/orders/**`, `/payments/**`, and their `/admin/**` equivalents. The combined Swagger UI is at `/swagger-ui.html`. There is no gateway route to `/internal/**`, and the gateway explicitly denies that path.

| Area | Current endpoints | Access |
| --- | --- | --- |
| Users | `POST /users`, `GET /users/me`, `GET /users/{id}`, `GET /users` | Bearer token; creation and list require `ADMIN`; `/me` creates a profile when needed; a normal user can read only their own ID |
| Products | `GET /products`, `GET /products/{id}`, `POST /products` | Reads public; creation requires `ADMIN` |
| Orders | `POST /orders`, `GET /orders`, `GET /orders/{id}` | Bearer token; customer list and detail use the JWT subject as owner |
| Payments | `POST /payments/qr`, `GET /payments/{paymentId}`, `POST /payments/{paymentId}/verify` | Bearer token; payment access checks the related order through order-service |
| Legacy payment verification | `POST /payments/verify` | Deprecated, hidden from Swagger; still accepts order ID and MD5 |
| Private order update | `PATCH /internal/orders/{orderId}/payment` | Direct service call with `X-Internal-Service-Token`; only `PAID` is accepted |

Admin controllers use `@PreAuthorize("hasRole('ADMIN')")`. Their current endpoints are:

| Owner | Admin endpoints | List filters |
| --- | --- | --- |
| user-service | `GET /admin/users`, `GET /admin/users/{id}`, `GET /admin/users/stats` | `search`, `page`, `size`, `sort` |
| product-service | `GET /admin/products`, `GET /admin/products/{id}`, `POST /admin/products`, `PATCH /admin/products/{id}`, `PATCH /admin/products/{id}/status`, `GET /admin/products/stats` | `search`, `status`, `page`, `size`, `sort` |
| order-service | `GET /admin/orders`, `GET /admin/orders/{id}`, `GET /admin/orders/stats` | `orderId`, `userId`, `status`, `from`, `to`, `page`, `size`, `sort` |
| payment-service | `GET /admin/payments`, `GET /admin/payments/{id}`, `POST /admin/payments/{id}/retry-verification`, `GET /admin/payments/stats` | `paymentId`, `orderId`, `status`, `currency`, amount and date ranges, pagination, sort |

Admin lists default to page 0 and size 20, with a maximum size of 100. Admin retry calls the same `PaymentService.verify` method as customer verification; there is no admin API to directly mark a payment `VERIFIED` or an order `PAID`. Product writes and payment verification retries create local admin audit records. Successful API responses use `{status, message, data}`; handled errors use `{status, message, details}`.

## Data and payment flow

`User` stores an application profile and optional Keycloak subject. `GET /users/me` creates a profile for a new subject. A legacy profile with the same email is not linked automatically. `Product` stores a `BigDecimal` price and `ACTIVE`/`INACTIVE` status. `PurchaseOrder` stores the user and product IDs, the product's price at order creation, quantity, total, owner subject, creation time, and `PENDING_PAYMENT`/`PAID`/`CANCELLED` status.

`PaymentQr` stores payment ID, order ID, KHQR text and MD5, `BigDecimal` amount, KHR currency, receiving account, status, Bakong transaction fields, failure reason, and timestamps. Statuses are `PENDING`, `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, and `EXPIRED`. The payment service uses Flyway migrations to preserve older records and a partial unique PostgreSQL index to allow at most one `PENDING` or `UNCONFIRMED` QR per order. The other data services use Hibernate `ddl-auto: update`.

```text
Keycloak login → application user profile → order (PENDING_PAYMENT)
    → payment-service reads the owned order through Eureka
    → Individual Bakong KHQR, KHR, five-minute expiry
    → payment-service checks Bakong by stored MD5
    → amount, currency, receiving account, and order checked
    → Payment VERIFIED → private order-service call → Order PAID
```

QR creation gets the amount from order-service and reuses an unexpired active QR. Expired unpaid QRs are marked `EXPIRED` before a new one is generated. Bakong verification uses the payment-service token from environment configuration. A successful verification is saved before the order update call; a later retry of an already `VERIFIED` payment skips Bakong and retries the order update. The API does not return the Bakong token.

`run-all.sh` reads local `.env`, starts PostgreSQL and Keycloak, starts Eureka and all data services, starts the gateway, waits for `/products` through the gateway, and writes logs to `.run/logs`. `.env` and `.run/` are ignored by Git. Use `.env.example` for variable names; never put real tokens or passwords in this note or source control.

## Findings to address

1. **Admin payment retry for another user's order can fail.** `AdminPaymentController.retry` calls `PaymentService.verify`, which calls `OrderClient.requireOrder`. That client requests customer `GET /orders/{id}` using the admin JWT, while `OrderService.getOwned` permits only the order's owner subject. An administrator investigating another user's payment can therefore receive an upstream 403, which `OrderClient` currently translates into a 503. Give this service-to-service lookup a narrowly authorized path that preserves customer ownership rules.
2. **Soft deactivation does not fully prevent purchase.** `GET /products` hides inactive products, but public `GET /products/{id}` still returns them. `CatalogClient.requireProduct` uses that detail endpoint when creating orders. A customer who knows an inactive product ID can still order it.
3. **Order synchronization failure reason may not persist.** `PaymentService.synchronizeOrder` calls `payment.markOrderSyncFailed()` after the earlier repository save, without another save. The `VERIFIED` status is persisted, but `ORDER_SYNC_FAILED` in `failureReason` is not guaranteed to be stored for a later admin detail request.
4. **README's early curl examples omit authentication.** `POST /users` and `POST /products` now require `ADMIN`, while `POST /orders` requires a signed-in owner. The examples under “Try the API” predate that security behavior and will return 401 without a bearer token.
5. **Security and HTTP integration coverage is limited.** The service-layer tests cover many order and payment rules, including the new JWT realm-role converters, but gateway and discovery currently have no test sources. There are no controller-level tests proving anonymous 401, normal-user 403, admin success, or gateway rejection of `/internal/**`.

These are review findings, not changes made as part of this documentation task.

## Verification snapshot

On 2026-09-26, all six `./gradlew -p <service> test --offline` commands completed successfully. The discovery-service and api-gateway test tasks reported `NO-SOURCE`; user-service, product-service, order-service, and payment-service had test classes. At the end of this review, ports 8761 and 8080 were not reachable, so this note does not claim a live end-to-end browser or Bakong check. Automated tests do not send a real Bakong payment.

For setup and examples, see [README.md](README.md) and [api-guide.md](api-guide.md). The admin plan is in [admin.md](admin.md).
