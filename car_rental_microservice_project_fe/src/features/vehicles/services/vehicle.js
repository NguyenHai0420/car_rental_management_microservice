import apiClient from '../../../services/axios-client.js'

const base = (vehicleId) => `/api/v1/vehicles/${encodeURIComponent(vehicleId)}`
const data = (response) => response.data.data

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
