-- Preserve QR records created before payments had numeric IDs and status.
CREATE TABLE IF NOT EXISTS payment_qr (
    md5 varchar(32) PRIMARY KEY,
    order_id bigint NOT NULL,
    amount numeric(12, 2) NOT NULL,
    currency varchar(3) NOT NULL,
    expires_at timestamptz NOT NULL,
    verified_at timestamptz
);

ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS id bigint;
CREATE SEQUENCE IF NOT EXISTS payment_qr_id_seq;
UPDATE payment_qr SET id = nextval('payment_qr_id_seq') WHERE id IS NULL;
SELECT setval('payment_qr_id_seq', COALESCE((SELECT MAX(id) FROM payment_qr), 1),
              EXISTS (SELECT 1 FROM payment_qr));
ALTER TABLE payment_qr ALTER COLUMN id SET DEFAULT nextval('payment_qr_id_seq');
ALTER TABLE payment_qr ALTER COLUMN id SET NOT NULL;
ALTER TABLE payment_qr DROP CONSTRAINT IF EXISTS payment_qr_pkey;
ALTER TABLE payment_qr ADD CONSTRAINT payment_qr_pkey PRIMARY KEY (id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_qr_md5 ON payment_qr (md5);

ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS qr text;
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS receiving_account_id varchar(255);
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS status varchar(32);
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS bakong_hash varchar(255);
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS from_account_id varchar(255);
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS to_account_id varchar(255);
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE payment_qr ADD COLUMN IF NOT EXISTS paid_at timestamptz;

UPDATE payment_qr SET paid_at = verified_at WHERE paid_at IS NULL AND verified_at IS NOT NULL;
UPDATE payment_qr SET status = CASE WHEN paid_at IS NULL THEN 'PENDING' ELSE 'VERIFIED' END
WHERE status IS NULL;
UPDATE payment_qr SET created_at = expires_at - interval '5 minutes' WHERE created_at IS NULL;
ALTER TABLE payment_qr ALTER COLUMN status SET NOT NULL;
ALTER TABLE payment_qr ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE payment_qr DROP COLUMN IF EXISTS verified_at;
