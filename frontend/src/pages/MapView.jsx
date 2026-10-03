import { useCallback, useEffect, useRef, useState } from 'react'
import FieldUpdateForm from '../components/FieldUpdateForm'
import api from '../api/axios'
import { apiErrorMessage } from '../api/errors'
import { getSession } from '../auth/session'
import { WS_URL } from '../config/backend'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'

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

const DELAY_RISK_LABEL = {
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  HIGH_DELAY_RISK: 'High delay risk',
}
const PRIORITY_STYLE = {
  LOW: { background: 'var(--green-lt)', color: 'var(--green)' },
  MEDIUM: { background: 'var(--amber-lt)', color: 'var(--amber)' },
  HIGH: { background: 'var(--red-lt)', color: 'var(--red)' },
  CRITICAL: { background: 'var(--red-lt)', color: 'var(--red)', fontWeight: 600 },
}

const DELAY_RISK_STYLE = {
  ON_TRACK: { background: 'var(--green-lt)', color: 'var(--green)' },
  AT_RISK: { background: 'var(--amber-lt)', color: 'var(--amber)' },
  HIGH_DELAY_RISK: { background: 'var(--red-lt)', color: 'var(--red)' },
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') return 'Unavailable'
  const score = Number(value)
  return Number.isFinite(score) ? score.toFixed(1) : 'Unavailable'
}

function hasScore(value) {
  return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
}

