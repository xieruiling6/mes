import http from './http'

export const listUsers = (params) => http.get('/v1/system/users', { params })
export const createUser = (data) => http.post('/v1/system/users', data)
export const updateUser = (id, data) => http.put(`/v1/system/users/${id}`, data)
export const replaceUserRoles = (id, roleIds) => http.put(`/v1/system/users/${id}/roles`, { roleIds })
export const getUserRoleIds = (id) => http.get(`/v1/system/users/${id}/roles`)
export const listRoles = () => http.get('/v1/system/roles')
export const createRole = (data) => http.post('/v1/system/roles', data)
export const updateRole = (id, data) => http.put(`/v1/system/roles/${id}`, data)
export const getRolePermissionIds = (id) => http.get(`/v1/system/roles/${id}/permissions`)
export const replaceRolePermissions = (id, permissionIds) => http.put(`/v1/system/roles/${id}/permissions`, { permissionIds })
export const listPermissions = () => http.get('/v1/system/permissions')
