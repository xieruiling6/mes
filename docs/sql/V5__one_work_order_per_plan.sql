-- Check existing data for duplicate plan_id values before applying this migration.
ALTER TABLE mes_work_order
  DROP INDEX uk_mes_work_order_plan_sequence,
  ADD UNIQUE KEY uk_mes_work_order_plan (plan_id);
