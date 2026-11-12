import { useState } from 'react'
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Spin,
  Steps,
  Tag,
  Timeline,
  Typography,
} from 'antd'
import { EyeOutlined } from '@ant-design/icons'
import {
  BOOKING_STATUS_CONFIG,
  bookingService,
  formatDateTime,
  formatVnd,
} from '../../bookings/services/booking'

const { Text } = Typography

const BOOKING_STEPS = [
  { title: 'Chờ thanh toán' },
  { title: 'Đã xác nhận' },
  { title: 'Đang thuê' },
  { title: 'Đã trả xe' },
  { title: 'Hoàn tất' },
]

export function RentalHistoryTable({ rentals = [], loading = false }) {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [selectedBooking, setSelectedBooking] = useState(null)
  const [detailError, setDetailError] = useState(null)

  const handleOpenDetail = async (bookingId) => {
    if (!bookingId) return
    setDrawerOpen(true)
    setDetailLoading(true)
    setDetailError(null)
    setSelectedBooking(null)
    try {
      const data = await bookingService.getBookingById(bookingId)
      setSelectedBooking(data)
    } catch (err) {
      console.error('Failed to fetch booking detail:', err)
      setDetailError(
        err.response?.data?.message ||
        err.message ||
        'Không thể tải chi tiết đơn đặt xe từ Booking Service'
      )
    } finally {
      setDetailLoading(false)
    }
  }

  const handleCloseDrawer = () => {
    setDrawerOpen(false)
    setSelectedBooking(null)
    setDetailError(null)
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '24px', color: 'var(--muted, #64748b)' }}>
        Đang tải lịch sử thuê xe…
      </div>
    )
  }

  if (!rentals || rentals.length === 0) {
    return (
      <div
        className="empty"
        style={{
          textAlign: 'center',
          padding: '32px 16px',
          background: '#fafafa',
          borderRadius: '12px',
        }}
      >
        <p style={{ margin: '0 0 6px', fontWeight: 600 }}>Chưa có lịch sử chuyến đi</p>
        <p className="subtitle" style={{ margin: 0, fontSize: '13.5px', color: 'var(--muted, #64748b)' }}>
          Các chuyến xe bạn đã thuê thành công sẽ được ghi nhận tại đây sau khi hoàn tất hành trình.
        </p>
      </div>
    )
  }

  const statusConfig = {
    ACTIVE: { label: 'Đang diễn ra', color: '#0284c7', bg: '#e0f2fe' },
    COMPLETED: { label: 'Đã hoàn thành', color: '#16a34a', bg: '#dcfce7' },
    CANCELLED: { label: 'Đã hủy', color: '#dc2626', bg: '#fee2e2' },
  }

  const currentStep =
    selectedBooking && BOOKING_STATUS_CONFIG[selectedBooking.status]
      ? BOOKING_STATUS_CONFIG[selectedBooking.status].step
      : 0

  return (
    <>
      <div className="table-responsive" style={{ overflowX: 'auto' }}>
        <table className="table table-hover align-middle" style={{ marginBottom: 0 }}>
          <thead
            style={{
              background: '#f8fafc',
              fontSize: '13px',
              textTransform: 'uppercase',
              letterSpacing: '.03em',
              color: '#64748b',
            }}
          >
            <tr>
              <th scope="col" style={{ padding: '12px 16px' }}>Xe thuê</th>
              <th scope="col" style={{ padding: '12px 16px' }}>Thời gian thuê</th>
              <th scope="col" style={{ padding: '12px 16px' }}>Tổng thanh toán</th>
              <th scope="col" style={{ padding: '12px 16px' }}>Trạng thái</th>
              <th scope="col" style={{ padding: '12px 16px' }}>Đánh giá</th>
              <th scope="col" style={{ padding: '12px 16px', textAlign: 'center' }}>Thao tác</th>
            </tr>
          </thead>
          <tbody style={{ fontSize: '14px' }}>
            {rentals.map((item) => {
              const status = statusConfig[item.status] || {
                label: item.status,
                color: '#64748b',
                bg: '#f1f5f9',
              }
              return (
                <tr key={item.id}>
                  <td style={{ padding: '14px 16px' }}>
                    <strong style={{ display: 'block', color: 'var(--ink, #0f172a)' }}>
                      {item.vehicleInfo || 'Chi tiết xe'}
                    </strong>
                    <small style={{ color: 'var(--muted, #64748b)', fontSize: '12px' }}>
                      Mã đơn: {item.bookingId ? item.bookingId.substring(0, 8) + '...' : '—'}
                    </small>
                  </td>
                  <td style={{ padding: '14px 16px' }}>
                    <div>{item.rentalStart || '—'}</div>
                    <small style={{ color: 'var(--muted, #64748b)' }}>
                      đến {item.rentalEnd || '—'}
                    </small>
                  </td>
                  <td style={{ padding: '14px 16px', fontWeight: 600, color: '#d946ef' }}>
                    {item.totalAmount
                      ? Number(item.totalAmount).toLocaleString('vi-VN') + ' ₫'
                      : '—'}
                  </td>
                  <td style={{ padding: '14px 16px' }}>
                    <span
                      style={{
                        display: 'inline-block',
                        padding: '4px 10px',
                        borderRadius: '999px',
                        fontSize: '12px',
                        fontWeight: 600,
                        color: status.color,
                        background: status.bg,
                      }}
                    >
                      ● {status.label}
                    </span>
                  </td>
                  <td style={{ padding: '14px 16px' }}>
                    {item.rating ? (
                      <div>
                        <span style={{ color: '#eab308' }}>{'★'.repeat(item.rating)}</span>
                        {item.reviewText && (
                          <p style={{ margin: '4px 0 0', fontSize: '12px', color: '#64748b' }}>
                            "{item.reviewText}"
                          </p>
                        )}
                      </div>
                    ) : (
                      <span style={{ color: 'var(--muted, #94a3b8)', fontSize: '13px' }}>
                        Chưa đánh giá
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '14px 16px', textAlign: 'center' }}>
                    <Button
                      type="primary"
                      ghost
                      size="small"
                      icon={<EyeOutlined />}
                      onClick={() => handleOpenDetail(item.bookingId)}
                      disabled={!item.bookingId}
                    >
                      Chi tiết
                    </Button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Drawer xem chi tiết đơn đặt xe từ Booking Service */}
      <Drawer
        title={
          selectedBooking
            ? `Chi tiết đơn đặt xe: ${selectedBooking.bookingCode || selectedBooking.id}`
            : 'Chi tiết đơn đặt xe'
        }
        width={580}
        open={drawerOpen}
        onClose={handleCloseDrawer}
        destroyOnClose
      >
        {detailLoading && (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <Spin size="large" />
            <p style={{ marginTop: 16, color: '#64748b' }}>Đang tải chi tiết đơn từ Booking Service…</p>
          </div>
        )}

        {detailError && (
          <Alert
            type="error"
            showIcon
            message="Không thể tải chi tiết đơn"
            description={detailError}
            style={{ marginBottom: 16 }}
          />
        )}

        {!detailLoading && !detailError && selectedBooking && (
          <div>
            {/* Tiến trình / Trạng thái */}
            {selectedBooking.status === 'CANCELLED' ? (
              <Alert
                type="error"
                showIcon
                message="Đơn thuê này đã bị hủy"
                description={
                  selectedBooking.cancellationReason || 'Khách hàng hoặc hệ thống đã hủy đơn.'
                }
                style={{ marginBottom: 18 }}
              />
            ) : (
              <div style={{ marginBottom: 20 }}>
                <Text type="secondary" style={{ display: 'block', marginBottom: 8, fontSize: 13 }}>
                  Tiến trình đơn đặt xe:
                </Text>
                <Steps current={currentStep} size="small" items={BOOKING_STEPS} />
              </div>
            )}

            <Descriptions
              bordered
              size="small"
              column={1}
              labelStyle={{ width: '180px', fontWeight: 600, background: '#f8fafc' }}
              items={[
                {
                  key: 'code',
                  label: 'Mã đơn đặt xe',
                  children: <Text strong copyable>{selectedBooking.bookingCode || selectedBooking.id}</Text>,
                },
                {
                  key: 'status',
                  label: 'Trạng thái hiện tại',
                  children: (
                    <Tag
                      color={
                        BOOKING_STATUS_CONFIG[selectedBooking.status]?.color || 'default'
                      }
                    >
                      {BOOKING_STATUS_CONFIG[selectedBooking.status]?.label || selectedBooking.status}
                    </Tag>
                  ),
                },
                {
                  key: 'vehicle',
                  label: 'Thông tin xe thuê',
                  children: (
                    <Text strong style={{ color: '#0284c7' }}>
                      {selectedBooking.vehicleInfo || selectedBooking.vehicleId}
                    </Text>
                  ),
                },
                {
                  key: 'pickupTime',
                  label: 'Thời gian nhận xe',
                  children: formatDateTime(selectedBooking.pickupDatetime),
                },
                {
                  key: 'returnTime',
                  label: 'Thời gian trả dự kiến',
                  children: formatDateTime(selectedBooking.returnDatetime),
                },
                {
                  key: 'pickupLoc',
                  label: 'Địa điểm nhận xe',
                  children: selectedBooking.pickupLocation || '—',
                },
                {
                  key: 'returnLoc',
                  label: 'Địa điểm trả xe',
                  children: selectedBooking.returnLocation || '—',
                },
                {
                  key: 'mileage',
                  label: 'Số km nhận / trả',
                  children: `${selectedBooking.pickupMileage ?? '—'} km / ${selectedBooking.returnMileage ?? '—'} km`,
                },
                {
                  key: 'actualReturn',
                  label: 'Thời gian trả thực tế',
                  children: selectedBooking.actualReturnDatetime
                    ? formatDateTime(selectedBooking.actualReturnDatetime)
                    : 'Chưa hoàn tất bàn giao',
                },
                {
                  key: 'total',
                  label: 'Tổng chi phí ước tính',
                  children: (
                    <Text type="danger" strong style={{ fontSize: 15 }}>
                      {formatVnd(selectedBooking.estimatedTotal, selectedBooking.currency)}
                    </Text>
                  ),
                },
                {
                  key: 'deposit',
                  label: 'Tổng tiền thanh toán',
                  children: (
                    <Text strong style={{ color: '#1677ff' }}>
                      {formatVnd(selectedBooking.estimatedTotal, selectedBooking.currency)}
                    </Text>
                  ),
                },
                {
                  key: 'notes',
                  label: 'Ghi chú đơn thuê',
                  children: selectedBooking.notes || 'Không có ghi chú thêm',
                },
              ]}
            />

            {/* Timeline lịch sử chuyển trạng thái */}
            {selectedBooking.statusHistories && selectedBooking.statusHistories.length > 0 && (
              <>
                <Divider orientation="left" style={{ margin: '20px 0 16px', fontSize: 14 }}>
                  Lịch sử tiến trình đơn
                </Divider>
                <Timeline
                  items={selectedBooking.statusHistories.map((h, idx) => ({
                    key: idx,
                    color: h.status === 'CANCELLED' ? 'red' : 'green',
                    children: (
                      <div>
                        <Text strong>
                          {BOOKING_STATUS_CONFIG[h.status]?.label || h.status}
                        </Text>
                        <div style={{ fontSize: 12, color: '#64748b' }}>
                          {formatDateTime(h.changedAt)}
                          {h.changedBy ? ` • ${h.changedBy}` : ''}
                        </div>
                        {h.notes && (
                          <div style={{ fontSize: 12, marginTop: 2, color: '#334155' }}>
                            {h.notes}
                          </div>
                        )}
                      </div>
                    ),
                  }))}
                />
              </>
            )}
          </div>
        )}

        {!detailLoading && !detailError && !selectedBooking && (
          <Empty description="Không có thông tin chi tiết cho đơn thuê này" />
        )}
      </Drawer>
    </>
  )
}

export default RentalHistoryTable