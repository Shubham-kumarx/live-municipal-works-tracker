export function getSession() {
  const token = localStorage.getItem('token')
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    if (!token || !user || typeof user !== 'object' || typeof user.role !== 'string') return null
    return { token, user }
  } catch {
    return null
  }
}

export function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
}

export function landingPath(role) {
  return ['MUNICIPAL_ADMIN', 'WARD_OFFICER'].includes(role) ? '/dashboard' : '/map'
}
