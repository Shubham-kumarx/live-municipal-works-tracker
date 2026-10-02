const configuredBase = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').trim()

export const API_BASE_URL = configuredBase.replace(/\/$/, '')
export const WS_URL = `${API_BASE_URL}/ws`
