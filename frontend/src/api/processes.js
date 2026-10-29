import http from './http'

export const listProcesses = (params) => http.get('/v1/processes', { params })
export const getProcess = (id) => http.get(`/v1/processes/${id}`)
export const createProcess = (data) => http.post('/v1/processes', data)
export const updateProcess = (id, data) => http.put(`/v1/processes/${id}`, data)
export const updateProcessStatus = (id, status) => http.patch(`/v1/processes/${id}/status`, { status })
