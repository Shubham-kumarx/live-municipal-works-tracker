import { useState } from 'react'

const TEAMS = [
  {
    id: 'T-001', name: 'Zone B Road Crew', lead: 'Ramesh Kumar', dept: 'Roads & Pavements',
    members: 6, status: 'on_site', location: 'Sector 14, Block B', phone: '98100-44231',
    today: { assigned: 4, completed: 2, pending: 1, overdue: 1 },
    current: 'MW-2026-01842 · Pothole repair near Sector 14 market',
    tasks: [
      { id: 'MW-2026-01842', title: 'Pothole repair near Sector 14 market', status: 'in_progress', due: 'Aug 15', progress: 62 },
      { id: 'MW-2026-01820', title: 'Footpath repair near bus stand', status: 'done', due: 'Aug 13', progress: 100 },
      { id: 'MW-2026-01810', title: 'Road marking renewal — Sector 14', status: 'pending', due: 'Aug 17', progress: 0 },
      { id: 'MW-2026-01808', title: 'Speed breaker installation', status: 'overdue', due: 'Aug 11', progress: 40 },
    ]
  },
  {
    id: 'T-002', name: 'Suresh Electricals', lead: 'Suresh Menon', dept: 'Electrical',
    members: 4, status: 'on_site', location: 'Avenue 4, Rohini', phone: '98104-77823',
    today: { assigned: 3, completed: 1, pending: 0, overdue: 2 },
    current: 'MW-2026-01839 · Streetlight outage Avenue 4',
    tasks: [
      { id: 'MW-2026-01839', title: 'Streetlight outage — 12 poles Avenue 4', status: 'overdue', due: 'Aug 10', progress: 35 },
      { id: 'MW-2026-01822', title: 'Transformer repair — Sec 9 substation', status: 'overdue', due: 'Aug 11', progress: 20 },
      { id: 'MW-2026-01800', title: 'New pole installation — Park Road', status: 'done', due: 'Aug 12', progress: 100 },
    ]
  },
  {
    id: 'T-003', name: 'Drain Repair Unit 2', lead: 'Anita Verma', dept: 'Drainage & Sewage',
    members: 5, status: 'on_site', location: 'Pocket 3, Sector 6', phone: '98105-33210',
    today: { assigned: 2, completed: 0, pending: 1, overdue: 0 },
    current: 'MW-2026-01815 · Broken sewer line residential area',
    tasks: [
      { id: 'MW-2026-01815', title: 'Broken sewer line — residential area', status: 'in_progress', due: 'Aug 17', progress: 28 },
      { id: 'MW-2026-01835', title: 'Drainage blockage causing waterlogging', status: 'pending', due: 'Aug 14', progress: 0 },
    ]
  },
  {
    id: 'T-004', name: 'Anil Kumar Crew', lead: 'Anil Kumar', dept: 'Water Supply',
    members: 7, status: 'on_site', location: 'Sector 3, Main Road', phone: '98107-11098',
    today: { assigned: 3, completed: 1, pending: 1, overdue: 0 },
    current: 'MW-2026-01828 · Water supply pipe burst Sector 3',
    tasks: [
      { id: 'MW-2026-01828', title: 'Water supply pipe burst — Sector 3', status: 'in_progress', due: 'Aug 16', progress: 44 },
      { id: 'MW-2026-01812', title: 'Leaking water meter — Block D', status: 'done', due: 'Aug 13', progress: 100 },
      { id: 'MW-2026-01806', title: 'New connection — Pocket 6 Sec 2', status: 'pending', due: 'Aug 18', progress: 0 },
    ]
  },
  {
    id: 'T-005', name: 'Team C Sanitation', lead: 'Mohan Das', dept: 'Sanitation',
    members: 8, status: 'transit', location: 'En route to Block C', phone: '98103-55671',
    today: { assigned: 4, completed: 1, pending: 1, overdue: 2 },
    current: 'MW-2026-01831 · Garbage collection Block C Sector 9',
    tasks: [
      { id: 'MW-2026-01831', title: 'Garbage collection missed — Block C', status: 'overdue', due: 'Aug 12', progress: 20 },
      { id: 'MW-2026-01829', title: 'Drain cleaning — Sector 9 main road', status: 'overdue', due: 'Aug 11', progress: 10 },
      { id: 'MW-2026-01825', title: 'Community bin replacement — Sec 7', status: 'pending', due: 'Aug 16', progress: 0 },
      { id: 'MW-2026-01819', title: 'Street sweeping — Sector 11', status: 'done', due: 'Aug 13', progress: 100 },
    ]
  },
  {
    id: 'T-006', name: 'Park Maintenance Team', lead: 'Sunita Bhatt', dept: 'Horticulture',
    members: 4, status: 'off_duty', location: '—', phone: '98102-88134',
    today: { assigned: 2, completed: 2, pending: 0, overdue: 0 },
    current: 'All tasks completed',
    tasks: [
      { id: 'MW-2026-01818', title: 'Park boundary wall repair', status: 'done', due: 'Aug 13', progress: 100 },
      { id: 'MW-2026-01814', title: 'Tree trimming — Sector 5 park', status: 'done', due: 'Aug 13', progress: 100 },
    ]
  },
]

