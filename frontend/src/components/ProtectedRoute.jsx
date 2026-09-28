import { Navigate } from 'react-router-dom'
import { getSession, landingPath } from '../auth/session'

export default function ProtectedRoute({ roles, children }) {
  const session = getSession()
  if (!session) return <Navigate to="/login" replace />
  if (roles && !roles.includes(session.user.role)) {
    return <Navigate to={landingPath(session.user.role)} replace />
  }
  return children
}

export function PublicOnlyRoute({ children }) {
  const session = getSession()
  return session ? <Navigate to={landingPath(session.user.role)} replace /> : children
}
