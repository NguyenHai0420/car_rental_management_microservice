import apiClient from '../../../services/axios-client.js'

const customers = '/api/v1/customers'
const base = (id) => `${customers}/${encodeURIComponent(id)}`
const data = (response) => response.data.data

// 1. Khách hàng lấy thông tin cá nhân của chính mình (tự tạo nếu mới)
export const getMyProfile = (signal) =>
  apiClient.get(`${customers}/me`, { signal }).then(data)

// 2. Khách hàng cập nhật thông tin cá nhân
export const updateMyProfile = (fields) =>
  apiClient.put(`${customers}/me`, fields).then(data)

// 3. Khách hàng nộp/cập nhật thông tin bằng lái xe
export const submitDriverLicense = (fields) =>
  apiClient.post(`${customers}/me/license`, fields).then(data)

// 4. Khách hàng xem bằng lái của chính mình
export const getMyDriverLicense = (signal) =>
  apiClient.get(`${customers}/me/license`, { signal }).then(data)

// 5. Staff/Admin: Lấy danh sách toàn bộ khách hàng
export const getAllCustomers = (signal) =>
  apiClient.get(customers, { signal }).then(data)

// 6. Staff/Admin: Xem chi tiết khách hàng theo ID
export const getCustomerById = (id, signal) =>
  apiClient.get(base(id), { signal }).then(data)

// 7. Staff: Duyệt hoặc từ chối bằng lái xe
export const verifyDriverLicense = (id, fields) =>
  apiClient.put(`${base(id)}/license/verify`, fields).then(data)

// 8. Kiểm tra điều kiện thuê xe (Booking Eligibility)
export const checkCustomerEligibility = (id, signal) =>
  apiClient.get(`${base(id)}/eligibility`, { signal }).then((res) => res.data)

// 9. Khách hàng xem lịch sử các chuyến thuê xe của mình
export const getMyRentalHistory = (signal) =>
  apiClient.get(`${customers}/me/rentals`, { signal }).then(data)

// 10. Staff/Admin: Xem lịch sử thuê xe của một khách hàng cụ thể
export const getCustomerRentalHistory = (id, signal) =>
  apiClient.get(`${base(id)}/rentals`, { signal }).then(data)

// 11. Tải ảnh lên MinIO Storage
export const uploadCustomerImage = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post(`${customers}/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(data)
}

// Helper định dạng thông báo lỗi từ backend
export function customerError(error) {
  if (error?.response?.status === 401) {
    return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  }
  if (error?.response?.status === 403) {
    return 'Bạn không có quyền thực hiện thao tác này (yêu cầu quyền STAFF hoặc ADMIN).'
  }
  if (error?.response?.status === 404) {
    return error?.response?.data?.message || 'Không tìm thấy thông tin khách hàng.'
  }
  return error?.response?.data?.message || error?.message || 'Có lỗi xảy ra, vui lòng thử lại.'
}
