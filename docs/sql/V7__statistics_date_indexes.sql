-- Add date-leading indexes for dashboard and report date range aggregation.
-- Apply once to existing databases; new databases receive these indexes from init.sql.
ALTER TABLE mes_report
  ADD KEY idx_mes_report_status_date (report_status, report_date);

ALTER TABLE mes_quality_inspection
  ADD KEY idx_mes_quality_inspection_date (inspection_date);
