import apiClient from '../../../services/axios-client.js'
import { unwrapApiData } from '../../../services/api-helpers.js'

const BASE = '/api/v1/notifications'

export const NOTIFICATION_STATUS_META = {
  PENDING: { label: 'Chờ gửi', color: 'default' },
  SENDING: { label: 'Đang gửi', color: 'processing' },
  SENT: { label: 'Đã gửi', color: 'success' },
  DELIVERED: { label: 'Đã giao', color: 'green' },
  RETRY: { label: 'Chờ gửi lại', color: 'orange' },
  FAILED: { label: 'Gửi lỗi', color: 'error' },
  DEAD_LETTER: { label: 'Ngừng thử lại', color: 'volcano' },
}

export const SOURCE_EVENT_LABELS = {
  BookingConfirmed: 'Xác nhận đặt xe',
  PaymentConfirmed: 'Biên nhận thanh toán',
  ManualTestEmail: 'Email thủ công',
  GenericEmailRequested: 'Email hệ thống',
}

async function postNotification(path, payload) {
  const response = await apiClient.post(`${BASE}${path}`, payload)
  return {
    httpStatus: response.status,
    code: response.data?.code,
    message: response.data?.message,
    data: unwrapApiData(response),
  }
}

export const notificationService = {
  async listNotifications(params = {}) {
    const cleanParams = Object.fromEntries(
      Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
    )
    const response = await apiClient.get(BASE, { params: cleanParams })
    return unwrapApiData(response)
  },

  async getSummary() {
    const response = await apiClient.get(`${BASE}/summary`)
    return unwrapApiData(response)
  },

  sendBookingConfirmation(payload) {
    return postNotification('/booking-confirmation', payload)
  },

  sendPaymentConfirmation(payload) {
    return postNotification('/payment-confirmation', payload)
  },

  sendGenericEmail(payload) {
    return postNotification('/email', payload)
  },

  async getById(id) {
    const response = await apiClient.get(`${BASE}/${encodeURIComponent(id)}`)
    return unwrapApiData(response)
  },

  async getByReference(referenceId) {
    const response = await apiClient.get(`${BASE}/reference/${encodeURIComponent(referenceId)}`)
    return unwrapApiData(response) || []
  },
}
