import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axios'
import { apiErrorMessage } from '../api/errors'
import { complaintIssueTypeLabel } from '../constants/complaintIssueTypes'

const label = value => value?.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())

export default function AdminComplaints() {
  const [complaints, setComplaints] = useState([])
  const [projects, setProjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [selections, setSelections] = useState({})
  const [busyId, setBusyId] = useState(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    setSuccess('')
    try {
      const [complaintResponse, projectResponse] = await Promise.all([
        api.get('/api/complaints'),
        api.get('/api/complaints/linkable-projects'),
      ])
      setComplaints(complaintResponse.data)
      setProjects(projectResponse.data)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Complaints could not be loaded.'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadData() }, [loadData])

  const projectById = new Map(projects.map(project => [project.id, project]))

  async function linkComplaint(complaintId) {
    const projectId = Number(selections[complaintId])
    if (!projectId) return
    setBusyId(complaintId); setError(''); setSuccess('')
    try {
      await api.patch(`/api/complaints/${complaintId}/project/${projectId}`)
      setSelections(current => ({ ...current, [complaintId]: '' }))
      await loadData()
      setSuccess(`Complaint #${complaintId} was linked successfully.`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Complaint could not be linked.'))
    } finally {
      setBusyId(null)
    }
  }

  async function unlinkComplaint(complaintId) {
    setBusyId(complaintId); setError(''); setSuccess('')
    try {
      await api.delete(`/api/complaints/${complaintId}/project`)
      await loadData()
      setSuccess(`Complaint #${complaintId} was unlinked successfully.`)
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Complaint could not be unlinked.'))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">Complaint management</h1>
          <span className="t-caption">Review reported issues and associate them with municipal work.</span>
        </div>
        <button className="btn btn-sm" onClick={loadData} disabled={loading}>Refresh</button>
      </div>

      {success && <div className="complaint-message complaint-success" role="status">{success}</div>}
      {error ? (
        <div className="complaint-message complaint-error" role="alert">
          {error} <button className="btn btn-ghost btn-sm" onClick={loadData}>Retry</button>
        </div>
      ) : loading ? (
        <div className="panel"><div className="panel-body t-caption">Loading complaints...</div></div>
      ) : complaints.length === 0 ? (
        <div className="panel"><div className="panel-body t-caption">No complaints are available.</div></div>
      ) : (
        <div className="panel operational-table" style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead><tr>
              <th>ID</th><th>Issue</th><th>Severity</th><th>Location</th>
              <th>Reported</th><th>Linked work</th><th>Action</th>
            </tr></thead>
            <tbody>
              {complaints.map(complaint => {
                const linked = projectById.get(complaint.municipalProjectId)
                return (
                  <tr key={complaint.id}>
                    <td className="col-id">#{complaint.id}</td>
                    <td><div style={{ fontWeight: 500 }}>{complaintIssueTypeLabel(complaint.finalIssueType)}</div>
                      <div className="t-caption">{complaint.description}</div></td>
                    <td>{label(complaint.finalSeverity)}</td>
                    <td>{complaint.locationAddress}</td>
                    <td>{complaint.createdAt ? new Date(complaint.createdAt).toLocaleString('en-IN') : '—'}</td>
                    <td>{linked ? (
                      <Link to={`/map?projectId=${linked.id}&wardId=${linked.wardId}`} className="linked-work-link">
                        {linked.projectName} (#{linked.id})
                      </Link>
                    ) : complaint.municipalProjectId ? (
                      <Link to={`/map?projectId=${complaint.municipalProjectId}`} className="linked-work-link">
                        Project #{complaint.municipalProjectId}
                      </Link>
                    ) : 'Unlinked'}</td>
                    <td style={{ minWidth: 230 }}>
                      {!complaint.municipalProjectId && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <select className="input input-sm" value={selections[complaint.id] || ''}
                            disabled={projects.length === 0 || busyId === complaint.id}
                            onChange={event => setSelections(current => ({
                              ...current, [complaint.id]: event.target.value
                            }))}>
                            <option value="">{projects.length === 0 ? 'No linkable work' : 'Select work'}</option>
                            {projects.map(project => (
                              <option key={project.id} value={project.id}>
                                #{project.id} {project.projectName}
                              </option>
                            ))}
                          </select>
                          <button className="btn btn-primary btn-sm"
                            disabled={!selections[complaint.id] || busyId === complaint.id}
                            onClick={() => linkComplaint(complaint.id)}>
                            {busyId === complaint.id ? 'Linking...' : 'Link'}
                          </button>
                        </div>
                      )}
                      {complaint.municipalProjectId && (
                        <button className="btn btn-sm" disabled={busyId === complaint.id}
                          onClick={() => unlinkComplaint(complaint.id)}>
                          {busyId === complaint.id ? 'Unlinking...' : 'Unlink'}
                        </button>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
