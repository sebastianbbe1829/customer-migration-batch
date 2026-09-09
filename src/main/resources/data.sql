-- Local/demo seed data.
-- Spring Boot executes this script on startup. The INSERT is guarded by
-- NOT EXISTS so application restarts do not duplicate the demo dataset.
-- The dataset contains 500 legacy records, including intentionally dirty data
-- to exercise validation, normalization and duplicate handling.

INSERT INTO legacy.customers (document_number, full_name, email, phone, status)
SELECT seed.document_number,
       seed.full_name,
       seed.email,
       seed.phone,
       seed.status
FROM (
    VALUES
        ('  1001  ', 'JUAN PEREZ', 'juan.perez@example.com', '3001234567', 'ACTIVE'),
        ('1002', ' MARIA GOMEZ ', 'maria.gomez@example.com', '3012345678', 'ACTIVE'),
        ('1003', 'CARLOS LOPEZ', 'carlos.lopez@example.com', '3023456789', 'INACTIVE'),
        ('1004', 'ANA TORRES', 'ana.torres@example.com', '3034567890', 'ACTIVE'),
        ('1005', 'PEDRO RUIZ', 'pedro.ruiz@example.com', '3045678901', 'ACTIVE'),
        ('1005', 'PEDRO RUIZ DUPLICADO', 'pedro.duplicado@example.com', '3045678902', 'ACTIVE'),
        (NULL, 'DOCUMENTO NULO', 'null.doc@example.com', '3056789012', 'ACTIVE'),
        ('1007', 'EMAIL INVALIDO', 'email-invalido', '3067890123', 'ACTIVE'),
        ('1008', 'ESTADO DESCONOCIDO', 'estado@example.com', '3078901234', 'BLOCKED'),
        ('1009', '   LUCIA MARTINEZ   ', ' lucia.martinez@example.com ', '3089012345', 'INACTIVE'),
        ('1010', 'DANIEL CASTRO', NULL, '3090123456', 'ACTIVE'),
        ('1011', 'SOFIA VARGAS', 'sofia.vargas@example.com', NULL, 'ACTIVE')
) AS seed(document_number, full_name, email, phone, status)
WHERE NOT EXISTS (SELECT 1 FROM legacy.customers)

UNION ALL

SELECT
    (2000 + gs)::varchar(30) AS document_number,
    'CUSTOMER ' || gs AS full_name,
    'customer' || gs || '@example.com' AS email,
    '310' || lpad(gs::text, 7, '0') AS phone,
    CASE WHEN gs % 10 = 0 THEN 'INACTIVE' ELSE 'ACTIVE' END AS status
FROM generate_series(1, 488) AS gs
WHERE NOT EXISTS (SELECT 1 FROM legacy.customers)
