import axios from 'axios'
import { API_BASE_URL } from '../config/backend'

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  withXSRFToken: true,
})

const csrfClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true })
let csrfRequest

async function ensureCsrfToken() {
  if (!csrfRequest) {
    csrfRequest = csrfClient.get('/api/auth/csrf').catch(error => {
      csrfRequest = null
      throw error
    })
  }
  await csrfRequest
}

api.interceptors.request.use(async config => {
  const method = (config.method || 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method)) await ensureCsrfToken()
  return config
})

// If token expires redirect to login
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('user')
      if (!error.config?.skipAuthRedirect) window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
