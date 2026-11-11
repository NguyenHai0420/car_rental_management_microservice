import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  App,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useAuth } from '../../../app/providers/auth-context.js'
import { extractApiError, formatCurrency, formatDateTime } from '../../../services/api-helpers.js'
import { listVehicles } from '../../vehicles/services/vehicle.js'
import { MaintenanceForm } from '../components/MaintenanceForm.jsx'
import { MaintenanceDetailDrawer } from '../hooks/maintenanceDetail.jsx'
import {
  MAINTENANCE_STATUS_META,
  PRIORITY_META,
  formatVehicleDisplay,
  maintenanceService,
} from '../services/maintenance.js'

const { Title, Text } = Typography

const INITIAL_COUNTS = {
  total: 0,
  planned: 0,
  inProgress: 0,
  completed: 0,
}

async function fetchStatusCount(status) {
  const page = await maintenanceService.listSchedules({ status, page: 0, size: 1 })
  return page?.page?.totalElements ?? page?.totalElements ?? 0
}

async function loadSummaryCounts() {
  const [scheduled, overdue, startReq, inProg, compReq, completed, cancelled] = await Promise.all([
    fetchStatusCount('SCHEDULED'),
    fetchStatusCount('OVERDUE'),
    fetchStatusCount('START_REQUESTED'),
    fetchStatusCount('IN_PROGRESS'),
    fetchStatusCount('COMPLETE_REQUESTED'),
    fetchStatusCount('COMPLETED'),
    fetchStatusCount('CANCELLED'),
  ])
  return {
    total: scheduled + overdue + startReq + inProg + compReq + completed + cancelled,
    planned: scheduled + overdue,
    inProgress: startReq + inProg + compReq,
    completed,
  }
}

