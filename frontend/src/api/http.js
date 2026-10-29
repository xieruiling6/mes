import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

export default http
