# 工擎 MES 开发约定

## 当前阶段

项目初始化已完成基础前后端骨架。后续开发必须以 `docs/requirements.md`、`docs/architecture.md` 和 `docs/database-design.md` 为基线。

## 目录职责

- `backend/`：Java 17、Spring Boot 后端。
- `frontend/`：Vue 3、Vite 前端。
- `docs/`：需求、架构、数据库设计和迁移脚本文档。

## 开发规则

- 不提交真实密码、API 密钥、Cookie、数据库备份或本地 `.env` 文件。
- 后端业务规则必须在服务端校验，不能依赖前端传入的数量、状态、用户身份或权限。
- API 使用统一响应结构；跨模块写操作使用事务。
- 报工数量、工单状态和有效报工口径必须遵循需求文档及数据库设计。
- 数据库结构通过版本化迁移管理；不要在应用启动时自动执行破坏性 DDL。
- 新增依赖时同步更新 Maven 配置或 npm lockfile，并验证 Java 17/Node LTS 兼容性。
- 完成变更后运行相关测试和构建；文档、配置或脚本变更也要在 README 中保持可运行说明一致。

## 常用命令

```bash
cd backend && mvn test
cd frontend && npm run build
```
