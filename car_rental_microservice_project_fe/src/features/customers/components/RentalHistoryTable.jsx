export function RentalHistoryTable({ rentals = [], loading = false }) {
  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '24px', color: 'var(--muted, #64748b)' }}>
        Đang tải lịch sử thuê xe…
      </div>
    )
  }

  if (!rentals || rentals.length === 0) {
    return (
      <div className="empty" style={{ textAlign: 'center', padding: '32px 16px', background: '#fafafa', borderRadius: '12px' }}>
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

  return (
    <div className="table-responsive" style={{ overflowX: 'auto' }}>
      <table className="table table-hover align-middle" style={{ marginBottom: 0 }}>
        <thead style={{ background: '#f8fafc', fontSize: '13px', textTransform: 'uppercase', letterSpacing: '.03em', color: '#64748b' }}>
          <tr>
            <th scope="col" style={{ padding: '12px 16px' }}>Xe thuê</th>
            <th scope="col" style={{ padding: '12px 16px' }}>Thời gian thuê</th>
            <th scope="col" style={{ padding: '12px 16px' }}>Tổng thanh toán</th>
            <th scope="col" style={{ padding: '12px 16px' }}>Trạng thái</th>
            <th scope="col" style={{ padding: '12px 16px' }}>Đánh giá</th>
          </tr>
        </thead>
        <tbody style={{ fontSize: '14px' }}>
          {rentals.map((item) => {
            const status = statusConfig[item.status] || { label: item.status, color: '#64748b', bg: '#f1f5f9' }
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
                  <small style={{ color: 'var(--muted, #64748b)' }}>đến {item.rentalEnd || '—'}</small>
                </td>
                <td style={{ padding: '14px 16px', fontWeight: 600, color: '#d946ef' }}>
                  {item.totalAmount ? Number(item.totalAmount).toLocaleString('vi-VN') + ' ₫' : '—'}
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
                    <span style={{ color: 'var(--muted, #94a3b8)', fontSize: '13px' }}>Chưa đánh giá</span>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export default RentalHistoryTable

