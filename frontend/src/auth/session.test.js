import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'

import { clearSession, getSession, landingPath, setSessionUser } from './session.js'

class MemoryStorage {
  #values = new Map()

  getItem(key) { return this.#values.has(key) ? this.#values.get(key) : null }
  setItem(key, value) { this.#values.set(key, String(value)) }
  removeItem(key) { this.#values.delete(key) }
}

beforeEach(() => {
  globalThis.localStorage = new MemoryStorage()
})

test('session returns null for absent, malformed, or role-free stored users', () => {
  assert.equal(getSession(), null)
  localStorage.setItem('user', '{bad json')
  assert.equal(getSession(), null)
  localStorage.setItem('user', JSON.stringify({ email: 'citizen@example.com' }))
  assert.equal(getSession(), null)
})

test('session persists only the supported user fields and can be cleared', () => {
  setSessionUser({
    email: 'officer@example.com', fullName: 'Ward Officer', role: 'WARD_OFFICER',
    wardId: 7, password: 'must-not-be-stored',
  })

  assert.deepEqual(getSession(), { user: {
    email: 'officer@example.com', fullName: 'Ward Officer', role: 'WARD_OFFICER', wardId: 7,
  } })
  assert.equal(localStorage.getItem('user').includes('password'), false)
  clearSession()
  assert.equal(getSession(), null)
})

test('landing paths follow administrative and citizen roles', () => {
  assert.equal(landingPath('MUNICIPAL_ADMIN'), '/dashboard')
  assert.equal(landingPath('WARD_OFFICER'), '/dashboard')
  assert.equal(landingPath('FIELD_WORKER'), '/map')
  assert.equal(landingPath('CITIZEN'), '/map')
})
