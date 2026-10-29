import http from './http'

export const getDashboardStatistics = (params) => http.get('/v1/statistics/dashboard', { params })
export const getProductionReportDetails = (params) => http.get('/v1/statistics/reports', { params })
