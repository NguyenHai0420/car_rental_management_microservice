import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { extractApiError, formatDateTime } from '../../../services/api-helpers.js'
import { NotificationForm } from '../components/NotificationForm.jsx'
import { NotificationDetail, NotificationDetailDrawer } from '../hooks/notificationDetail.jsx'
import {
  NOTIFICATION_STATUS_META,
  SOURCE_EVENT_LABELS,
  notificationService,
} from '../services/notification.js'

const { Title, Text } = Typography

const INITIAL_SUMMARY = {
  total: 0,
  pending: 0,
  sending: 0,
  sent: 0,
  delivered: 0,
  retry: 0,
  failed: 0,
  deadLetter: 0,
}

export function NotificationPage() {
  const { message } = App.useApp()
  const [activeTab, setActiveTab] = useState('history')
  const [summary, setSummary] = useState(INITIAL_SUMMARY)
  const [items, setItems] = useState([])
  const [loadingList, setLoadingList] = useState(false)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [filters, setFilters] = useState({ recipient: '', status: '', sourceEvent: '' })
  const [lookupUuid, setLookupUuid] = useState('')
  const [lookupMode, setLookupMode] = useState('reference')
  const [loadingLookup, setLoadingLookup] = useState(false)
  const [composedResult, setComposedResult] = useState(null)
  const [composedHttpStatus, setComposedHttpStatus] = useState(null)
  const [drawerItem, setDrawerItem] = useState(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [refreshingDetail, setRefreshingDetail] = useState(false)
  const [serviceError, setServiceError] = useState(null)

  const handleServiceError = useCallback((reason) => {
    const status = reason?.response?.status
    const rawMsg = reason?.response?.data?.message || reason?.message || ''
    if (status === 500 || status === 502 || status === 503) {
      setServiceError({
        title: 'Chưa kết nối được Notification Service',
        description:
          'Dịch vụ thông báo & email (notification-service cổng 8086) chưa được khởi động hoặc ApiGateway không thể kết nối tới cổng này. Vui lòng bật notification-service và MySQL (cổng 3307).',
      })
    } else {
      setServiceError({
        title: 'Không tải được lịch sử thông báo',
        description: extractApiError(reason, 'Lỗi kết nối tới máy chủ.'),
      })
    }
  }, [])

  const loadData = useCallback(
    async (page = 0, size = 10, activeFilters = filters) => {
      setLoadingList(true)
      try {
        const [listRes, summaryRes] = await Promise.allSettled([
          notificationService.listNotifications({
            recipient: activeFilters.recipient?.trim() || undefined,
            status: activeFilters.status || undefined,
            sourceEvent: activeFilters.sourceEvent || undefined,
            page,
            size,
          }),
          notificationService.getSummary(),
        ])

        if (listRes.status === 'fulfilled') {
          const pageData = listRes.value
          setItems(pageData?.content || [])
          setPagination({
            current: (pageData?.number ?? page) + 1,
            pageSize: pageData?.size ?? size,
            total: pageData?.totalElements ?? 0,
          })
          setServiceError(null)
        } else {
          handleServiceError(listRes.reason)
          message.error(extractApiError(listRes.reason, 'Không tải được lịch sử thông báo.'))
        }

        if (summaryRes.status === 'fulfilled' && summaryRes.value) {
          setSummary(summaryRes.value)
        }
      } finally {
        setLoadingList(false)
      }
    },
    [filters, message, handleServiceError]
  )

  useEffect(() => {
    let active = true

    async function bootstrap() {
      setLoadingList(true)
      try {
        const [listRes, summaryRes] = await Promise.allSettled([
          notificationService.listNotifications({ page: 0, size: 10 }),
          notificationService.getSummary(),
        ])
        if (!active) return
        if (listRes.status === 'fulfilled') {
          const pageData = listRes.value
          setItems(pageData?.content || [])
          setPagination({
            current: (pageData?.number ?? 0) + 1,
            pageSize: pageData?.size ?? 10,
            total: pageData?.totalElements ?? 0,
          })
          setServiceError(null)
        } else {
          handleServiceError(listRes.reason)
          message.error(extractApiError(listRes.reason, 'Không tải được lịch sử thông báo.'))
        }
        if (summaryRes.status === 'fulfilled' && summaryRes.value) {
          setSummary(summaryRes.value)
        }
      } finally {
        if (active) setLoadingList(false)
      }
    }

    bootstrap()
    return () => {
      active = false
    }
  }, [message, handleServiceError])

  const openDetailDrawer = async (record) => {
    setDrawerItem(record)
    setDrawerOpen(true)
    try {
      const fresh = await notificationService.getById(record.id)
      setDrawerItem(fresh)
    } catch {
      // Giữ bản ghi hiện tại nếu không làm mới được
    }
  }

  const handleRefreshDetail = async (id, target = 'drawer') => {
    if (!id) return
    setRefreshingDetail(true)
    try {
      const fresh = await notificationService.getById(id)
      if (target === 'compose') {
        setComposedResult(fresh)
        setComposedHttpStatus(200)
      } else {
        setDrawerItem(fresh)
      }
      setItems((prev) => prev.map((x) => (x.id === fresh.id ? fresh : x)))
      notificationService.getSummary().then((res) => res && setSummary(res)).catch(() => {})
    } catch (err) {
      message.error(extractApiError(err, 'Không làm mới được trạng thái thông báo.'))
    } finally {
      setRefreshingDetail(false)
    }
  }

  const handleSubmitted = (result) => {
    const created = result?.data
    if (!created) return
    setComposedResult(created)
    setComposedHttpStatus(result.httpStatus)
    loadData(0, pagination.pageSize, filters)
  }

  const handleQuickLookup = async () => {
    const query = lookupUuid.trim()
    if (!query) {
      message.warning('Nhập Notification UUID hoặc Reference UUID (Booking / Payment ID) để tra cứu.')
      return
    }
    setLoadingLookup(true)
    try {
      if (lookupMode === 'id') {
        const data = await notificationService.getById(query)
        setDrawerItem(data)
        setDrawerOpen(true)
      } else {
        const list = await notificationService.getByReference(query)
        if (!list.length) {
          message.info('Không có bản ghi thông báo nào gắn với Reference UUID này.')
        } else {
          setDrawerItem(list[0])
          setDrawerOpen(true)
          if (list.length > 1) {
            message.info(`Tìm thấy ${list.length} thông báo cho Reference UUID này; đang hiển thị bản mới nhất.`)
          }
        }
      }
    } catch (err) {
      message.error(extractApiError(err, 'Không tìm thấy thông báo phù hợp.'))
    } finally {
      setLoadingLookup(false)
    }
  }

  const applyStatFilter = (status) => {
    const next = { ...filters, status }
    setFilters(next)
    setActiveTab('history')
    loadData(0, pagination.pageSize, next)
  }

  const handleResetFilters = () => {
    const clean = { recipient: '', status: '', sourceEvent: '' }
    setFilters(clean)
    loadData(0, pagination.pageSize, clean)
  }

  const columns = [
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status) => {
        const meta = NOTIFICATION_STATUS_META[status] || { label: status, color: 'default' }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: 'Người nhận',
      key: 'recipient',
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 600 }}>{record.recipientName || 'Khách hàng'}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.recipient}
          </Text>
        </div>
      ),
    },
    {
      title: 'Tiêu đề & kết quả',
      key: 'subject',
      render: (_, record) => (
        <div>
          <div>{record.subject || '—'}</div>
          {record.errorMessage && (
            <Text type="danger" style={{ fontSize: 12 }} ellipsis={{ tooltip: record.errorMessage }}>
              {record.errorMessage}
            </Text>
          )}
        </div>
      ),
    },
    {
      title: 'Phân loại',
      key: 'sourceEvent',
      width: 170,
      render: (_, record) => (
        <div>
          <div>{SOURCE_EVENT_LABELS[record.sourceEvent] || record.sourceEvent || '—'}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.sourceService || ''}
          </Text>
        </div>
      ),
    },
    {
      title: 'Thử lại',
      key: 'retry',
      width: 85,
      render: (_, record) => `${record.retryCount ?? 0}/${record.maxRetries ?? 3}`,
    },
    {
      title: 'Thời gian',
      key: 'time',
      width: 170,
      render: (_, record) => (
        <div style={{ fontSize: 12 }}>
          <div>Tạo: {formatDateTime(record.createdAt)}</div>
          {record.sentAt && <Text type="success">Gửi: {formatDateTime(record.sentAt)}</Text>}
        </div>
      ),
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Button size="small" onClick={() => openDetailDrawer(record)}>
          Xem chi tiết
        </Button>
      ),
    },
  ]

  const sentCount = (summary.sent || 0) + (summary.delivered || 0)
  const pendingCount = (summary.pending || 0) + (summary.sending || 0) + (summary.retry || 0)
  const failedCount = (summary.failed || 0) + (summary.deadLetter || 0)

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      <div className="page-header" style={{ marginBottom: 0 }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Trung Tâm Thông Báo &amp; Email
          </Title>
          <Text type="secondary">
            Theo dõi nhật ký chuyển phát SMTP, kiểm tra trạng thái email giao dịch và gửi thông báo tới khách hàng.
          </Text>
        </div>
        <Space wrap>
          <Button icon={<ReloadOutlined />} loading={loadingList} onClick={() => loadData(pagination.current - 1, pagination.pageSize, filters)}>
            Làm mới
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setActiveTab('compose')}>
            Soạn &amp; gửi email
          </Button>
        </Space>
      </div>

      {serviceError && (
        <Alert
          type="error"
          showIcon
          message={serviceError.title}
          description={serviceError.description}
          closable
          onClose={() => setServiceError(null)}
        />
      )}

      <div className="stats" style={{ marginBottom: 0 }}>
        <button
          type="button"
          className="stat"
          aria-pressed={filters.status === ''}
          onClick={() => applyStatFilter('')}
        >
          <span>Tổng thông báo</span>
          <strong>{summary.total || 0}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filters.status === 'SENT'}
          onClick={() => applyStatFilter('SENT')}
        >
          <span>
            <i style={{ background: '#16a34a' }} />
            Đã gửi SMTP
          </span>
          <strong>{sentCount}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filters.status === 'PENDING' || filters.status === 'RETRY'}
          onClick={() => applyStatFilter('RETRY')}
        >
          <span>
            <i style={{ background: '#d97706' }} />
            Đang chờ / Thử lại
          </span>
          <strong>{pendingCount}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filters.status === 'FAILED' || filters.status === 'DEAD_LETTER'}
          onClick={() => applyStatFilter('FAILED')}
        >
          <span>
            <i style={{ background: '#dc2626' }} />
            Gửi lỗi
          </span>
          <strong>{failedCount}</strong>
        </button>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'history',
            label: `Lịch sử thông báo (${pagination.total})`,
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Card size="small">
                  <Row gutter={[12, 12]} align="middle">
                    <Col xs={24} md={7}>
                      <Input
                        allowClear
                        placeholder="Tìm theo email người nhận..."
                        value={filters.recipient}
                        onChange={(e) => setFilters((prev) => ({ ...prev, recipient: e.target.value }))}
                        onPressEnter={() => loadData(0, pagination.pageSize, filters)}
                      />
                    </Col>
                    <Col xs={12} md={5}>
                      <Select
                        allowClear
                        style={{ width: '100%' }}
                        placeholder="Trạng thái..."
                        value={filters.status || undefined}
                        onChange={(val) => {
                          const next = { ...filters, status: val || '' }
                          setFilters(next)
                          loadData(0, pagination.pageSize, next)
                        }}
                        options={Object.entries(NOTIFICATION_STATUS_META).map(([value, meta]) => ({
                          value,
                          label: meta.label,
                        }))}
                      />
                    </Col>
                    <Col xs={12} md={5}>
                      <Select
                        allowClear
                        style={{ width: '100%' }}
                        placeholder="Loại sự kiện..."
                        value={filters.sourceEvent || undefined}
                        onChange={(val) => {
                          const next = { ...filters, sourceEvent: val || '' }
                          setFilters(next)
                          loadData(0, pagination.pageSize, next)
                        }}
                        options={Object.entries(SOURCE_EVENT_LABELS).map(([value, label]) => ({
                          value,
                          label,
                        }))}
                      />
                    </Col>
                    <Col xs={24} md={7}>
                      <Space wrap>
                        <Button
                          type="primary"
                          icon={<SearchOutlined />}
                          loading={loadingList}
                          onClick={() => loadData(0, pagination.pageSize, filters)}
                        >
                          Tìm kiếm
                        </Button>
                        <Button onClick={handleResetFilters}>Đặt lại</Button>
                      </Space>
                    </Col>

                    <Col xs={24}>
                      <Space.Compact style={{ width: '100%', maxWidth: 640 }}>
                        <Select
                          value={lookupMode}
                          onChange={setLookupMode}
                          style={{ width: 170 }}
                          options={[
                            { value: 'reference', label: 'Reference UUID' },
                            { value: 'id', label: 'Notification UUID' },
                          ]}
                        />
                        <Input
                          allowClear
                          placeholder="Tra cứu nhanh theo UUID đơn đặt xe / thanh toán / thông báo..."
                          value={lookupUuid}
                          onChange={(e) => setLookupUuid(e.target.value)}
                          onPressEnter={handleQuickLookup}
                        />
                        <Button loading={loadingLookup} icon={<SearchOutlined />} onClick={handleQuickLookup}>
                          Tra cứu UUID
                        </Button>
                      </Space.Compact>
                    </Col>
                  </Row>
                </Card>

                <Card size="small">
                  <Table
                    size="small"
                    rowKey="id"
                    loading={loadingList}
                    columns={columns}
                    dataSource={items}
                    locale={{
                      emptyText: <Empty description="Chưa có thông báo nào phù hợp bộ lọc." />,
                    }}
                    pagination={{
                      current: pagination.current,
                      pageSize: pagination.pageSize,
                      total: pagination.total,
                      showSizeChanger: true,
                      onChange: (page, pageSize) => loadData(page - 1, pageSize, filters),
                    }}
                  />
                </Card>
              </Space>
            ),
          },
          {
            key: 'compose',
            label: 'Soạn & gửi thông báo',
            children: (
              <Row gutter={[16, 16]}>
                <Col xs={24} lg={13}>
                  <Card size="small" title="Nội dung email gửi đi">
                    <NotificationForm onSubmitted={handleSubmitted} />
                  </Card>
                </Col>
                <Col xs={24} lg={11}>
                  <NotificationDetail
                    notification={composedResult}
                    httpStatus={composedHttpStatus}
                    onRefresh={(id) => handleRefreshDetail(id, 'compose')}
                    refreshing={refreshingDetail}
                  />
                </Col>
              </Row>
            ),
          },
        ]}
      />

      <NotificationDetailDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        notification={drawerItem}
        onRefresh={(id) => handleRefreshDetail(id, 'drawer')}
        refreshing={refreshingDetail}
      />
    </Space>
  )
}
