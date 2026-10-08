import { expect, test } from '@playwright/test'

const citizen = {
  email: 'citizen@example.test',
  fullName: 'Citizen Tester',
  role: 'CITIZEN',
  wardId: 1,
}

const municipalAdmin = {
  email: 'admin@example.test',
  fullName: 'Municipal Admin',
  role: 'MUNICIPAL_ADMIN',
  wardId: 1,
}

const ownComplaint = {
  id: 71,
  reportingUserId: 12,
  reportingUserName: 'Citizen Tester',
  description: 'Large pothole near the school entrance',
  locationAddress: 'School Road',
  finalIssueType: 'POTHOLE',
  finalSeverity: 'HIGH',
  status: 'SUBMITTED',
  predictionState: 'MANUAL',
  createdAt: '2026-10-07T10:30:00',
}

async function mockSession(page, user = citizen) {
  await page.route('**/api/auth/session', route => route.fulfill({ json: user }))
}

test('mobile menu button opens and closes the sidebar', async ({ page }) => {
  await page.setViewportSize({ width: 600, height: 800 })
  await mockSession(page)
  await page.goto('/complaints')

  const toggle = page.getByRole('button', { name: 'Open navigation' })
  const sidebar = page.locator('#app-sidebar')
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(sidebar).not.toHaveClass(/sidebar-open/)

  await toggle.click()
  const closeToggle = page.locator('.mobile-nav-toggle')
  await expect(closeToggle).toHaveAttribute('aria-expanded', 'true')
  await expect(sidebar).toHaveClass(/sidebar-open/)

  await closeToggle.click()
  await expect(toggle).toHaveAttribute('aria-expanded', 'false')
  await expect(sidebar).not.toHaveClass(/sidebar-open/)

  await toggle.click()
  await sidebar.getByRole('link', { name: 'My Complaints' }).click()
  await expect(sidebar).not.toHaveClass(/sidebar-open/)
  await expect(page).toHaveURL(/\/complaints$/)
})

test('desktop sidebar remains visible without the mobile toggle', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 })
  await mockSession(page)
  await page.goto('/complaints')

  await expect(page.locator('#app-sidebar')).toBeVisible()
  await expect(page.locator('.mobile-nav-toggle')).toBeHidden()
})

test('citizen sees only the authenticated-user complaint data source', async ({ page }) => {
  const complaintRequests = []
  page.on('request', request => {
    if (request.url().includes('/api/complaints')) complaintRequests.push(request.url())
  })
  await mockSession(page)
  await page.route('**/api/complaints/my', route => route.fulfill({ json: [ownComplaint] }))

  await page.goto('/complaints')

  await expect(page.getByRole('heading', { name: 'My Complaints' })).toBeVisible()
  const table = page.getByRole('table')
  await expect(table.getByText('Pothole', { exact: true })).toBeVisible()
  await expect(table.getByText('Large pothole near the school entrance')).toBeVisible()
  await expect(table.getByText('School Road')).toBeVisible()
  await expect(table.getByText('Submitted', { exact: true })).toBeVisible()
  expect(complaintRequests.some(url => url.endsWith('/api/complaints/my'))).toBe(true)
  expect(complaintRequests.some(url => url.endsWith('/api/complaints'))).toBe(false)
})

test('citizen with no complaints sees only the empty state', async ({ page }) => {
  await mockSession(page)
  await page.route('**/api/complaints/my', route => route.fulfill({ json: [] }))

  await page.goto('/complaints')

  await expect(page.getByText('No complaints reported yet.', { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.getByText('Access denied', { exact: true })).toHaveCount(0)
})

test('municipal admin retains complaint management and linking controls', async ({ page }) => {
  await mockSession(page, municipalAdmin)
  await page.route('**/api/complaints/linkable-projects', route => route.fulfill({
    json: [{ id: 9, projectName: 'Ward Road Repair', wardId: 1 }],
  }))
  await page.route('**/api/complaints', route => route.fulfill({ json: [ownComplaint] }))

  await page.goto('/complaints')

  await expect(page.getByRole('heading', { name: 'Complaint management' })).toBeVisible()
  await expect(page.getByRole('table').getByText('Pothole', { exact: true })).toBeVisible()
  await expect(page.getByRole('option', { name: '#9 Ward Road Repair' })).toBeAttached()
  await expect(page.getByRole('button', { name: 'Link' })).toBeDisabled()
  await expect(page.getByRole('link', { name: 'Complaints' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'My Complaints' })).toHaveCount(0)
})

test('management request error does not also render the empty state', async ({ page }) => {
  await mockSession(page, municipalAdmin)
  await page.route('**/api/complaints/linkable-projects', route => route.fulfill({ json: [] }))
  await page.route('**/api/complaints', route => route.fulfill({
    status: 403,
    contentType: 'application/json',
    body: JSON.stringify({ status: 403, message: 'Access denied' }),
  }))

  await page.goto('/complaints')

  await expect(page.getByRole('alert')).toContainText('Access denied')
  await expect(page.getByText('No complaints are available.', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('table')).toHaveCount(0)
})
