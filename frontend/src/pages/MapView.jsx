import { useEffect, useRef, useState } from 'react'

const PROJECTS = [
  { id: 'MW-2026-01842', title: 'Pothole repair near Sector 14 market', type: 'Road Repair', lat: 28.7180, lng: 77.1100, status: 'in_progress', priority: 'high', progress: 62, worker: 'Zone B Road Crew', dept: 'Roads & Pavements', due: 'Aug 15', location: 'Sector 14, Block B' },
  { id: 'MW-2026-01839', title: 'Streetlight outage — 12 poles on Avenue 4', type: 'Streetlight', lat: 28.7220, lng: 77.1160, status: 'overdue', priority: 'high', progress: 35, worker: 'Suresh Electricals', dept: 'Electrical', due: 'Aug 10', location: 'Avenue 4, Rohini' },
  { id: 'MW-2026-01835', title: 'Drainage blockage causing waterlogging', type: 'Drainage', lat: 28.7150, lng: 77.1080, status: 'pending', priority: 'critical', progress: 0, worker: 'Unassigned', dept: 'Drainage & Sewage', due: 'Aug 14', location: 'Sector 7, Near School' },
  { id: 'MW-2026-01831', title: 'Garbage collection missed — 3 days', type: 'Garbage', lat: 28.7200, lng: 77.1200, status: 'overdue', priority: 'med', progress: 20, worker: 'Team C Sanitation', dept: 'Sanitation', due: 'Aug 12', location: 'Block C, Sector 9' },
  { id: 'MW-2026-01828', title: 'Water supply pipe burst — Sector 3', type: 'Water Supply', lat: 28.7130, lng: 77.1140, status: 'in_progress', priority: 'critical', progress: 44, worker: 'Anil Kumar Crew', dept: 'Water Supply', due: 'Aug 16', location: 'Sector 3, Main Road' },
  { id: 'MW-2026-01820', title: 'Footpath repair near bus stand', type: 'Road Repair', lat: 28.7250, lng: 77.1050, status: 'done', priority: 'low', progress: 100, worker: 'Zone A Road Crew', dept: 'Roads & Pavements', due: 'Aug 13', location: 'Bus Stand Road, Sec 11' },
  { id: 'MW-2026-01815', title: 'Broken sewer line — residential area', type: 'Drainage', lat: 28.7170, lng: 77.1190, status: 'in_progress', priority: 'high', progress: 28, worker: 'Drain Repair Unit 2', dept: 'Drainage & Sewage', due: 'Aug 17', location: 'Pocket 3, Sector 6' },
  { id: 'MW-2026-01818', title: 'Park boundary wall repair', type: 'Sanitation', lat: 28.7100, lng: 77.1120, status: 'done', priority: 'low', progress: 100, worker: 'Park Maintenance Team', dept: 'Horticulture', due: 'Aug 13', location: 'Sector 5 Park' },
]

const STATUS_COLOR = {
  in_progress: '#1A4B6E',
  overdue:     '#8B1C1C',
  pending:     '#854D0E',
  done:        '#2D6A4F',
}

const STATUS_LABEL = {
  in_progress: 'In Progress',
  overdue:     'Overdue',
  pending:     'Pending',
  done:        'Completed',
}

function statusBadge(s) {
  const map = {
    in_progress: 'badge-ip',
    overdue: 'badge-late',
    pending: 'badge-pend',
    done: 'badge-done',
  }
  return <span className={`badge ${map[s]}`}>{STATUS_LABEL[s]}</span>
}

