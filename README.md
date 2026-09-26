# Spring microservices practice

A Java 21 Spring Boot project with six services, Eureka discovery, an API
gateway, and PostgreSQL. The root `src/` application is an older starter app;
the services used by the examples below are in their own directories.

| Service | Port | Role |
| --- | ---: | --- |
| `discovery-service` | 8761 | Eureka service registry |
| `api-gateway` | 8080 | Public API and combined Swagger UI |
| `user-service` | 8081 | Users |
| `product-service` | 8082 | Products and KHR prices |
| `order-service` | 8083 | Orders; looks up users and products through Eureka |
| `payment-service` | 8084 | Generates Bakong KHQR and verifies payments |

The gateway routes `/users`, `/products`, `/orders`, and `/payments` to the
corresponding services. Send application requests to port **8080**.

## Start everything

Install Java 21 and Docker with Docker Compose. The launcher also uses Bash,
`curl`, and `setsid` (available on Linux). Then run from the project root:

```bash
./run-all.sh
```

The script starts PostgreSQL, creates any missing service databases in an
existing volume, starts Keycloak, Eureka, then starts the other five services.
It waits until the public `/products` route works through the gateway before
reporting that startup is complete. The first run can take longer while Docker
and Gradle download dependencies.

Open the [Swagger UI](http://localhost:8080/swagger-ui.html) to browse all four
APIs, or the [Eureka dashboard](http://localhost:8761) to see registered
services. Each service writes a log to `.run/logs/<service>.log`.

Press **Ctrl+C** in the script's terminal to stop the Java services. PostgreSQL
remains running so its data is available on the next start. Stop it with:

```bash
docker compose down
```

`docker compose down` keeps the PostgreSQL volume. It does not delete saved
users, products, orders, or payment records.

### Run services separately

To use separate terminals, start PostgreSQL first:

```bash
docker compose up -d --wait postgres
```

On a fresh volume, `docker/init-databases.sql` creates `userdb`, `productdb`,
`orderdb`, and `paymentdb`. Existing volumes do not rerun that file; use
`./run-all.sh` to create any missing databases, or create them manually.

Then run each command in its own terminal, in this order:

```bash
./gradlew -p discovery-service bootRun
./gradlew -p user-service bootRun
./gradlew -p product-service bootRun
./gradlew -p order-service bootRun
./gradlew -p payment-service bootRun
./gradlew -p api-gateway bootRun
```

Allow Eureka and the gateway roughly 30 seconds to refresh their service lists
after startup. During that time a gateway route may return 503.

## Try the API

Create a user and a product. Products are priced in **whole Cambodian riel
(KHR)**:

```bash
curl -X POST http://localhost:8080/users \
  -H 'Content-Type: application/json' \
  -d '{"name":"Seyha","email":"seyha@example.com"}'

curl -X POST http://localhost:8080/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Notebook","price":5500}'
```

Use the IDs in those responses to create an order. For a new database, both
IDs will usually be `1`:

```bash
curl -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":2}'

curl http://localhost:8080/orders/1
```

The order stores the product price at the time of creation as `unitPrice`,
calculates `totalAmount` with `BigDecimal`, and starts with status
`PENDING_PAYMENT`. The frontend does not send an order status. A missing user
or product prevents order creation. List or fetch records with `GET /users`,
`GET /users/{id}`, `GET /products`, `GET /products/{id}`, `GET /orders`, and
`GET /orders/{id}`.

For the example product at 5,500 KHR and quantity 2, the order response has
`totalAmount: 11000`, `currency: "KHR"`, and `status: "PENDING_PAYMENT"`.

Successful responses contain `status`, `message`, and `data`. For example:

```json
{"status":"success","message":"User found.","data":{"id":1,"name":"Seyha","email":"seyha@example.com"}}
```

Errors use the matching HTTP status and contain `status`, `message`, and
`details`. Invalid fields appear in `details`; missing records return 404.

## KHQR payments

To generate a QR, set your receiving Bakong account ID and merchant name
before starting `payment-service` or `./run-all.sh`. To verify transactions
against Bakong, also set an access token. Put these values in a local `.env`
file at the project root (which Git ignores), then run:

```bash
./run-all.sh
```

The `.env` file uses shell assignments such as
`BAKONG_ACCOUNT_ID='theoun_seyha@bkrt'`,
`BAKONG_MERCHANT_NAME='SEYHA THEOUN'`, and
`BAKONG_TOKEN='YOUR-NEW-TOKEN'`. Replace the token placeholder with a newly
issued token.
Do not commit `BAKONG_TOKEN` or put it in API requests, responses, or logs.
`run-all.sh` exports `.env` values to the services through the process
environment; it contains no Bakong credential. You can also export the values
in your shell. The services can start
without these variables. Without the account ID
and merchant name, QR creation returns 503. A new or unconfirmed payment needs
the configured account ID and token for verification; otherwise it returns
503. Retrying an already verified payment does not call Bakong again.

> [!WARNING]
> This project can generate a KHQR pointing to a real Bakong account.
> When using Bakong production APIs, a payment may transfer real money.
> Use a small test amount and never commit the Bakong access token.

The learning flow is: Create User → Create Product → Create Order →
`PENDING_PAYMENT` → Generate KHQR → Scan and pay using a Bakong-supported
application → Verify Payment → Payment `VERIFIED` → Order `PAID`.

Create a QR for an existing order (replace `1` with its actual ID):

```bash
curl -X POST http://localhost:8080/payments/qr \
  -H 'Content-Type: application/json' \
  -d '{"orderId":1}'
```

The response includes a numeric `paymentId`, the order amount in KHR, a
dynamic KHQR string, its 32-character `md5`, a PNG `imageDataUrl`,
`paymentStatus: "PENDING"`, and an expiry five minutes after creation.
Generating a QR does not transfer money. If the order already has an active QR,
the same `paymentId` and QR are returned. An expired QR is marked `EXPIRED`
before a new one is created. A paid order returns HTTP 409 with
`Order is already paid.`

After the customer scans and pays, verify using the `paymentId` from the QR
response. The request has **no body**:

```bash
curl -X POST http://localhost:8080/payments/1/verify
```

The payment service loads the order ID, MD5, amount, currency, and receiving
account from `paymentdb`. The old `POST /payments/verify` endpoint still works
temporarily for existing clients but is deprecated and hidden from Swagger.

Read the saved payment and its current state with:

```bash
curl http://localhost:8080/payments/1
```

`GET /payments/{paymentId}` returns the payment ID, order ID, amount, KHR
currency, status, expiry, and payment time. It omits the Bakong token, account
credentials, QR text, and MD5. An unknown payment returns HTTP 404 with
`Payment not found.`

After a successful payment and verification, this payment response shows
`status: "VERIFIED"`. Confirm that the order was updated too:

```bash
curl http://localhost:8080/orders/1
```

The order response should then show `status: "PAID"`. The client sends only
the payment ID in the verification URL; it does not send the MD5.

The result's `data.status` is `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, or `EXPIRED`.
`VERIFIED` means Bakong reported the expected amount, KHR currency, and
receiving account. A generated QR and its verification state are saved in
`paymentdb`, so a confirmed result survives a restart. The payment record also
stores the receiving account, Bakong transaction details, timestamps, and a
status such as `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, or `EXPIRED`. On a match,
the payment service saves `VERIFIED` first, then asks the order service to mark
the order `PAID` through its internal API. If the order service is unavailable,
the payment stays `VERIFIED`; repeat the same verification request to retry
the order update without calling Bakong again. `MISMATCH` and `EXPIRED` never
mark an order paid. The gateway does not route `/internal/**`.

An unpaid `PENDING` or `UNCONFIRMED` QR becomes `EXPIRED` once its five-minute
validity has passed. Verification then returns its expired status without
calling Bakong. An already `VERIFIED` payment stays verified after expiry.
Payment statuses are `PENDING`, `VERIFIED`, `UNCONFIRMED`, `MISMATCH`, and
`EXPIRED`; order statuses are `PENDING_PAYMENT`, `PAID`, and `CANCELLED`.
Missing required Bakong configuration returns HTTP 503 with
`Bakong configuration is unavailable.`

Optional payment settings are `BAKONG_MERCHANT_CITY` (default `PHNOM PENH`),
`BAKONG_ACQUIRING_BANK`, and `BAKONG_ACCOUNT_INFORMATION`. The default
`BAKONG_CURRENCY` is `KHR`; QR creation requires KHR. `BAKONG_BASE_URL`
defaults to `https://api-bakong.nbc.gov.kh` and can be overridden for a test
environment.

## Admin API

The admin control panel uses Keycloak realm `practice` and requires the
`ADMIN` realm role. Send an access token issued to an administrator in the
`Authorization` header. A request without a token returns `401`; a logged-in
user without `ADMIN` returns `403`.

New `practice` accounts inherit the `USER` realm role. Assign `ADMIN` to a
specific account in Keycloak **Users → Role mapping → Assign role → Realm
roles**. Administrators also inherit `USER`; the `ADMIN` role is never a
default registration role. Sign out and sign in again after changing roles so
the browser receives a new access token.

### Admin users

```text
GET /admin/users
GET /admin/users/{id}
GET /admin/users/stats
```

`GET /admin/users` supports `search`, `page`, `size`, and `sort`. Results are
paginated and the maximum page size is 100.

### Admin products

```text
GET   /admin/products
GET   /admin/products/{id}
POST  /admin/products
PATCH /admin/products/{id}
PATCH /admin/products/{id}/status
GET   /admin/products/stats
```

Products use `ACTIVE` and `INACTIVE` status. Deactivation is soft; products
are never hard-deleted. Price values use `BigDecimal` and KHR.

```bash
curl http://localhost:8080/admin/products \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>'

curl -X POST http://localhost:8080/admin/products \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Notebook","price":5500}'

curl -X PATCH http://localhost:8080/admin/products/1 \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"price":6500}'

curl -X PATCH http://localhost:8080/admin/products/1/status \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{"status":"INACTIVE"}'
```

### Admin orders

```text
GET /admin/orders
GET /admin/orders/{id}
GET /admin/orders/stats
```

Order lists support `orderId`, `userId`, `status`, `from`, `to`, `page`,
`size`, and `sort` query parameters.

```bash
curl 'http://localhost:8080/admin/orders?status=PENDING_PAYMENT' \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>'
```

### Admin payments

```text
GET  /admin/payments
GET  /admin/payments/{id}
POST /admin/payments/{id}/retry-verification
GET  /admin/payments/stats
```

Payment lists support `paymentId`, `orderId`, `status`, `currency`,
`minAmount`, `maxAmount`, `from`, `to`, `page`, `size`, and `sort`.

```bash
curl 'http://localhost:8080/admin/payments?status=MISMATCH' \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>'

curl -X POST http://localhost:8080/admin/payments/1/retry-verification \
  -H 'Authorization: Bearer <ADMIN_ACCESS_TOKEN>'
```

Admin payment retry calls the same Bakong verification logic as the customer
verification endpoint. Administrators cannot manually mark a payment
`VERIFIED`, manually mark an order `PAID`, change transaction hashes, change
the receiving account, or change `paidAt`.

The `/internal/**` API is private service-to-service communication. It is not
routed through the gateway and must never be called by the admin browser.

## Login and registration

Login and registration are handled by Keycloak with OpenID Connect. The local
realm is available at `http://localhost:8180/realms/practice`, with the public
frontend client `payment-web`. The frontend's **Sign in** and **Register**
buttons open the Keycloak browser flow.

After a successful login, the frontend sends the Keycloak access token as a
Bearer token to the API gateway. User profiles, orders, and payments require
authentication. Product reads are public. Creating products or legacy user
records requires the `ADMIN` realm role.

The first authenticated `GET /users/me` creates the signed-in user's profile in
user-service. Existing database users and orders remain separate and are not
automatically matched by email.

The backend `.env` file contains the private `INTERNAL_SERVICE_TOKEN` used for
payment-service to order-service communication. Never expose it to the
frontend, Swagger, or logs.

## Database and configuration

PostgreSQL is exposed at `localhost:55432`. Each data service has its own
database in the same server:

| Service | Database | URL override |
| --- | --- | --- |
| `user-service` | `userdb` | `USER_DB_URL` |
| `product-service` | `productdb` | `PRODUCT_DB_URL` |
| `order-service` | `orderdb` | `ORDER_DB_URL` |
| `payment-service` | `paymentdb` | `PAYMENT_DB_URL` |

The local Compose credentials are `practice` / `practice`. The services accept
`DB_USERNAME` and `DB_PASSWORD` environment overrides, but changing them
alone does not change the Compose container's credentials. `EUREKA_URL` can
override the default `http://localhost:8761/eureka/` in clients.

The data services use Hibernate `ddl-auto: update` for practice. The payment
service also runs a Flyway migration that preserves older QR records while
adding numeric payment IDs and lifecycle fields. The root
starter app runs on port 8090, uses the `postgres` database by default, and is
not started by `run-all.sh`. For frontend integration details, see
[api-guide.md](api-guide.md).

## Build and test

Each service is a separate Gradle project. Run its tests from the root with:

```bash
./gradlew -p discovery-service test
./gradlew -p api-gateway test
./gradlew -p user-service test
./gradlew -p product-service test
./gradlew -p order-service test
./gradlew -p payment-service test
```
