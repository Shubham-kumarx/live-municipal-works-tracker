
import { useState, useEffect } from 'react'
import axios from 'axios'
import api from '../api/axios'

const WORK_ORDERS = [
  { id: 'MW-2026-01842', title: 'Pothole repair near Sector 14 market', location: 'Sector 14, Block B', ward: 'W-014', dept: 'Roads', priority: 'high', worker: 'Zone B Road Crew', status: 'in_progress', due: 'Aug 15', progress: 62 },
  { id: 'MW-2026-01839', title: 'Streetlight outage — 12 poles on Avenue 4', location: 'Avenue 4, Rohini', ward: 'W-014', dept: 'Electrical', priority: 'high', worker: 'Suresh Electricals', status: 'overdue', due: 'Aug 10', progress: 35 },
  { id: 'MW-2026-01835', title: 'Drainage blockage causing waterlogging', location: 'Sector 7, Near School', ward: 'W-014', dept: 'Drainage', priority: 'critical', worker: 'Unassigned', status: 'pending', due: 'Aug 14', progress: 0 },
  { id: 'MW-2026-01831', title: 'Garbage collection missed — 3 days overdue', location: 'Block C, Sector 9', ward: 'W-014', dept: 'Sanitation', priority: 'med', worker: 'Team C Sanitation', status: 'overdue', due: 'Aug 12', progress: 20 },
  { id: 'MW-2026-01828', title: 'Water supply pipe burst — Sector 3', location: 'Sector 3, Main Road', ward: 'W-014', dept: 'Water Supply', priority: 'critical', worker: 'Anil Kumar Crew', status: 'in_progress', due: 'Aug 16', progress: 44 },
  { id: 'MW-2026-01820', title: 'Footpath repair near bus stand', location: 'Bus Stand Road, Sec 11', ward: 'W-014', dept: 'Roads', priority: 'low', worker: 'Zone A Road Crew', status: 'done', due: 'Aug 13', progress: 100 },
  { id: 'MW-2026-01818', title: 'Park boundary wall repair', location: 'Sector 5 Park', ward: 'W-014', dept: 'Horticulture', priority: 'low', worker: 'Park Maintenance Team', status: 'done', due: 'Aug 13', progress: 100 },
]

const COMPLAINTS = [
  { id: 'CMP-4821', text: 'Large pothole causing accidents near school gate', location: 'Sec 14 School Road', time: '2h ago', priority: 'critical' },
  { id: 'CMP-4820', text: 'Open manhole uncovered since 3 days', location: 'Block A, Sec 8', time: '4h ago', priority: 'high' },
  { id: 'CMP-4819', text: 'Stray animal menace near residential block', location: 'Pocket 4, Sec 7', time: '5h ago', priority: 'med' },
  { id: 'CMP-4817', text: 'Illegal construction blocking public drain', location: 'Sec 12, Lane 3', time: '8h ago', priority: 'high' },
]

function statusBadge(s) {
  if (s === 'in_progress') return <span className="badge badge-ip">In Progress</span>
  if (s === 'overdue')     return <span className="badge badge-late">Overdue</span>
  if (s === 'pending')     return <span className="badge badge-pend">Pending</span>
  if (s === 'done')        return <span className="badge badge-done">Completed</span>
  return null
}

function priBadge(p) {
  if (p === 'critical') return <span className="badge badge-late">Critical</span>
  if (p === 'high')     return <span className="badge badge-pri-high">High</span>
  if (p === 'med')      return <span className="badge badge-pri-med">Medium</span>
  if (p === 'low')      return <span className="badge badge-pri-low">Low</span>
  return null
}

