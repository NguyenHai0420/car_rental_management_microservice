import apiClient from '../../../services/axios-client.js'

const vehicles = '/api/v1/vehicles'
const base = (vehicleId) => `${vehicles}/${encodeURIComponent(vehicleId)}`
const data = (response) => response.data.data

export const listVehicles = (params, signal) => apiClient.get(vehicles, { params, signal }).then(data)
// Public: AVAILABLE vehicles with their MinIO cover image, no login needed.
export const listShowroom = (params, signal) => apiClient.get(`${vehicles}/showroom`, { params, signal }).then(data)
export const getVehicle = (vehicleId, signal) => apiClient.get(base(vehicleId), { signal }).then(data)
export const createVehicle = (fields) => apiClient.post(vehicles, fields).then(data)
export const updateVehicle = (vehicleId, fields) => apiClient.put(base(vehicleId), fields).then(data)
export const deleteVehicle = (vehicleId) => apiClient.delete(base(vehicleId)).then(data)

export const lookupVehicleByLicensePlate = (licensePlate, signal) =>
  apiClient.get(`${vehicles}/lookup`, { params: { licensePlate }, signal }).then(data)

export const changeVehicleStatus = (vehicleId, fields) =>
  apiClient.patch(`${base(vehicleId)}/status`, fields).then(data)
export const listVehicleStatusHistory = (vehicleId, signal) =>
  apiClient.get(`${base(vehicleId)}/status-history`, { signal }).then(data)

export const listVehicleCategories = (signal) => apiClient.get(`${vehicles}/categories`, { signal }).then(data)
export const createVehicleCategory = (fields) => apiClient.post(`${vehicles}/categories`, fields).then(data)
export const listVehicleModels = (signal) => apiClient.get(`${vehicles}/models`, { signal }).then(data)
export const createVehicleModel = (fields) => apiClient.post(`${vehicles}/models`, fields).then(data)

export function vehicleError(error) {
  if (error?.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error?.response?.status === 403) return 'Tài khoản cần quyền ADMIN hoặc STAFF.'
  return error?.response?.data?.message || 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
}

export const listVehicleImages = (vehicleId, signal) =>
  apiClient.get(`${base(vehicleId)}/images`, { signal }).then(data)

export function uploadVehicleImage(vehicleId, file, imageType = 'OTHER', options = {}) {
  const form = new FormData()
  form.append('file', file)
  form.append('imageType', imageType)
  form.append('isPrimary', String(options.isPrimary ?? false))
  form.append('sortOrder', String(options.sortOrder ?? 0))
  return apiClient.post(`${base(vehicleId)}/images`, form).then(data)
}

export const deleteVehicleImage = (vehicleId, imageId) =>
  apiClient.delete(`${base(vehicleId)}/images/${encodeURIComponent(imageId)}`).then(data)

export const listVehicleDocuments = (vehicleId, signal) =>
  apiClient.get(`${base(vehicleId)}/documents`, { signal }).then(data)

export function uploadVehicleDocument(vehicleId, file, fields = {}) {
  const form = new FormData()
  form.append('file', file)
  form.append('docType', fields.docType ?? 'OTHER')
  for (const name of ['docNumber', 'issueDate', 'expiryDate']) {
    if (fields[name]) form.append(name, fields[name])
  }
  return apiClient.post(`${base(vehicleId)}/documents`, form).then(data)
}

export const getVehicleDocumentUrl = (vehicleId, documentId) =>
  apiClient.get(`${base(vehicleId)}/documents/${encodeURIComponent(documentId)}/url`).then(data)

export const deleteVehicleDocument = (vehicleId, documentId) =>
  apiClient.delete(`${base(vehicleId)}/documents/${encodeURIComponent(documentId)}`).then(data)
