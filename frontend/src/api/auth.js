import http from './http'

export const fetchCsrf = () => http.get('/v1/auth/csrf')
export const loginRequest = (credentials) => http.post('/v1/auth/login', credentials)
export const fetchCurrentUser = () => http.get('/v1/auth/me')
export const logoutRequest = () => http.post('/v1/auth/logout')

export const updateProfileRequest = (data) => http.put('/v1/auth/profile', data)
export const changePasswordRequest = (data) => http.post('/v1/auth/change-password', data)
