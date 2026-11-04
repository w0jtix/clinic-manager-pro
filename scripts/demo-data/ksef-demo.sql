SET client_encoding = 'UTF8';

BEGIN;

INSERT INTO ksef_seller (nip, name, address, email, phone, auto_mark_products, default_expense_category) VALUES
  ('1111111111', 'Hurtownia Podologiczna Demo Sp. z o.o.', 'ul. Przykładowa 1, 00-001 Warszawa', NULL, NULL, false, 'SUPPLIES'),
  ('2222222222', 'DermaFoot Dystrybucja Sp. z o.o.', 'ul. Testowa 15, 30-001 Kraków', NULL, NULL, true, 'PRODUCTS'),
  ('3333333333', 'Energia Demo S.A.', 'ul. Prądowa 7, 80-001 Gdańsk', NULL, NULL, false, 'SERVICES'),
  ('4444444444', 'Szkolenia Medyczne Demo', 'ul. Wiedzy 3, 50-001 Wrocław', NULL, NULL, false, 'TRAINING'),
  ('5555555555', 'Sprzęt Gabinetowy Demo Sp. z o.o.', 'ul. Narzędziowa 22, 90-001 Łódź', NULL, NULL, false, 'EQUIPMENT')
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE demo_invoice (
  ref TEXT, number TEXT, seller_nip TEXT, days_ago INT, category TEXT, paid BOOLEAN
) ON COMMIT DROP;

INSERT INTO demo_invoice VALUES
  ('DEMO-KSEF-0001', 'FV/2026/0412', '1111111111', 6,   'SUPPLIES',  false),
  ('DEMO-KSEF-0002', 'FV/0981/2026', '2222222222', 9,   'PRODUCTS',  true),
  ('DEMO-KSEF-0003', 'E/2026/10/5521', '3333333333', 14, 'SERVICES', true),
  ('DEMO-KSEF-0004', 'FV/2026/0398', '1111111111', 21,  'SUPPLIES',  true),
  ('DEMO-KSEF-0005', 'SZK/77/2026', '4444444444', 27,   'TRAINING',  true),
  ('DEMO-KSEF-0006', 'FV/0942/2026', '2222222222', 36,  'PRODUCTS',  true),
  ('DEMO-KSEF-0007', 'E/2026/09/4410', '3333333333', 45, 'SERVICES', true),
  ('DEMO-KSEF-0008', 'SG/2026/118', '5555555555', 52,   'EQUIPMENT', true),
  ('DEMO-KSEF-0009', 'FV/2026/0351', '1111111111', 63,  'SUPPLIES',  true),
  ('DEMO-KSEF-0010', 'FV/0899/2026', '2222222222', 71,  'PRODUCTS',  true),
  ('DEMO-KSEF-0011', 'E/2026/08/3302', '3333333333', 76, 'SERVICES', true),
  ('DEMO-KSEF-0012', 'FV/0861/2026', '2222222222', 104, 'PRODUCTS',  true);

INSERT INTO ksef_invoice (
  ksef_reference_number, invoice_number, invoice_type, issue_date, payment_due_date,
  seller_id, buyer_nip, buyer_name, buyer_address,
  net_amount, vat_amount, gross_amount, currency, is_paid, payment_date, payment_method,
  category, awaiting_product_verification, fetched_at
)
SELECT d.ref, d.number, 'VAT', CURRENT_DATE - d.days_ago, CURRENT_DATE - d.days_ago + 14,
       s.id, '9999999999', 'Gabinet Demo', 'ul. Kliniczna 10, 00-950 Warszawa',
       0, 0, 0, 'PLN', d.paid, CASE WHEN d.paid THEN CURRENT_DATE - d.days_ago + 3 END, 'przelew',
       d.category, false, now()
FROM demo_invoice d
JOIN ksef_seller s ON s.nip = d.seller_nip
ON CONFLICT DO NOTHING;

INSERT INTO ksef_invoice_item (invoice_id, line_number, ksef_item_name, unit, quantity, declared_quantity,
                               unit_net_price, net_value, unit_gross_price, gross_value, vat_rate, product_id)
SELECT i.id, x.line, x.name, x.unit, x.qty, x.qty::int, x.unit_net, round(x.unit_net * x.qty, 2),
       round(x.unit_net * (1 + x.vat / 100.0), 4), round(x.unit_net * x.qty * (1 + x.vat / 100.0), 2),
       CASE x.vat WHEN 23 THEN 'VAT_23' WHEN 8 THEN 'VAT_8' ELSE 'VAT_23' END,
       (SELECT p.id FROM product p WHERE p.name = x.product_name LIMIT 1)
FROM ksef_invoice i
JOIN ksef_seller s ON s.id = i.seller_id
JOIN LATERAL (
  VALUES
    ('1111111111', 1, 'Frezy diamentowe jednorazowe op. 10 szt.', 'op', 4, 52.03, 23, NULL),
    ('1111111111', 2, 'Rękawiczki nitrylowe M op. 100 szt.', 'op', 6, 30.09, 8, NULL),
    ('1111111111', 3, 'Płyn do dezynfekcji narzędzi 1 l', 'szt', 2, 64.23, 23, NULL),
    ('2222222222', 1, 'Krem z mocznikiem 10% 75 ml', 'szt', 12, 24.00, 23, 'Krem z mocznikiem 10%'),
    ('2222222222', 2, 'Krem przeciwgrzybiczy 50 ml', 'szt', 8, 31.00, 23, 'Krem przeciwgrzybiczy'),
    ('2222222222', 3, 'Krem na pękające pięty 75 ml', 'szt', 10, 27.00, 23, 'Krem na pękające pięty'),
    ('3333333333', 1, 'Energia elektryczna - okres rozliczeniowy', 'kWh', 520, 0.79, 23, NULL),
    ('3333333333', 2, 'Opłata dystrybucyjna', 'szt', 1, 61.40, 23, NULL),
    ('4444444444', 1, 'Szkolenie: terapia klamrami ortonyksyjnymi', 'szt', 1, 1290.00, 23, NULL),
    ('5555555555', 1, 'Lampa lupa LED z ramieniem', 'szt', 1, 849.00, 23, NULL),
    ('5555555555', 2, 'Autoklaw - przegląd okresowy', 'usł', 1, 350.00, 23, NULL)
) AS x(nip, line, name, unit, qty, unit_net, vat, product_name) ON x.nip = s.nip
WHERE i.ksef_reference_number LIKE 'DEMO-KSEF-%'
  AND NOT EXISTS (SELECT 1 FROM ksef_invoice_item e WHERE e.invoice_id = i.id);

UPDATE ksef_invoice i
SET net_amount = t.net, vat_amount = t.gross - t.net, gross_amount = t.gross
FROM (
  SELECT invoice_id, sum(net_value) AS net, sum(gross_value) AS gross
  FROM ksef_invoice_item GROUP BY invoice_id
) t
WHERE t.invoice_id = i.id AND i.ksef_reference_number LIKE 'DEMO-KSEF-%';

COMMIT;
