import { useState } from 'react'

const ORDERS = [
  { id: 'MW-2026-01842', title: 'Pothole repair near Sector 14 market', location: 'Sector 14, Block B', ward: 'W-014', dept: 'Roads & Pavements', priority: 'high', worker: 'Zone B Road Crew', status: 'in_progress', due: '2026-08-15', reported: '2026-08-10', progress: 62, description: 'Large pothole approximately 4ft x 3ft has developed near the main market entrance. Causing traffic disruption and risk of vehicle damage. Immediate repair required using cold mix asphalt.', supervisor: 'Rajesh Kumar Sharma' },
  { id: 'MW-2026-01839', title: 'Streetlight outage — 12 poles on Avenue 4', location: 'Avenue 4, Rohini', ward: 'W-014', dept: 'Electrical', priority: 'high', worker: 'Suresh Electricals', status: 'overdue', due: '2026-08-10', reported: '2026-08-07', progress: 35, description: '12 consecutive streetlight poles have been non-functional for 5 days. Area is a known crime-prone zone and lack of lighting poses safety risk for residents.', supervisor: 'Priya Nair' },
  { id: 'MW-2026-01835', title: 'Drainage blockage causing waterlogging', location: 'Sector 7, Near School', ward: 'W-014', dept: 'Drainage & Sewage', priority: 'critical', worker: 'Unassigned', status: 'pending', due: '2026-08-14', reported: '2026-08-09', progress: 0, description: 'Main drainage channel blocked by construction debris. Waterlogging of approximately 200 sq meters affecting school access road. Health risk due to stagnant water.', supervisor: 'Anita Verma' },
  { id: 'MW-2026-01831', title: 'Garbage collection missed — 3 days overdue', location: 'Block C, Sector 9', ward: 'W-014', dept: 'Sanitation', priority: 'med', worker: 'Team C Sanitation', status: 'overdue', due: '2026-08-12', reported: '2026-08-09', progress: 20, description: 'Scheduled garbage collection missed for 3 consecutive days. Waste accumulation at 4 community bins. Residents have filed multiple complaints.', supervisor: 'Mohan Das' },
  { id: 'MW-2026-01828', title: 'Water supply pipe burst — Sector 3', location: 'Sector 3, Main Road', ward: 'W-014', dept: 'Water Supply', priority: 'critical', worker: 'Anil Kumar Crew', status: 'in_progress', due: '2026-08-16', reported: '2026-08-11', progress: 44, description: 'Underground water supply pipe burst causing significant water loss and road surface damage. Supply to 340 households affected. Emergency repair in progress.', supervisor: 'Vijay Malhotra' },
  { id: 'MW-2026-01820', title: 'Footpath repair near bus stand', location: 'Bus Stand Road, Sec 11', ward: 'W-014', dept: 'Roads & Pavements', priority: 'low', worker: 'Zone A Road Crew', status: 'done', due: '2026-08-13', reported: '2026-08-05', progress: 100, description: 'Uneven footpath tiles near bus stand causing trip hazard. All tiles replaced and surface levelled.', supervisor: 'Rajesh Kumar Sharma' },
  { id: 'MW-2026-01818', title: 'Park boundary wall repair', location: 'Sector 5 Park', ward: 'W-014', dept: 'Horticulture', priority: 'low', worker: 'Park Maintenance Team', status: 'done', due: '2026-08-13', reported: '2026-08-04', progress: 100, description: 'Boundary wall collapsed in two sections totalling 12 meters. Wall rebuilt using standard municipal brick specification.', supervisor: 'Sunita Bhatt' },
  { id: 'MW-2026-01815', title: 'Broken sewer line — residential area', location: 'Pocket 3, Sector 6', ward: 'W-014', dept: 'Drainage & Sewage', priority: 'high', worker: 'Drain Repair Unit 2', status: 'in_progress', due: '2026-08-17', reported: '2026-08-11', progress: 28, description: 'Sewer line broken causing sewage overflow into street. Health hazard for residents. CCTV inspection completed, excavation work started.', supervisor: 'Anita Verma' },
]

function statusBadge(s) {
  const map = {
    in_progress: ['badge-ip', 'In Progress'],
    overdue:     ['badge-late', 'Overdue'],
    pending:     ['badge-pend', 'Pending'],
    done:        ['badge-done', 'Completed'],
  }
  const [cls, label] = map[s] || ['', s]
  return <span className={`badge ${cls}`}>{label}</span>
}

function priBadge(p) {
  const map = {
    critical: ['badge-late', 'Critical'],
    high:     ['badge-pri-high', 'High'],
    med:      ['badge-pri-med', 'Medium'],
    low:      ['badge-pri-low', 'Low'],
  }
  const [cls, label] = map[p] || ['', p]
  return <span className={`badge ${cls}`}>{label}</span>
}

const TIMELINE_STEPS = ['Reported', 'Assigned', 'Inspection', 'Work Started', 'Completed']

