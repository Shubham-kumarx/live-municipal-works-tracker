import { Outlet, NavLink, useNavigate } from 'react-router-dom'

const NAV = [
  {
    section: 'Operations',
    links: [
      { to: '/dashboard', label: 'Overview', icon: <IconGrid /> },
      { to: '/work-orders', label: 'Work Orders', icon: <IconClipboard /> },
      { to: '/map', label: 'Live Map', icon: <IconMap /> },
      { to: '/field-teams', label: 'Field Teams', icon: <IconUsers /> },
    ]
  },
  {
    section: 'Management',
    links: [
      { to: '/complaints', label: 'Complaints', icon: <IconAlert /> },
      { to: '/reports', label: 'Reports', icon: <IconChart /> },
      { to: '/settings', label: 'Settings', icon: <IconSettings /> },
    ]
  }
]

export default function Shell() {
  const navigate = useNavigate()
  const user = JSON.parse(localStorage.getItem('user') || '{}')

  function handleLogout() {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    navigate('/login')
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        {/* Brand */}
        <div className="sidebar-brand">
          <div className="sidebar-brand-dot" />
          <span className="sidebar-brand-text">CivicOps</span>
        </div>

        {/* Nav */}
        <nav style={{ flex: 1 }}>
          {NAV.map(section => (
            <div key={section.section}>
              <div className="sidebar-section-label">{section.section}</div>
              {section.links.map(link => (
                <NavLink
                  key={link.to}
                  to={link.to}
                  className={({ isActive }) =>
                    'sidebar-link' + (isActive ? ' active' : '')
                  }
                >
                  {link.icon}
                  {link.label}
                </NavLink>
              ))}
            </div>
          ))}
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
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)' }}>
              {new Date().toLocaleDateString('en-IN', {
                weekday: 'long', day: 'numeric',
                month: 'long', year: 'numeric'
              })}
            </span>
            <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>
              North Delhi Municipal Corporation
            </span>
          </div>

          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
            {/* Search */}
            <div style={{ position: 'relative' }}>
              <input
                className="input input-sm"
                placeholder="Search work orders, complaints..."
                style={{ width: 240, paddingLeft: 28 }}
              />
              <IconSearch style={{
                position: 'absolute', left: 8, top: '50%',
                transform: 'translateY(-50%)',
                width: 13, height: 13, color: 'var(--text-muted)'
              }} />
            </div>

            {/* Ward badge */}
            <div style={{
              fontSize: 11.5, padding: '3px 10px',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
              color: 'var(--text-secondary)',
              background: 'var(--bg-hover)'
            }}>
              Ward W-014 · Rohini
            </div>

            {/* Notifications */}
            <button className="btn btn-ghost btn-sm" style={{ padding: '4px 6px' }}>
              <IconBell style={{ width: 15, height: 15 }} />
            </button>
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
function IconChart() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M1 12l4-5 3 3 3-6 4 8"/><path d="M1 15h14"/>
  </svg>
}
function IconSettings() {
  return <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <circle cx="8" cy="8" r="2"/><path d="M8 1v2M8 13v2M1 8h2M13 8h2M3 3l1.4 1.4M11.6 11.6L13 13M3 13l1.4-1.4M11.6 4.4L13 3"/>
  </svg>
}
function IconLogout() {
  return <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M6 2H3a1 1 0 00-1 1v10a1 1 0 001 1h3M11 11l4-3-4-3M7 8h8"/>
  </svg>
}
function IconSearch({ style }) {
  return <svg style={style} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <circle cx="6.5" cy="6.5" r="4.5"/><path d="M10 10l3.5 3.5"/>
  </svg>
}
function IconBell({ style }) {
  return <svg style={style} viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
    <path d="M8 1a5 5 0 015 5v3l1 2H2l1-2V6a5 5 0 015-5zM6.5 13a1.5 1.5 0 003 0"/>
  </svg>
}