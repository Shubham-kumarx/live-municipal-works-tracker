import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api/axios'
function distanceKm(lat1, lng1, lat2, lng2) {
  const R = 6371
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLng = (lng2 - lng1) * Math.PI / 180
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLng / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}


export default function Register() {
  const navigate = useNavigate()
  const [wards, setWards] = useState([])
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', phone: '', wardId: ''
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [detectingLocation, setDetectingLocation] = useState(false)
  const [detectedWardName, setDetectedWardName] = useState('')

  useEffect(() => {
    api.get('/api/wards').then(res => setWards(res.data)).catch(() => {})
  }, [])
function detectNearestWard() {
  setDetectingLocation(true)
  setDetectedWardName('')

  navigator.geolocation.getCurrentPosition(
    pos => {
      const userLat = pos.coords.latitude
      const userLng = pos.coords.longitude

      let nearest = null
      let minDist = Infinity

      wards.forEach(w => {
        if (w.centerLatitude == null || w.centerLongitude == null) return
        const d = distanceKm(userLat, userLng, w.centerLatitude, w.centerLongitude)
        if (d < minDist) {
          minDist = d
          nearest = w
        }
      })

      if (nearest) {
        setForm(prev => ({ ...prev, wardId: String(nearest.id) }))
        setDetectedWardName(`${nearest.wardNumber} — ${nearest.wardName} (${minDist.toFixed(1)} km away)`)
      }
      setDetectingLocation(false)
    },
    () => {
      setDetectingLocation(false)
      setError('Could not detect your location. Please select your ward manually.')
    },
    { enableHighAccuracy: true, timeout: 10000 }
  )
}
  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (!form.fullName || !form.email || !form.password || !form.phone || !form.wardId) {
      setError('All fields are required')
      return
    }
    if (form.password.length < 6) {
      setError('Password must be at least 6 characters')
      return
    }

    setLoading(true)
    try {
      const res = await api.post('/api/auth/register', {
        ...form,
        wardId: Number(form.wardId),
        role: 'CITIZEN' // ignored by backend anyway, kept for clarity
      })
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify({
        email: res.data.email,
        fullName: res.data.fullName,
        role: res.data.role,
        wardId: res.data.wardId,
      }))
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed. Try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      height: '100vh', display: 'grid',
      gridTemplateColumns: '1fr 420px', background: '#F2F1EE'
    }}>
      <div style={{
        background: '#1C1F24', display: 'flex', flexDirection: 'column',
        padding: '48px 56px', justifyContent: 'center'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#4A8F6F' }} />
          <span style={{ fontSize: 15, fontWeight: 600, color: '#E8E6E1' }}>CivicOps</span>
        </div>
        <div style={{ fontSize: 28, fontWeight: 500, color: '#E8E6E1', lineHeight: 1.3, marginBottom: 14 }}>
          See what's happening<br />in your ward, in real time.
        </div>
        <div style={{ fontSize: 13.5, color: '#6B7280', lineHeight: 1.8, maxWidth: 380 }}>
          Track municipal work orders, flag issues, and follow
          progress on civic infrastructure projects near you.
        </div>
      </div>

      <div style={{
        display: 'flex', flexDirection: 'column', justifyContent: 'center',
        padding: '48px 40px', background: '#FFFFFF', borderLeft: '1px solid #E2E0DB',
        overflowY: 'auto'
      }}>
        <div style={{ marginBottom: 28 }}>
          <h1 style={{ fontSize: 22, fontWeight: 500, color: '#1C1F24', marginBottom: 6 }}>
            Create citizen account
          </h1>
          <p style={{ fontSize: 13, color: '#8A8F98' }}>
            Free for residents · North Delhi Municipal Corporation
          </p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Full name
            </label>
            <input className="input" placeholder="Your full name"
              value={form.fullName}
              onChange={e => setForm({ ...form, fullName: e.target.value })}
              disabled={loading} />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Email address
            </label>
            <input className="input" type="email" placeholder="you@example.com"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              disabled={loading} />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Phone number
            </label>
            <input className="input" placeholder="10-digit mobile number"
              value={form.phone}
              onChange={e => setForm({ ...form, phone: e.target.value })}
              disabled={loading} />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Your ward
            </label>

            <button
              type="button"
              className="btn btn-sm"
              onClick={detectNearestWard}
              disabled={detectingLocation || wards.length === 0}
              style={{ width: '100%', justifyContent: 'center', padding: '7px 0', marginBottom: 8 }}
            >
              {detectingLocation ? '📡 Detecting your location...' : '📍 Use my current location'}
            </button>

            {detectedWardName && (
              <div style={{ fontSize: 11.5, color: 'var(--green)', marginBottom: 8 }}>
                ✓ Nearest ward detected: {detectedWardName}
              </div>
            )}

            <select className="input" value={form.wardId}
              onChange={e => setForm({ ...form, wardId: e.target.value })}
              disabled={loading}>
              <option value="">Select your ward</option>
              {wards.map(w => (
                <option key={w.id} value={w.id}>
                  {w.wardNumber} — {w.wardName}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Password
            </label>
            <input className="input" type="password" placeholder="At least 6 characters"
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })}
              disabled={loading} />
          </div>

          {error && (
            <div style={{
              padding: '8px 12px', background: '#FDE8E8',
              border: '1px solid #FCA5A5', borderRadius: 'var(--r-md)',
              fontSize: 12.5, color: '#8B1C1C'
            }}>
              {error}
            </div>
          )}

          <button type="submit" className="btn btn-primary" disabled={loading}
            style={{ width: '100%', justifyContent: 'center', padding: '8px 0', fontSize: 13.5, marginTop: 4 }}>
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <div style={{ marginTop: 20, fontSize: 12.5, color: '#8A8F98', textAlign: 'center' }}>
          Already have an account?{' '}
          <Link to="/login" style={{ color: '#1A4B6E', fontWeight: 500 }}>Sign in</Link>
        </div>

        <div style={{ marginTop: 20, fontSize: 11, color: '#8A8F98', textAlign: 'center', lineHeight: 1.6 }}>
          Field workers and officers cannot self-register.<br />
          Accounts are created by your municipal administrator.
        </div>
      </div>
    </div>
  )
}