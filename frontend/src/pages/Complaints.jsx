import { useCallback, useEffect, useRef, useState } from 'react'
import api from '../api/axios'
import { apiErrorMessage } from '../api/errors'
import { COMPLAINT_ISSUE_TYPES, complaintIssueTypeLabel } from '../constants/complaintIssueTypes'

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const SEVERITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const label = value => value?.replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase())

export default function Complaints() {
  const [complaints, setComplaints] = useState([])
  const [complaintsLoading, setComplaintsLoading] = useState(true)
  const [complaintsError, setComplaintsError] = useState('')
  const [image, setImage] = useState(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [error, setError] = useState('')
  const [analysis, setAnalysis] = useState(null)
  const [analyzing, setAnalyzing] = useState(false)
  const [predictionState, setPredictionState] = useState('MANUAL')
  const [finalIssueType, setFinalIssueType] = useState('')
  const [finalSeverity, setFinalSeverity] = useState('MEDIUM')
  const [description, setDescription] = useState('')
  const [locationAddress, setLocationAddress] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submittedId, setSubmittedId] = useState(null)
  const fileInput = useRef(null)

  const loadComplaints = useCallback(async () => {
    setComplaintsLoading(true)
    setComplaintsError('')
    try {
      const { data } = await api.get('/api/complaints/my')
      setComplaints(Array.isArray(data) ? data : [])
    } catch (requestError) {
      setComplaintsError(apiErrorMessage(requestError, 'Your complaints could not be loaded.'))
    } finally {
      setComplaintsLoading(false)
    }
  }, [])

  useEffect(() => { loadComplaints() }, [loadComplaints])

  useEffect(() => {
    if (!image) {
      setPreviewUrl('')
      return undefined
    }
    const url = URL.createObjectURL(image)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [image])

  function selectImage(event) {
    const file = event.target.files?.[0]
    setError('')
    setSubmittedId(null)
    setAnalysis(null)
    setPredictionState('MANUAL')
    setFinalIssueType('')
    if (!file) {
      setImage(null)
      return
    }
    if (file.size === 0) {
      setError('Choose a non-empty image file.')
      event.target.value = ''
      setImage(null)
      return
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Choose a JPEG or PNG image.')
      event.target.value = ''
      setImage(null)
      return
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setError('Image must not exceed 10 MB.')
      event.target.value = ''
      setImage(null)
      return
    }
    setImage(file)
  }

  async function analyzeImage() {
    if (!image) {
      setError('Select an image before requesting analysis.')
      return
    }
    setAnalyzing(true)
    setError('')
    const body = new FormData()
    body.append('image', image)
    try {
      const { data } = await api.post('/api/complaints/analyze', body)
      setAnalysis(data)
      if (data.issueType) {
        setFinalIssueType(data.issueType)
        setFinalSeverity(data.suggestedSeverity)
        setPredictionState('CONFIRMED')
      } else {
        setFinalIssueType('')
        setFinalSeverity('MEDIUM')
        setPredictionState('EDITED')
      }
    } catch (requestError) {
      setAnalysis(null)
      setPredictionState('MANUAL')
      setError(apiErrorMessage(requestError,
        'AI analysis is unavailable. You can classify and submit the issue manually.'))
    } finally {
      setAnalyzing(false)
    }
  }

  function choosePredictionState(nextState) {
    setPredictionState(nextState)
    if (nextState === 'CONFIRMED' && analysis?.issueType) {
      setFinalIssueType(analysis.issueType)
      setFinalSeverity(analysis.suggestedSeverity)
    }
    if (nextState === 'REJECTED') {
      setFinalIssueType('')
      setFinalSeverity('MEDIUM')
    }
    if (nextState === 'MANUAL') setAnalysis(null)
  }

  async function submitComplaint() {
    setError('')
    setSubmittedId(null)
    if (!image || !description.trim() || !locationAddress.trim()) {
      setError('Add an image, description, and location before submitting.')
      return
    }
    if (description.trim().length > 2000 || locationAddress.trim().length > 255) {
      setError('Description or location exceeds the allowed length.')
      return
    }
    const aiFields = analysis ? {
      aiPredictedIssueType: analysis.candidateIssueType,
      aiConfidence: analysis.confidence,
      aiConfidenceLevel: analysis.confidenceLevel,
      aiSuggestedSeverity: analysis.suggestedSeverity,
    } : {
      aiPredictedIssueType: null,
      aiConfidence: null,
      aiConfidenceLevel: null,
      aiSuggestedSeverity: null,
    }
    const complaint = {
      description: description.trim(),
      locationAddress: locationAddress.trim(),
      latitude: null,
      longitude: null,
      ...aiFields,
      finalIssueType,
      finalSeverity,
      predictionState: analysis ? predictionState : 'MANUAL',
    }
    const body = new FormData()
    body.append('complaint', new Blob([JSON.stringify(complaint)], { type: 'application/json' }))
    body.append('image', image)
    setSubmitting(true)
    try {
      const { data } = await api.post('/api/complaints', body)
      setSubmittedId(data.id)
      await loadComplaints()
      setImage(null)
      setAnalysis(null)
      setPredictionState('MANUAL')
      setFinalIssueType('')
      setFinalSeverity('MEDIUM')
      setDescription('')
      setLocationAddress('')
      if (fileInput.current) fileInput.current.value = ''
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Complaint could not be submitted. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  function handlePreviewError() {
    setError('The selected image could not be previewed. Choose another JPEG or PNG image.')
    setImage(null)
    setAnalysis(null)
    if (fileInput.current) fileInput.current.value = ''
  }

  const canSubmit = image && finalIssueType && description.trim() && locationAddress.trim()
    && !submitting && !analyzing

  return (
    <div className="complaint-page">
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">My Complaints</h1>
          <div className="t-caption">Review issues reported from your account or report another municipal issue.</div>
        </div>
        <button type="button" className="btn btn-sm" onClick={loadComplaints}
          disabled={complaintsLoading}>Refresh</button>
      </div>

      {complaintsLoading ? (
        <div className="panel"><div className="panel-body t-caption">Loading your complaints...</div></div>
      ) : complaintsError ? (
        <div className="complaint-message complaint-error" role="alert">
          {complaintsError} <button type="button" className="btn btn-ghost btn-sm"
            onClick={loadComplaints}>Retry</button>
        </div>
      ) : complaints.length === 0 ? (
        <div className="panel"><div className="panel-body t-caption">No complaints reported yet.</div></div>
      ) : (
        <div className="panel operational-table" style={{ overflowX: 'auto' }}>
          <table className="data-table">
            <thead><tr>
              <th>ID</th><th>Issue</th><th>Severity</th><th>Status</th><th>Location</th><th>Reported</th>
            </tr></thead>
            <tbody>
              {complaints.map(complaint => (
                <tr key={complaint.id}>
                  <td className="col-id">#{complaint.id}</td>
                  <td>
                    <div style={{ fontWeight: 500 }}>{complaintIssueTypeLabel(complaint.finalIssueType)}</div>
                    <div className="t-caption">{complaint.description}</div>
                  </td>
                  <td>{label(complaint.finalSeverity)}</td>
                  <td>{label(complaint.status)}</td>
                  <td>{complaint.locationAddress}</td>
                  <td>{complaint.createdAt
                    ? new Date(complaint.createdAt).toLocaleString('en-IN') : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="page-header">
        <div className="page-header-left">
          <h2 className="t-heading">Report a municipal issue</h2>
          <div className="t-caption">Upload a clear photo, review any AI-assisted suggestion, and confirm the final details.</div>
        </div>
      </div>

      <div className="panel complaint-panel">
        <div className="panel-header">
          <span className="t-strong">Issue photo</span>
          <span className="t-caption">JPEG or PNG · maximum 10 MB</span>
        </div>
        <div className="panel-body">
          <label className="complaint-upload">
            <input ref={fileInput} type="file" accept="image/jpeg,image/png" onChange={selectImage}
              disabled={analyzing || submitting} />
            {previewUrl ? (
              <img src={previewUrl} alt="Selected municipal issue" onError={handlePreviewError} />
            ) : (
              <span>Select a photo of the issue</span>
            )}
          </label>
          {image && (
            <div className="complaint-file-row">
              <span className="t-caption">{image.name} · {(image.size / 1024 / 1024).toFixed(2)} MB</span>
              <button type="button" className="btn btn-ghost btn-sm" onClick={() => {
                setImage(null); setAnalysis(null); setPredictionState('MANUAL')
                if (fileInput.current) fileInput.current.value = ''
              }}>Remove</button>
            </div>
          )}
          {error && <div className="complaint-message complaint-error" role="alert">{error}</div>}
          <div className="complaint-actions">
            <button type="button" className="btn btn-primary" disabled={!image || analyzing} onClick={analyzeImage}>
              {analyzing ? 'Analyzing…' : 'Analyze image'}
            </button>
            <button type="button" className="btn" onClick={() => choosePredictionState('MANUAL')}>
              Classify manually
            </button>
          </div>
        </div>
      </div>

      {analysis && (
        <div className="panel complaint-panel">
          <div className="panel-header">
            <span className="t-strong">AI-assisted suggestion</span>
            <span className={`badge ${analysis.confidenceLevel === 'HIGH' ? 'badge-done' : analysis.confidenceLevel === 'MEDIUM' ? 'badge-pend' : 'badge-late'}`}>
              {analysis.confidenceLevel} confidence
            </span>
          </div>
          <div className="panel-body">
            <div className="complaint-prediction-grid">
              <div><div className="t-label">Detected type</div><div className="t-strong">{complaintIssueTypeLabel(analysis.candidateIssueType)}</div></div>
              <div><div className="t-label">Relative model score</div><div className="t-strong">{(analysis.confidence * 100).toFixed(1)}%</div></div>
              <div><div className="t-label">Category</div><div className="t-strong">{label(analysis.category)}</div></div>
              <div><div className="t-label">Suggested severity</div><div className="t-strong">{label(analysis.suggestedSeverity)}</div></div>
            </div>
            <div className="complaint-message complaint-ai-note">
              {analysis.confidenceLevel === 'HIGH'
                ? 'High confidence prediction. Confirm or edit it before submitting.'
                : analysis.confidenceLevel === 'MEDIUM'
                  ? 'Possible prediction — please verify the issue and severity before submitting.'
                  : 'Unable to confidently identify the issue. Select the correct issue and severity manually.'}
            </div>
            <div className="complaint-actions">
              <button type="button" className="btn btn-primary" disabled={!analysis.issueType}
                onClick={() => choosePredictionState('CONFIRMED')}>Confirm suggestion</button>
              <button type="button" className="btn" onClick={() => choosePredictionState('EDITED')}>Edit classification</button>
              <button type="button" className="btn" onClick={() => choosePredictionState('REJECTED')}>Reject suggestion</button>
              <button type="button" className="btn btn-ghost" onClick={() => choosePredictionState('MANUAL')}>Remove AI result</button>
            </div>
          </div>
        </div>
      )}

      <div className="panel complaint-panel">
        <div className="panel-header">
          <span className="t-strong">Confirmed classification</span>
          <span className="t-caption">{label(predictionState)}</span>
        </div>
        <div className="panel-body complaint-form-grid">
          <label className="complaint-form-wide">
            <span className="t-label">Description</span>
            <textarea className="input complaint-textarea" maxLength={2000} required value={description}
              onChange={event => setDescription(event.target.value)}
              placeholder="Describe what is visible and how it affects the area" />
          </label>
          <label className="complaint-form-wide">
            <span className="t-label">Location</span>
            <input className="input" maxLength={255} required value={locationAddress}
              onChange={event => setLocationAddress(event.target.value)}
              placeholder="Street, landmark, or nearby address" />
          </label>
          <label>
            <span className="t-label">Issue type</span>
            <select className="input" value={finalIssueType} onChange={event => {
              setFinalIssueType(event.target.value)
              if (analysis && predictionState !== 'REJECTED') setPredictionState('EDITED')
            }}>
              <option value="">Select issue type</option>
              {COMPLAINT_ISSUE_TYPES.map(type => (
                <option key={type} value={type}>{complaintIssueTypeLabel(type)}</option>
              ))}
            </select>
          </label>
          <label>
            <span className="t-label">Severity</span>
            <select className="input" value={finalSeverity} onChange={event => {
              setFinalSeverity(event.target.value)
              if (analysis && predictionState !== 'REJECTED') setPredictionState('EDITED')
            }}>
              {SEVERITIES.map(severity => <option key={severity} value={severity}>{label(severity)}</option>)}
            </select>
          </label>
          <div className="complaint-form-wide complaint-submit-row">
            <span className="t-caption">Your confirmed values are saved separately from the AI suggestion.</span>
            <button type="button" className="btn btn-primary" disabled={!canSubmit} onClick={submitComplaint}>
              {submitting ? 'Submitting…' : 'Submit complaint'}
            </button>
          </div>
        </div>
      </div>
      {submittedId && <div className="complaint-message complaint-success" role="status">Complaint #{submittedId} was submitted.</div>}
    </div>
  )
}
