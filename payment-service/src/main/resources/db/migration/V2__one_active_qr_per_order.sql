-- Old records may lack a QR string, and previously issued QRs may be expired.
UPDATE payment_qr
SET status = 'EXPIRED'
WHERE status IN ('PENDING', 'UNCONFIRMED', 'MISMATCH')
  AND (expires_at <= now() OR qr IS NULL);

UPDATE payment_qr AS payment
SET status = 'EXPIRED'
WHERE payment.status IN ('PENDING', 'UNCONFIRMED', 'MISMATCH')
  AND EXISTS (
      SELECT 1 FROM payment_qr AS paid
      WHERE paid.order_id = payment.order_id AND paid.status = 'VERIFIED'
  );

-- Keep only the newest still-active QR when upgrading an existing database.
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY order_id ORDER BY created_at DESC, id DESC) AS position
    FROM payment_qr
    WHERE status IN ('PENDING', 'UNCONFIRMED', 'MISMATCH')
)
UPDATE payment_qr AS payment
SET status = 'EXPIRED'
FROM ranked
WHERE payment.id = ranked.id AND ranked.position > 1;

CREATE UNIQUE INDEX ux_payment_qr_one_active_per_order
ON payment_qr (order_id)
WHERE status IN ('PENDING', 'UNCONFIRMED', 'MISMATCH');