export default function MapView() {
  const mapRef = useRef(null)
  const mapInstance = useRef(null)
  const markersRef = useRef([])
  const [selected, setSelected] = useState(null)
  const [filter, setFilter] = useState('all')
  const [typeFilter, setTypeFilter] = useState('all')

  const filtered = PROJECTS.filter(p => {
    const matchStatus = filter === 'all' || p.status === filter
    const matchType = typeFilter === 'all' || p.type === typeFilter
    return matchStatus && matchType
  })

  useEffect(() => {
    if (mapInstance.current) return

    const L = window.L
    if (!L) return

    const map = L.map(mapRef.current, {
      center: [28.7180, 77.1120],
      zoom: 14,
      zoomControl: true,
    })

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map)

    mapInstance.current = map
  }, [])

  useEffect(() => {
    const L = window.L
    if (!L || !mapInstance.current) return

    // Clear existing markers
    markersRef.current.forEach(m => m.remove())
    markersRef.current = []

    filtered.forEach(project => {
      const color = STATUS_COLOR[project.status]

      const icon = L.divIcon({
        className: '',
        html: `
          <div style="
            width: 28px; height: 28px;
            border-radius: 50% 50% 50% 0;
            transform: rotate(-45deg);
            background: ${color};
            border: 2.5px solid white;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            cursor: pointer;
          ">
            <div style="
              width: 10px; height: 10px;
              background: rgba(255,255,255,0.8);
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

      const marker = L.marker([project.lat, project.lng], { icon })
        .addTo(mapInstance.current)
        .on('click', () => setSelected(project))

      markersRef.current.push(marker)
    })
  }, [filtered])

  const types = ['all', ...new Set(PROJECTS.map(p => p.type))]

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 46px)', margin: '-20px -24px' }}>

      {/* Map toolbar */}
      <div style={{
        background: 'var(--bg-surface)',
        borderBottom: '1px solid var(--border)',
        padding: '8px 16px',
        display: 'flex', alignItems: 'center', gap: 10,
        flexShrink: 0
      }}>
        <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--text-primary)', marginRight: 4 }}>
          Live Map
        </span>
        <div style={{ width: 1, height: 16, background: 'var(--border)' }} />

        {/* Status filter */}
        {[
          { key: 'all', label: 'All' },
          { key: 'in_progress', label: 'In Progress' },
          { key: 'overdue', label: 'Overdue' },
          { key: 'pending', label: 'Pending' },
          { key: 'done', label: 'Completed' },
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
              cursor: 'pointer', fontWeight: filter === f.key ? 500 : 400
            }}
          >
            {f.label}
          </button>
        ))}

        <div style={{ width: 1, height: 16, background: 'var(--border)' }} />

        <select
          className="input input-sm"
          style={{ width: 140 }}
          value={typeFilter}
          onChange={e => setTypeFilter(e.target.value)}
        >
          {types.map(t => (
            <option key={t} value={t}>{t === 'all' ? 'All types' : t}</option>
          ))}
        </select>

        <span style={{ fontSize: 11.5, color: 'var(--text-muted)', marginLeft: 4 }}>
          {filtered.length} markers
        </span>

        {/* Live indicator */}
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--green)' }} />
          <span style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>Live · WebSocket connected</span>
        </div>
      </div>

      {/* Map + detail panel */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>

        {/* Map */}
        <div ref={mapRef} style={{ flex: 1 }} />

        {/* Legend */}
        <div style={{
          position: 'absolute', bottom: 40, left: 232, zIndex: 1000,
          background: 'rgba(28,31,36,0.92)',
          border: '1px solid #3A3F48',
          borderRadius: 'var(--r-md)',
          padding: '8px 12px'
        }}>
          <div style={{ fontSize: 10, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: 6 }}>
            Status
          </div>
          {Object.entries(STATUS_COLOR).map(([s, color]) => (
            <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
              <div style={{ width: 8, height: 8, borderRadius: '50%', background: color }} />
              <span style={{ fontSize: 11, color: '#9AA0AB' }}>{STATUS_LABEL[s]}</span>
            </div>
          ))}
        </div>

        {/* Detail panel */}
        {selected && (
          <div style={{
            width: 320, flexShrink: 0,
            background: 'var(--bg-surface)',
            borderLeft: '1px solid var(--border)',
            display: 'flex', flexDirection: 'column',
            overflow: 'auto'
          }}>
            {/* Header */}
            <div style={{
              background: '#1C1F24', padding: '12px 16px',
              borderBottom: '1px solid #2E3238'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <div style={{ fontSize: 11, color: '#6B7280', marginBottom: 3 }}>{selected.id}</div>
                  <div style={{ fontSize: 13.5, fontWeight: 500, color: '#E8E6E1', lineHeight: 1.3 }}>
                    {selected.title}
                  </div>
                </div>
                <button
                  onClick={() => setSelected(null)}
                  style={{ background: 'none', border: 'none', color: '#6B7280', cursor: 'pointer', padding: 4, marginLeft: 8 }}
                >✕</button>
              </div>
              <div style={{ fontSize: 11.5, color: '#6B7280', marginTop: 5 }}>
                {selected.location} · Ward W-014
              </div>
            </div>

            <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>

              {/* Status + priority */}
              <div style={{ display: 'flex', gap: 6 }}>
                {statusBadge(selected.status)}
                <span className={`badge badge-pri-${selected.priority === 'critical' ? 'high' : selected.priority === 'high' ? 'high' : selected.priority === 'med' ? 'med' : 'low'}`}>
                  {selected.priority.charAt(0).toUpperCase() + selected.priority.slice(1)}
                </span>
              </div>

              {/* Progress */}
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11.5, color: 'var(--text-muted)', marginBottom: 5 }}>
                  <span>Completion</span>
                  <span style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{selected.progress}%</span>
                </div>
                <div style={{ height: 5, background: 'var(--border)', borderRadius: 3 }}>
                  <div style={{
                    height: 5, borderRadius: 3,
                    width: `${selected.progress}%`,
                    background: selected.status === 'overdue' ? 'var(--red)'
                      : selected.status === 'done' ? 'var(--green)'
                      : 'var(--accent)'
                  }} />
                </div>
              </div>

              {/* Details */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 0, border: '1px solid var(--border)', borderRadius: 'var(--r-md)', overflow: 'hidden' }}>
                {[
                  ['Type', selected.type],
                  ['Department', selected.dept],
                  ['Assigned to', selected.worker],
                  ['Due date', selected.due],
                  ['Coordinates', `${selected.lat.toFixed(4)}, ${selected.lng.toFixed(4)}`],
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

              {/* Actions */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                <button className="btn btn-primary btn-sm w-full">Update status</button>
                <div style={{ display: 'flex', gap: 6 }}>
                  <button className="btn btn-sm w-full">Add photo</button>
                  <button className="btn btn-sm w-full">Flag issue</button>
                </div>
              </div>

              {/* Live feed */}
              <div>
                <div className="t-label" style={{ marginBottom: 8 }}>Recent activity</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
                  {[
                    { text: 'Progress updated to 62%', time: '2 min ago', by: 'Ramesh Kumar' },
                    { text: 'Photo evidence uploaded', time: '1h ago', by: 'Ramesh Kumar' },
                    { text: 'Work started on site', time: '3h ago', by: 'Zone B Road Crew' },
                    { text: 'Assigned to Zone B Road Crew', time: '2026-08-10', by: 'Rajesh Sharma' },
                  ].map((item, i) => (
                    <div key={i} style={{
                      padding: '7px 0',
                      borderBottom: i < 3 ? '1px solid var(--border)' : 'none',
                      fontSize: 12
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                        <span style={{ color: 'var(--text-primary)' }}>{item.text}</span>
                        <span style={{ fontSize: 10.5, color: 'var(--text-muted)', flexShrink: 0, marginLeft: 8 }}>{item.time}</span>
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>by {item.by}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
} 