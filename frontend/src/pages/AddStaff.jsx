import { useState } from 'react'
import api from '../api/axios'

export default function AddStaff() {
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', phone: '', role: 'FIELD_WORKER'
  })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)

  const user = JSON.parse(localStorage.getItem('user') || '{}')

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')
    setSuccess('')

    if (!form.fullName || !form.email || !form.password || !form.phone) {
      setError('All fields are required')
      return
    }

    setLoading(true)
    try {
      await api.post('/api/auth/register-staff', {
        ...form,
        wardId: user.wardId
      })
      setSuccess(`${form.role === 'FIELD_WORKER' ? 'Field worker' : 'Ward officer'} account created for ${form.fullName}`)
      setForm({ fullName: '', email: '', password: '', phone: '', role: 'FIELD_WORKER' })
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
                Full name
              </label>
              <input className="input" value={form.fullName}
                onChange={e => setForm({ ...form, fullName: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Email address
              </label>
              <input className="input" type="email" value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Phone number
              </label>
              <input className="input" value={form.phone}
                onChange={e => setForm({ ...form, phone: e.target.value })} />
            </div>

            <div>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--text-secondary)', marginBottom: 5 }}>
                Temporary password
              </label>
              <input className="input" type="password" value={form.password}
                onChange={e => setForm({ ...form, password: e.target.value })} />
            </div>

            {error && (
              <div style={{ padding: '8px 12px', background: 'var(--red-lt)', border: '1px solid #FCA5A5', borderRadius: 'var(--r-md)', fontSize: 12.5, color: 'var(--red)' }}>
                {error}
              </div>
            )}
            {success && (
              <div style={{ padding: '8px 12px', background: 'var(--green-lt)', border: '1px solid #86D3AE', borderRadius: 'var(--r-md)', fontSize: 12.5, color: 'var(--green)' }}>
                {success}
              </div>
            )}

            <button type="submit" className="btn btn-primary" disabled={loading} style={{ justifyContent: 'center' }}>
              {loading ? 'Creating...' : 'Create account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}