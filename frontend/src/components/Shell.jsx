import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { clearSession, getSession } from '../auth/session'
import api from '../api/axios'



const NAV = [
  {
    section: 'Operations',
    links: [
      { to: '/dashboard', label: 'Overview', icon: <IconGrid />, roles: ['MUNICIPAL_ADMIN', 'WARD_OFFICER'] },
      { to: '/work-orders', label: 'Work Orders', icon: <IconClipboard />, roles: ['MUNICIPAL_ADMIN', 'WARD_OFFICER'] },
      { to: '/map', label: 'Live Map', icon: <IconMap />, roles: ['CITIZEN', 'FIELD_WORKER', 'WARD_OFFICER', 'MUNICIPAL_ADMIN', 'AUDITOR'] },
      { to: '/field-teams', label: 'Field Teams', icon: <IconUsers />, roles: ['MUNICIPAL_ADMIN', 'WARD_OFFICER'] },
    ]
  },
  {
    section: 'Management',
    links: [
      { to: '/complaints', label: 'Complaints', icon: <IconAlert />, roles: ['CITIZEN', 'MUNICIPAL_ADMIN', 'WARD_OFFICER'] },
    ]
  }
]

export default function Shell() {
  const navigate = useNavigate()
  const user = getSession()?.user || {}
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  async function handleLogout() {
    try { await api.post('/api/auth/logout') } finally {
      clearSession()
      navigate('/login')
    }
  }

  return (
    <div className="shell">
      {mobileNavOpen && <button className="sidebar-overlay" aria-label="Close navigation"
        onClick={() => setMobileNavOpen(false)} />}
      <aside id="app-sidebar" className={`sidebar${mobileNavOpen ? ' sidebar-open' : ''}`}>
        {/* Brand */}
        <div className="sidebar-brand">
          <div className="sidebar-brand-dot" />
          <span className="sidebar-brand-text">CivicOps</span>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1 }}>
          {NAV.map(section => {
            const visibleLinks = section.links.filter(l => l.roles.includes(user.role))
            if (visibleLinks.length === 0) return null
            return (
              <div key={section.section}>
                <div className="sidebar-section-label">{section.section}</div>
                {visibleLinks.map(link => (
                  <NavLink
                    key={link.to}
                    to={link.to}
                    className={({ isActive }) => 'sidebar-link' + (isActive ? ' active' : '')}
                    onClick={() => setMobileNavOpen(false)}
                  >
                    {link.icon}
                    {link.to === '/complaints' && user.role === 'CITIZEN'
                      ? 'My Complaints' : link.label}
                  </NavLink>
                ))}
              </div>
            )
          })}

          {(user.role === 'MUNICIPAL_ADMIN' || user.role === 'WARD_OFFICER') && (
            <div>
              <div className="sidebar-section-label">Admin</div>
              <NavLink to="/add-staff" onClick={() => setMobileNavOpen(false)}
                className={({ isActive }) => 'sidebar-link' + (isActive ? ' active' : '')}>
                <IconUsers /> Add Team Member
              </NavLink>
            </div>
          )}
        </nav>
        

        {/* Footer */}
        <div className="sidebar-footer">
          <div style={{
            padding: '8px 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 8
          }}>
            <div style={{
              width: 26, height: 26,
              borderRadius: '50%',
              background: '#2E5F44',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 10, fontWeight: 600, color: '#7CC4A0',
              flexShrink: 0
            }}>
              {user.fullName ? user.fullName.slice(0, 2).toUpperCase() : 'SK'}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12, color: '#D0D4DA', fontWeight: 500,
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {user.fullName || 'Officer'}
              </div>
              <div style={{ fontSize: 10.5, color: '#6B7280' }}>
                {user.role?.replace('_', ' ') || 'Municipal Admin'}
              </div>
            </div>
            <button
              onClick={handleLogout}
              style={{ background: 'none', border: 'none',
                color: '#6B7280', cursor: 'pointer', padding: 4 }}
              title="Logout"
            >
              <IconLogout />
            </button>
          </div>
        </div>
      </aside>

      <div className="content-area">
        {/* Topbar */}
        <header className="topbar">
          <button className="btn btn-ghost btn-sm mobile-nav-toggle"
            aria-label={mobileNavOpen ? 'Close navigation' : 'Open navigation'}
            aria-controls="app-sidebar" aria-expanded={mobileNavOpen}
            onClick={() => setMobileNavOpen(open => !open)}>
            <IconMenu />
          </button>
          <div className="topbar-date" style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)' }}>
              {new Date().toLocaleDateString('en-IN', {
                weekday: 'long', day: 'numeric',
                month: 'long', year: 'numeric'
              })}
            </span>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              Municipal works monitoring and decision support
            </span>
          </div>

          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
            {/* Ward badge */}
            <div className="topbar-ward" style={{
              fontSize: 11.5, padding: '3px 10px',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
              color: 'var(--text-secondary)',
              background: 'var(--bg-hover)'
            }}>
              {user.wardId ? `Ward ${user.wardId}` : 'No ward assigned'}
            </div>

          </div>
        </header>

        {/* Page content */}
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

/* ── Inline SVG icons ─────────────────── */
function IconGrid() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <rect x="1" y="1" width="6" height="6" rx="1"/><rect x="9" y="1" width="6" height="6" rx="1"/>
    <rect x="1" y="9" width="6" height="6" rx="1"/><rect x="9" y="9" width="6" height="6" rx="1"/>
  </svg>
}
function IconClipboard() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M5 2H3a1 1 0 00-1 1v11a1 1 0 001 1h10a1 1 0 001-1V3a1 1 0 00-1-1h-2"/>
    <rect x="5" y="1" width="6" height="3" rx="1"/><path d="M4 7h8M4 10h6"/>
  </svg>
}
function IconMap() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M1 3l4-1 6 2 4-1v11l-4 1-6-2-4 1V3z"/>
    <path d="M5 2v11M11 4v11"/>
  </svg>
}
function IconUsers() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <circle cx="6" cy="5" r="2.5"/><path d="M1 13c0-2.8 2.2-4 5-4s5 1.2 5 4"/>
    <path d="M11 7a2 2 0 100-4M15 13c0-2-1.3-3.3-4-3.7"/>
  </svg>
}
function IconAlert() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M8 1L1 14h14L8 1z"/><path d="M8 6v4M8 11.5v.5"/>
  </svg>
}
function IconLogout() {
  return <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M6 2H3a1 1 0 00-1 1v10a1 1 0 001 1h3M11 11l4-3-4-3M7 8h8"/>
  </svg>
}
function IconMenu() {
  return <svg viewBox="0 0 16 16" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M2 4h12M2 8h12M2 12h12" />
  </svg>
}
