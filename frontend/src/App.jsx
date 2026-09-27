import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Shell from './components/Shell'
import Dashboard from './pages/Dashboard'
import WorkOrders from './pages/WorkOrders'
import MapView from './pages/MapView'
import FieldTeams from './pages/FieldTeams'
import Login from './pages/Login'
import Register from './pages/Register'
import AddStaff from './pages/AddStaff'
import ProtectedRoute from './components/ProtectedRoute'
import { getSession, landingPath } from './auth/session'

function ComingSoon({ page }) {
  return (
    <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-muted)' }}>
      <div style={{ fontSize: 32, marginBottom: 12 }}>🚧</div>
      <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--text-primary)', marginBottom: 6 }}>
        {page}
      </div>
      <div style={{ fontSize: 13 }}>This module is under development</div>
    </div>
  )
}

export default function App() {
  const role = getSession()?.user.role
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/register" element={<Register />} />
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<ProtectedRoute><Shell /></ProtectedRoute>}>
          <Route path="add-staff" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><AddStaff /></ProtectedRoute>} />
          <Route index element={<Navigate to={role ? landingPath(role) : '/login'} replace />} />
          <Route path="dashboard" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><Dashboard /></ProtectedRoute>} />
          <Route path="work-orders" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><WorkOrders /></ProtectedRoute>} />
          <Route path="map" element={<ProtectedRoute roles={['CITIZEN', 'FIELD_WORKER', 'WARD_OFFICER', 'MUNICIPAL_ADMIN', 'AUDITOR']}><MapView /></ProtectedRoute>} />
          <Route path="field-teams" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><FieldTeams /></ProtectedRoute>} />
          <Route path="complaints" element={<ComingSoon page="Complaints" />} />
          <Route path="reports" element={<ComingSoon page="Reports" />} />
          <Route path="settings" element={<ComingSoon page="Settings" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
