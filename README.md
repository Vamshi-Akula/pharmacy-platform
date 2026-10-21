# Pharmacy & Prescription Management Platform

Spring Boot 3 (Java 21) + MongoDB 7, run entirely in Docker.

## Run (only Docker required)

```bash
docker compose up --build
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html (OpenAPI JSON: `/v3/api-docs`)
- Health: http://localhost:8080/actuator/health
- A small sample catalog is seeded on first start (`SEED_ENABLED=false` in `docker-compose.yml` disables it).
- Reset data: `docker compose down -v`

## Acceptance evidence

Import `postman/pharmacy-platform.postman_collection.json` into Postman and run the collection in order on a fresh database (Collection Runner). Every request has assertions; folders cover the happy path, cancellation, failed payment, OTC skip-verification, rejected prescription and low stock.

Automated end-to-end test (embedded MongoDB, downloaded on first run): `mvn test` (needs JDK 21+ and Maven).

## API

| Endpoint | Purpose | FR |
|---|---|---|
| `POST /drugs` | Add drug | 01 |
| `GET /drugs/{id}` | Drug with stock, reserved, available | 02 |
| `GET /drugs?category=&name=&search=&page=&size=` | Browse/search | 03 |
| `GET /drugs/low-stock` | Available < threshold | 03, 15 |
| `PUT /drugs/{id}` | Update details/price (not stock) | 04 |
| `PATCH /drugs/{id}/stock` `{delta, reason}` | Restock / remove stock | 05 |
| `POST /prescription-orders` `{patientId, items[{drugId, quantity}]}` | Submit; returns total; reserves stock | 06, 07, 12, 14 |
| `GET /prescription-orders/{id}` | Order + payment history | 08 |
| `GET /prescription-orders/patient/{patientId}` | Patient's orders | 09 |
| `GET /prescription-orders?status=` | Filter by status | 11 |
| `PATCH /prescription-orders/{id}/cancel` `{cancelledBy, reason}` | Cancel, release stock | 10, 13 |
| `PATCH /prescription-orders/{id}/verify` `{pharmacistId, approved, notes}` | Simulated verification | 06 |
| `PATCH /prescription-orders/{id}/dispense` | Dispense (added; needed to reach `DISPENSED`) | - |
| `GET /inventory/{drugId}` | Stock / reserved / available / low-stock | 12-15 |
| `POST /payments` `{orderId, method, outcome}` | Simulated payment; `outcome` = `SUCCESS` (default) or `FAILURE` | 16 |
| `GET /payments/order/{orderId}` | Payment history | 17 |

Errors: `400` validation, `404` not found, `409` business-rule conflict (insufficient stock, illegal status transition, double payment).

## Order lifecycle

```
SUBMITTED --pay ok--> PENDING_VERIFICATION --verify(approve)--> VERIFIED --dispense--> DISPENSED
    |                         |  verify(reject) -> CANCELLED         |
    |--pay ok, no Rx drugs------------------------------------------>|
    |--pay fails--> FAILED (stock released)
SUBMITTED / PENDING_VERIFICATION / VERIFIED --cancel--> CANCELLED (stock released)
```

Stock: `available = stockQuantity - reservedQuantity`. Submit reserves; cancel / failed payment / rejection releases; dispense deducts stock and reservation.

## Design notes (how MongoDB is used, and why)

- **Collections:** `drugs`, `prescription_orders` (items and status history embedded; prices snapshotted), `payments`. Indexes: drug `name`/`category`, order `status` and `(patientId, createdAt)`, payment `orderId`.
- **Concurrency without transactions:** stock changes are single-document conditional `$inc` updates (filter `stock - reserved >= qty`), so two buyers cannot take the last unit and stock never goes negative. Order status changes are `findAndModify` guarded by the expected current status; only the caller that wins the transition releases or commits stock, so a cancel racing a payment cannot double-release.
- **Multi-item orders** are reserved item by item; if one fails, earlier reservations are released (compensation).
- **Dropped alternatives:** (1) multi-document transactions - need a replica set, heavier Docker setup, and unnecessary given single-document atomic updates; (2) a separate `order_items` collection - orders are always read whole, so embedding avoids joins; (3) storing `availableQuantity` redundantly - derived on read so it cannot drift; (4) Mongo text index for search - regex substring matching gives friendlier partial matches at this data size.

## Known limitations

- Compensation is best-effort: a crash between a status change and the stock release/commit could leave a reservation held (a production fix would be an outbox/reconciliation job).
- Payment is simulated and the amount always comes from the order total. A failed payment is terminal (`FAILED`); the patient must submit a new order. Hence payment "history" has one entry per order today.
- No authentication: `patientId`/`pharmacistId` are trusted from the request. Single currency, single stock pool.
- Search uses regex scans (fine for demo scale; use a text index or Atlas Search at scale).
- Reservations never expire.
- Docker build and `docker compose` were not executed in the authoring environment (Docker unavailable); the app and MongoDB logic were verified with `mvn test` against embedded MongoDB 7.
