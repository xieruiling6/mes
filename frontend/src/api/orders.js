import http from './http'

export const listOrders = (params) => http.get('/v1/orders', { params })
export const getOrder = (id) => http.get(`/v1/orders/${id}`)
export const getOrderExecution = (id) => http.get(`/v1/orders/${id}/execution`)
export const createOrder = (data) => http.post('/v1/orders', data)
export const updateOrder = (id, data) => http.put(`/v1/orders/${id}`, data)
export const confirmOrder = (id) => http.patch(`/v1/orders/${id}/confirm`)
export const cancelOrder = (id) => http.patch(`/v1/orders/${id}/cancel`)
