import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axios from 'axios'

export default function Login() {
  const navigate = useNavigate()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e) {
    e.preventDefault()
    setError('')

    if (!form.email || !form.password) {
      setError('Email and password are required')
      return
    }

    setLoading(true)
    try {
      const res = await axios.post(
        'http://localhost:8080/api/auth/login',
        form
      )
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify({
        email: res.data.email,
        fullName: res.data.fullName,
        role: res.data.role,
        wardId: res.data.wardId,
      }))
      navigate('/dashboard')
    } catch (err) {
      setError(
        err.response?.data?.message ||
        'Invalid email or password'
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{
      height: '100vh',
      display: 'grid',
      gridTemplateColumns: '1fr 420px',
      background: '#F2F1EE'
    }}>

      {/* Left — branding panel */}
      <div style={{
        background: '#1C1F24',
        display: 'flex',
        flexDirection: 'column',
        padding: '48px 56px',
        position: 'relative',
        overflow: 'hidden'
      }}>
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 'auto' }}>
          <div style={{
            width: 8, height: 8, borderRadius: '50%', background: '#4A8F6F'
          }} />
          <span style={{ fontSize: 15, fontWeight: 600, color: '#E8E6E1', letterSpacing: .2 }}>
            CivicOps
          </span>
        </div>

        {/* Main text */}
        <div style={{ marginBottom: 'auto' }}>
          <div style={{
            fontSize: 32, fontWeight: 500, color: '#E8E6E1',
            lineHeight: 1.2, marginBottom: 16, letterSpacing: -.5
          }}>
            Municipal Operations<br />Management System
          </div>
          <div style={{ fontSize: 14, color: '#6B7280', lineHeight: 1.8, maxWidth: 400 }}>
            Real-time tracking of civic infrastructure work orders,
            field team coordination, and complaint resolution
            for North Delhi Municipal Corporation.
          </div>
        </div>

        {/* Stats row */}
        <div style={{
          display: 'grid', gridTemplateColumns: 'repeat(3,1fr)',
          gap: 0, borderTop: '1px solid #2E3238', paddingTop: 32
        }}>
          {[
            { num: '14', label: 'Wards covered' },
            { num: '340+', label: 'Active work orders' },
            { num: '86', label: 'Field workers' },
          ].map(s => (
            <div key={s.label} style={{ paddingRight: 24 }}>
              <div style={{ fontSize: 26, fontWeight: 500, color: '#E8E6E1', fontVariantNumeric: 'tabular-nums' }}>
                {s.num}
              </div>
              <div style={{ fontSize: 11.5, color: '#6B7280', marginTop: 3 }}>{s.label}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Right — login form */}
      <div style={{
        display: 'flex', flexDirection: 'column',
        justifyContent: 'center', padding: '48px 40px',
        background: '#FFFFFF',
        borderLeft: '1px solid #E2E0DB'
      }}>
        <div style={{ marginBottom: 32 }}>
          <h1 style={{ fontSize: 22, fontWeight: 500, color: '#1C1F24', marginBottom: 6 }}>
            Sign in
          </h1>
          <p style={{ fontSize: 13, color: '#8A8F98' }}>
            North Delhi Municipal Corporation · Officer portal
          </p>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Email */}
          <div>
            <label style={{ display: 'block', fontSize: 12, fontWeight: 500, color: '#52575E', marginBottom: 5 }}>
              Email address
            </label>
            <input
              className="input"
              type="email"
              placeholder="officer@ndmc.gov.in"
              value={form.email}
              onChange={e => setForm({ ...form, email: e.target.value })}
              autoComplete="email"
              disabled={loading}
            />
          </div>

          {/* Password */}
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
              <label style={{ fontSize: 12, fontWeight: 500, color: '#52575E' }}>
                Password
              </label>
              <button type="button" className="btn btn-ghost btn-sm"
                style={{ padding: 0, fontSize: 12, color: '#1A4B6E' }}>
                Forgot password?
              </button>
            </div>
            <input
              className="input"
              type="password"
              placeholder="Enter your password"
              value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })}
              autoComplete="current-password"
              disabled={loading}
            />
          </div>

          {/* Error */}
          {error && (
            <div style={{
              padding: '8px 12px',
              background: '#FDE8E8',
              border: '1px solid #FCA5A5',
              borderRadius: 'var(--r-md)',
              fontSize: 12.5, color: '#8B1C1C'
            }}>
              {error}
            </div>
          )}

          {/* Submit */}
          <button
            type="submit"
            className="btn btn-primary"
            disabled={loading}
            style={{
              width: '100%', justifyContent: 'center',
              padding: '8px 0', fontSize: 13.5,
              marginTop: 4,
              opacity: loading ? .7 : 1
            }}
          >
            {loading ? 'Signing in...' : 'Sign in'}
          </button>

        </form>

        {/* Demo accounts */}
        <div style={{
          marginTop: 28,
          padding: '14px 16px',
          background: '#F7F6F3',
          border: '1px solid #E2E0DB',
          borderRadius: 'var(--r-lg)'
        }}>
          <div style={{ fontSize: 11, fontWeight: 500, color: '#8A8F98',
            textTransform: 'uppercase', letterSpacing: '.5px', marginBottom: 10 }}>
            Demo accounts
          </div>
          {[
            { label: 'Municipal Admin', email: 'shubham@gmail.com', pass: 'secret123' },
            { label: 'Ward Officer', email: 'officer@gmail.com', pass: 'officer123' },
            { label: 'Field Worker', email: 'worker@gmail.com', pass: 'worker123' },
          ].map(acc => (
            <div
              key={acc.email}
              style={{
                display: 'flex', justifyContent: 'space-between',
                alignItems: 'center', padding: '6px 0',
                borderBottom: '1px solid #E2E0DB',
                cursor: 'pointer'
              }}
              onClick={() => setForm({ email: acc.email, password: acc.pass })}
            >
              <div>
                <div style={{ fontSize: 12, fontWeight: 500, color: '#1C1F24' }}>{acc.label}</div>
                <div style={{ fontSize: 11, color: '#8A8F98' }}>{acc.email}</div>
              </div>
              <span style={{ fontSize: 11, color: '#1A4B6E', fontWeight: 500 }}>Use →</span>
            </div>
          ))}
        </div>

        {/* Footer */}
        <div style={{ marginTop: 24, fontSize: 11.5, color: '#8A8F98', textAlign: 'center' }}>
          North Delhi Municipal Corporation · Internal system<br />
          Unauthorized access is prohibited
        </div>
      </div>
    </div>
  )
}