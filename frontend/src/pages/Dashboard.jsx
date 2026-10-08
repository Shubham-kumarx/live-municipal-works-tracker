import { useCallback, useEffect, useState } from 'react'
import api from '../api/axios'
import { apiErrorMessage } from '../api/errors'
import { COMPLAINT_ISSUE_TYPES, complaintIssueTypeLabel } from '../constants/complaintIssueTypes'

function Kpi({ value, label, detail, tone }) {
  return (
    <div className="kpi-cell">
      <div className="kpi-val" style={{ color: `var(--${tone})` }}>{value}</div>
      <div className="kpi-label">{label}</div>
      <div className="kpi-sub">{detail}</div>
    </div>
  )
}

function formatEnum(value) {
  if (!value) return 'Unavailable'
  return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase())
}

function formatDate(value) {
  if (!value) return 'No deadline'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(`${value}T00:00:00`))
}

function formatScore(value) {
  if (value === null || value === undefined || value === '') return 'Unavailable'
  const score = Number(value)
  return Number.isFinite(score) ? score.toFixed(1) : 'Unavailable'
}

function decisionBadge(value) {
  const className = ['CRITICAL', 'HIGH', 'HIGH_DELAY_RISK', 'DELAYED'].includes(value)
    ? 'badge-late'
    : value === 'COMPLETED' || value === 'ON_TRACK'
      ? 'badge-done'
      : value === 'IN_PROGRESS'
        ? 'badge-ip'
        : 'badge-pend'
  return <span className={`badge ${className}`}>{formatEnum(value)}</span>
}

