import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Shell from './components/Shell'
import Dashboard from './pages/Dashboard'
import WorkOrders from './pages/WorkOrders'
import MapView from './pages/MapView'
import FieldTeams from './pages/FieldTeams'
import Login from './pages/Login'

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
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<Shell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="work-orders" element={<WorkOrders />} />
          <Route path="map" element={<MapView />} />
          <Route path="field-teams" element={<FieldTeams />} />
          <Route path="complaints" element={<ComingSoon page="Complaints" />} />
          <Route path="reports" element={<ComingSoon page="Reports" />} />
          <Route path="settings" element={<ComingSoon page="Settings" />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}