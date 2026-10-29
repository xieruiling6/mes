-- Add dispatched and paused states to the work-order lifecycle.
ALTER TABLE mes_work_order
  DROP CHECK chk_mes_work_order_status,
  ADD CONSTRAINT chk_mes_work_order_status
    CHECK (status IN ('PENDING', 'RELEASED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'CANCELLED'));