function ProtectedComplaintImage({ url }) {
  const [src, setSrc] = useState(null)
  useEffect(() => {
    if (!url) return undefined
    let active = true
    let objectUrl
    api.get(url, { responseType: 'blob' }).then(({ data }) => {
      if (!active) return
      objectUrl = URL.createObjectURL(data)
      setSrc(objectUrl)
    }).catch(() => setSrc(null))
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [url])
  return src ? <img src={src} alt="Municipal complaint" style={{
    width: 48, height: 48, objectFit: 'cover', borderRadius: 'var(--r-sm)'
  }} /> : null
}

function formatProgress(value) {
  if (!Number.isFinite(value)) return 'Unavailable'
  return `${Number(value.toFixed(2))}%`
}

export default function MapView() {
  const mapRef      = useRef(null)
  const mapInstance = useRef(null)
  const markersRef  = useRef([])
  const stompRef    = useRef(null)
  const fetchSequence = useRef(0)
  const centeredWardRef = useRef(null)

  const [projects, setProjects]   = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [filter, setFilter]       = useState('all')
  const [typeFilter, setTypeFilter] = useState('all')
  const [loading, setLoading]     = useState(true)
  const [loadError, setLoadError] = useState('')
  const [wsStatus, setWsStatus]   = useState('connecting')
  const [showUpdateForm, setShowUpdateForm] = useState(false)
  const [delayRisk, setDelayRisk] = useState(null)
  const [delayRiskLoading, setDelayRiskLoading] = useState(false)
  const [delayRiskError, setDelayRiskError] = useState('')
  const [priority, setPriority] = useState(null)
  const [priorityLoading, setPriorityLoading] = useState(false)
  const [priorityError, setPriorityError] = useState('')
  const [priorityRetry, setPriorityRetry] = useState(0)
  const [linkedComplaints, setLinkedComplaints] = useState([])
  const [linkedComplaintsLoading, setLinkedComplaintsLoading] = useState(false)
  const [linkedComplaintsError, setLinkedComplaintsError] = useState('')
  const [updateSuccess, setUpdateSuccess] = useState('')
  const [delayRiskRetry, setDelayRiskRetry] = useState(0)
  const [complaintsRetry, setComplaintsRetry] = useState(0)
  
  

  // Get wardId from logged-in user
  const user   = getSession()?.user || {}
  const wardId = Number.isInteger(user.wardId) && user.wardId > 0 ? user.wardId : null
  const canUpdateStatus = ['FIELD_WORKER', 'WARD_OFFICER', 'MUNICIPAL_ADMIN'].includes(user.role)
  const selected = projects.find(project => project.id === selectedId) || null

  const loadProjects = useCallback(async (showLoading = false) => {
    if (!wardId) {
      setLoading(false)
      setProjects([])
      setLoadError('')
      return
    }
    const sequence = ++fetchSequence.current
    if (showLoading) setLoading(true)
    try {
      const res = await api.get(`/api/projects/ward/${wardId}`)
      if (sequence === fetchSequence.current) {
        setProjects(res.data)
        setLoadError('')
        setLoading(false)
      }
    } catch (err) {
      if (sequence === fetchSequence.current) {
        setLoadError(apiErrorMessage(err, 'Projects could not be loaded. Please try again.'))
        setLoading(false)
      }
      throw err
    }
  }, [wardId])

  // ── Fetch projects from backend ──────────
  useEffect(() => {
    loadProjects(true).catch(() => {})
  }, [loadProjects])

  useEffect(() => {
    if (!selected) {
      setDelayRisk(null)
      setDelayRiskError('')
      setDelayRiskLoading(false)
      return
    }

    let cancelled = false
    setDelayRiskLoading(true)
    setDelayRiskError('')
    api.get(`/api/projects/${selected.id}/delay-risk`)
      .then(({ data }) => {
        if (!cancelled) setDelayRisk(data)
      })
      .catch(() => {
        if (!cancelled) {
          setDelayRisk(null)
          setDelayRiskError('Delay risk could not be calculated.')
        }
      })
      .finally(() => {
        if (!cancelled) setDelayRiskLoading(false)
      })

    return () => { cancelled = true }
  }, [selected, delayRiskRetry])

  useEffect(() => {
    if (!selected) {
      setPriority(null); setPriorityError(''); setPriorityLoading(false)
      return
    }
    let cancelled = false
    setPriorityLoading(true); setPriorityError('')
    api.get(`/api/projects/${selected.id}/priority`)
      .then(({ data }) => { if (!cancelled) setPriority(data) })
      .catch(() => {
        if (!cancelled) {
          setPriority(null)
          setPriorityError('Priority score could not be calculated.')
        }
      })
      .finally(() => { if (!cancelled) setPriorityLoading(false) })
    return () => { cancelled = true }
  }, [selected, priorityRetry])

  useEffect(() => {
    if (!selected) {
      setLinkedComplaints([]); setLinkedComplaintsError(''); setLinkedComplaintsLoading(false)
      return
    }
    let cancelled = false
    setLinkedComplaintsLoading(true); setLinkedComplaintsError('')
    api.get(`/api/projects/${selected.id}/complaints`)
      .then(({ data }) => { if (!cancelled) setLinkedComplaints(data) })
      .catch(() => {
        if (!cancelled) {
          setLinkedComplaints([])
          setLinkedComplaintsError('Linked complaints could not be loaded.')
        }
      })
      .finally(() => { if (!cancelled) setLinkedComplaintsLoading(false) })
    return () => { cancelled = true }
  }, [selected, complaintsRetry])

  // ── WebSocket connection ─────────────────
  useEffect(() => {
    if (!wardId) {
      setWsStatus('disconnected')
      return
    }

    const client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        beforeConnect: () => setWsStatus('connecting'),
        onConnect: () => {
          setWsStatus('connected')
          loadProjects().catch(() => {})
          // Subscribe to ward's project updates
          client.subscribe(
            `/topic/ward/${wardId}/projects`,
            message => {
              JSON.parse(message.body)
              loadProjects().catch(() => {})
            }
          )
        },
        onDisconnect: () => setWsStatus('disconnected'),
        onStompError: () => setWsStatus('error'),
        onWebSocketError: () => setWsStatus('error'),
        onWebSocketClose: () => setWsStatus('disconnected'),
        reconnectDelay: 5000,
    })
    client.activate()
    stompRef.current = client

    return () => {
      client.deactivate()
      if (stompRef.current === client) stompRef.current = null
    }
  }, [wardId, loadProjects])

  // ── Init Leaflet map ─────────────────────
  useEffect(() => {
    if (mapInstance.current) return
    if (!mapRef.current) return

    const map = L.map(mapRef.current, {
      center: [20.5937, 78.9629],
      zoom: 5,
    })

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map)

    mapInstance.current = map
    return () => {
      markersRef.current = []
      map.remove()
      if (mapInstance.current === map) mapInstance.current = null
    }
  }, [])

  useEffect(() => {
    if (!wardId || !mapInstance.current || centeredWardRef.current === wardId) return
    const project = projects.find(item => Number.isFinite(item.latitude) && Number.isFinite(item.longitude))
    if (project) {
      mapInstance.current.setView([project.latitude, project.longitude], 14)
      centeredWardRef.current = wardId
      return
    }
    let cancelled = false
    api.get(`/api/wards/${wardId}`).then(({ data }) => {
      if (!cancelled && Number.isFinite(data.centerLatitude) && Number.isFinite(data.centerLongitude)) {
        mapInstance.current?.setView([data.centerLatitude, data.centerLongitude], 14)
        centeredWardRef.current = wardId
      }
    }).catch(() => {})
    return () => { cancelled = true }
  }, [wardId, projects])

  // ── Render markers when projects change ──
  useEffect(() => {
    if (!mapInstance.current) return

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
      if (!Number.isFinite(project.latitude) || !Number.isFinite(project.longitude)) return

      const color = STATUS_COLOR[project.status] || '#52575E'

      const markerElement = document.createElement('div')
      markerElement.title = project.projectName || ''
      Object.assign(markerElement.style, {
        width: '28px', height: '28px', borderRadius: '50% 50% 50% 0',
        transform: 'rotate(-45deg)', background: color, border: '2.5px solid white',
        boxShadow: '0 2px 6px rgba(0,0,0,0.35)', cursor: 'pointer', position: 'relative',
      })
      const center = document.createElement('div')
      Object.assign(center.style, {
        width: '10px', height: '10px', background: 'rgba(255,255,255,0.85)',
        borderRadius: '50%', position: 'absolute', top: '50%', left: '50%',
        transform: 'translate(-50%,-50%)',
      })
      markerElement.appendChild(center)

      const icon = L.divIcon({
        className: '',
        html: markerElement,
        iconSize: [28, 28],
        iconAnchor: [14, 28],
      })

      const marker = L.marker([project.latitude, project.longitude], { icon })
        .addTo(mapInstance.current)
        .on('click', () => setSelectedId(project.id))

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
    <div className="map-page" style={{
      display: 'flex', flexDirection: 'column',
      height: 'calc(100vh - 46px)',
      margin: '-20px -24px'
    }}>

      {/* Toolbar */}
      <div className="map-toolbar" style={{
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
        {!loading && !wardId && (
          <div style={{
            position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)',
            zIndex: 500, background: 'var(--bg-surface)', border: '1px solid var(--border)',
            borderRadius: 'var(--r-lg)', padding: '24px 32px', textAlign: 'center'
          }}>
            <div style={{ fontSize: 13, fontWeight: 500 }}>No ward is assigned to this account</div>
          </div>
        )}
        {!loading && loadError && (
          <div style={{
            position: 'absolute', top: 16, left: '50%', transform: 'translateX(-50%)', zIndex: 1000,
            background: 'var(--red-lt)', border: '1px solid #FCA5A5', color: 'var(--red)',
            borderRadius: 'var(--r-md)', padding: '8px 12px', fontSize: 12.5
          }} role="alert">
            {loadError} <button className="btn btn-ghost btn-sm"
              onClick={() => loadProjects(true).catch(() => {})}>Retry</button>
          </div>
        )}
        {!loading && !loadError && wardId && projects.length === 0 && (
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
        {!loading && !loadError && projects.length > 0 && filteredProjects.length === 0 && (
          <div className="map-empty-overlay">
            <div style={{ fontSize: 13, fontWeight: 500, marginBottom: 4 }}>No projects match these filters</div>
            <button className="btn btn-sm" onClick={() => { setFilter('all'); setTypeFilter('all') }}>Clear filters</button>
          </div>
        )}

        {/* Detail panel */}
        {selected && (
          <div className="map-detail-panel" style={{
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
                  onClick={() => setSelectedId(null)}
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

              <div style={{
                border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                padding: '10px 12px', background: 'var(--bg-hover)'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <span className="t-label">Advisory priority</span>
                  {priorityLoading && <span className="t-caption">Calculating...</span>}
                  {!priorityLoading && priority && (
                    <span className="badge" style={PRIORITY_STYLE[priority.priorityLevel]}>
                      {priority.priorityLevel}
                    </span>
                  )}
                </div>
                {priorityError ? (
                  <div style={{ fontSize: 11.5, color: 'var(--red)' }} role="alert">
                    {priorityError} <button className="btn btn-ghost btn-sm"
                      onClick={() => setPriorityRetry(value => value + 1)}>Retry</button>
                  </div>
                ) : !priorityLoading && priority && (
                  <>
                    <div style={{ fontSize: 18, fontWeight: 500 }}>{formatScore(priority.totalScore)}{hasScore(priority.totalScore) ? ' / 100' : ''}</div>
                    <div className="t-caption">Decision-support score; not an official government formula.</div>
                  </>
                )}
              </div>

              {/* Rule-based delay risk */}
              <div style={{
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-md)', padding: '10px 12px',
                background: 'var(--bg-hover)'
              }}>
                <div style={{
                  display: 'flex', justifyContent: 'space-between',
                  alignItems: 'center', marginBottom: 8
                }}>
                  <span className="t-label">Delay risk</span>
                  {delayRiskLoading && (
                    <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>Calculating...</span>
                  )}
                  {!delayRiskLoading && delayRisk?.available && (
                    <span className="badge" style={DELAY_RISK_STYLE[delayRisk.delayRisk]}>
                      {DELAY_RISK_LABEL[delayRisk.delayRisk] || delayRisk.delayRisk}
                    </span>
                  )}
                  {!delayRiskLoading && delayRisk && !delayRisk.available && (
                    <span className="badge badge-sanc">Unavailable</span>
                  )}
                </div>

                {delayRiskError ? (
                  <div style={{ fontSize: 11.5, color: 'var(--red)' }} role="alert">
                    {delayRiskError} <button className="btn btn-ghost btn-sm"
                      onClick={() => setDelayRiskRetry(value => value + 1)}>Retry</button>
                  </div>
                ) : !delayRiskLoading && delayRisk && (
                  <>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '6px 12px' }}>
                      {[
                        ['Expected progress', formatProgress(delayRisk.expectedProgress)],
                        ['Actual progress', formatProgress(delayRisk.actualProgress)],
                        ['Progress gap', formatProgress(delayRisk.progressGap)],
                        ['Deadline', delayRisk.overdue ? 'Passed' : 'Not passed'],
                      ].map(([label, value]) => (
                        <div key={label}>
                          <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>{label}</div>
                          <div style={{ fontSize: 12, fontWeight: 500, color: 'var(--text-primary)' }}>
                            {value}
                          </div>
                        </div>
                      ))}
                    </div>
                    <div style={{
                      fontSize: 11, color: 'var(--text-secondary)', lineHeight: 1.5,
                      marginTop: 8, paddingTop: 8, borderTop: '1px solid var(--border)'
                    }}>
                      {delayRisk.reason}
                    </div>
                  </>
                )}
              </div>

              {/* Linked complaints */}
              <div>
                <div className="t-label" style={{ marginBottom: 6 }}>
                  Linked complaints {linkedComplaints.length > 0 ? `(${linkedComplaints.length})` : ''}
                </div>
                {linkedComplaintsLoading && <div className="t-caption">Loading linked complaints...</div>}
                {linkedComplaintsError && (
                  <div style={{ fontSize: 11.5, color: 'var(--red)' }} role="alert">
                    {linkedComplaintsError} <button className="btn btn-ghost btn-sm"
                      onClick={() => setComplaintsRetry(value => value + 1)}>Retry</button>
                  </div>
                )}
                {!linkedComplaintsLoading && !linkedComplaintsError && linkedComplaints.length === 0 && (
                  <div className="t-caption">No complaints are linked to this work.</div>
                )}
                {linkedComplaints.length > 0 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxHeight: 260, overflowY: 'auto' }}>
                    {linkedComplaints.map(complaint => (
                      <div key={complaint.id} style={{
                        border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                        padding: 8, display: 'flex', gap: 8
                      }}>
                        {complaint.imageUrl && <ProtectedComplaintImage url={complaint.imageUrl} />}
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 11.5, fontWeight: 500 }}>
                            #{complaint.id} · {complaint.finalIssueType?.replaceAll('_', ' ')}
                          </div>
                          <div className="t-caption">{complaint.finalSeverity} · {complaint.locationAddress}</div>
                          <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                            {complaint.description}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
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
                  ['Coordinates', Number.isFinite(selected.latitude) && Number.isFinite(selected.longitude)
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
               {canUpdateStatus && (
                  <button
                    className="btn btn-primary btn-sm w-full"
                    onClick={() => { setUpdateSuccess(''); setShowUpdateForm(true) }}
                  >
                    📝 Log progress update
                  </button>
                )}
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
            onUpdated={({ status, photoCount }) => {
              loadProjects(false).catch(() => {})
              setUpdateSuccess(`Project updated to ${STATUS_LABEL[status] || status}${photoCount ? ` with ${photoCount} photo${photoCount === 1 ? '' : 's'}` : ''}.`)
              setSelectedId(null)
            }}
          />
        )}
        {updateSuccess && (
          <div className="map-success-notice" role="status">
            <span>{updateSuccess}</span>
            <button className="btn btn-ghost btn-sm" onClick={() => setUpdateSuccess('')}>Dismiss</button>
          </div>
        )}
      </div>
    </div>
  )
}