export function MaintenancePage() {
  const { message } = App.useApp()
  const auth = useAuth()
  const [schedules, setSchedules] = useState([])
  const [vehicles, setVehicles] = useState([])
  const [counts, setCounts] = useState(INITIAL_COUNTS)
  const [loading, setLoading] = useState(false)
  const [filterVehicleId, setFilterVehicleId] = useState('')
  const [filterStatus, setFilterStatus] = useState('')
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false)
  const [selectedSchedule, setSelectedSchedule] = useState(null)
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false)

  const vehicleMap = useMemo(() => {
    const map = {}
    for (const v of vehicles) {
      map[v.id] = v
    }
    return map
  }, [vehicles])

  const loadSchedules = useCallback(
    async (page = 0, size = 10, vehicleId = filterVehicleId, status = filterStatus) => {
      setLoading(true)
      try {
        const [result, summaryRes] = await Promise.allSettled([
          maintenanceService.listSchedules({
            vehicleId: vehicleId?.trim() || undefined,
            status: status || undefined,
            page,
            size,
          }),
          loadSummaryCounts(),
        ])
        if (result.status === 'fulfilled') {
          const data = result.value
          setSchedules(data?.content || [])
          setPagination({
            current: (data?.number ?? page) + 1,
            pageSize: data?.size ?? size,
            total: data?.page?.totalElements ?? data?.totalElements ?? 0,
          })
        } else {
          message.error(extractApiError(result.reason, 'Không tải được danh sách lịch bảo dưỡng.'))
        }
        if (summaryRes.status === 'fulfilled') {
          setCounts(summaryRes.value)
        }
      } finally {
        setLoading(false)
      }
    },
    [filterVehicleId, filterStatus, message]
  )

  useEffect(() => {
    let active = true

    async function bootstrap() {
      setLoading(true)
      try {
        const [vehiclesRes, schedulesRes, countsRes] = await Promise.allSettled([
          listVehicles({ page: 0, size: 50 }),
          maintenanceService.listSchedules({ page: 0, size: 10 }),
          loadSummaryCounts(),
        ])
        if (!active) return
        if (vehiclesRes.status === 'fulfilled') {
          setVehicles(vehiclesRes.value?.content || [])
        }
        if (schedulesRes.status === 'fulfilled') {
          const result = schedulesRes.value
          setSchedules(result?.content || [])
          setPagination({
            current: (result?.number ?? 0) + 1,
            pageSize: result?.size ?? 10,
            total: result?.page?.totalElements ?? result?.totalElements ?? 0,
          })
        } else {
          message.error(extractApiError(schedulesRes.reason, 'Không tải được danh sách lịch bảo dưỡng.'))
        }
        if (countsRes.status === 'fulfilled') {
          setCounts(countsRes.value)
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    bootstrap()
    return () => {
      active = false
    }
  }, [message])

  const openDetail = async (record) => {
    setSelectedSchedule(record)
    setDetailDrawerOpen(true)
    try {
      const fresh = await maintenanceService.getScheduleById(record.id)
      setSelectedSchedule(fresh)
    } catch {
      // Giữ bản ghi hiện tại nếu không làm mới được
    }
  }

  const handleResetFilters = () => {
    setFilterVehicleId('')
    setFilterStatus('')
    loadSchedules(0, pagination.pageSize, '', '')
  }

  const applyStatFilter = (status) => {
    setFilterStatus(status)
    loadSchedules(0, pagination.pageSize, filterVehicleId, status)
  }

  const columns = [
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status) => {
        const meta = MAINTENANCE_STATUS_META[status] || { label: status, color: 'default' }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: 'Phương tiện',
      dataIndex: 'vehicleId',
      key: 'vehicleId',
      render: (id) => {
        const vehicle = vehicleMap[id]
        const label = vehicle ? formatVehicleDisplay(vehicle) : 'Xe trong hệ thống'
        return (
          <div>
            <div style={{ fontWeight: 600 }}>{label}</div>
            <Text code copyable style={{ fontSize: 11 }}>
              {id}
            </Text>
          </div>
        )
      },
    },
    {
      title: 'Lịch bảo dưỡng',
      key: 'window',
      render: (_, record) => (
        <div style={{ fontSize: 13 }}>
          <div>Bắt đầu: {formatDateTime(record.scheduledStartAt)}</div>
          <div>Kết thúc: {formatDateTime(record.scheduledEndAt)}</div>
        </div>
      ),
    },
    {
      title: 'Ưu tiên',
      dataIndex: 'priority',
      key: 'priority',
      width: 110,
      render: (priority) => {
        const meta = PRIORITY_META[priority] || { label: priority || '—', color: 'default' }
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    {
      title: 'Chi phí',
      key: 'cost',
      render: (_, record) => (
        <div style={{ fontSize: 13 }}>
          <div>Dự kiến: {formatCurrency(record.estimatedCost)}</div>
          {record.actualCost != null && (
            <Text type="secondary">Thực tế: {formatCurrency(record.actualCost)}</Text>
          )}
        </div>
      ),
    },
    {
      title: 'Xưởng / Phụ trách',
      dataIndex: 'assignedTo',
      key: 'assignedTo',
      render: (val) => val || '—',
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 110,
      render: (_, record) => (
        <Button size="small" onClick={() => openDetail(record)}>
          Xem chi tiết
        </Button>
      ),
    },
  ]

  return (
    <Space direction="vertical" size={18} style={{ width: '100%' }}>
      <div className="page-header" style={{ marginBottom: 0 }}>
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Quản Lý Bảo Dưỡng &amp; Kiểm Định Xe
          </Title>
          <Text type="secondary">
            Lập lịch bảo dưỡng định kỳ, kiểm tra trùng lịch xe, theo dõi biên bản sửa chữa và kết quả kiểm định an toàn.
          </Text>
        </div>
        <Space wrap>
          <Button
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => loadSchedules(pagination.current - 1, pagination.pageSize)}
          >
            Làm mới
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>
            Tạo lịch bảo dưỡng
          </Button>
        </Space>
      </div>

      <div className="stats" style={{ marginBottom: 0 }}>
        <button
          type="button"
          className="stat"
          aria-pressed={filterStatus === ''}
          onClick={() => applyStatFilter('')}
        >
          <span>Tổng lịch bảo dưỡng</span>
          <strong>{counts.total}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filterStatus === 'SCHEDULED' || filterStatus === 'OVERDUE'}
          onClick={() => applyStatFilter('SCHEDULED')}
        >
          <span>
            <i style={{ background: '#2563eb' }} />
            Đã lên lịch / Quá hạn
          </span>
          <strong>{counts.planned}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filterStatus === 'IN_PROGRESS'}
          onClick={() => applyStatFilter('IN_PROGRESS')}
        >
          <span>
            <i style={{ background: '#d97706' }} />
            Đang bảo dưỡng
          </span>
          <strong>{counts.inProgress}</strong>
        </button>
        <button
          type="button"
          className="stat"
          aria-pressed={filterStatus === 'COMPLETED'}
          onClick={() => applyStatFilter('COMPLETED')}
        >
          <span>
            <i style={{ background: '#16a34a' }} />
            Hoàn thành
          </span>
          <strong>{counts.completed}</strong>
        </button>
      </div>

      <Card size="small" title={`Danh sách lịch bảo dưỡng (${pagination.total})`}>
        <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
          <Col xs={24} md={8}>
            {vehicles.length > 0 ? (
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                style={{ width: '100%' }}
                placeholder="Chọn xe theo biển số / mẫu xe..."
                value={vehicleMap[filterVehicleId] ? filterVehicleId : undefined}
                onChange={(val) => {
                  const next = val || ''
                  setFilterVehicleId(next)
                  loadSchedules(0, pagination.pageSize, next, filterStatus)
                }}
                options={vehicles.map((v) => ({ value: v.id, label: formatVehicleDisplay(v) }))}
              />
            ) : (
              <Input
                allowClear
                placeholder="Lọc theo Vehicle UUID..."
                value={filterVehicleId}
                onChange={(e) => setFilterVehicleId(e.target.value)}
                onPressEnter={() => loadSchedules(0, pagination.pageSize, filterVehicleId, filterStatus)}
              />
            )}
          </Col>
          <Col xs={24} md={6}>
            <Input
              allowClear
              placeholder="Hoặc nhập Vehicle UUID..."
              value={filterVehicleId}
              onChange={(e) => setFilterVehicleId(e.target.value)}
              onPressEnter={() => loadSchedules(0, pagination.pageSize, filterVehicleId, filterStatus)}
            />
          </Col>
          <Col xs={12} md={5}>
            <Select
              allowClear
              style={{ width: '100%' }}
              placeholder="Lọc trạng thái..."
              value={filterStatus || undefined}
              onChange={(val) => {
                const next = val || ''
                setFilterStatus(next)
                loadSchedules(0, pagination.pageSize, filterVehicleId, next)
              }}
              options={Object.entries(MAINTENANCE_STATUS_META).map(([value, meta]) => ({
                value,
                label: meta.label,
              }))}
            />
          </Col>
          <Col xs={12} md={5}>
            <Space wrap>
              <Button
                type="primary"
                icon={<SearchOutlined />}
                onClick={() => loadSchedules(0, pagination.pageSize, filterVehicleId, filterStatus)}
                loading={loading}
              >
                Tìm kiếm
              </Button>
              <Button onClick={handleResetFilters}>Đặt lại</Button>
            </Space>
          </Col>
        </Row>

        <Table
          size="small"
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={schedules}
          locale={{
            emptyText: (
              <Empty description="Chưa có lịch bảo dưỡng nào phù hợp.">
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>
                  Tạo lịch bảo dưỡng đầu tiên
                </Button>
              </Empty>
            ),
          }}
          pagination={{
            current: pagination.current,
            pageSize: pagination.pageSize,
            total: pagination.total,
            showSizeChanger: true,
            onChange: (page, size) => loadSchedules(page - 1, size),
          }}
        />
      </Card>

      <Drawer
        title="Tạo lịch bảo dưỡng mới"
        width={720}
        open={createDrawerOpen}
        onClose={() => setCreateDrawerOpen(false)}
      >
        <MaintenanceForm
          vehicles={vehicles}
          createdByDefault={auth.name || 'admin'}
          onCreated={(created) => {
            setCreateDrawerOpen(false)
            setSelectedSchedule(created)
            setDetailDrawerOpen(true)
            loadSchedules(0, pagination.pageSize)
          }}
        />
      </Drawer>

      <MaintenanceDetailDrawer
        schedule={selectedSchedule}
        vehicleLabel={
          selectedSchedule && vehicleMap[selectedSchedule.vehicleId]
            ? formatVehicleDisplay(vehicleMap[selectedSchedule.vehicleId])
            : ''
        }
        open={detailDrawerOpen}
        onClose={() => setDetailDrawerOpen(false)}
        onUpdated={(updated) => {
          setSelectedSchedule(updated)
          loadSchedules(pagination.current - 1, pagination.pageSize)
        }}
      />
    </Space>
  )
}
