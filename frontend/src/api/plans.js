import http from './http'

export const listAvailableOrders = () => http.get('/v1/plans/available-orders')
export const listPlans = (params) => http.get('/v1/plans', { params })
export const getPlan = (id) => http.get(`/v1/plans/${id}`)
export const createPlan = (data) => http.post('/v1/plans', data)
export const updatePlan = (id, data) => http.put(`/v1/plans/${id}`, data)
export const releasePlan = (id, data) => http.patch(`/v1/plans/${id}/release`, data)
export const cancelPlan = (id, data) => http.patch(`/v1/plans/${id}/cancel`, data)
