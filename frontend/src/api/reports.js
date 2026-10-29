import http from './http'

export const listAvailableReportWorkOrders = () => http.get('/v1/reports/available-work-orders')
export const getReportFilterOptions = () => http.get('/v1/reports/filter-options')
export const submitReport = (data) => http.post('/v1/reports', data)
export const listMyReports = (params) => http.get('/v1/reports/mine', { params })
export const listReports = (params) => http.get('/v1/reports', { params })
export const getWorkOrderReportSummary = (workOrderId) => http.get(`/v1/reports/work-orders/${workOrderId}/summary`)
export const voidReport = (id, reason) => http.patch(`/v1/reports/${id}/void`, { reason })
