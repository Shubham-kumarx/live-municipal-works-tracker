import { useState, useRef } from 'react'
import api from '../api/axios'

export default function FieldUpdateForm({ project, onClose, onUpdated }) {
  const [status, setStatus] = useState(project.status)
  const [progress, setProgress] = useState(project.progressPercentage || 0)
  const [note, setNote] = useState('')
  const [photos, setPhotos] = useState([])
  const [gps, setGps] = useState(null)
  const [gpsLoading, setGpsLoading] = useState(false)
  const [gpsError, setGpsError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [statusSaved, setStatusSaved] = useState(false)
  const fileRef = useRef()
  const allowedStatuses = {
    SANCTIONED: ['SANCTIONED', 'IN_PROGRESS', 'CANCELLED'],
    IN_PROGRESS: ['IN_PROGRESS', 'DELAYED', 'COMPLETED', 'CANCELLED'],
    DELAYED: ['DELAYED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'],
    COMPLETED: ['COMPLETED'],
    CANCELLED: ['CANCELLED'],
  }[project.status] || [project.status]

  // Auto-capture GPS
  function captureGPS() {
    setGpsLoading(true)
    setGpsError('')
    navigator.geolocation.getCurrentPosition(
      pos => {
        setGps({
          lat: pos.coords.latitude.toFixed(6),
          lng: pos.coords.longitude.toFixed(6),
          accuracy: Math.round(pos.coords.accuracy)
        })
        setGpsLoading(false)
      },
      () => {
        setGpsError('Location access denied. Please enable GPS.')
        setGpsLoading(false)
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }

  // Handle photo selection
  function handlePhotoChange(e) {
    const files = Array.from(e.target.files)
    files.forEach(file => {
      const reader = new FileReader()
      const id = crypto.randomUUID()
      reader.onload = ev => setPhotos(prev => [...prev, { id, file, preview: ev.target.result }])
      reader.readAsDataURL(file)
    })
  }

  function removePhoto(index) {
    setPhotos(prev => prev.filter((_, i) => i !== index))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    let saved = statusSaved

    try {
      // 1. Update status and progress
      if (!statusSaved) {
        await api.patch(`/api/projects/${project.id}/status`, {
          status,
          progressNote: note,
          progressPercentage: progress
        })
        setStatusSaved(true)
        saved = true
      }

      // 2. Upload photos if any
      if (photos.length > 0) {
        const formData = new FormData()
        photos.forEach(photo => formData.append('files', photo.file))
        await api.post(
          `/api/upload/project/${project.id}/photos`,
          formData,
          { headers: { 'Content-Type': 'multipart/form-data' } }
        )
      }

      onUpdated()
      onClose()
    } catch (err) {
      setError(saved
        ? `Status was saved, but photos failed: ${err.response?.data?.message || 'Try the upload again.'}`
        : (err.response?.data?.message || 'Update failed. Try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 9999,
      background: 'rgba(0,0,0,0.5)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      padding: 16
    }}>
      <div style={{
        background: 'var(--bg-surface)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--r-lg)',
        width: '100%', maxWidth: 520,
        maxHeight: '90vh', overflow: 'auto'
      }}>

        {/* Header */}
        <div style={{
          padding: '14px 20px',
          borderBottom: '1px solid var(--border)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start'
        }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 500, color: 'var(--text-primary)' }}>
              Log Progress Update
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
              {project.projectName}
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit} style={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 18 }}>

          {/* GPS Section */}
          <div>
            <div className="t-label" style={{ marginBottom: 8 }}>
              Location preview (GPS)
            </div>
            {gps ? (
              <div style={{
                padding: '10px 14px',
                background: 'var(--bg-hover)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--r-md)',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center'
              }}>
                <div>
                  <div style={{ fontSize: 12.5, fontWeight: 500, color: 'var(--green)' }}>
                    ✓ Location captured
                  </div>
                  <div style={{ fontSize: 11.5, color: 'var(--text-muted)', marginTop: 2 }}>
                    {gps.lat}, {gps.lng} · ±{gps.accuracy}m accuracy
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-ghost btn-sm"
                  onClick={() => setGps(null)}
                >
                  Recapture
                </button>
              </div>
            ) : (
              <div>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={captureGPS}
                  disabled={gpsLoading}
                  style={{ width: '100%', justifyContent: 'center', padding: '8px 0' }}
                >
                  {gpsLoading ? '📡 Getting location...' : '📍 Capture my GPS location'}
                </button>
                {gpsError && (
                  <div style={{ fontSize: 11.5, color: 'var(--red)', marginTop: 6 }}>
                    {gpsError}
                  </div>
                )}
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 5 }}>
                  Browser permission is used only for this preview. Coordinates are not submitted or stored.
                </div>
              </div>
            )}
          </div>

          {/* Status */}
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>
              Update status
            </label>
            <select
              className="input"
              value={status}
              onChange={e => setStatus(e.target.value)}
            >
              {allowedStatuses.map(value => (
                <option key={value} value={value}>
                  {{ SANCTIONED: 'Sanctioned (not started)', IN_PROGRESS: 'In Progress',
                    COMPLETED: 'Completed', DELAYED: 'Delayed', CANCELLED: 'Cancelled' }[value]}
                </option>
              ))}
            </select>
          </div>

          {/* Progress slider */}
          <div>
            <div style={{
              display: 'flex', justifyContent: 'space-between',
              marginBottom: 8
            }}>
              <label className="t-label">Work completion</label>
              <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--accent)' }}>
                {progress}%
              </span>
            </div>
            <input
              type="range"
              min={0} max={100} step={5}
              value={progress}
              onChange={e => setProgress(Number(e.target.value))}
              style={{ width: '100%', accentColor: 'var(--accent)' }}
            />
            <div style={{
              display: 'flex', justifyContent: 'space-between',
              fontSize: 10.5, color: 'var(--text-muted)', marginTop: 3
            }}>
              <span>0%</span>
              <span>25%</span>
              <span>50%</span>
              <span>75%</span>
              <span>100%</span>
            </div>
          </div>

          {/* Progress bar preview */}
          <div style={{ height: 4, background: 'var(--border)', borderRadius: 2 }}>
            <div style={{
              height: 4, borderRadius: 2,
              width: `${progress}%`,
              background: progress === 100 ? 'var(--green)' : 'var(--accent)',
              transition: 'width .2s'
            }} />
          </div>

          {/* Note */}
          <div>
            <label className="t-label" style={{ display: 'block', marginBottom: 6 }}>
              Progress note
            </label>
            <textarea
              className="input"
              placeholder="Describe what was done today, any issues faced..."
              value={note}
              onChange={e => setNote(e.target.value)}
              rows={3}
              style={{ height: 'auto', resize: 'vertical', padding: '8px 10px' }}
            />
          </div>

          {/* Photo upload */}
          <div>
            <div className="t-label" style={{ marginBottom: 8 }}>
              Photo evidence
            </div>

            {/* Hidden file input */}
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,.jpg,.jpeg,.png"
              capture="environment"
              multiple
              style={{ display: 'none' }}
              onChange={handlePhotoChange}
            />

            {/* Upload button */}
            <button
              type="button"
              className="btn btn-sm"
              onClick={() => fileRef.current.click()}
              style={{ width: '100%', justifyContent: 'center', padding: '8px 0' }}
            >
              📷 Take photo / Upload from gallery
            </button>

            <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 5 }}>
              On mobile, opens camera directly. On desktop, opens file picker.
            </div>

            {/* Photo previews */}
            {photos.length > 0 && (
              <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(3,1fr)',
                gap: 8, marginTop: 12
              }}>
                {photos.map((photo, i) => (
                  <div key={photo.id} style={{ position: 'relative' }}>
                    <img
                      src={photo.preview}
                      alt={`Photo ${i + 1}`}
                      style={{
                        width: '100%', aspectRatio: '1',
                        objectFit: 'cover',
                        borderRadius: 'var(--r-md)',
                        border: '1px solid var(--border)'
                      }}
                    />
                    <button
                      type="button"
                      onClick={() => removePhoto(i)}
                      style={{
                        position: 'absolute', top: 4, right: 4,
                        width: 20, height: 20,
                        borderRadius: '50%',
                        background: 'rgba(0,0,0,0.6)',
                        border: 'none', color: '#fff',
                        fontSize: 10, cursor: 'pointer',
                        display: 'flex', alignItems: 'center', justifyContent: 'center'
                      }}
                    >✕</button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Error */}
          {error && (
            <div style={{
              padding: '8px 12px',
              background: 'var(--red-lt)',
              border: '1px solid #FCA5A5',
              borderRadius: 'var(--r-md)',
              fontSize: 12.5, color: 'var(--red)'
            }}>
              {error}
            </div>
          )}

          {/* Submit */}
          <div style={{
            display: 'flex', gap: 8,
            paddingTop: 4, borderTop: '1px solid var(--border)'
          }}>
            <button
              type="button"
              className="btn btn-sm"
              onClick={onClose}
              style={{ flex: 1 }}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn btn-primary btn-sm"
              disabled={submitting}
              style={{ flex: 2, justifyContent: 'center' }}
            >
              {submitting ? 'Submitting...' : 'Submit update'}
            </button>
          </div>

        </form>
      </div>
    </div>
  )
}
