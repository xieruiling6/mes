-- 工擎 MES MySQL 8 initial schema and RBAC seed data.
-- Review this file before running. This file has not been executed.
-- Target: MySQL 8.0.16+ (enforced CHECK constraints), InnoDB, utf8mb4.
-- Application/database connection should use UTC for DATETIME values.

CREATE DATABASE IF NOT EXISTS gongqing_mes
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE gongqing_mes;

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_status (status),
  CONSTRAINT chk_sys_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  role_code VARCHAR(40) NOT NULL,
  role_name VARCHAR(80) NOT NULL,
  description VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (role_code),
  KEY idx_sys_role_status (status),
  CONSTRAINT chk_sys_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  permission_code VARCHAR(100) NOT NULL,
  permission_name VARCHAR(100) NOT NULL,
  module_code VARCHAR(40) NOT NULL,
  resource_type VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_permission_code (permission_code),
  KEY idx_sys_permission_module_status (module_code, status),
  CONSTRAINT chk_sys_permission_type CHECK (resource_type IN ('MENU', 'ACTION')),
  CONSTRAINT chk_sys_permission_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, role_id),
  KEY idx_sys_user_role_role (role_id),
  CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (role_id, permission_id),
  KEY idx_sys_role_permission_permission (permission_id),
  CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_code VARCHAR(40) NOT NULL,
  product_name VARCHAR(120) NOT NULL,
  specification VARCHAR(120) NULL,
  unit VARCHAR(20) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_product_code (product_code),
  KEY idx_mes_product_status_deleted (status, is_deleted),
  CONSTRAINT chk_mes_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT chk_mes_product_deleted CHECK (is_deleted IN (0, 1)),
  CONSTRAINT fk_mes_product_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_product_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_process (
  id BIGINT NOT NULL AUTO_INCREMENT,
  process_code VARCHAR(40) NOT NULL,
  process_name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_process_code (process_code),
  KEY idx_mes_process_status_deleted (status, is_deleted),
  CONSTRAINT chk_mes_process_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
  CONSTRAINT chk_mes_process_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_product_process (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  process_id BIGINT NOT NULL,
  sequence_no SMALLINT NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_product_process_sequence (product_id, sequence_no),
  KEY idx_mes_product_process_process (process_id),
  CONSTRAINT chk_mes_product_process_sequence CHECK (sequence_no > 0),
  CONSTRAINT fk_mes_product_process_product FOREIGN KEY (product_id) REFERENCES mes_product (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_product_process_process FOREIGN KEY (process_id) REFERENCES mes_process (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_product_process_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_production_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL,
  product_id BIGINT NOT NULL,
  order_qty BIGINT NOT NULL,
  due_date DATE NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_production_order_no (order_no),
  KEY idx_mes_production_order_status_due (status, due_date),
  KEY idx_mes_production_order_product_created (product_id, created_at),
  KEY idx_mes_production_order_created_by (created_by),
  CONSTRAINT chk_mes_production_order_qty CHECK (order_qty > 0),
  CONSTRAINT chk_mes_production_order_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT fk_mes_production_order_product FOREIGN KEY (product_id) REFERENCES mes_product (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_production_order_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_production_order_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_production_plan (
  id BIGINT NOT NULL AUTO_INCREMENT,
  plan_no VARCHAR(40) NOT NULL,
  production_order_id BIGINT NOT NULL,
  plan_qty BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  priority SMALLINT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  released_by BIGINT NULL,
  released_at DATETIME(3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_production_plan_no (plan_no),
  UNIQUE KEY uk_mes_production_plan_order (production_order_id),
  KEY idx_mes_production_plan_status_dates (status, start_date, end_date),
  KEY idx_mes_production_plan_created_by (created_by),
  CONSTRAINT chk_mes_production_plan_qty CHECK (plan_qty > 0),
  CONSTRAINT chk_mes_production_plan_priority CHECK (priority >= 0),
  CONSTRAINT chk_mes_production_plan_dates CHECK (end_date >= start_date),
  CONSTRAINT chk_mes_production_plan_status CHECK (status IN ('DRAFT', 'RELEASED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT fk_mes_production_plan_order FOREIGN KEY (production_order_id) REFERENCES mes_production_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_production_plan_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_production_plan_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_production_plan_released_by FOREIGN KEY (released_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_work_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  work_order_no VARCHAR(40) NOT NULL,
  plan_id BIGINT NOT NULL,
  sequence_no SMALLINT NOT NULL,
  planned_qty BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_work_order_no (work_order_no),
  UNIQUE KEY uk_mes_work_order_plan (plan_id),
  KEY idx_mes_work_order_status_dates (status, start_date, end_date),
  KEY idx_mes_work_order_created_by (created_by),
  CONSTRAINT chk_mes_work_order_sequence CHECK (sequence_no > 0),
  CONSTRAINT chk_mes_work_order_qty CHECK (planned_qty > 0),
  CONSTRAINT chk_mes_work_order_dates CHECK (end_date >= start_date),
  CONSTRAINT chk_mes_work_order_status CHECK (status IN ('PENDING', 'RELEASED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED')),
  CONSTRAINT fk_mes_work_order_plan FOREIGN KEY (plan_id) REFERENCES mes_production_plan (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_work_order_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_work_order_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  report_no VARCHAR(40) NOT NULL,
  request_key CHAR(36) NOT NULL,
  work_order_id BIGINT NOT NULL,
  reporter_id BIGINT NOT NULL,
  report_date DATE NOT NULL,
  good_qty BIGINT NOT NULL DEFAULT 0,
  scrap_qty BIGINT NOT NULL DEFAULT 0,
  report_status VARCHAR(10) NOT NULL DEFAULT 'VALID',
  remark VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  voided_by BIGINT NULL,
  voided_at DATETIME(3) NULL,
  void_reason VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mes_report_no (report_no),
  UNIQUE KEY uk_mes_report_request_key (request_key),
  KEY idx_mes_report_work_order_status_date (work_order_id, report_status, report_date),
  KEY idx_mes_report_status_date (report_status, report_date),
  KEY idx_mes_report_reporter_date (reporter_id, report_date),
  KEY idx_mes_report_voided_by (voided_by),
  CONSTRAINT chk_mes_report_good_qty CHECK (good_qty >= 0),
  CONSTRAINT chk_mes_report_scrap_qty CHECK (scrap_qty >= 0),
  CONSTRAINT chk_mes_report_nonzero CHECK (good_qty > 0 OR scrap_qty > 0),
  CONSTRAINT chk_mes_report_status CHECK (report_status IN ('VALID', 'VOID')),
  CONSTRAINT chk_mes_report_void_fields CHECK (
    (report_status = 'VALID' AND voided_by IS NULL AND voided_at IS NULL AND void_reason IS NULL)
    OR
    (report_status = 'VOID' AND voided_by IS NOT NULL AND voided_at IS NOT NULL AND void_reason IS NOT NULL AND CHAR_LENGTH(TRIM(void_reason)) > 0)
  ),
  CONSTRAINT fk_mes_report_work_order FOREIGN KEY (work_order_id) REFERENCES mes_work_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_report_voided_by FOREIGN KEY (voided_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_quality_inspection (
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
  PRIMARY KEY (id), UNIQUE KEY uk_mes_quality_inspection_no (inspection_no),
  KEY idx_mes_quality_work_order_date (work_order_id, inspection_date, id),
  KEY idx_mes_quality_inspection_date (inspection_date),
  KEY idx_mes_quality_report (report_id, inspection_date), KEY idx_mes_quality_disposition (disposition_status, inspection_date),
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

CREATE TABLE IF NOT EXISTS mes_equipment (
  id BIGINT NOT NULL AUTO_INCREMENT, equipment_code VARCHAR(40) NOT NULL, equipment_name VARCHAR(120) NOT NULL,
  specification VARCHAR(120) NULL, location VARCHAR(120) NULL, status VARCHAR(20) NOT NULL DEFAULT 'IDLE', remark VARCHAR(500) NULL,
  created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3), PRIMARY KEY (id),
  UNIQUE KEY uk_mes_equipment_code (equipment_code), KEY idx_mes_equipment_status_name (status, equipment_name),
  CONSTRAINT chk_mes_equipment_status CHECK (status IN ('IDLE', 'IN_USE', 'MAINTENANCE', 'DISABLED')),
  CONSTRAINT fk_mes_equipment_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS mes_equipment_maintenance (
  id BIGINT NOT NULL AUTO_INCREMENT, maintenance_no VARCHAR(48) NOT NULL, equipment_id BIGINT NOT NULL,
  maintenance_reason VARCHAR(500) NOT NULL, maintenance_note VARCHAR(1000) NULL, status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  started_by BIGINT NOT NULL, started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), completed_by BIGINT NULL,
  completed_at DATETIME(3) NULL,
  active_equipment_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN equipment_id ELSE NULL END) STORED,
  PRIMARY KEY (id), UNIQUE KEY uk_mes_equipment_maintenance_no (maintenance_no),
  UNIQUE KEY uk_mes_equipment_maintenance_open (active_equipment_id),
  KEY idx_mes_equipment_maintenance_history (equipment_id, started_at, id), KEY idx_mes_equipment_maintenance_status (status, started_at),
  CONSTRAINT chk_mes_equipment_maintenance_status CHECK ((status = 'OPEN' AND completed_by IS NULL AND completed_at IS NULL) OR (status = 'COMPLETED' AND completed_by IS NOT NULL AND completed_at IS NOT NULL AND CHAR_LENGTH(TRIM(maintenance_note)) > 0)),
  CONSTRAINT fk_mes_equipment_maintenance_equipment FOREIGN KEY (equipment_id) REFERENCES mes_equipment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_maintenance_starter FOREIGN KEY (started_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_mes_equipment_maintenance_completer FOREIGN KEY (completed_by) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NULL,
  operation_code VARCHAR(80) NOT NULL,
  target_type VARCHAR(40) NOT NULL,
  target_id BIGINT NULL,
  result VARCHAR(10) NOT NULL,
  detail VARCHAR(1000) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_sys_operation_log_user_time (user_id, created_at),
  KEY idx_sys_operation_log_target (target_type, target_id, created_at),
  KEY idx_sys_operation_log_code_time (operation_code, created_at),
  CONSTRAINT chk_sys_operation_log_result CHECK (result IN ('SUCCESS', 'FAILURE')),
  CONSTRAINT fk_sys_operation_log_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Fixed roles. The seed is re-runnable for role names/descriptions.
INSERT INTO sys_role (role_code, role_name, description, status) VALUES
  ('ADMIN', '系统管理员', '系统配置及全部业务权限', 'ACTIVE'),
  ('PRODUCTION_MANAGER', '生产经理', '生产订单与生产总体管理', 'ACTIVE'),
  ('PLANNER', '计划员', '生产计划和工单编制发布', 'ACTIVE'),
  ('WORKSHOP_SUPERVISOR', '车间主管', '车间执行跟踪', 'ACTIVE'),
  ('OPERATOR', '操作员', '工单查询和生产报工', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description);

-- Stable menu/action permission codes consumed by the API and frontend.
INSERT INTO sys_permission (permission_code, permission_name, module_code, resource_type, status) VALUES
  ('dashboard:read', '查看生产看板', 'dashboard', 'MENU', 'ACTIVE'),
  ('product:read', '查看产品', 'product', 'MENU', 'ACTIVE'),
  ('product:manage', '管理产品', 'product', 'ACTION', 'ACTIVE'),
  ('process:read', '查看工序', 'process', 'MENU', 'ACTIVE'),
  ('process:manage', '管理工序', 'process', 'ACTION', 'ACTIVE'),
  ('order:read', '查看生产订单', 'order', 'MENU', 'ACTIVE'),
  ('order:manage', '管理生产订单', 'order', 'ACTION', 'ACTIVE'),
  ('order:confirm', '确认生产订单', 'order', 'ACTION', 'ACTIVE'),
  ('order:cancel', '取消生产订单', 'order', 'ACTION', 'ACTIVE'),
  ('plan:read', '查看生产计划', 'plan', 'MENU', 'ACTIVE'),
  ('plan:manage', '管理生产计划', 'plan', 'ACTION', 'ACTIVE'),
  ('plan:publish', '发布生产计划', 'plan', 'ACTION', 'ACTIVE'),
  ('plan:cancel', '取消生产计划', 'plan', 'ACTION', 'ACTIVE'),
  ('workorder:read', '查看生产工单', 'workorder', 'MENU', 'ACTIVE'),
  ('workorder:manage', '管理生产工单', 'workorder', 'ACTION', 'ACTIVE'),
  ('report:read', '查看生产报工', 'report', 'MENU', 'ACTIVE'),
  ('report:read:own', '查看本人报工', 'report', 'ACTION', 'ACTIVE'),
  ('report:create', '提交生产报工', 'report', 'ACTION', 'ACTIVE'),
  ('report:void', '作废生产报工', 'report', 'ACTION', 'ACTIVE'),
  ('quality:read', '查看质检记录', 'quality', 'MENU', 'ACTIVE'),
  ('quality:create', '创建质检记录', 'quality', 'ACTION', 'ACTIVE'),
  ('quality:dispose', '处理不合格品', 'quality', 'ACTION', 'ACTIVE'),
  ('equipment:read', '查看设备档案', 'equipment', 'MENU', 'ACTIVE'),
  ('equipment:manage', '管理设备档案', 'equipment', 'ACTION', 'ACTIVE'),
  ('equipment:maintain', '管理设备维修', 'equipment', 'ACTION', 'ACTIVE'),
  ('statistics:read', '查看生产统计', 'statistics', 'MENU', 'ACTIVE'),
  ('system:user:manage', '管理用户', 'system', 'ACTION', 'ACTIVE'),
  ('system:role:manage', '管理角色', 'system', 'ACTION', 'ACTIVE'),
  ('system:permission:read', '查看权限配置', 'system', 'ACTION', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  module_code = VALUES(module_code),
  resource_type = VALUES(resource_type);

-- Admin receives every seeded permission.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN (
  'quality:read','quality:create','quality:dispose','equipment:read','equipment:manage','equipment:maintain'
) WHERE r.role_code = 'PRODUCTION_MANAGER';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.permission_code IN ('quality:read','equipment:read')
WHERE r.role_code = 'WORKSHOP_SUPERVISOR';

-- Production manager: product/order management and read access elsewhere.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'dashboard:read', 'product:read', 'product:manage', 'process:read', 'process:manage',
  'order:read', 'order:manage', 'order:confirm', 'order:cancel',
  'plan:read', 'workorder:read', 'report:read', 'statistics:read'
)
WHERE r.role_code = 'PRODUCTION_MANAGER';

-- Planner: manage plans/work orders; view product, orders, reports and statistics.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'dashboard:read', 'product:read', 'process:read', 'order:read',
  'plan:read', 'plan:manage', 'plan:publish', 'plan:cancel',
  'workorder:read', 'workorder:manage', 'report:read', 'statistics:read'
)
WHERE r.role_code = 'PLANNER';

-- Workshop supervisor: read-only production monitoring.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'dashboard:read', 'product:read', 'process:read', 'order:read', 'plan:read',
  'workorder:read', 'report:read', 'statistics:read'
)
WHERE r.role_code = 'WORKSHOP_SUPERVISOR';

-- Operator: execute work orders and read own reports.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  'workorder:read', 'report:read:own', 'report:create'
)
WHERE r.role_code = 'OPERATOR';

-- No user/password is seeded. Create the first administrator through a controlled
-- bootstrap flow that accepts a deployment-provided password and stores only its hash.
