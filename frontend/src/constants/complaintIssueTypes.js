export const COMPLAINT_ISSUE_TYPES = Object.freeze([
  'DOMESTIC_TRASH',
  'ILLEGAL_PARKING',
  'DAMAGED_SIGN',
  'POTHOLE',
])

const ISSUE_TYPE_LABELS = Object.freeze({
  DOMESTIC_TRASH: 'Domestic Trash',
  ILLEGAL_PARKING: 'Illegal Parking',
  DAMAGED_SIGN: 'Damaged Sign',
  POTHOLE: 'Pothole',
})

export function complaintIssueTypeLabel(value) {
  if (!value) return 'Unavailable'
  return ISSUE_TYPE_LABELS[value]
    || value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase())
}
