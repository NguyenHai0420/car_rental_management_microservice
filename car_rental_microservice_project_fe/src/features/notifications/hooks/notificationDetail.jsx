import { Alert, Button, Card, Descriptions, Drawer, Empty, Space, Tag, Timeline, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { formatDateTime } from '../../../services/api-helpers.js'
import { NOTIFICATION_STATUS_META, SOURCE_EVENT_LABELS } from '../services/notification.js'

const { Text } = Typography

const DELIVERY_EVENT_COLOR = {
  QUEUED: 'blue',
  SENT: 'green',
  DELIVERED: 'green',
  FAILED: 'red',
  BOUNCED: 'red',
  OPENED: 'cyan',
  CLICKED: 'purple',
}

export function NotificationDetail({ notification, httpStatus, onRefresh, refreshing }) {
  if (!notification) {
    return <Empty description="Chưa chọn bản ghi thông báo nào." />
  }

  const statusMeta = NOTIFICATION_STATUS_META[notification.status] || {
    label: notification.status || 'UNKNOWN',
    color: 'default',
  }
  const eventLabel = SOURCE_EVENT_LABELS[notification.sourceEvent] || notification.sourceEvent || '—'
  const events = notification.deliveryEvents || []

  return (
    <Card
      size="small"
      title={
        <Space wrap>
          <span>Chi tiết thông báo</span>
          {httpStatus && (
            <Tag color={httpStatus === 202 ? 'green' : httpStatus === 200 ? 'blue' : 'default'}>
              HTTP {httpStatus}
            </Tag>
          )}
          {notification.idempotentReplay && <Tag color="purple">Yêu cầu trùng (Replay)</Tag>}
          <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
        </Space>
      }
      extra={
        onRefresh && (
          <Button
            size="small"
            icon={<ReloadOutlined />}
            loading={refreshing}
            onClick={() => onRefresh(notification.id)}
          >
            Làm mới trạng thái
          </Button>
        )
      }
    >
      {notification.errorMessage && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message="Chi tiết lỗi gửi email"
          description={notification.errorMessage}
        />
      )}

      <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
        <Descriptions.Item label="Người nhận" span={2}>
          {notification.recipientName
            ? `${notification.recipientName} <${notification.recipient}>`
            : notification.recipient}
        </Descriptions.Item>
        <Descriptions.Item label="Tiêu đề" span={2}>
          {notification.subject || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Sự kiện / Nguồn">
          {eventLabel} {notification.sourceService ? `(${notification.sourceService})` : ''}
        </Descriptions.Item>
        <Descriptions.Item label="Kênh / Số lần thử">
          {notification.channel} · {notification.retryCount ?? 0}/{notification.maxRetries ?? 3} lần
        </Descriptions.Item>
        <Descriptions.Item label="Thời gian tạo">
          {formatDateTime(notification.createdAt)}
        </Descriptions.Item>
        <Descriptions.Item label="Đã gửi / Lịch thử lại">
          {notification.sentAt
            ? formatDateTime(notification.sentAt)
            : notification.nextRetryAt
              ? `Thử lại: ${formatDateTime(notification.nextRetryAt)}`
              : '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Notification ID">
          <Text code copyable>{notification.id}</Text>
        </Descriptions.Item>
        <Descriptions.Item label="Reference ID">
          {notification.referenceId ? <Text code copyable>{notification.referenceId}</Text> : '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Idempotency Key" span={2}>
          <Text code copyable>{notification.idempotencyKey}</Text>
        </Descriptions.Item>
      </Descriptions>

      <div style={{ marginTop: 16 }}>
        <Text strong style={{ display: 'block', marginBottom: 10 }}>
          Nhật ký chuyển phát ({events.length})
        </Text>
        {events.length === 0 ? (
          <Text type="secondary">Chưa có sự kiện chuyển phát.</Text>
        ) : (
          <Timeline
            items={events.map((ev) => ({
              key: ev.id,
              color: DELIVERY_EVENT_COLOR[ev.eventType] || 'gray',
              children: (
                <div>
                  <Space size={8} wrap>
                    <Tag color={DELIVERY_EVENT_COLOR[ev.eventType] || 'default'}>{ev.eventType}</Tag>
                    {ev.provider && <Tag>{ev.provider}</Tag>}
                    <Text type="secondary">{formatDateTime(ev.eventAt)}</Text>
                  </Space>
                  {ev.detail && (
                    <div style={{ marginTop: 4 }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {ev.detail}
                      </Text>
                    </div>
                  )}
                </div>
              ),
            }))}
          />
        )}
      </div>
    </Card>
  )
}

export function NotificationDetailDrawer({ open, onClose, notification, httpStatus, onRefresh, refreshing }) {
  return (
    <Drawer
      title="Chi tiết thông báo & chuyển phát SMTP"
      width={680}
      open={open}
      onClose={onClose}
      destroyOnClose={false}
    >
      <NotificationDetail
        notification={notification}
        httpStatus={httpStatus}
        onRefresh={onRefresh}
        refreshing={refreshing}
      />
    </Drawer>
  )
}
