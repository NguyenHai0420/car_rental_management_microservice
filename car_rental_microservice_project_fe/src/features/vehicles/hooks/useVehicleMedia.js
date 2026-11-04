import { useCallback, useEffect, useState } from 'react'
import { listVehicleDocuments, listVehicleImages } from '../services/vehicle'

export function mediaError(error) {
  if (error?.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (error?.response?.status === 403) return 'Bạn không có quyền quản lý hồ sơ xe.'
  if (error?.response?.status === 404) return 'Không tìm thấy xe hoặc tệp được yêu cầu.'
  if (error?.response?.status === 413) return 'Tệp vượt quá giới hạn 10 MB.'
  if (error?.response?.status === 503) return 'Kho lưu trữ đang gián đoạn. Vui lòng thử lại sau.'
  return error?.response?.data?.message || 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.'
}

async function fetchMedia(vehicleId, canManage, signal) {
    const [images, documents] = await Promise.allSettled([
      listVehicleImages(vehicleId, signal),
      canManage ? listVehicleDocuments(vehicleId, signal) : Promise.resolve([]),
    ])
    return {
      images: images.status === 'fulfilled' ? images.value : [],
      documents: documents.status === 'fulfilled' ? documents.value : [],
      loading: false,
      error: images.status === 'rejected' ? mediaError(images.reason)
        : documents.status === 'rejected' ? mediaError(documents.reason) : '',
    }
}

export function useVehicleMedia(vehicleId, canManage) {
  const [state, setState] = useState({ images: [], documents: [], loading: true, error: '' })
  const refresh = useCallback(async () => {
    setState(await fetchMedia(vehicleId, canManage))
  }, [vehicleId, canManage])

  useEffect(() => {
    const controller = new AbortController()
    fetchMedia(vehicleId, canManage, controller.signal).then((next) => {
      if (!controller.signal.aborted) setState(next)
    })
    return () => controller.abort()
  }, [vehicleId, canManage])

  return { ...state, refresh }
}
