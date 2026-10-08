import assert from 'node:assert/strict'
import test from 'node:test'
import { COMPLAINT_ISSUE_TYPES, complaintIssueTypeLabel } from './complaintIssueTypes.js'

test('exports exactly the four supported complaint issue types', () => {
  assert.deepEqual(COMPLAINT_ISSUE_TYPES, [
    'DOMESTIC_TRASH',
    'ILLEGAL_PARKING',
    'DAMAGED_SIGN',
    'POTHOLE',
  ])
})

test('uses explicit labels and preserves readable historical labels', () => {
  assert.equal(complaintIssueTypeLabel('DOMESTIC_TRASH'), 'Domestic Trash')
  assert.equal(complaintIssueTypeLabel('ILLEGAL_PARKING'), 'Illegal Parking')
  assert.equal(complaintIssueTypeLabel('DAMAGED_SIGN'), 'Damaged Sign')
  assert.equal(complaintIssueTypeLabel('POTHOLE'), 'Pothole')
  assert.equal(complaintIssueTypeLabel('ROAD_CRACK'), 'Road Crack')
})
