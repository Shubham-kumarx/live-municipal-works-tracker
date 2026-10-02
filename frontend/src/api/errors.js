export function apiErrorMessage(error, fallback) {
  const response = error?.response?.data
  if (typeof response?.message === 'string' && response.message.trim()) {
    return response.message.trim()
  }
  if (response?.fieldErrors && typeof response.fieldErrors === 'object') {
    const fieldMessage = Object.values(response.fieldErrors)
      .find(message => typeof message === 'string' && message.trim())
    if (fieldMessage) return fieldMessage.trim()
  }
  return fallback
}
