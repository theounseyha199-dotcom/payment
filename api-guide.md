# Frontend API guide

Use this guide when building a frontend for the Spring microservices practice
project. The frontend talks to the **API gateway**, not to the individual
services or PostgreSQL.

## Connection

- Local gateway: `http://localhost:8080`
- Interactive API docs: `http://localhost:8080/swagger-ui.html`
- JSON request bodies need `Content-Type: application/json`.
- The frontend does not need an authorization header for these practice APIs.
- The gateway has no browser CORS configuration. If the frontend runs on a
  different origin, configure the frontend development server to proxy `/api`
  to `http://localhost:8080` and remove `/api` from the forwarded path. Then
  use `/api` as the frontend base URL. A same-origin deployment can call the
  gateway paths directly.
- Start PostgreSQL, Eureka, the needed services, and the gateway before using
  the API. Allow about 30 seconds for Eureka discovery to refresh. See
  [README.md](README.md) for startup commands.
- Restart the running services after a backend code change so the gateway serves
  the current API.

## Response format

Read, QR generation, and verification endpoints return HTTP `200`. Creating a
user, product, or order returns `201` with a `Location` header. All successful
endpoints use this JSON shape:

```json
{
  "status": "success",
  "message": "User found.",
  "data": { "id": 1, "name": "Practice User", "email": "practice@example.com" }
}
```

Read values from `data`, such as `response.data.id`. List endpoints return an
array in `data`. Do not expect `id` at the top level.

Errors keep an appropriate HTTP status and use this shape:

```json
{
  "status": "error",
  "message": "Please check the request fields.",
  "details": { "email": "must be a well-formed email address" }
}
```

Show `message` for a general error. When `details` is not empty, show its
messages next to the matching form fields. Common codes are `400` for invalid
input, `404` for a missing item, `409` for a duplicate user email, and `503`
when a required service or payment configuration is unavailable. A request
reaching the gateway before Eureka discovers a service can also return `503`.

## Endpoints

All paths below are relative to the gateway base URL.

| Method | Path | Request body | `data` on success |
| --- | --- | --- | --- |
| `GET` | `/users` | None | `User[]` |
| `GET` | `/users/{id}` | None | `User` |
| `POST` | `/users` | `CreateUser` | Created `User`; HTTP `201` |
| `GET` | `/products` | None | `Product[]` |
| `GET` | `/products/{id}` | None | `Product` |
| `POST` | `/products` | `CreateProduct` | Created `Product`; HTTP `201` |
| `GET` | `/orders` | None | `Order[]` |
| `GET` | `/orders/{id}` | None | `Order` |
| `POST` | `/orders` | `CreateOrder` | Created `Order`; HTTP `201` |
| `POST` | `/payments/qr` | `{ "orderId": number }` | Stored payment and dynamic KHQR for the order total in KHR; HTTP `200` |
| `GET` | `/payments/{paymentId}` | None | Saved payment status, amount, and timestamps |
| `POST` | `/payments/{paymentId}/verify` | None | `PaymentVerification` |

There are currently no public update, delete, login, or direct charge endpoints.
`PATCH /internal/orders/{orderId}/payment` belongs to the order service and is
not routed through the gateway. The payment service uses it to set `PAID` after
successful verification.
The old `POST /payments/verify` endpoint remains temporarily for existing
clients, but new frontend code should use the payment ID URL above. The old
endpoint is deprecated and hidden from Swagger.

## How the backend flow works

1. The browser sends requests to the API gateway on port `8080`. The gateway
   uses Eureka to route users, products, orders, and payments to their services.
2. `POST /orders` asks the user and product services to confirm both IDs. The
   order service saves the unit price at creation time, computes
   `unitPrice × quantity` in KHR, and starts the order as `PENDING_PAYMENT`.
   The frontend cannot set the price, total, currency, or status. Each order
   contains one product and a positive quantity.
3. `POST /payments/qr` asks the order service for that saved total. The total
   must be a positive whole KHR amount. The payment service uses it to build
   a dynamic individual KHQR, saves its MD5 and order details in `paymentdb`,
   and returns a payment ID, QR image, and five-minute expiry. If there is an
   active QR for the order, the same payment is returned. If it has expired,
   it is marked `EXPIRED` and a new QR is created. This step does not contact
   Bakong or move money.
4. The customer scans the QR and confirms the payment in a Bakong-compatible
   banking app. The frontend then calls
   `POST /payments/{paymentId}/verify` with the ID from step 3 and no body.
5. The payment service loads the MD5, order ID, amount, currency, and receiving
   account from `paymentdb`. It asks
   Bakong about the transaction and checks its amount, KHR currency, receiving
   account, and order relationship against the saved payment. On a match, it
   stores `VERIFIED` in `paymentdb` before calling the order service's internal
   payment endpoint. If that call fails, the payment remains `VERIFIED` and
   repeating verification retries the order update without calling Bakong again.

