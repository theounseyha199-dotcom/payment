DROP INDEX IF EXISTS ux_payment_qr_one_active_per_order;

CREATE UNIQUE INDEX ux_payment_qr_one_active_per_order
ON payment_qr (order_id)
WHERE status IN ('PENDING', 'UNCONFIRMED');
