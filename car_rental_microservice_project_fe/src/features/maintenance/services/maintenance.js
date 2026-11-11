import apiClient from '../../../services/axios-client.js'
import { unwrapApiData } from '../../../services/api-helpers.js'
import { vehicleStatuses } from '../../vehicles/vehicle-fields.js'

const BASE = '/api/v1/maintenances'

export function formatVehicleDisplay(vehicle) {
  if (!vehicle) return ''
  const plate = vehicle.licensePlate || vehicle.vin || vehicle.id
  const brand = vehicle.brand || vehicle.model?.brand || ''
  const model = vehicle.modelName || vehicle.model?.modelName || vehicle.model?.name || ''
  const title = `${brand} ${model}`.trim()
  const statusLabel = vehicleStatuses[vehicle.status] || vehicle.status
  return [plate, title, statusLabel ? `(${statusLabel})` : ''].filter(Boolean).join(' — ')
}

export const MAINTENANCE_STATUS_META = {
  SCHEDULED: { label: 'Đã lên lịch', color: 'blue' },
  START_REQUESTED: { label: 'Đang đồng bộ bắt đầu', color: 'gold' },
  IN_PROGRESS: { label: 'Đang bảo dưỡng', color: 'processing' },
  COMPLETE_REQUESTED: { label: 'Đang đồng bộ hoàn tất', color: 'orange' },
  COMPLETED: { label: 'Hoàn thành', color: 'success' },
  OVERDUE: { label: 'Quá hạn', color: 'volcano' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
}

export const PRIORITY_OPTIONS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT']

export const PRIORITY_META = {
  LOW: { label: 'Thấp', color: 'default' },
  MEDIUM: { label: 'Trung bình', color: 'blue' },
  HIGH: { label: 'Cao', color: 'orange' },
  URGENT: { label: 'Khẩn cấp', color: 'red' },
}

export const SERVICE_TYPE_OPTIONS = [
  'OIL_CHANGE',
  'TIRE_ROTATION',
  'BRAKE_SERVICE',
  'BATTERY_CHECK',
  'ENGINE_TUNE',
  'AC_SERVICE',
  'TRANSMISSION',
  'BODY_REPAIR',
  'CLEANING',
  'FULL_INSPECTION',
  'OTHER',
]

export const SERVICE_TYPE_LABELS = {
  OIL_CHANGE: 'Thay nhớt động cơ',
  TIRE_ROTATION: 'Đảo lốp & cân chỉnh',
  BRAKE_SERVICE: 'Bảo dưỡng phanh',
  BATTERY_CHECK: 'Kiểm tra ắc quy',
  ENGINE_TUNE: 'Hiệu chỉnh động cơ',
  AC_SERVICE: 'Bảo dưỡng điều hòa',
  TRANSMISSION: 'Kiểm tra hộp số',
  BODY_REPAIR: 'Sửa chữa thân vỏ',
  CLEANING: 'Vệ sinh xe',
  FULL_INSPECTION: 'Kiểm định tổng quát',
  OTHER: 'Khác',
}

export const INSPECTION_RESULT_OPTIONS = ['PASS', 'WARNING', 'FAIL', 'NOT_CHECKED']

export const INSPECTION_RESULT_META = {
  PASS: { label: 'Đạt', color: 'success' },
  WARNING: { label: 'Lưu ý', color: 'warning' },
  FAIL: { label: 'Không đạt', color: 'error' },
  NOT_CHECKED: { label: 'Chưa kiểm tra', color: 'default' },
}

export const INSPECTION_RESULT_COLOR = {
  PASS: 'success',
  FAIL: 'error',
  WARNING: 'warning',
  NOT_CHECKED: 'default',
}

export const maintenanceService = {
  async listSchedules(params = {}) {
    const cleanParams = Object.fromEntries(
      Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== '')
    )
    const response = await apiClient.get(BASE, { params: cleanParams })
    return unwrapApiData(response)
  },

  async getScheduleById(id) {
    const response = await apiClient.get(`${BASE}/${encodeURIComponent(id)}`)
    return unwrapApiData(response)
  },

  async getSchedulesByVehicle(vehicleId) {
    const response = await apiClient.get(`${BASE}/vehicle/${encodeURIComponent(vehicleId)}`)
    return unwrapApiData(response) || []
  },

  async createSchedule(payload) {
    const response = await apiClient.post(BASE, payload)
    return unwrapApiData(response)
  },

  async updateSchedule(id, payload) {
    const response = await apiClient.put(`${BASE}/${encodeURIComponent(id)}`, payload)
    return unwrapApiData(response)
  },

  async startMaintenance(id) {
    const response = await apiClient.patch(`${BASE}/${encodeURIComponent(id)}/start`)
    return unwrapApiData(response)
  },

  async completeMaintenance(id, payload = {}) {
    const response = await apiClient.patch(`${BASE}/${encodeURIComponent(id)}/complete`, payload)
    return unwrapApiData(response)
  },

  async cancelSchedule(id, cancellationReason) {
    const response = await apiClient.patch(`${BASE}/${encodeURIComponent(id)}/cancel`, {
      cancellationReason,
    })
    return unwrapApiData(response)
  },

  async checkConflicts(vehicleId, params) {
    const response = await apiClient.get(`${BASE}/vehicle/${encodeURIComponent(vehicleId)}/conflicts`, {
      params,
    })
    return response.data?.data ?? response.data
  },

  async listServiceRecords(scheduleId) {
    const response = await apiClient.get(`${BASE}/${encodeURIComponent(scheduleId)}/records`)
    return unwrapApiData(response) || []
  },

  async addServiceRecord(scheduleId, payload) {
    const response = await apiClient.post(`${BASE}/${encodeURIComponent(scheduleId)}/records`, payload)
    return unwrapApiData(response)
  },

  async listChecklists(scheduleId) {
    const response = await apiClient.get(`${BASE}/${encodeURIComponent(scheduleId)}/checklists`)
    return unwrapApiData(response) || []
  },

  async addChecklistItem(scheduleId, payload) {
    const response = await apiClient.post(`${BASE}/${encodeURIComponent(scheduleId)}/checklists`, payload)
    return unwrapApiData(response)
  },

  async updateChecklistItem(scheduleId, checklistId, payload) {
    const response = await apiClient.patch(
      `${BASE}/${encodeURIComponent(scheduleId)}/checklists/${encodeURIComponent(checklistId)}`,
      payload
    )
    return unwrapApiData(response)
  },
}