function statusBadge(s) {
  const map = {
    in_progress: ['badge-ip', 'In Progress'],
    overdue: ['badge-late', 'Overdue'],
    pending: ['badge-pend', 'Pending'],
    done: ['badge-done', 'Completed'],
  }
  const [cls, label] = map[s] || ['', s]
  return <span className={`badge ${cls}`}>{label}</span>
}

function teamStatusBadge(s) {
  if (s === 'on_site') return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11.5, color: 'var(--green)', fontWeight: 500 }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--green)', display: 'inline-block' }} />
      On site
    </span>
  )
  if (s === 'transit') return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11.5, color: 'var(--amber)', fontWeight: 500 }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--amber)', display: 'inline-block' }} />
      In transit
    </span>
  )
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, fontSize: 11.5, color: 'var(--text-muted)', fontWeight: 500 }}>
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--text-muted)', display: 'inline-block' }} />
      Off duty
    </span>
  )
}

export default function FieldTeams() {
  const [selected, setSelected] = useState(null)
  const selectedTeam = TEAMS.find(t => t.id === selected)

  return (
    <div style={{ display: 'grid', gridTemplateColumns: selected ? '1fr 360px' : '1fr', gap: 16 }}>

      {/* Left — teams table */}
      <div>
        <div className="page-header">
          <div className="page-header-left">
            <h1 className="t-page">Field Teams</h1>
            <span className="t-caption">
              {TEAMS.length} teams · {TEAMS.filter(t => t.status === 'on_site').length} on site · {TEAMS.filter(t => t.status === 'transit').length} in transit
            </span>
          </div>
          <button className="btn btn-primary btn-sm">+ Add team</button>
        </div>

        {/* Summary strip */}
        <div className="kpi-strip" style={{ marginBottom: 16 }}>
          <div className="kpi-cell">
            <div className="kpi-val">{TEAMS.reduce((s, t) => s + t.members, 0)}</div>
            <div className="kpi-label">Total workers</div>
          </div>
          <div className="kpi-cell">
            <div className="kpi-val" style={{ color: 'var(--green)' }}>
              {TEAMS.filter(t => t.status === 'on_site').length}
            </div>
            <div className="kpi-label">On site</div>
          </div>
          <div className="kpi-cell">
            <div className="kpi-val" style={{ color: 'var(--red)' }}>
              {TEAMS.reduce((s, t) => s + t.today.overdue, 0)}
            </div>
            <div className="kpi-label">Overdue tasks</div>
          </div>
          <div className="kpi-cell">
            <div className="kpi-val" style={{ color: 'var(--green)' }}>
              {TEAMS.reduce((s, t) => s + t.today.completed, 0)}
            </div>
            <div className="kpi-label">Completed today</div>
          </div>
        </div>

        {/* Teams table */}
        <div className="panel">
          <table className="data-table">
            <thead>
              <tr>
                <th>Team</th>
                <th>Department</th>
                <th>Lead</th>
                <th>Members</th>
                <th>Status</th>
                <th>Location</th>
                <th style={{ textAlign: 'center' }}>Assigned</th>
                <th style={{ textAlign: 'center' }}>Done</th>
                <th style={{ textAlign: 'center' }}>Overdue</th>
                <th>Current assignment</th>
              </tr>
            </thead>
            <tbody>
              {TEAMS.map(team => (
                <tr
                  key={team.id}
                  style={{
                    cursor: 'pointer',
                    background: selected === team.id ? 'var(--accent-lt)' : undefined
                  }}
                  onClick={() => setSelected(selected === team.id ? null : team.id)}
                >
                  <td>
                    <div style={{ fontWeight: 500, fontSize: 12.5 }}>{team.name}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>{team.id}</div>
                  </td>
                  <td style={{ fontSize: 12, color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>{team.dept}</td>
                  <td style={{ fontSize: 12.5, whiteSpace: 'nowrap' }}>{team.lead}</td>
                  <td style={{ textAlign: 'center', fontSize: 12 }}>{team.members}</td>
                  <td>{teamStatusBadge(team.status)}</td>
                  <td style={{ fontSize: 12, color: 'var(--text-secondary)' }}>{team.location}</td>
                  <td style={{ textAlign: 'center', fontSize: 13, fontWeight: 500 }}>{team.today.assigned}</td>
                  <td style={{ textAlign: 'center', fontSize: 13, color: 'var(--green)', fontWeight: 500 }}>{team.today.completed}</td>
                  <td style={{ textAlign: 'center', fontSize: 13, color: team.today.overdue > 0 ? 'var(--red)' : 'var(--text-muted)', fontWeight: 500 }}>
                    {team.today.overdue}
                  </td>
                  <td style={{ fontSize: 11.5, color: 'var(--text-secondary)', maxWidth: 200 }}>
                    <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {team.current}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Right — team detail */}
      {selectedTeam && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="panel">
            <div className="panel-header">
              <div>
                <div style={{ fontWeight: 500, fontSize: 14 }}>{selectedTeam.name}</div>
                <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 2 }}>
                  {selectedTeam.dept} · {selectedTeam.id}
                </div>
              </div>
              <button className="btn btn-ghost btn-sm" onClick={() => setSelected(null)}>✕</button>
            </div>
            <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {[
                ['Lead', selectedTeam.lead],
                ['Members', `${selectedTeam.members} workers`],
                ['Phone', selectedTeam.phone],
                ['Status', selectedTeam.status.replace('_', ' ')],
                ['Location', selectedTeam.location],
              ].map(([label, value]) => (
                <div key={label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12.5 }}>
                  <span style={{ color: 'var(--text-muted)' }}>{label}</span>
                  <span style={{ fontWeight: 500, color: 'var(--text-primary)' }}>{value}</span>
                </div>
              ))}
            </div>

            {/* Today's stats */}
            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(4,1fr)',
              borderTop: '1px solid var(--border)'
            }}>
              {[
                ['Assigned', selectedTeam.today.assigned, 'var(--text-primary)'],
                ['Done', selectedTeam.today.completed, 'var(--green)'],
                ['Pending', selectedTeam.today.pending, 'var(--amber)'],
                ['Overdue', selectedTeam.today.overdue, 'var(--red)'],
              ].map(([label, val, color]) => (
                <div key={label} style={{
                  padding: '10px 0', textAlign: 'center',
                  borderRight: '1px solid var(--border)'
                }}>
                  <div style={{ fontSize: 18, fontWeight: 500, color }}>{val}</div>
                  <div style={{ fontSize: 10, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '.4px' }}>{label}</div>
                </div>
              ))}
            </div>
          </div>

          {/* Task list */}
          <div className="panel">
            <div className="panel-header">
              <span className="t-strong">Today's Tasks</span>
            </div>
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Task</th>
                  <th>Due</th>
                  <th>Progress</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {selectedTeam.tasks.map(task => (
                  <tr key={task.id}>
                    <td className="col-id" style={{ fontSize: 11, fontWeight: 500 }}>{task.id}</td>
                    <td style={{ fontSize: 12, maxWidth: 160 }}>
                      <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {task.title}
                      </div>
                    </td>
                    <td style={{ fontSize: 11.5, color: task.status === 'overdue' ? 'var(--red)' : 'var(--text-muted)', whiteSpace: 'nowrap' }}>
                      {task.due}
                    </td>
                    <td style={{ minWidth: 70 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                        <div style={{ flex: 1, height: 3, background: 'var(--border)', borderRadius: 2 }}>
                          <div style={{
                            height: 3, borderRadius: 2,
                            width: `${task.progress}%`,
                            background: task.status === 'overdue' ? 'var(--red)'
                              : task.status === 'done' ? 'var(--green)'
                              : 'var(--accent)'
                          }} />
                        </div>
                        <span style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>{task.progress}%</span>
                      </div>
                    </td>
                    <td>{statusBadge(task.status)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-primary btn-sm w-full">Assign new task</button>
            <button className="btn btn-sm">Contact team</button>
          </div>
        </div>
      )}
    </div>
  )
}   