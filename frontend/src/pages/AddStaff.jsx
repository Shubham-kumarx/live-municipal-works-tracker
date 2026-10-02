import { useCallback, useEffect, useState } from 'react'
import api from '../api/axios'
import { getSession } from '../auth/session'

export default function AddStaff() {
  const user = getSession()?.user || {}
  const isAdmin = user.role === 'MUNICIPAL_ADMIN'
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', phone: '', role: 'FIELD_WORKER',
    wardId: user.wardId ? String(user.wardId) : ''
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const [wards, setWards] = useState([])
  const [wardsLoading, setWardsLoading] = useState(isAdmin)
  const [wardsError, setWardsError] = useState('')

  const loadWards = useCallback(async () => {
    if (!isAdmin) return
    setWardsLoading(true); setWardsError('')
    try {
      const response = await api.get('/api/wards')
      setWards(response.data)
    } catch {
      setWards([])
      setWardsError('Wards could not be loaded.')
    } finally {
      setWardsLoading(false)
    }
  }, [isAdmin])

  useEffect(() => { loadWards() }, [loadWards])

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')

    const fullName = form.fullName.trim()
    const email = form.email.trim().toLowerCase()
    const phone = form.phone.replace(/[\s()-]/g, '')
    if (!fullName || !email || !form.password || !phone || !form.wardId) {
      setError('All fields are required')
      return
    }
    if (form.password.length < 6) {
      setError('Temporary password must be at least 6 characters')
      return
    }
    if (!/^\d{10}$/.test(phone)) {
      setError('Enter a valid 10-digit mobile number')
      return
    }

    setLoading(true)
    try {
      await api.post('/api/auth/register-staff', {
        ...form, fullName, email, phone,
        wardId: Number(form.wardId)
      })
      setSuccess(`${form.role === 'FIELD_WORKER' ? 'Field worker' : 'Ward officer'} account created for ${form.fullName}`)
      setForm({ fullName: '', email: '', password: '', phone: '', role: 'FIELD_WORKER',
        wardId: isAdmin ? '' : String(user.wardId || '') })
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create account')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 480 }}>
      <div className="page-header">
        <div className="page-header-left">
          <h1 className="t-page">Add Team Member</h1>
          <span className="t-caption">Create Field Worker or Ward Officer accounts</span>
        </div>
      </div>

      <div className="panel">
        <div className="panel-body">
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Role
              </label>
              <select className="input" value={form.role}
                onChange={e => setForm({ ...form, role: e.target.value })}>
                <option value="FIELD_WORKER">Field Worker</option>
                <option value="WARD_OFFICER">Ward Officer</option>
              </select>
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Ward
              </label>
              {isAdmin ? (
                <>
                  <select className="input" value={form.wardId} required disabled={wardsLoading || Boolean(wardsError)}
                    onChange={e => setForm({ ...form, wardId: e.target.value })}>
                    <option value="">Select ward</option>
                    {wards.map(ward => <option key={ward.id} value={ward.id}>{ward.wardNumber} — {ward.wardName}</option>)}
                  </select>
                  {wardsLoading && <div className="t-caption" style={{ marginTop: 5 }}>Loading wards...</div>}
                  {wardsError && <div className="complaint-message complaint-error" role="alert">
                    {wardsError} <button type="button" className="btn btn-ghost btn-sm" onClick={loadWards}>Retry</button>
                  </div>}
                  {!wardsLoading && !wardsError && wards.length === 0 && <div className="t-caption" style={{ marginTop: 5 }}>No wards are available.</div>}
                </>
              ) : <div className="input" aria-readonly="true">Ward {user.wardId}</div>}
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Full name
              </label>
              <input className="input" value={form.fullName}
                required
                onChange={e => setForm({ ...form, fullName: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Email address
              </label>
              <input className="input" type="email" value={form.email}
                required
                onChange={e => setForm({ ...form, email: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Phone number
              </label>
              <input className="input" value={form.phone}
                inputMode="numeric" maxLength={14} required
                onChange={e => setForm({ ...form, phone: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Temporary password
              </label>
              <input className="input" type="password" value={form.password}
                minLength={6} required
                onChange={e => setForm({ ...form, password: e.target.value })} />
            </div>

            {error && (
              <div role="alert" style={{ padding: '8px 12px', background: 'var(--red-lt)', border: '1px solid #FCA5A5', borderRadius: 'var(--r-md)', fontSize: 12.5, color: 'var(--red)' }}>
                {error}
              </div>
            )}
            {success && (
              <div role="status" style={{ padding: '8px 12px', background: 'var(--green-lt)', border: '1px solid #86D3AE', borderRadius: 'var(--r-md)', fontSize: 12.5, color: 'var(--green)' }}>
                {success}
              </div>
            )}

            <button type="submit" className="btn btn-primary"
              disabled={loading || wardsLoading || Boolean(wardsError) || (isAdmin && wards.length === 0)} style={{ justifyContent: 'center' }}>
              {loading ? 'Creating...' : 'Create account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
