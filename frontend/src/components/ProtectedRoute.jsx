import { Navigate } from 'react-router-dom'
import { getSession } from '../auth/session'

export default function ProtectedRoute({ roles, children }) {
  const session = getSession()
  if (!session) return <Navigate to="/login" replace />
  if (roles && !roles.includes(session.user.role)) return <Navigate to="/map" replace />
  return children
}