export default function Dashboard() {
  const [filter, setFilter] = useState('all')
  useEffect(() => {
      const user = JSON.parse(localStorage.getItem('user') || '{}')
      if (user.wardId) {
        api.get(`/api/projects/ward/${user.wardId}/stats`)
          .then(res => console.log('Real stats:', res.data))
          .catch(err => console.log('Stats error:', err))
      }
    }, [])
  const active    = WORK_ORDERS.filter(w => w.status === 'in_progress').length
  const overdue   = WORK_ORDERS.filter(w => w.status === 'overdue').length
  const doneToday = WORK_ORDERS.filter(w => w.status === 'done').length
  const critical  = COMPLAINTS.filter(c => c.priority === 'critical' || c.priority === 'high').length

  const filtered = filter === 'all'
    ? WORK_ORDERS
    : WORK_ORDERS.filter(w => w.status === filter)

  return (
    <div>
      {/* Page header */}
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">Operations Overview</h1>
          <span className="t-caption">Ward W-014 · Rohini · North Delhi Municipal Corporation</span>
        </div>
        <div className="flex-center gap-8">
          <button className="btn btn-sm">Export report</button>
          <button className="btn btn-primary btn-sm">+ New work order</button>
        </div>
      </div>

      {/* KPI strip */}
      <div className="kpi-strip" style={{ marginBottom: 20 }}>
        <div className="kpi-cell">
          <div className="kpi-val" style={{ color: 'var(--blue)' }}>{active}</div>
          <div className="kpi-label">Active work</div>
          <div className="kpi-sub">currently in progress</div>
        </div>
        <div className="kpi-cell">
          <div className="kpi-val" style={{ color: 'var(--red)' }}>{overdue}</div>
          <div className="kpi-label">Overdue</div>
          <div className="kpi-sub">past due date</div>
        </div>
        <div className="kpi-cell">
          <div className="kpi-val" style={{ color: 'var(--green)' }}>{doneToday}</div>
          <div className="kpi-label">Completed today</div>
          <div className="kpi-sub">Aug 15, 2026</div>
        </div>
        <div className="kpi-cell">
          <div className="kpi-val" style={{ color: 'var(--red)' }}>{critical}</div>
          <div className="kpi-label">Critical complaints</div>
          <div className="kpi-sub">need immediate attention</div>
        </div>
      </div>

      {/* Main grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 16 }}>

        {/* Work orders table */}
        <div className="panel">
          <div className="panel-header">
            <span className="t-strong">Priority Work Queue</span>
            <div className="flex-center gap-8">
              {/* Filter tabs */}
              <div style={{ display: 'flex', border: '1px solid var(--border)', borderRadius: 'var(--r-md)', overflow: 'hidden' }}>
                {[
                  { key: 'all', label: 'All' },
                  { key: 'in_progress', label: 'Active' },
                  { key: 'overdue', label: 'Overdue' },
                  { key: 'pending', label: 'Pending' },
                ].map(f => (
                  <button
                    key={f.key}
                    onClick={() => setFilter(f.key)}
                    style={{
                      padding: '3px 10px',
                      fontSize: 11.5,
                      border: 'none',
                      borderRight: '1px solid var(--border)',
                      background: filter === f.key ? 'var(--accent)' : 'var(--bg-surface)',
                      color: filter === f.key ? '#fff' : 'var(--text-secondary)',
                      cursor: 'pointer',
                      fontWeight: filter === f.key ? 500 : 400
                    }}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
              <button className="btn btn-ghost btn-sm">View all</button>
            </div>
          </div>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Work ID</th>
                  <th>Description</th>
                  <th>Department</th>
                  <th>Assigned to</th>
                  <th>Priority</th>
                  <th>Due</th>
                  <th>Progress</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(w => (
                  <tr key={w.id} style={{ cursor: 'pointer' }}>
                    <td className="col-id" style={{ fontWeight: 500, whiteSpace: 'nowrap' }}>{w.id}</td>
                    <td>
                      <div style={{ fontWeight: 500, fontSize: 12.5, marginBottom: 2 }}>{w.title}</div>
                      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                        {w.location} · {w.ward}
                      </div>
                    </td>
                    <td style={{ whiteSpace: 'nowrap', color: 'var(--text-secondary)' }}>{w.dept}</td>
                    <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>{w.worker}</td>
                    <td>{priBadge(w.priority)}</td>
                    <td style={{ whiteSpace: 'nowrap', fontSize: 12, color: w.status === 'overdue' ? 'var(--red)' : 'var(--text-secondary)' }}>
                      {w.due}
                    </td>
                    <td style={{ minWidth: 80 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <div style={{ flex: 1, height: 3, background: 'var(--border)', borderRadius: 2 }}>
                          <div style={{
                            height: 3, borderRadius: 2,
                            width: `${w.progress}%`,
                            background: w.status === 'overdue' ? 'var(--red)'
                              : w.status === 'done' ? 'var(--green)'
                              : 'var(--accent)'
                          }} />
                        </div>
                        <span style={{ fontSize: 11, color: 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                          {w.progress}%
                        </span>
                      </div>
                    </td>
                    <td>{statusBadge(w.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right column */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Critical complaints */}
          <div className="panel">
            <div className="panel-header">
              <span className="t-strong">Critical Complaints</span>
              <span className="badge badge-late">{critical} urgent</span>
            </div>
            <div>
              {COMPLAINTS.map((c, i) => (
                <div key={c.id} style={{
                  padding: '10px 14px',
                  borderBottom: i < COMPLAINTS.length - 1 ? '1px solid var(--border)' : 'none',
                  cursor: 'pointer'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 500 }}>{c.id}</span>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      {priBadge(c.priority)}
                      <span style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>{c.time}</span>
                    </div>
                  </div>
                  <div style={{ fontSize: 12.5, fontWeight: 500, color: 'var(--text-primary)', marginBottom: 2 }}>
                    {c.text}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{c.location}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Department load */}
          <div className="panel">
            <div className="panel-header">
              <span className="t-strong">Department Load</span>
            </div>
            <div style={{ padding: '10px 14px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[
                { dept: 'Roads & Pavements', active: 3, total: 8 },
                { dept: 'Drainage & Sewage', active: 2, total: 5 },
                { dept: 'Electrical', active: 1, total: 4 },
                { dept: 'Water Supply', active: 2, total: 6 },
                { dept: 'Sanitation', active: 1, total: 7 },
              ].map(d => (
                <div key={d.dept}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4, fontSize: 12 }}>
                    <span style={{ color: 'var(--text-primary)' }}>{d.dept}</span>
                    <span style={{ color: 'var(--text-muted)' }}>{d.active}/{d.total} active</span>
                  </div>
                  <div style={{ height: 3, background: 'var(--border)', borderRadius: 2 }}>
                    <div style={{
                      height: 3, borderRadius: 2,
                      width: `${(d.active / d.total) * 100}%`,
                      background: 'var(--accent)'
                    }} />
                  </div>
                </div>
              ))}
            </div>
          </div>

        </div>
      </div>
    </div>
  )
}