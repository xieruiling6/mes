CREATE TABLE mes_quality_inspection (
  id BIGINT NOT NULL AUTO_INCREMENT,
  inspection_no VARCHAR(48) NOT NULL,
  work_order_id BIGINT NOT NULL,
  report_id BIGINT NULL,
  inspection_date DATE NOT NULL,
  inspection_qty BIGINT NOT NULL,
  good_qty BIGINT NOT NULL,
  unqualified_qty BIGINT NOT NULL,
  unqualified_reason VARCHAR(500) NULL,
  disposition_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  disposition_note VARCHAR(500) NULL,
  inspected_by BIGINT NOT NULL,
  disposed_by BIGINT NULL,
  disposed_at DATETIME(3) NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_quality_inspection_no (inspection_no),
  KEY idx_mes_quality_work_order_date (work_order_id, inspection_date, id),
  KEY idx_mes_quality_report (report_id, inspection_date),
  KEY idx_mes_quality_disposition (disposition_status, inspection_date),
  CONSTRAINT chk_mes_quality_qty CHECK (inspection_qty > 0 AND good_qty >= 0 AND unqualified_qty >= 0 AND inspection_qty = good_qty + unqualified_qty),
  CONSTRAINT chk_mes_quality_reason CHECK (unqualified_qty = 0 OR CHAR_LENGTH(TRIM(unqualified_reason)) > 0),
  CONSTRAINT chk_mes_quality_disposition CHECK (disposition_status IN ('NOT_REQUIRED', 'PENDING', 'ACCEPTED', 'REWORK', 'SCRAPPED')),
  CONSTRAINT chk_mes_quality_disposition_qty CHECK ((unqualified_qty = 0 AND disposition_status = 'NOT_REQUIRED') OR (unqualified_qty > 0 AND disposition_status <> 'NOT_REQUIRED')),
  CONSTRAINT chk_mes_quality_disposed CHECK ((disposition_status IN ('PENDING', 'NOT_REQUIRED') AND disposed_by IS NULL AND disposed_at IS NULL) OR (disposition_status IN ('ACCEPTED', 'REWORK', 'SCRAPPED') AND disposed_by IS NOT NULL AND disposed_at IS NOT NULL)),
  CONSTRAINT fk_mes_quality_work_order FOREIGN KEY (work_order_id) REFERENCES mes_work_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_quality_report FOREIGN KEY (report_id) REFERENCES mes_report (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_quality_inspector FOREIGN KEY (inspected_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_quality_disposer FOREIGN KEY (disposed_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mes_equipment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  equipment_code VARCHAR(40) NOT NULL,
  equipment_name VARCHAR(120) NOT NULL,
  specification VARCHAR(120) NULL,
  location VARCHAR(120) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_equipment_code (equipment_code),
  KEY idx_mes_equipment_status_name (status, equipment_name),
  CONSTRAINT chk_mes_equipment_status CHECK (status IN ('IDLE', 'IN_USE', 'MAINTENANCE', 'DISABLED')),
  CONSTRAINT fk_mes_equipment_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mes_equipment_maintenance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  maintenance_no VARCHAR(48) NOT NULL,
  equipment_id BIGINT NOT NULL,
  maintenance_reason VARCHAR(500) NOT NULL,
  maintenance_note VARCHAR(1000) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  started_by BIGINT NOT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_by BIGINT NULL,
  completed_at DATETIME(3) NULL,
  active_equipment_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN equipment_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_equipment_maintenance_no (maintenance_no),
  UNIQUE KEY uk_mes_equipment_maintenance_open (active_equipment_id),
  KEY idx_mes_equipment_maintenance_history (equipment_id, started_at, id),
  KEY idx_mes_equipment_maintenance_status (status, started_at),
  CONSTRAINT chk_mes_equipment_maintenance_status CHECK ((status = 'OPEN' AND completed_by IS NULL AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_by IS NOT NULL AND completed_at IS NOT NULL AND CHAR_LENGTH(TRIM(maintenance_note)) > 0)),
  CONSTRAINT fk_mes_equipment_maintenance_equipment FOREIGN KEY (equipment_id) REFERENCES mes_equipment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_maintenance_starter FOREIGN KEY (started_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_maintenance_completer FOREIGN KEY (completed_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (permission_code, permission_name, module_code, resource_type, status) VALUES
  ('quality:read', '查看质检记录', 'quality', 'MENU', 'ACTIVE'),
  ('quality:create', '创建质检记录', 'quality', 'ACTION', 'ACTIVE'),
  ('quality:dispose', '处理不合格品', 'quality', 'ACTION', 'ACTIVE'),
  ('equipment:read', '查看设备档案', 'equipment', 'MENU', 'ACTIVE'),
  ('equipment:manage', '管理设备档案', 'equipment', 'ACTION', 'ACTIVE'),
  ('equipment:maintain', '管理设备维修', 'equipment', 'ACTION', 'ACTIVE')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), module_code = VALUES(module_code), resource_type = VALUES(resource_type), status = 'ACTIVE';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN' AND p.permission_code IN ('quality:read','quality:create','quality:dispose','equipment:read','equipment:manage','equipment:maintain');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('quality:read','quality:create','quality:dispose','equipment:read','equipment:manage','equipment:maintain')
WHERE r.role_code = 'PRODUCTION_MANAGER';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('quality:read','equipment:read')
WHERE r.role_code = 'WORKSHOP_SUPERVISOR';