The Bakong token stays on the backend. `UNCONFIRMED` is a normal response while
the customer has not paid; let the customer retry verification while the QR is
valid. `MISMATCH` means the transaction details differ. A provider error
(`502` or `503`) can be retried. An HTTP `200` from verification only means the
check completed; use `data.verified` and `data.status` to decide what to show.

## Data types and request bodies

The IDs are positive integers. Prices and totals are JSON numbers in **KHR**;
new product prices must be whole riel amounts of at least `1`. The backend
uses decimal arithmetic. Timestamps are ISO 8601 strings in UTC. The
backend calculates order `totalAmount` from the saved `unitPrice` and quantity,
so the frontend must not send its own money values or status.

```ts
type User = { id: number; name: string; email: string };
type CreateUser = { name: string; email: string };

type Product = { id: number; name: string; price: number };
type CreateProduct = { name: string; price: number };

type Order = {
  id: number;
  userId: number;
  productId: number;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  currency: "KHR";
  status: "PENDING_PAYMENT" | "PAID" | "CANCELLED";
  createdAt: string;
};
type CreateOrder = { userId: number; productId: number; quantity: number };

type QrPayment = {
  paymentId: number;
  orderId: number;
  amount: number;
  currency: "KHR";
  qr: string;
  md5: string;
  imageDataUrl: string;
  paymentStatus: "PENDING" | "UNCONFIRMED";
  expiresAt: string;
};
type PaymentStatus = "PENDING" | "VERIFIED" | "UNCONFIRMED" | "MISMATCH" | "EXPIRED";
type Payment = {
  id: number;
  orderId: number;
  amount: number;
  currency: "KHR";
  status: PaymentStatus;
  expiresAt: string;
  paidAt: string | null;
};
type PaymentVerification = {
  paymentId: number;
  orderId: number;
  verified: boolean;
  status: "VERIFIED" | "UNCONFIRMED" | "MISMATCH" | "EXPIRED";
};

type ApiSuccess<T> = { status: "success"; message: string; data: T };
type ApiError = {
  status: "error";
  message: string;
  details: Record<string, string>;
};
```

Example QR request and response for an order totaling 11,000 KHR (the QR and
image data are shortened here):

```http
POST /payments/qr
Content-Type: application/json

{"orderId": 1}
```

```json
{
  "status": "success",
  "message": "KHQR generated.",
  "data": {
    "paymentId": 1,
    "orderId": 1,
    "amount": 11000,
    "currency": "KHR",
    "qr": "000201...",
    "md5": "0123456789abcdef0123456789abcdef",
    "imageDataUrl": "data:image/png;base64,...",
    "paymentStatus": "PENDING",
    "expiresAt": "2026-09-26T14:00:00Z"
  }
}
```

The QR and MD5 above are shortened/example values; use the full values
returned by the API. Do not generate an MD5 in the browser.

`GET /payments/{paymentId}` returns a `Payment` in the standard success
envelope. It never returns Bakong credentials or the stored QR and MD5.
An unknown ID returns HTTP 404 with `Payment not found.`

After the customer pays, verify the stored payment by ID. The request has no
body and does not include the MD5:

```http
POST /payments/1/verify
```

On a confirmed transaction, the response is:

```json
{
  "status": "success",
  "message": "Payment verified for this order.",
  "data": { "paymentId": 1, "orderId": 1, "verified": true, "status": "VERIFIED" }
}
```

`GET /payments/1` then returns the saved payment status:

```json
{
  "status": "success",
  "message": "Payment found.",
  "data": {
    "id": 1,
    "orderId": 1,
    "amount": 11000,
    "currency": "KHR",
    "status": "VERIFIED",
    "expiresAt": "2026-09-26T14:00:00Z",
    "paidAt": "2026-09-26T13:58:32Z"
  }
}
```

Fetch `GET /orders/1` to confirm the order's `data.status` is `PAID`.

Validation rules:

- `CreateUser`: nonblank `name`; nonblank, valid `email`. Email must be unique.
- `CreateProduct`: nonblank `name`; whole KHR `price` from `1` to
  `9,999,999,999`.
- `CreateOrder`: positive `userId`, `productId`, and `quantity`. User and
  product must already exist. The computed total cannot exceed
  `9,999,999,999` KHR.
- `VerifyPayment`: use the numeric `paymentId` in the URL. The frontend sends
  no order ID, MD5, amount, account ID, or request body.

## Suggested frontend flow

