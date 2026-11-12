import { Alert, Tag, Typography } from 'antd'
import { formatDateTime } from '../../../services/api-helpers.js'
import { MAINTENANCE_STATUS_META } from '../services/maintenance.js'

const { Text } = Typography

const HOLD_OWNER_LABEL = {
  BOOKING: 'Đặt xe (Booking)',
  MAINTENANCE: 'Giữ chỗ bảo dưỡng',
}

const HOLD_STATUS_LABEL = {
  HELD: 'Đang giữ tạm',
  CONFIRMED: 'Đã chốt',
}

export function ErrorAlertBanner({ error, onClose, style }) {
  if (!error) return null
  const normalized = typeof error === 'string' ? { title: 'Không thể thực hiện thao tác', description: error } : error

  return (
    <Alert
      type="error"
      showIcon
      closable={Boolean(onClose)}
      onClose={onClose}
      style={style}
      title={<strong>{normalized.title || 'Đã xảy ra lỗi'}</strong>}
      message={<strong>{normalized.title || 'Đã xảy ra lỗi'}</strong>}
      description={
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, width: '100%' }}>
          {normalized.description && <span>{normalized.description}</span>}
          {normalized.detail && normalized.detail !== normalized.description && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Chi tiết kỹ thuật: {normalized.detail}
            </Text>
          )}
        </div>
      }
    />
  )
}

export function ConflictSummaryAlert({ conflictResult, onClose, style }) {
  if (!conflictResult) return null

  const schedules = conflictResult.conflictingSchedules || []
  const holds = conflictResult.conflictingHolds || []
  const vehicleBlocked = conflictResult.vehicleStatus === 'DECOMMISSIONED'
  const hasConflict = Boolean(conflictResult.hasConflict)
  const totalItems = schedules.length + holds.length

  let summaryMessage = 'Khung giờ khả dụng — không bị trùng lịch'
  if (hasConflict) {
    if (totalItems > 0) {
      const parts = []
      if (schedules.length > 0) parts.push(`${schedules.length} lịch bảo dưỡng`)
      if (holds.length > 0) parts.push(`${holds.length} giữ chỗ / đặt xe`)
      summaryMessage = `Phát hiện trùng lịch (${parts.join(' và ')})`
    } else if (vehicleBlocked) {
      summaryMessage = 'Xe đang ở trạng thái Ngừng hoạt động (DECOMMISSIONED)'
    } else {
      summaryMessage = 'Phát hiện xung đột lịch trên xe'
    }
  }

  return (
    <Alert
      type={hasConflict ? 'warning' : 'success'}
      showIcon
      closable={Boolean(onClose)}
      onClose={onClose}
      style={style}
      title={<strong>{summaryMessage}</strong>}
      message={<strong>{summaryMessage}</strong>}
      description={
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, width: '100%' }}>
          <div>
            Khoảng kiểm tra: <strong>{formatDateTime(conflictResult.requestedStartAt)}</strong> →{' '}
            <strong>{formatDateTime(conflictResult.requestedEndAt)}</strong>
          </div>

          {vehicleBlocked && (
            <div style={{ color: '#cf1322' }}>
              • Trạng thái xe hiện tại là DECOMMISSIONED — không thể tạo mới hoặc điều phối bảo dưỡng.
            </div>
          )}

          {schedules.map((sch) => {
            const statusMeta = MAINTENANCE_STATUS_META[sch.status] || { label: sch.status, color: 'default' }
            return (
              <div key={`sch-${sch.id}`} style={{ fontSize: 13 }}>
                • <Tag color="orange">Lịch bảo dưỡng</Tag>
                <strong>
                  {formatDateTime(sch.scheduledStartAt)} → {formatDateTime(sch.scheduledEndAt)}
                </strong>{' '}
                <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
                {sch.assignedTo ? ` · ${sch.assignedTo}` : ''}
                {sch.notes ? ` · "${sch.notes}"` : ''}{' '}
                <Text type="secondary" code style={{ fontSize: 11 }}>
                  {String(sch.id).slice(0, 8)}
                </Text>
              </div>
            )
          })}

          {holds.map((hold) => (
            <div key={`hold-${hold.holdId}`} style={{ fontSize: 13 }}>
              • <Tag color={hold.ownerType === 'BOOKING' ? 'red' : 'gold'}>
                {HOLD_OWNER_LABEL[hold.ownerType] || hold.ownerType}
              </Tag>
              <strong>
                {formatDateTime(hold.startAt)} → {formatDateTime(hold.endAt)}
              </strong>{' '}
              <Tag>{HOLD_STATUS_LABEL[hold.status] || hold.status}</Tag>
              {hold.reason ? ` · ${hold.reason}` : ''}{' '}
              <Text type="secondary" code style={{ fontSize: 11 }}>
                {String(hold.holdId).slice(0, 8)}
              </Text>
            </div>
          ))}

          {conflictResult.partialWarning && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Lưu ý: {conflictResult.partialWarning}
            </Text>
          )}
        </div>
      }
    />
  )
}

