-- DEV ONLY seed. Generated from resources/seed/users.json. Password for every user: see resources/seed/README.md
-- Run with: make seed   (idempotent)
INSERT INTO users (id, email, full_name, password_hash, role, department_code, active) VALUES
  ('11111111-1111-4111-8111-000000000001', 'alice.admin@helios.example.com', 'Alice Admin', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'ADMIN', 'GENERAL', TRUE),
  ('11111111-1111-4111-8111-000000000002', 'harper.hrmgr@helios.example.com', 'Harper Hughes', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'MANAGER', 'HR', TRUE),
  ('11111111-1111-4111-8111-000000000003', 'hannah.hr@helios.example.com', 'Hannah Reyes', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'HR', TRUE),
  ('11111111-1111-4111-8111-000000000004', 'ethan.engmgr@helios.example.com', 'Ethan Park', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'MANAGER', 'ENGINEERING', TRUE),
  ('11111111-1111-4111-8111-000000000005', 'eric.eng@helios.example.com', 'Eric Nguyen', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'ENGINEERING', TRUE),
  ('11111111-1111-4111-8111-000000000006', 'frank.finmgr@helios.example.com', 'Frank Ortiz', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'MANAGER', 'FINANCE', TRUE),
  ('11111111-1111-4111-8111-000000000007', 'fiona.fin@helios.example.com', 'Fiona Brooks', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'FINANCE', TRUE),
  ('11111111-1111-4111-8111-000000000008', 'lena.legalmgr@helios.example.com', 'Lena Schmidt', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'MANAGER', 'LEGAL', TRUE),
  ('11111111-1111-4111-8111-000000000009', 'leo.legal@helios.example.com', 'Leo Martins', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'LEGAL', TRUE),
  ('11111111-1111-4111-8111-000000000010', 'mark.mktmgr@helios.example.com', 'Mark Olsen', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'MANAGER', 'MARKETING', TRUE),
  ('11111111-1111-4111-8111-000000000011', 'maya.mkt@helios.example.com', 'Maya Chen', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'MARKETING', TRUE),
  ('11111111-1111-4111-8111-000000000012', 'ivan.inactive@helios.example.com', 'Ivan Idle', '$2a$10$9ctQjZ1/QVAcUYzGWJ9NSevG2V3M.pTnhtLWkO1jpxRU0JLZZ7due', 'EMPLOYEE', 'MARKETING', FALSE)
ON CONFLICT (email) DO NOTHING;
