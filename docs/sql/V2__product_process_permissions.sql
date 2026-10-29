-- Add permissions required by product and process master-data APIs.
INSERT INTO sys_permission (permission_code, permission_name, module_code, resource_type, status) VALUES
  ('process:read', '查看工序', 'process', 'MENU', 'ACTIVE'),
  ('process:manage', '管理工序', 'process', 'ACTION', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  module_code = VALUES(module_code),
  resource_type = VALUES(resource_type),
  status = 'ACTIVE';

-- Production managers maintain master data; planners and workshop supervisors may view it.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('process:read', 'process:manage')
WHERE r.role_code = 'PRODUCTION_MANAGER';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'process:read'
WHERE r.role_code IN ('PLANNER', 'WORKSHOP_SUPERVISOR');
