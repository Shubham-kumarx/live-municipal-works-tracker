import assert from 'node:assert/strict'
import test from 'node:test'

import { apiErrorMessage } from './errors.js'

test('API error parser prefers a trimmed standard response message', () => {
  assert.equal(apiErrorMessage({ response: { data: {
    message: '  Project not found  ', fieldErrors: { title: 'Title is required' },
  } } }, 'Fallback'), 'Project not found')
})

test('API error parser uses the first nonblank field error', () => {
  assert.equal(apiErrorMessage({ response: { data: { fieldErrors: {
    title: ' ', progress: '  Progress must be at most 100  ',
  } } } }, 'Fallback'), 'Progress must be at most 100')
})

test('API error parser safely falls back for absent or malformed responses', () => {
  assert.equal(apiErrorMessage(null, 'Unable to load'), 'Unable to load')
  assert.equal(apiErrorMessage({ response: { data: 'gateway failure' } }, 'Unable to load'),
    'Unable to load')
  assert.equal(apiErrorMessage({ response: { data: { message: 500, fieldErrors: [] } } },
    'Unable to load'), 'Unable to load')
})
