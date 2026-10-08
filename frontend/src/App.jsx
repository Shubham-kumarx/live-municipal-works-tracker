import { Component, lazy, Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Shell from './components/Shell'
import ProtectedRoute, { PublicOnlyRoute } from './components/ProtectedRoute'
import { clearSession, getSession, landingPath, setSessionUser } from './auth/session'
import api from './api/axios'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const WorkOrders = lazy(() => import('./pages/WorkOrders'))
const MapView = lazy(() => import('./pages/MapView'))
const FieldTeams = lazy(() => import('./pages/FieldTeams'))
const Login = lazy(() => import('./pages/Login'))
const Register = lazy(() => import('./pages/Register'))
const AddStaff = lazy(() => import('./pages/AddStaff'))
const Complaints = lazy(() => import('./pages/Complaints'))
const AdminComplaints = lazy(() => import('./pages/AdminComplaints'))

class AppErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { failed: false }
  }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  handleSignOut = async () => {
    try { await api.post('/api/auth/logout') } finally {
      clearSession()
      window.location.assign('/login')
    }
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: 'var(--bg-page)' }}>
        <div className="panel" role="alert" style={{ width: 'min(440px, 100%)', padding: 24, textAlign: 'center' }}>
          <h1 style={{ fontSize: 20, marginBottom: 8 }}>This page could not be displayed</h1>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 18 }}>
            Reload the page to try again. If the problem continues, sign in again.
          </p>
          <div style={{ display: 'flex', justifyContent: 'center', gap: 10, flexWrap: 'wrap' }}>
            <button className="btn btn-primary" onClick={() => window.location.reload()}>Reload page</button>
            <button className="btn" onClick={this.handleSignOut}>Sign out</button>
          </div>
        </div>
      </main>
    )
  }
}

function PageLoading() {
  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24 }}>
      <div className="panel" role="status" style={{ padding: 24, textAlign: 'center' }}>
        Loading page...
      </div>
    </main>
  )
}

export default function App() {
  const [sessionChecked, setSessionChecked] = useState(false)
  const [, setSessionVersion] = useState(0)
  useEffect(() => {
    api.get('/api/auth/session', { skipAuthRedirect: true })
      .then(({ data }) => setSessionUser(data))
      .catch(() => clearSession())
      .finally(() => {
        setSessionVersion(value => value + 1)
        setSessionChecked(true)
      })
  }, [])
  if (!sessionChecked) return <PageLoading />
  const role = getSession()?.user.role
  return (
    <AppErrorBoundary>
      <BrowserRouter>
        <Suspense fallback={<PageLoading />}>
          <Routes>
            <Route path="/register" element={<PublicOnlyRoute><Register /></PublicOnlyRoute>} />
            <Route path="/login" element={<PublicOnlyRoute><Login /></PublicOnlyRoute>} />
            <Route path="/" element={<ProtectedRoute><Shell /></ProtectedRoute>}>
              <Route path="add-staff" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><AddStaff /></ProtectedRoute>} />
              <Route index element={<Navigate to={role ? landingPath(role) : '/login'} replace />} />
              <Route path="dashboard" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><Dashboard /></ProtectedRoute>} />
              <Route path="work-orders" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><WorkOrders /></ProtectedRoute>} />
              <Route path="map" element={<ProtectedRoute roles={['CITIZEN', 'FIELD_WORKER', 'WARD_OFFICER', 'MUNICIPAL_ADMIN', 'AUDITOR']}><MapView /></ProtectedRoute>} />
              <Route path="field-teams" element={<ProtectedRoute roles={['MUNICIPAL_ADMIN', 'WARD_OFFICER']}><FieldTeams /></ProtectedRoute>} />
              <Route path="complaints" element={
                <ProtectedRoute roles={['CITIZEN', 'MUNICIPAL_ADMIN', 'WARD_OFFICER']}>
                  {role === 'CITIZEN' ? <Complaints /> : <AdminComplaints />}
                </ProtectedRoute>
              } />
            </Route>
            <Route path="*" element={<Navigate to={role ? landingPath(role) : '/login'} replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </AppErrorBoundary>
  )
}
