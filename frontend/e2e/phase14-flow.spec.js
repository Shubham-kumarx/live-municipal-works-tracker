import { expect, test } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDirectory = path.dirname(fileURLToPath(import.meta.url))
const potholeImage = path.resolve(currentDirectory, '../../artifacts/codex/Pothole.jpg')

function requiredEnvironment(name) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required for the Phase 14 browser test`)
  return value
}

async function login(page, email, password) {
  await page.goto('/login')
  await page.getByPlaceholder('officer@ndmc.gov.in').fill(email)
  await page.getByPlaceholder('Enter your password').fill(password)
  const loginResponse = page.waitForResponse(response => response.url().endsWith('/api/auth/login'))
  await page.getByRole('button', { name: 'Sign in' }).click()
  const response = await loginResponse
  expect(response.status(), await response.text()).toBe(200)
}

test('citizen previews the image and receives real AI analysis', async ({ page }) => {
  await login(page, requiredEnvironment('E2E_CITIZEN_EMAIL'), requiredEnvironment('E2E_CITIZEN_PASSWORD'))
  await expect(page).toHaveURL(/\/map$/)
  await page.goto('/complaints')
  await expect(page.getByText('Report a municipal issue')).toBeVisible()

  await page.locator('input[type="file"]').setInputFiles(potholeImage)
  const preview = page.getByRole('img', { name: 'Selected municipal issue' })
  await expect(preview).toBeVisible()
  await expect.poll(() => preview.evaluate(image => image.naturalWidth)).toBeGreaterThan(0)

  await page.getByRole('button', { name: 'Analyze image' }).click()
  await expect(page.getByText('AI-assisted suggestion', { exact: true })).toBeVisible({ timeout: 120_000 })
  const suggestion = page.locator('.complaint-panel').filter({
    has: page.getByText('AI-assisted suggestion', { exact: true }),
  })
  await expect(suggestion.getByText(/^(HIGH|MEDIUM|LOW) confidence$/)).toBeVisible()
  await expect(suggestion.getByText('Pothole', { exact: true })).toBeVisible()
  await expect(suggestion.getByText(/^\d{1,3}\.\d%$/)).toBeVisible()
  await expect(suggestion.getByText('High', { exact: true })).toBeVisible()
})

test('administrator opens linked work and sees decision-support explanations', async ({ page }) => {
  await login(page, requiredEnvironment('E2E_ADMIN_EMAIL'), requiredEnvironment('E2E_ADMIN_PASSWORD'))
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto('/complaints')
  await expect(page.getByText('Complaint management')).toBeVisible()

  const linkedWork = page.getByRole('link', { name: /Phase 14 Rohini Road Repair/ })
  await expect(linkedWork).toBeVisible()
  await linkedWork.click()
  await expect(page).toHaveURL(/\/map\?projectId=1&wardId=1$/)
  await expect(page.getByText('Phase 14 Rohini Road Repair')).toBeVisible()
  await expect(page.getByText('CRITICAL', { exact: true })).toBeVisible()
  await expect(page.getByText('High delay risk')).toBeVisible()

  const priorityFactors = page.getByLabel('Priority score factors')
  for (const factor of ['Severity', 'Complaint volume', 'Impact', 'Deadline risk', 'Progress gap']) {
    await expect(priorityFactors.getByText(factor, { exact: true })).toBeVisible()
  }
  await expect(priorityFactors.getByText('22.5 points')).toBeVisible()
})
