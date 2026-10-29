-- Add persisted scheduling priority to production plans.
ALTER TABLE mes_production_plan
  ADD COLUMN priority SMALLINT NOT NULL DEFAULT 0 AFTER end_date;

ALTER TABLE mes_production_plan
  ADD CONSTRAINT chk_mes_production_plan_priority CHECK (priority >= 0);
