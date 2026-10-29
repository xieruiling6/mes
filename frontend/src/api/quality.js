import http from './http'

export const listQualityRecords = (params) => http.get('/v1/quality/records', { params })
export const getQualityRecord = (id) => http.get(`/v1/quality/records/${id}`)
export const createQualityRecord = (data) => http.post('/v1/quality/records', data)
export const disposeQualityRecord = (id, data) => http.patch(`/v1/quality/records/${id}/disposition`, data)
export const getQualityOptions = () => http.get('/v1/quality/filter-options')
export const listQualityReports = (workOrderId) => http.get(`/v1/quality/work-orders/${workOrderId}/reports`)