function WorkDecisionTable({ title, works, emptyMessage, showRisk = false }) {
  return (
    <div className="panel dashboard-decision-panel">
      <div className="panel-header">
        <span className="t-strong">{title}</span>
        <span className="badge badge-sanc">{works.length}</span>
      </div>
      {works.length === 0 ? (
        <div className="dashboard-empty">{emptyMessage}</div>
      ) : (
        <div className="dashboard-table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>Work</th><th>Location</th><th>Weighted priority</th>
                {showRisk && <th>Rule-based delay risk</th>}
                <th>Deadline</th><th>Progress</th>
              </tr>
            </thead>
            <tbody>
              {works.map(work => (
                <tr key={work.id}>
                  <td>
                    <div className="dashboard-primary">{work.projectName}</div>
                    <div className="t-caption">#{work.id} · {formatEnum(work.status)}</div>
                  </td>
                  <td>{work.locationAddress || 'Not provided'}</td>
                  <td>{decisionBadge(work.priorityLevel)} <span className="t-caption">{formatScore(work.priorityScore)}</span></td>
                  {showRisk && <td>{decisionBadge(work.delayRisk)}</td>}
                  <td>{formatDate(work.expectedEndDate)}</td>
                  <td>{work.progressPercentage ?? 0}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function DistributionChart({ title, items }) {
  const total = items.reduce((sum, item) => sum + item.count, 0)
  return (
    <div className="panel dashboard-distribution">
      <div className="panel-header"><span className="t-strong">{title}</span></div>
      <div className="dashboard-distribution-body">
        {items.map(item => {
          const percentage = total === 0 ? 0 : (item.count / total) * 100
          return (
            <div key={item.label} className="dashboard-distribution-row">
              <div className="dashboard-distribution-label">
                <span>{formatEnum(item.label)}</span><span>{item.count}</span>
              </div>
              <div className="dashboard-distribution-track" aria-label={`${formatEnum(item.label)} ${item.count}`}>
                <div className="dashboard-distribution-fill" style={{ width: `${percentage}%` }} />
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function Dashboard() {
  const [dashboard, setDashboard] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [filters, setFilters] = useState({
    priority: '', delayRisk: '', status: '', severity: '', issueType: '',
    location: '', deadline: '', workSort: 'priority', complaintSort: 'newest',
  })

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await api.get('/api/dashboard')
      setDashboard(response.data)
    } catch (requestError) {
      setDashboard(null)
      setError(apiErrorMessage(requestError, 'Dashboard data could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  if (loading) {
    return <div className="panel dashboard-state">Loading dashboard data...</div>
  }

  if (error) {
    return (
      <div className="panel dashboard-state">
        <div>{error}</div>
        <button className="btn btn-primary btn-sm" onClick={loadDashboard}>Retry</button>
      </div>
    )
  }

  if (!dashboard) {
    return <div className="panel dashboard-state">No dashboard data is available.</div>
  }

  const { workMetrics, complaintMetrics } = dashboard
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const deadlineMatches = work => {
    if (!filters.deadline) return true
    if (!work.expectedEndDate) return filters.deadline === 'NONE'
    const deadline = new Date(`${work.expectedEndDate}T00:00:00`)
    const days = Math.ceil((deadline - today) / 86400000)
    if (filters.deadline === 'OVERDUE') return days < 0 && work.status !== 'COMPLETED'
    if (filters.deadline === '7_DAYS') return days >= 0 && days <= 7
    if (filters.deadline === '30_DAYS') return days >= 0 && days <= 30
    return false
  }
  const priorityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 }
  const filteredWorks = dashboard.works
    .filter(work => !filters.priority || work.priorityLevel === filters.priority)
    .filter(work => !filters.delayRisk || (filters.delayRisk === 'UNAVAILABLE'
      ? !work.delayRiskAvailable : work.delayRisk === filters.delayRisk))
    .filter(work => !filters.status || work.status === filters.status)
    .filter(work => !filters.location
      || work.locationAddress?.toLowerCase().includes(filters.location.toLowerCase()))
    .filter(deadlineMatches)
    .toSorted((a, b) => {
      if (filters.workSort === 'deadline') {
        return (a.expectedEndDate || '9999-12-31').localeCompare(b.expectedEndDate || '9999-12-31')
      }
      if (filters.workSort === 'progress') return (a.progressPercentage ?? 0) - (b.progressPercentage ?? 0)
      if (filters.workSort === 'status') return a.status.localeCompare(b.status)
      return priorityRank[b.priorityLevel] - priorityRank[a.priorityLevel] || b.priorityScore - a.priorityScore
    })
  const filteredComplaints = dashboard.recentComplaints
    .filter(complaint => !filters.severity || complaint.severity === filters.severity)
    .filter(complaint => !filters.issueType || complaint.issueType === filters.issueType)
    .filter(complaint => !filters.location
      || complaint.locationAddress?.toLowerCase().includes(filters.location.toLowerCase()))
    .toSorted((a, b) => filters.complaintSort === 'severity'
      ? severityRank[b.severity] - severityRank[a.severity]
      : new Date(b.createdAt) - new Date(a.createdAt))
  const issueTypeOptions = [...new Set([
    ...COMPLAINT_ISSUE_TYPES,
    ...dashboard.recentComplaints.map(complaint => complaint.issueType).filter(Boolean),
  ])]
  const highPriorityWorks = filteredWorks
    .filter(work => ['HIGH', 'CRITICAL'].includes(work.priorityLevel))
    .toSorted((a, b) => b.priorityScore - a.priorityScore)
  const highDelayRiskWorks = filteredWorks
    .filter(work => work.delayRisk === 'HIGH_DELAY_RISK')
    .toSorted((a, b) => (b.progressGap ?? 0) - (a.progressGap ?? 0))
  const updateFilter = event => setFilters(current => ({ ...current, [event.target.name]: event.target.value }))
  const resetFilters = () => setFilters({
    priority: '', delayRisk: '', status: '', severity: '', issueType: '',
    location: '', deadline: '', workSort: 'priority', complaintSort: 'newest',
  })

  return (
    <div className="dashboard-page">
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">Operations Overview</h1>
          <span className="t-caption">
            Live municipal records · generated {new Date(dashboard.generatedAt).toLocaleString()}
          </span>
        </div>
        <button className="btn btn-sm" onClick={loadDashboard}>Refresh</button>
      </div>

      <div className="kpi-strip dashboard-kpis">
        <Kpi value={workMetrics.total} label="Total work" detail="all work records" tone="blue" />
        <Kpi value={workMetrics.active} label="Active" detail="currently in progress" tone="blue" />
        <Kpi value={workMetrics.completed} label="Completed" detail="completed work" tone="green" />
        <Kpi value={workMetrics.delayed} label="Delayed" detail="marked delayed" tone="red" />
        <Kpi value={workMetrics.highPriority} label="High priority" detail="explainable weighted score" tone="red" />
        <Kpi value={workMetrics.highDelayRisk} label="High delay risk" detail="threshold-based assessment" tone="red" />
        <Kpi value={complaintMetrics.total} label="Complaints" detail="all complaints" tone="blue" />
        <Kpi value={complaintMetrics.unresolved} label="Unresolved" detail="awaiting resolution" tone="amber" />
        <Kpi value={complaintMetrics.aiAssisted} label="AI-assisted" detail="included a reviewable AI suggestion" tone="green" />
      </div>

      <div className="panel dashboard-filter-panel">
        <div className="panel-header">
          <span className="t-strong">Dashboard Filters</span>
          <button className="btn btn-ghost btn-sm" onClick={resetFilters}>Reset</button>
        </div>
        <div className="dashboard-filter-grid">
          <label><span className="t-label">Weighted priority</span><select className="input" name="priority" value={filters.priority} onChange={updateFilter}>
            <option value="">All priorities</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option>
          </select></label>
          <label><span className="t-label">Rule-based delay risk</span><select className="input" name="delayRisk" value={filters.delayRisk} onChange={updateFilter}>
            <option value="">All risk levels</option><option>HIGH_DELAY_RISK</option><option>AT_RISK</option><option>ON_TRACK</option><option>UNAVAILABLE</option>
          </select></label>
          <label><span className="t-label">Work status</span><select className="input" name="status" value={filters.status} onChange={updateFilter}>
            <option value="">All statuses</option><option>SANCTIONED</option><option>IN_PROGRESS</option><option>DELAYED</option><option>COMPLETED</option><option>CANCELLED</option>
          </select></label>
          <label><span className="t-label">Complaint severity</span><select className="input" name="severity" value={filters.severity} onChange={updateFilter}>
            <option value="">All severities</option><option>CRITICAL</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option>
          </select></label>
          <label><span className="t-label">Issue type</span><select className="input" name="issueType" value={filters.issueType} onChange={updateFilter}>
            <option value="">All issue types</option>
            {issueTypeOptions.map(type => (
              <option key={type} value={type}>{complaintIssueTypeLabel(type)}</option>
            ))}
          </select></label>
          <label><span className="t-label">Deadline</span><select className="input" name="deadline" value={filters.deadline} onChange={updateFilter}>
            <option value="">All deadlines</option><option value="OVERDUE">Overdue</option><option value="7_DAYS">Due within 7 days</option><option value="30_DAYS">Due within 30 days</option><option value="NONE">No deadline</option>
          </select></label>
          <label><span className="t-label">Location</span><input className="input" name="location" value={filters.location} onChange={updateFilter} placeholder="Search location" /></label>
          <label><span className="t-label">Work sorting</span><select className="input" name="workSort" value={filters.workSort} onChange={updateFilter}>
            <option value="priority">Priority</option><option value="deadline">Deadline</option><option value="progress">Lowest progress</option><option value="status">Status</option>
          </select></label>
          <label><span className="t-label">Complaint sorting</span><select className="input" name="complaintSort" value={filters.complaintSort} onChange={updateFilter}>
            <option value="newest">Newest</option><option value="severity">Severity</option>
          </select></label>
        </div>
      </div>

      <WorkDecisionTable title="Filtered Work Register" works={filteredWorks}
        emptyMessage="No work matches the selected filters." showRisk />

      <div className="dashboard-decision-grid">
        <WorkDecisionTable title="High Priority Works" works={highPriorityWorks}
          emptyMessage="No high-priority work is currently in scope." />
        <WorkDecisionTable title="High Delay-Risk Works" works={highDelayRiskWorks}
          emptyMessage="No work currently has high delay risk." showRisk />
      </div>

      <div className="panel dashboard-complaints-panel">
        <div className="panel-header">
          <span className="t-strong">Recent Complaints</span>
          <span className="badge badge-sanc">{filteredComplaints.length}</span>
        </div>
        {filteredComplaints.length === 0 ? (
          <div className="dashboard-empty">No recent complaints match the selected filters.</div>
        ) : (
          <div className="dashboard-complaint-list">
            {filteredComplaints.map(complaint => (
              <article key={complaint.id} className="dashboard-complaint-item">
                <div>
                  <div className="dashboard-primary">{complaint.description}</div>
                  <div className="t-caption">
                    #{complaint.id} · {complaint.locationAddress} · {new Date(complaint.createdAt).toLocaleString()}
                  </div>
                </div>
                <div className="dashboard-complaint-badges">
                  {decisionBadge(complaint.severity)}
                  <span className="badge badge-sanc">{complaintIssueTypeLabel(complaint.issueType)}</span>
                  {complaint.aiAssisted && <span className="badge badge-ip">AI assisted</span>}
                </div>
              </article>
            ))}
          </div>
        )}
      </div>

      <div className="dashboard-distribution-grid">
        <DistributionChart title="Work Status Distribution" items={dashboard.workStatusDistribution} />
        <DistributionChart title="Complaint Severity Distribution" items={dashboard.complaintSeverityDistribution} />
      </div>
    </div>
  )
}
