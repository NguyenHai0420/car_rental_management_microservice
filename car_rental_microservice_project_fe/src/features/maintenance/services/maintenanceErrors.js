import { extractApiError, formatDateTime } from '../../../services/api-helpers.js'

const ISO_UTC_REGEX = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})/g

/** Convert raw ISO-8601 timestamps inside backend error strings into readable local times. */
export function formatTimestampsInText(text) {
  if (!text) return ''
  return String(text).replace(ISO_UTC_REGEX, (match) => formatDateTime(match))
}

export function isConflictError(error) {
  const status = error?.response?.status
  const raw = String(error?.response?.data?.message || error?.message || '').toLowerCase()
  return status === 409 || raw.includes('conflicting') || raw.includes('conflict')
}

export function formatMaintenanceError(error, fallbackMessage = 'Không thể thực hiện thao tác.') {
  if (typeof error === 'string') {
    return { title: fallbackMessage, description: error, detail: null, status: null, isConflict: false }
  }

  const status = error?.response?.status ?? null
  const rawMessage = error?.response?.data?.message || error?.response?.data?.error || error?.message || ''
  const localizedRaw = formatTimestampsInText(rawMessage)
  const fallbackFormatted = extractApiError(error, fallbackMessage)
  const lower = rawMessage.toLowerCase()

  if (status === 409 || isConflictError(error)) {
    if (lower.includes('conflicting booking hold')) {
      return {
        title: 'Xe bị trùng lịch đặt thuê (Booking)',
        description: `Xe đã được khách đặt/giữ chỗ trong khung giờ này. Chi tiết: ${localizedRaw}`,
        detail: null,
        status: status || 409,
        isConflict: true,
      }
    }
    if (lower.includes('conflicting maintenance') || lower.includes('conflicting') && lower.includes('maintenance')) {
      return {
        title: 'Xe bị trùng lịch bảo dưỡng',
        description: `Xe đã có lịch bảo dưỡng khác giao nhau với khung giờ bạn chọn. Chi tiết: ${localizedRaw}`,
        detail: null,
        status: status || 409,
        isConflict: true,
      }
    }
    if (lower.includes('decommissioned')) {
      return {
        title: 'Xe đã ngừng hoạt động',
        description: 'Không thể lập lịch bảo dưỡng cho xe đang ở trạng thái Ngừng hoạt động.',
        detail: localizedRaw,
        status: status || 409,
        isConflict: true,
      }
    }
    if (lower.includes('while vehicle') && (lower.includes('reserved') || lower.includes('rented'))) {
      return {
        title: 'Xe đang được đặt hoặc đang cho thuê',
        description: 'Trạng thái vật lý hiện tại của xe đang là Đã đặt (RESERVED) hoặc Đang thuê (RENTED), chưa thể tạo lịch hoặc bắt đầu bảo dưỡng.',
        detail: localizedRaw,
        status: status || 409,
        isConflict: true,
      }
    }
    if (lower.includes('in_maintenance')) {
      return {
        title: 'Xe đang trong trạng thái bảo dưỡng',
        description: localizedRaw,
        detail: null,
        status: status || 409,
        isConflict: true,
      }
    }
    return {
      title: 'Xung đột trạng thái / Trùng lịch',
      description: localizedRaw || fallbackFormatted,
      detail: null,
      status: status || 409,
      isConflict: true,
    }
  }

  if (lower.includes('without at least one inspection checklist item')) {
    return {
      title: 'Chưa thể hoàn tất: thiếu biên bản kiểm định',
      description: 'Vui lòng mở tab "Kiểm định an toàn", thêm ít nhất 1 hạng mục kiểm tra và đánh giá "Đạt" hết trước khi bấm Hoàn tất.',
      detail: localizedRaw,
      status,
      isConflict: false,
    }
  }

  if (lower.includes('unresolved or failed inspection checklist items:')) {
    const itemsPart = rawMessage.split('unresolved or failed inspection checklist items:')[1]?.trim() || localizedRaw
    const viItems = itemsPart
      .replaceAll('(FAIL)', '(Không đạt)')
      .replaceAll('(NOT_CHECKED)', '(Chưa kiểm tra)')
    return {
      title: 'Chưa thể hoàn tất: kiểm định an toàn chưa đạt',
      description: `Các hạng mục sau chưa đạt yêu cầu: ${viItems}. Vui lòng chuyển sang tab "Kiểm định an toàn" để đánh giá "Đạt" trước khi hoàn tất.`,
      detail: null,
      status,
      isConflict: false,
    }
  }

  if (lower.includes('invalid time interval') || lower.includes('strictly after')) {
    return {
      title: 'Khung thời gian không hợp lệ',
      description: 'Thời gian kết thúc phải lớn hơn thời gian bắt đầu.',
      detail: localizedRaw,
      status,
      isConflict: false,
    }
  }

  if (status === 404 && lower.includes('maintenance standard not found')) {
    return {
      title: 'Không tìm thấy Standard UUID',
      description: 'Mã Standard UUID bạn nhập không tồn tại trong cơ sở dữ liệu. Hãy để trống ô Standard UUID nếu không gắn định mức chuẩn.',
      detail: localizedRaw,
      status,
      isConflict: false,
    }
  }

  if (status === 404 && lower.includes('vehicle not found')) {
    return {
      title: 'Không tìm thấy xe trong hệ thống',
      description: 'Vehicle UUID không tồn tại hoặc xe đã bị xóa bên Quản lý xe.',
      detail: localizedRaw,
      status,
      isConflict: false,
    }
  }

  if (status === 502 || status === 503) {
    return {
      title: 'Lỗi kết nối giữa các service',
      description: localizedRaw || fallbackFormatted,
      detail: 'Kiểm tra các service liên quan (vehicle-service, maintenance-service, eureka) đang chạy và cấu hình đúng token nội bộ.',
      status,
      isConflict: false,
    }
  }

  if (status === 401 || status === 403) {
    return {
      title: status === 401 ? 'Phiên đăng nhập hết hạn hoặc thiếu xác thực' : 'Không đủ quyền thực hiện',
      description: fallbackFormatted,
      detail: null,
      status,
      isConflict: false,
    }
  }

  return {
    title: fallbackMessage,
    description: localizedRaw ? formatTimestampsInText(fallbackFormatted) : fallbackFormatted,
    detail: null,
    status,
    isConflict: false,
  }
}