1. Load `/products` and show products and prices.
2. Create or select an existing user. Keep the returned `data.id`.
3. Create an order with the selected `userId`, `productId`, and `quantity`.
   Keep the returned `data.id` as the order ID and show `data.totalAmount`.
4. Call `POST /payments/qr` with the order ID. Show `data.imageDataUrl` as an
   image, `data.amount`, `data.currency`, and the `expiresAt` time. Keep
   `data.paymentId` in UI state or route state. A refresh can use the same
   order ID to request an active QR again.
5. After the user scans and confirms the payment, call
   `POST /payments/{paymentId}/verify` with no body. When
   `data.verified === true` and `data.status === "VERIFIED"`, fetch the order.
   Show the completed order only when its `data.status` is `PAID`.

The QR endpoint creates a dynamic KHQR for the saved order total in KHR.
Generating it does not charge anyone. The QR expires after five minutes. An
active QR is reused, so repeated requests for the same order return the same
payment ID and QR. If it expires before payment, request a new QR for the same
order. Payment records and verification results survive a service restart.
The order can briefly remain `PENDING_PAYMENT` if the order service was
unavailable after verification. Repeating verification on a `VERIFIED` payment
retries that order update without calling Bakong again.

Use these payment states in the checkout UI:

| Payment status | Meaning | Frontend action |
| --- | --- | --- |
| `PENDING` | QR was generated; payment has not been checked. | Show the QR and expiry. |
| `UNCONFIRMED` | Bakong has no matching transaction yet. | Keep the QR visible while valid; allow another verification attempt. |
| `VERIFIED` | Transaction details matched. | Fetch the order; show complete when it is `PAID`. |
| `MISMATCH` | A transaction was found, but its details differ. | Do not show paid; let the customer request a new QR or contact support. |
| `EXPIRED` | An unpaid QR passed its five-minute validity. | Request a new QR for the order. |

`GET /payments/{paymentId}` and verification mark an overdue `PENDING` or
`UNCONFIRMED` QR as `EXPIRED`. An expired unpaid QR is not sent to Bakong for
verification. An already `VERIFIED` payment remains verified after QR expiry.
The API may return HTTP `200` with `UNCONFIRMED`, `MISMATCH`, or `EXPIRED`; those
are payment outcomes, not successful payments.

Payment errors: `/payments/qr` returns `503` if the receiving account and
display name are not configured, `400` if the order total is not a positive
whole KHR amount, and `409` with `Order is already paid.` if the order is paid.
`/payments/{paymentId}/verify` returns `404` for an unknown payment ID, `503` if the
Bakong account or token is missing, and `502` if Bakong is unavailable. Missing
required configuration uses `Bakong configuration is unavailable.` Keep the QR screen
open and let the user retry verification after a temporary provider error.

The Bakong access token, receiving account ID, and recipient display name belong
on the backend as environment variables. An acquiring bank is optional for an
individual account. The receiving account must support KHR.
Never put the token in frontend code, browser storage, or a request to
`/payments/{paymentId}/verify`. If the backend has no payment credentials,
verification returns HTTP `503`.

## Fetch example

This example assumes a development proxy maps `/api/*` to the gateway `/*`.
Adjust `API_BASE_URL` for your frontend setup.

```ts
const API_BASE_URL = "/api";

class ApiRequestError extends Error {
  constructor(
    message: string,
    public httpStatus: number,
    public details: Record<string, string> = {},
  ) {
    super(message);
  }
}

async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });
  const body = (await response.json().catch(() => null)) as
    | ApiSuccess<T>
    | ApiError
    | null;

  if (!response.ok || !body || body.status !== "success") {
    const error = body?.status === "error" ? body : null;
    throw new ApiRequestError(
      error?.message ?? "The request could not be completed.",
      response.status,
      error?.details ?? {},
    );
  }
  return body.data;
}

const products = await apiRequest<Product[]>("/products");
const order = await apiRequest<Order>("/orders", {
  method: "POST",
  body: JSON.stringify({ userId: 1, productId: products[0].id, quantity: 2 }),
});
const qrPayment = await apiRequest<QrPayment>("/payments/qr", {
  method: "POST",
  body: JSON.stringify({ orderId: order.id }),
});
// After the customer scans and confirms the payment:
const verification = await apiRequest<PaymentVerification>(
  `/payments/${qrPayment.paymentId}/verify`,
  { method: "POST" },
);
if (verification.verified && verification.status === "VERIFIED") {
  const savedPayment = await apiRequest<Payment>(
    `/payments/${qrPayment.paymentId}`,
  );
  const updatedOrder = await apiRequest<Order>(`/orders/${order.id}`);
  // Show completion when savedPayment.status is VERIFIED and updatedOrder.status is PAID.
}
```

The UI can catch `ApiRequestError`, show its `message`, and display its
`details` next to form fields.
