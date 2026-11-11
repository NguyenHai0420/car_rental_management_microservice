import dayjs from 'dayjs'

export function unwrapApiData(response) {
  return response?.data?.data ?? response?.data
}

export function extractApiError(error, fallbackMessage = 'Không thể hoàn tất yêu cầu.') {
  const status = error?.response?.status
  const payload = error?.response?.data

  let detail = ''
  if (payload?.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) {
    const entries = Object.entries(payload.data)
      .filter(([, val]) => val !== null && val !== undefined && String(val).trim() !== '')
      .map(([field, val]) => `${field}: ${val}`)
    if (entries.length > 0) {
      detail = ` (${entries.join('; ')})`
    }
  }

  const baseMessage = payload?.message || payload?.error || error?.userMessage || error?.message || fallbackMessage

  if (status === 401) {
    return `401 Unauthorized: ${baseMessage} (Kiểm tra đăng nhập Keycloak và X-Gateway-Secret).`
  }
  if (status === 403) {
    return `403 Forbidden: ${baseMessage} (Backend mặc định yêu cầu vai trò ADMIN hoặc STAFF).`
  }
  if (status) {
    return `HTTP ${status}: ${baseMessage}${detail}`
  }
  return `${baseMessage}${detail}`
}

export function formatDateTime(value) {
  if (!value) return '—'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('DD/MM/YYYY HH:mm:ss') : String(value)
}

export function formatCurrency(amount, currency = 'VND') {
  if (amount === null || amount === undefined || amount === '') return '—'
  return `${Number(amount).toLocaleString('vi-VN')} ${currency || 'VND'}`
}

export function newUuid() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