function WorkOrderDetail({ order, onClose }) {
  const stepsDone = order.status === 'done' ? 5
    : order.status === 'in_progress' ? 4
    : order.status === 'pending' ? 2 : 1

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 100,
      background: 'rgba(0,0,0,0.35)',
      display: 'flex', alignItems: 'flex-start',
      justifyContent: 'flex-end'
    }} onClick={onClose}>
      <div style={{
        width: 540, height: '100vh',
        background: 'var(--bg-surface)',
        borderLeft: '1px solid var(--border)',
        overflow: 'auto',
        display: 'flex', flexDirection: 'column'
      }} onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div style={{
          padding: '14px 20px',
          borderBottom: '1px solid var(--border)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'
        }}>
          <div>
            <div style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500, marginBottom: 3 }}>
              {order.id}
            </div>
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--text-primary)', marginBottom: 4 }}>
              {order.title}
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-secondary)' }}>
              {order.location} · {order.ward}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onClose}
            style={{ marginLeft: 16, flexShrink: 0 }}>✕ Close</button>
        </div>

        <div style={{ padding: 20, flex: 1 }}>

          {/* Status row */}
          <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
            {statusBadge(order.status)}
            {priBadge(order.priority)}
            <span style={{ fontSize: 12, color: 'var(--text-muted)', marginLeft: 4, alignSelf: 'center' }}>
              Due {order.due}
            </span>
          </div>

          {/* Timeline */}
          <div style={{ marginBottom: 24 }}>
            <div className="t-label" style={{ marginBottom: 12 }}>Progress timeline</div>
            <div style={{ position: 'relative' }}>
              {/* Track line */}
              <div style={{
                position: 'absolute', top: 10, left: 10,
                right: 10, height: 2,
                background: 'var(--border)'
              }} />
              <div style={{
                position: 'absolute', top: 10, left: 10,
                height: 2,
                width: `${((stepsDone - 1) / (TIMELINE_STEPS.length - 1)) * 100}%`,
                background: order.status === 'overdue' ? 'var(--red)' : 'var(--accent)',
                transition: 'width .3s'
              }} />
              <div style={{ display: 'flex', justifyContent: 'space-between', position: 'relative' }}>
                {TIMELINE_STEPS.map((step, i) => {
                  const done = i < stepsDone
                  const active = i === stepsDone - 1
                  return (
                    <div key={step} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                      <div style={{
                        width: 20, height: 20, borderRadius: '50%',
                        border: `2px solid ${done ? (order.status === 'overdue' ? 'var(--red)' : 'var(--accent)') : 'var(--border)'}`,
                        background: done ? (order.status === 'overdue' ? 'var(--red)' : 'var(--accent)') : 'var(--bg-surface)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        zIndex: 1
                      }}>
                        {done && <span style={{ fontSize: 9, color: '#fff', fontWeight: 700 }}>✓</span>}
                      </div>
                      <span style={{
                        fontSize: 10.5, textAlign: 'center',
                        color: done ? 'var(--text-primary)' : 'var(--text-muted)',
                        fontWeight: active ? 500 : 400, maxWidth: 64
                      }}>
                        {step}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>

          {/* Details grid */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 0,
            border: '1px solid var(--border)', borderRadius: 'var(--r-lg)',
            overflow: 'hidden', marginBottom: 20 }}>
            {[
              ['Department', order.dept],
              ['Supervisor', order.supervisor],
              ['Assigned to', order.worker],
              ['Reported on', order.reported],
              ['Due date', order.due],
              ['Progress', `${order.progress}%`],
            ].map(([label, value], i) => (
              <div key={label} style={{
                padding: '9px 14px',
                borderBottom: i < 4 ? '1px solid var(--border)' : 'none',
                borderRight: i % 2 === 0 ? '1px solid var(--border)' : 'none',
                background: i % 2 === 0 ? 'var(--bg-surface)' : '#FAFAF8'
              }}>
                <div className="t-label" style={{ marginBottom: 2 }}>{label}</div>
                <div style={{ fontSize: 12.5, fontWeight: 500, color: 'var(--text-primary)' }}>{value}</div>
              </div>
            ))}
          </div>

          {/* Description */}
          <div style={{ marginBottom: 20 }}>
            <div className="t-label" style={{ marginBottom: 8 }}>Description</div>
            <div style={{
              fontSize: 12.5, color: 'var(--text-secondary)',
              lineHeight: 1.7, padding: '10px 14px',
              background: 'var(--bg-hover)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--r-md)'
            }}>
              {order.description}
            </div>
          </div>

          {/* Progress bar */}
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
              <div className="t-label">Completion</div>
              <span style={{ fontSize: 12, fontWeight: 500 }}>{order.progress}%</span>
            </div>
            <div style={{ height: 6, background: 'var(--border)', borderRadius: 3 }}>
              <div style={{
                height: 6, borderRadius: 3,
                width: `${order.progress}%`,
                background: order.status === 'overdue' ? 'var(--red)'
                  : order.status === 'done' ? 'var(--green)'
                  : 'var(--accent)'
              }} />
            </div>
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', gap: 8, paddingTop: 16, borderTop: '1px solid var(--border)' }}>
            <button className="btn btn-primary btn-sm">Update status</button>
            <button className="btn btn-sm">Assign worker</button>
            <button className="btn btn-sm">Add note</button>
            <button className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto', color: 'var(--red)' }}>
              Escalate
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function WorkOrders() {
  const [selected, setSelected] = useState(null)
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('all')
  const [filterDept, setFilterDept] = useState('all')

  const depts = ['all', ...new Set(ORDERS.map(o => o.dept))]

  const filtered = ORDERS.filter(o => {
    const matchSearch = !search ||
      o.id.toLowerCase().includes(search.toLowerCase()) ||
      o.title.toLowerCase().includes(search.toLowerCase()) ||
      o.location.toLowerCase().includes(search.toLowerCase())
    const matchStatus = filterStatus === 'all' || o.status === filterStatus
    const matchDept = filterDept === 'all' || o.dept === filterDept
    return matchSearch && matchStatus && matchDept
  })

  return (
    <div>
      {selected && (
        <WorkOrderDetail order={selected} onClose={() => setSelected(null)} />
      )}

      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">Work Orders</h1>
          <span className="t-caption">
            {ORDERS.length} total · {ORDERS.filter(o => o.status === 'overdue').length} overdue
          </span>
        </div>
        <div className="flex-center gap-8">
          <button className="btn btn-sm">Export</button>
          <button className="btn btn-primary btn-sm">+ New work order</button>
        </div>
      </div>

      {/* Filters row */}
      <div style={{
        display: 'flex', gap: 10, marginBottom: 16,
        alignItems: 'center', flexWrap: 'wrap'
      }}>
        <div style={{ position: 'relative', flex: '0 0 260px' }}>
          <input
            className="input input-sm"
            placeholder="Search by ID, title, location..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ paddingLeft: 28 }}
          />
          <span style={{
            position: 'absolute', left: 8, top: '50%',
            transform: 'translateY(-50%)', fontSize: 12,
            color: 'var(--text-muted)'
          }}>⌕</span>
        </div>

        <select
          className="input input-sm"
          style={{ width: 130 }}
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}
        >
          <option value="all">All statuses</option>
          <option value="in_progress">In progress</option>
          <option value="overdue">Overdue</option>
          <option value="pending">Pending</option>
          <option value="done">Completed</option>
        </select>

        <select
          className="input input-sm"
          style={{ width: 160 }}
          value={filterDept}
          onChange={e => setFilterDept(e.target.value)}
        >
          {depts.map(d => (
            <option key={d} value={d}>
              {d === 'all' ? 'All departments' : d}
            </option>
          ))}
        </select>

        <span style={{ fontSize: 12, color: 'var(--text-muted)', marginLeft: 4 }}>
          {filtered.length} results
        </span>
      </div>

      {/* Table */}
      <div className="panel">
        <div style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 130 }}>Work ID</th>
                <th>Description</th>
                <th>Dept</th>
                <th>Supervisor</th>
                <th>Assigned</th>
                <th>Priority</th>
                <th>Due date</th>
                <th>Progress</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={9} style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)' }}>
                    No work orders match your filters
                  </td>
                </tr>
              ) : filtered.map(o => (
                <tr
                  key={o.id}
                  style={{ cursor: 'pointer' }}
                  onClick={() => setSelected(o)}
                >
                  <td className="col-id" style={{ fontWeight: 600, fontSize: 12 }}>
                    {o.id}
                  </td>
                  <td>
                    <div style={{ fontWeight: 500, marginBottom: 2 }}>{o.title}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {o.location} · {o.ward}
                    </div>
                  </td>
                  <td style={{ color: 'var(--text-secondary)', whiteSpace: 'nowrap', fontSize: 12 }}>
                    {o.dept}
                  </td>
                  <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                    {o.supervisor}
                  </td>
                  <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                    {o.worker}
                  </td>
                  <td>{priBadge(o.priority)}</td>
                  <td style={{
                    fontSize: 12, whiteSpace: 'nowrap',
                    color: o.status === 'overdue' ? 'var(--red)' : 'var(--text-secondary)',
                    fontWeight: o.status === 'overdue' ? 500 : 400
                  }}>
                    {o.due}
                  </td>
                  <td style={{ minWidth: 90 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <div style={{ flex: 1, height: 3, background: 'var(--border)', borderRadius: 2 }}>
                        <div style={{
                          height: 3, borderRadius: 2,
                          width: `${o.progress}%`,
                          background: o.status === 'overdue' ? 'var(--red)'
                            : o.status === 'done' ? 'var(--green)'
                            : 'var(--accent)'
                        }} />
                      </div>
                      <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{o.progress}%</span>
                    </div>
                  </td>
                  <td>{statusBadge(o.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div style={{
          padding: '10px 16px',
          borderTop: '1px solid var(--border)',
          display: 'flex', alignItems: 'center',
          justifyContent: 'space-between'
        }}>
          <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            Showing {filtered.length} of {ORDERS.length} work orders
          </span>
          <div className="flex-center gap-8">
            <button className="btn btn-sm" disabled>← Previous</button>
            <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Page 1 of 1</span>
            <button className="btn btn-sm" disabled>Next →</button>
          </div>
        </div>
      </div>
    </div>
  )
}