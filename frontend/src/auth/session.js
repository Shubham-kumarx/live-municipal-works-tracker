export function getSession() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    if (!user || typeof user !== 'object' || typeof user.role !== 'string') return null
    return { user }
  } catch {
    return null
  }
}

export function clearSession() {
  localStorage.removeItem('user')
}

export function setSessionUser(user) {
  localStorage.setItem('user', JSON.stringify({
    email: user.email, fullName: user.fullName, role: user.role, wardId: user.wardId,
  }))
}

export function landingPath(role) {
  return ['MUNICIPAL_ADMIN', 'WARD_OFFICER'].includes(role) ? '/dashboard' : '/map'
}
