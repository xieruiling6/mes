import http from './http'

export const listProducts = (params) => http.get('/v1/products', { params })
export const getProduct = (id) => http.get(`/v1/products/${id}`)
export const createProduct = (data) => http.post('/v1/products', data)
export const updateProduct = (id, data) => http.put(`/v1/products/${id}`, data)
export const updateProductStatus = (id, status) => http.patch(`/v1/products/${id}/status`, { status })
