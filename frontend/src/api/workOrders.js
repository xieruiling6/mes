import http from './http'

export const listAvailablePlans = () => http.get('/v1/work-orders/available-plans')
export const listWorkOrders = (params) => http.get('/v1/work-orders', { params })
export const createWorkOrderFromPlan = (planId, data) => http.post(`/v1/work-orders/from-plan/${planId}`, data)
export const getWorkOrder = (id) => http.get(`/v1/work-orders/${id}`)
export const listWorkOrderReports = (id, params) => http.get(`/v1/work-orders/${id}/reports`, { params })
export const dispatchWorkOrder = (id, data) => http.patch(`/v1/work-orders/${id}/dispatch`, data)
export const startWorkOrder = (id, data) => http.patch(`/v1/work-orders/${id}/start`, data)
export const pauseWorkOrder = (id, data) => http.patch(`/v1/work-orders/${id}/pause`, data)
export const resumeWorkOrder = (id, data) => http.patch(`/v1/work-orders/${id}/resume`, data)
export const completeWorkOrder = (id, data) => http.patch(`/v1/work-orders/${id}/complete`, data)
