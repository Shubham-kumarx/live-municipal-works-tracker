import axios from 'axios'
import { API_BASE_URL } from '../config/backend'

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

const csrfClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true })
let csrfRequest
let csrfToken

const isUnsafeRequest = config =>
  !['get', 'head', 'options'].includes((config?.method || 'get').toLowerCase())

async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  if (!csrfRequest) {
    csrfRequest = csrfClient.get('/api/auth/csrf')
      .then(({ data }) => {
        if (!data?.token || !data?.headerName) throw new Error('Invalid CSRF token response')
        csrfToken = data
        return data
      })
      .finally(() => { csrfRequest = null })
  }
  return csrfRequest
}

api.interceptors.request.use(async config => {
  const method = (config.method || 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) {
    const { headerName, token } = await ensureCsrfToken()
    config.headers[headerName] = token
  }
  return config
})

// If token expires redirect to login
api.interceptors.response.use(
  response => {
    if (isUnsafeRequest(response.config)) csrfToken = null
    return response
  },
  error => {
    if (isUnsafeRequest(error.config)) csrfToken = null
    if (error.response?.status === 401) {
      localStorage.removeItem('user')
      if (!error.config?.skipAuthRedirect) window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
