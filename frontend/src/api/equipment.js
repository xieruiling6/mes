import http from './http'

export const listEquipment = (params) => http.get('/v1/equipment', { params })
export const getEquipment = (id) => http.get(`/v1/equipment/${id}`)
export const createEquipment = (data) => http.post('/v1/equipment', data)
export const updateEquipment = (id, data) => http.put(`/v1/equipment/${id}`, data)
export const changeEquipmentStatus = (id, status) => http.patch(`/v1/equipment/${id}/status`, { status })
export const listMaintenanceRecords = (id, params) => http.get(`/v1/equipment/${id}/maintenance-records`, { params })
export const startMaintenance = (id, reason) => http.post(`/v1/equipment/${id}/maintenance-records`, { reason })
export const completeMaintenance = (id, recordId, note) => http.patch(`/v1/equipment/${id}/maintenance-records/${recordId}/complete`, { note })
