import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Shell from './components/Shell'
import Dashboard from './pages/Dashboard'
import WorkOrders from './pages/WorkOrders'
import MapView from './pages/MapView'
import FieldTeams from './pages/FieldTeams'
import Login from './pages/Login'

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
        </Route>
      </Routes>
    </BrowserRouter>
  )
}