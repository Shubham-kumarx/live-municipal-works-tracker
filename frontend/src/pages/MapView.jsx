import { useEffect, useRef, useState } from 'react'
import FieldUpdateForm from '../components/FieldUpdateForm'
import api from '../api/axios'

const STATUS_COLOR = {
  SANCTIONED:  '#854D0E',
  IN_PROGRESS: '#1A4B6E',
  COMPLETED:   '#2D6A4F',
  DELAYED:     '#8B1C1C',
  CANCELLED:   '#52575E',
}

const STATUS_LABEL = {
  SANCTIONED:  'Sanctioned',
  IN_PROGRESS: 'In Progress',
  COMPLETED:   'Completed',
  DELAYED:     'Delayed',
  CANCELLED:   'Cancelled',
}

function statusBadge(s) {
  const map = {
    IN_PROGRESS: 'badge-ip',
    COMPLETED:   'badge-done',
    SANCTIONED:  'badge-pend',
    DELAYED:     'badge-late',
    CANCELLED:   'badge-sanc',
  }
  return <span className={`badge ${map[s] || 'badge-sanc'}`}>{STATUS_LABEL[s] || s}</span>
}

export default function MapView() {
  const mapRef      = useRef(null)
  const mapInstance = useRef(null)
  const markersRef  = useRef([])
  const stompRef    = useRef(null)

  const [projects, setProjects]   = useState([])
  const [selected, setSelected]   = useState(null)
  const [filter, setFilter]       = useState('all')
  const [typeFilter, setTypeFilter] = useState('all')
  const [loading, setLoading]     = useState(true)
  const [wsStatus, setWsStatus]   = useState('connecting')
  const [showUpdateForm, setShowUpdateForm] = useState(false)

  // Get wardId from logged-in user
  const user   = JSON.parse(localStorage.getItem('user') || '{}')
  const wardId = user.wardId || 2

  // ── Fetch projects from backend ──────────
  useEffect(() => {
    setLoading(true)
    api.get(`/api/projects/ward/${wardId}`)
      .then(res => {
        setProjects(res.data)
        setLoading(false)
      })
      .catch(err => {
        console.error('Failed to load projects:', err)
        setLoading(false)
      })
  }, [wardId])

  // ── WebSocket connection ─────────────────
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    // Dynamically import SockJS and STOMP
    import('@stomp/stompjs').then(({ Client }) => {
      const SockJS = window.SockJS

      const client = new Client({
        webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
        connectHeaders: {},
        onConnect: () => {
          setWsStatus('connected')
          // Subscribe to ward's project updates
          client.subscribe(
            `/topic/ward/${wardId}/projects`,
            message => {
              const update = JSON.parse(message.body)
              // Update the project in state when WebSocket fires
              setProjects(prev =>
                prev.map(p =>
                  p.id === update.projectId
                    ? {
                        ...p,
                        status: update.status,
                        progressPercentage: update.progressPercentage,
                        flagged: update.flagged,
                      }
                    : p
                )
              )
            }
          )
        },
        onDisconnect: () => setWsStatus('disconnected'),
        onStompError: () => setWsStatus('error'),
        reconnectDelay: 5000,
      })

      client.activate()
      stompRef.current = client
    })

    return () => {
      if (stompRef.current) stompRef.current.deactivate()
    }
  }, [wardId])

  // ── Init Leaflet map ─────────────────────
  useEffect(() => {
    if (mapInstance.current) return
    const L = window.L
    if (!L || !mapRef.current) return

    const map = L.map(mapRef.current, {
      center: [28.7180, 77.1120],
      zoom: 14,
    })

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map)

    mapInstance.current = map
  }, [])

  // ── Render markers when projects change ──
  useEffect(() => {
    const L = window.L
    if (!L || !mapInstance.current) return

    // Remove old markers
    markersRef.current.forEach(m => m.remove())
    markersRef.current = []

    const filtered = projects.filter(p => {
      const matchStatus = filter === 'all' || p.status === filter
      const matchType   = typeFilter === 'all' || p.projectType === typeFilter
      return matchStatus && matchType
    })

    filtered.forEach(project => {
      // Skip if no coordinates
      if (!project.latitude || !project.longitude) return

      const color = STATUS_COLOR[project.status] || '#52575E'

      const icon = L.divIcon({
        className: '',
        html: `
          <div title="${project.projectName}" style="
            width: 28px; height: 28px;
            border-radius: 50% 50% 50% 0;
            transform: rotate(-45deg);
            background: ${color};
            border: 2.5px solid white;
            box-shadow: 0 2px 6px rgba(0,0,0,0.35);
            cursor: pointer;
            position: relative;
          ">
            <div style="
              width: 10px; height: 10px;
              background: rgba(255,255,255,0.85);
              border-radius: 50%;
              position: absolute;
              top: 50%; left: 50%;
              transform: translate(-50%,-50%);
            "></div>
          </div>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 28],
      })

      const marker = L.marker([project.latitude, project.longitude], { icon })
        .addTo(mapInstance.current)
        .on('click', () => setSelected(project))

      markersRef.current.push(marker)
    })
  }, [projects, filter, typeFilter])

  const filteredProjects = projects.filter(p => {
    const matchStatus = filter === 'all' || p.status === filter
    const matchType   = typeFilter === 'all' || p.projectType === typeFilter
    return matchStatus && matchType
  })

  const types = ['all', ...new Set(projects.map(p => p.projectType).filter(Boolean))]

  return (
    <div style={{
      display: 'flex', flexDirection: 'column',
      height: 'calc(100vh - 46px)',
      margin: '-20px -24px'
    }}>

      {/* Toolbar */}
      <div style={{
        background: 'var(--bg-surface)',
        borderBottom: '1px solid var(--border)',
        padding: '8px 16px',
        display: 'flex', alignItems: 'center',
        gap: 10, flexShrink: 0
      }}>
        <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)', marginRight: 4 }}>
          Live Map
        </span>
        <div style={{ width: 1, height: 16, background: 'var(--border)' }} />

        {/* Status filters */}
        {[
          { key: 'all',         label: 'All' },
          { key: 'IN_PROGRESS', label: 'In Progress' },
          { key: 'DELAYED',     label: 'Delayed' },
          { key: 'SANCTIONED',  label: 'Pending' },
          { key: 'COMPLETED',   label: 'Completed' },
        ].map(f => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            style={{
              padding: '3px 10px', fontSize: 11.5,
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-sm)',
              background: filter === f.key ? 'var(--accent)' : 'var(--bg-surface)',
              color: filter === f.key ? '#fff' : 'var(--text-secondary)',
              cursor: 'pointer',
              fontWeight: filter === f.key ? 500 : 400
            }}
          >
            {f.label}
          </button>
        ))}

        <div style={{ width: 1, height: 16, background: 'var(--border)' }} />

        {/* Type filter */}
        <select
          className="input input-sm"
          style={{ width: 150 }}
          value={typeFilter}
          onChange={e => setTypeFilter(e.target.value)}
        >
          {types.map(t => (
            <option key={t} value={t}>
              {t === 'all' ? 'All types' : t.replace('_', ' ')}
            </option>
          ))}
        </select>

        {/* Count */}
        {loading
          ? <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>Loading...</span>
          : <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
              {filteredProjects.length} of {projects.length} projects
            </span>
        }

        {/* WS status */}
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{
            width: 7, height: 7, borderRadius: '50%',
            background: wsStatus === 'connected' ? 'var(--green)'
              : wsStatus === 'connecting' ? 'var(--amber)'
              : 'var(--red)'
          }} />
          <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
            {wsStatus === 'connected' ? 'Live · WebSocket connected'
              : wsStatus === 'connecting' ? 'Connecting...'
              : 'WebSocket disconnected'}
          </span>
        </div>
      </div>

      {/* Map + detail */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden', position: 'relative' }}>

        {/* Loading overlay */}
        {loading && (
          <div style={{
            position: 'absolute', inset: 0, zIndex: 500,
            background: 'rgba(242,241,238,0.8)',
            display: 'flex', alignItems: 'center', justifyContent: 'center'
          }}>
            <div style={{ fontSize: 13, color: 'var(--text-muted)' }}>
              Loading projects from database...
            </div>
          </div>
        )}

        {/* Map */}
        <div ref={mapRef} style={{ flex: 1 }} />

        {/* Legend */}
        <div style={{
          position: 'absolute', bottom: 40, left: 16, zIndex: 1000,
          background: 'rgba(28,31,36,0.92)',
          border: '1px solid #3A3F48',
          borderRadius: 'var(--r-md)',
          padding: '8px 12px'
        }}>
          <div style={{
            fontSize: 10, color: '#6B7280',
            textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: 6
          }}>
            Status
          </div>
          {Object.entries(STATUS_COLOR).map(([s, color]) => (
            <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: color }} />
              <span style={{ fontSize: 11, color: '#9AA0AB' }}>{STATUS_LABEL[s]}</span>
            </div>
          ))}
        </div>

        {/* Empty state */}
        {!loading && projects.length === 0 && (
          <div style={{
            position: 'absolute', top: '50%', left: '50%',
            transform: 'translate(-50%,-50%)', zIndex: 500,
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            borderRadius: 'var(--r-lg)',
            padding: '24px 32px', textAlign: 'center'
          }}>
            <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 4 }}>
              No projects in Ward {wardId}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
              Create a project from Postman or the admin panel
            </div>
          </div>
        )}

        {/* Detail panel */}
        {selected && (
          <div style={{
            width: 320, flexShrink: 0,
            background: 'var(--bg-surface)',
            borderLeft: '1px solid var(--border)',
            display: 'flex', flexDirection: 'column',
            overflow: 'auto', zIndex: 100
          }}>
            {/* Header */}
            <div style={{
              background: '#1C1F24', padding: '12px 16px',
              borderBottom: '1px solid #2E3238'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 11, color: '#6B7280', marginBottom: 3 }}>
                    Project #{selected.id}
                  </div>
                  <div style={{
                    fontSize: 13.5, fontWeight: 500,
                    color: '#E8E6E1', lineHeight: 1.3
                  }}>
                    {selected.projectName}
                  </div>
                </div>
                <button
                  onClick={() => setSelected(null)}
                  style={{
                    background: 'none', border: 'none',
                    color: '#6B7280', cursor: 'pointer',
                    padding: 4, marginLeft: 8, flexShrink: 0
                  }}
                >✕</button>
              </div>
              <div style={{ fontSize: 11.5, color: '#6B7280', marginTop: 5 }}>
                {selected.locationAddress}
              </div>
            </div>

            <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>

              {/* Status + flagged */}
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {statusBadge(selected.status)}
                {selected.flagged && (
                  <span className="badge badge-late">⚑ Flagged ({selected.flagCount})</span>
                )}
              </div>

              {/* Progress */}
              <div>
                <div style={{
                  display: 'flex', justifyContent: 'space-between',
                  fontSize: 11.5, color: 'var(--text-muted)', marginBottom: 5
                }}>
                  <span>Completion</span>
                  <span style={{ fontWeight: 500, color: 'var(--text-primary)' }}>
                    {selected.progressPercentage}%
                  </span>
                </div>
                <div style={{ height: 5, background: 'var(--border)', borderRadius: 3 }}>
                  <div style={{
                    height: 5, borderRadius: 3,
                    width: `${selected.progressPercentage}%`,
                    background: selected.status === 'DELAYED' ? 'var(--red)'
                      : selected.status === 'COMPLETED' ? 'var(--green)'
                      : 'var(--accent)'
                  }} />
                </div>
              </div>

              {/* Details grid */}
              <div style={{
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-md)', overflow: 'hidden'
              }}>
                {[
                  ['Type', selected.projectType?.replace(/_/g, ' ')],
                  ['Budget allocated', `₹${selected.budgetAllocated?.toLocaleString('en-IN')}`],
                  ['Budget spent', `₹${selected.budgetSpent?.toLocaleString('en-IN')}`],
                  ['Start date', selected.startDate],
                  ['Due date', selected.expectedEndDate || 'Not set'],
                  ['Coordinates', selected.latitude && selected.longitude
                    ? `${selected.latitude.toFixed(4)}, ${selected.longitude.toFixed(4)}`
                    : 'Not set'
                  ],
                ].map(([label, value], i, arr) => (
                  <div key={label} style={{
                    display: 'flex', justifyContent: 'space-between',
                    padding: '8px 12px', fontSize: 12.5,
                    borderBottom: i < arr.length - 1 ? '1px solid var(--border)' : 'none',
                    background: i % 2 === 0 ? 'var(--bg-surface)' : 'var(--bg-hover)'
                  }}>
                    <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                    <span style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{value}</span>
                  </div>
                ))}
              </div>

              {/* Description */}
              {selected.description && (
                <div>
                  <div className="t-label" style={{ marginBottom: 6 }}>Description</div>
                  <div style={{
                    fontSize: 12.5, color: 'var(--text-secondary)',
                    lineHeight: 1.7, padding: '8px 12px',
                    background: 'var(--bg-hover)',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--r-md)'
                  }}>
                    {selected.description}
                  </div>
                </div>
              )}

              {/* Actions */}
              <div style={{
                display: 'flex', flexDirection: 'column', gap: 6,
                paddingTop: 12, borderTop: '1px solid var(--border)'
              }}>
               <button
                  className="btn btn-primary btn-sm w-full"
                  onClick={() => setShowUpdateForm(true)}
                >
                  📝 Log progress update
                </button>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn-sm w-full">View full details</button>
                  <button className="btn btn-sm w-full">Add photo</button>
                </div>
              </div>

            </div>
          </div>
        )}
        {showUpdateForm && (
          <FieldUpdateForm
            project={selected}
            onClose={() => setShowUpdateForm(false)}
            onUpdated={() => {
              api.get(`/api/projects/ward/${wardId}`)
                .then(res => setProjects(res.data))

              setSelected(null)
              setShowUpdateForm(false)
            }}
          />
        )}        
      </div>
    </div>
  )
}