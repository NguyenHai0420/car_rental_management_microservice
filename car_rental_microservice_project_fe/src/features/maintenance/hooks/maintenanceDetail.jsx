import { useEffect, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import { formatCurrency, formatDateTime } from '../../../services/api-helpers.js'
import { ConflictSummaryAlert, ErrorAlertBanner } from '../components/ConflictSummaryAlert.jsx'
import { formatMaintenanceError, isConflictError } from '../services/maintenanceErrors.js'
import {
  INSPECTION_RESULT_META,
  INSPECTION_RESULT_OPTIONS,
  MAINTENANCE_STATUS_META,
  PRIORITY_META,
  PRIORITY_OPTIONS,
  SERVICE_TYPE_LABELS,
  SERVICE_TYPE_OPTIONS,
  maintenanceService,
} from '../services/maintenance.js'

const { Text } = Typography

export function MaintenanceDetailDrawer({ schedule, vehicleLabel, inspectorDefault = '', open, onClose, onUpdated }) {
  const { message } = App.useApp()
  const [records, setRecords] = useState([])
  const [checklists, setChecklists] = useState([])
  const [loadingSub, setLoadingSub] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [checkingEditConflict, setCheckingEditConflict] = useState(false)
  const [drawerError, setDrawerError] = useState(null)
  const [editError, setEditError] = useState(null)
  const [editConflictResult, setEditConflictResult] = useState(null)
  const [subError, setSubError] = useState(null)
  const [modalError, setModalError] = useState(null)
  const [modalMode, setModalMode] = useState(null)
  const [recordForm] = Form.useForm()
  const [checklistForm] = Form.useForm()
  const [updateForm] = Form.useForm()
  const [actionForm] = Form.useForm()

  const scheduleId = schedule?.id

  useEffect(() => {
    if (!open || !scheduleId) return
    let active = true

    async function loadSubResources() {
      setLoadingSub(true)
      try {
        const [recList, chkList] = await Promise.all([
          maintenanceService.listServiceRecords(scheduleId),
          maintenanceService.listChecklists(scheduleId),
        ])
        if (!active) return
        setRecords(recList)
        setChecklists(chkList)
      } catch (err) {
        if (active) {
          const parsed = formatMaintenanceError(err, 'Không tải được biên bản và danh mục kiểm định.')
          setDrawerError(parsed)
          message.error(parsed.description || parsed.title)
        }
      } finally {
        if (active) setLoadingSub(false)
      }
    }

    updateForm.setFieldsValue({
      scheduledStartAt: schedule.scheduledStartAt ? dayjs(schedule.scheduledStartAt) : null,
      scheduledEndAt: schedule.scheduledEndAt ? dayjs(schedule.scheduledEndAt) : null,
      scheduledAtKm: schedule.scheduledAtKm,
      estimatedCost: schedule.estimatedCost,
      assignedTo: schedule.assignedTo,
      priority: schedule.priority,
      notes: schedule.notes,
    })

    loadSubResources()
    return () => {
      active = false
    }
  }, [open, scheduleId, schedule, message, updateForm])

  if (!schedule) return null

  const statusMeta = MAINTENANCE_STATUS_META[schedule.status] || {
    label: schedule.status,
    color: 'default',
  }
  const priorityMeta = PRIORITY_META[schedule.priority] || {
    label: schedule.priority || '—',
    color: 'default',
  }

  const refreshParent = (updated) => {
    onUpdated?.(updated)
  }

  const handleStart = async () => {
    setActionLoading(true)
    setDrawerError(null)
    try {
      const updated = await maintenanceService.startMaintenance(schedule.id)
      message.success('Đã bắt đầu bảo dưỡng và đồng bộ trạng thái xe.')
      refreshParent(updated)
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Không thể bắt đầu bảo dưỡng.')
      setDrawerError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setActionLoading(false)
    }
  }

  const handleModalSubmit = async () => {
    setModalError(null)
    try {
      const values = await actionForm.validateFields()
      setActionLoading(true)
      let updated
      if (modalMode === 'complete') {
        updated = await maintenanceService.completeMaintenance(schedule.id, values)
        message.success('Đã hoàn tất bảo dưỡng xe.')
      } else if (modalMode === 'cancel') {
        updated = await maintenanceService.cancelSchedule(schedule.id, values.cancellationReason)
        message.success('Đã hủy lịch bảo dưỡng.')
      }
      setModalMode(null)
      setModalError(null)
      actionForm.resetFields()
      if (updated) refreshParent(updated)
    } catch (err) {
      if (err?.errorFields) return
      const parsed = formatMaintenanceError(err, 'Thao tác thất bại.')
      setModalError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setActionLoading(false)
    }
  }

  const handleCheckEditConflict = async () => {
    setEditError(null)
    try {
      const values = await updateForm.validateFields(['scheduledStartAt', 'scheduledEndAt'])
      if (!values.scheduledEndAt?.isAfter(values.scheduledStartAt)) {
        const errObj = {
          title: 'Khung thời gian không hợp lệ',
          description: 'Thời gian kết thúc phải lớn hơn thời gian bắt đầu.',
        }
        setEditError(errObj)
        setEditConflictResult(null)
        message.error(errObj.description)
        return
      }
      setCheckingEditConflict(true)
      const data = await maintenanceService.checkAllConflicts(schedule.vehicleId, {
        startAt: values.scheduledStartAt.toISOString(),
        endAt: values.scheduledEndAt.toISOString(),
        excludeScheduleId: schedule.id,
      })
      setEditConflictResult(data)
      if (data.hasConflict) {
        const total = (data.conflictingSchedules?.length || 0) + (data.conflictingHolds?.length || 0)
        message.warning(
          total > 0
            ? `Phát hiện ${total} lịch / giữ chỗ bị trùng khung giờ mới.`
            : 'Xe đang không khả dụng trong trạng thái hiện tại.'
        )
      } else {
        message.success('Khung giờ mới hợp lệ, chưa phát hiện trùng lịch.')
      }
    } catch (err) {
      if (err?.errorFields) return
      const parsed = formatMaintenanceError(err, 'Kiểm tra trùng lịch thất bại.')
      setEditError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setCheckingEditConflict(false)
    }
  }

  const handleUpdateSchedule = async (values) => {
    if (!values.scheduledEndAt?.isAfter(values.scheduledStartAt)) {
      const errObj = {
        title: 'Khung thời gian không hợp lệ',
        description: 'Thời gian kết thúc phải lớn hơn thời gian bắt đầu.',
      }
      setEditError(errObj)
      message.error(errObj.description)
      return
    }

    const startAt = values.scheduledStartAt.toISOString()
    const endAt = values.scheduledEndAt.toISOString()

    setActionLoading(true)
    setEditError(null)
    setDrawerError(null)
    try {
      const updated = await maintenanceService.updateSchedule(schedule.id, {
        scheduledStartAt: startAt,
        scheduledEndAt: endAt,
        scheduledAtKm: values.scheduledAtKm ?? null,
        estimatedCost: values.estimatedCost ?? null,
        assignedTo: values.assignedTo?.trim() || null,
        priority: values.priority,
        notes: values.notes?.trim() || null,
      })
      setEditConflictResult(null)
      message.success('Đã cập nhật thông tin lịch bảo dưỡng.')
      refreshParent(updated)
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Cập nhật lịch thất bại.')
      setEditError(parsed)
      message.error(parsed.description || parsed.title)
      if (isConflictError(err)) {
        try {
          const conflicts = await maintenanceService.checkAllConflicts(schedule.vehicleId, {
            startAt,
            endAt,
            excludeScheduleId: schedule.id,
          })
          if (conflicts?.hasConflict) {
            setEditConflictResult(conflicts)
          }
        } catch {
          // Giữ nguyên lỗi cập nhật chính nếu truy vấn chi tiết phụ không thành công
        }
      }
    } finally {
      setActionLoading(false)
    }
  }

  const handleAddRecord = async (values) => {
    setLoadingSub(true)
    setSubError(null)
    try {
      await maintenanceService.addServiceRecord(schedule.id, {
        ...values,
        serviceDate: values.serviceDate ? values.serviceDate.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
        warrantyUntil: values.warrantyUntil ? values.warrantyUntil.format('YYYY-MM-DD') : null,
      })
      recordForm.resetFields()
      const list = await maintenanceService.listServiceRecords(schedule.id)
      setRecords(list)
      message.success('Đã thêm biên bản công việc.')
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Thêm biên bản công việc thất bại.')
      setSubError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setLoadingSub(false)
    }
  }

  const handleAddChecklist = async (values) => {
    setLoadingSub(true)
    setSubError(null)
    try {
      await maintenanceService.addChecklistItem(schedule.id, values)
      checklistForm.resetFields()
      const list = await maintenanceService.listChecklists(schedule.id)
      setChecklists(list)
      message.success('Đã thêm hạng mục kiểm định.')
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Thêm hạng mục kiểm định thất bại.')
      setSubError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setLoadingSub(false)
    }
  }

  const handleQuickChecklistResult = async (item, result) => {
    setLoadingSub(true)
    setSubError(null)
    try {
      await maintenanceService.updateChecklistItem(schedule.id, item.id, {
        result,
        measuredValue: item.measuredValue,
        inspectorNotes: item.inspectorNotes,
        inspectorName: item.inspectorName || inspectorDefault || undefined,
      })
      const list = await maintenanceService.listChecklists(schedule.id)
      setChecklists(list)
      const label = INSPECTION_RESULT_META[result]?.label || result
      message.success(`Đã cập nhật "${item.checkItem}" → ${label}`)
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Cập nhật đánh giá kiểm định thất bại.')
      setSubError(parsed)
      message.error(parsed.description || parsed.title)
    } finally {
      setLoadingSub(false)
    }
  }

  const canStartOrCancel = schedule.status === 'SCHEDULED' || schedule.status === 'OVERDUE'
  const canComplete = schedule.status === 'IN_PROGRESS'
  const isFinished = schedule.status === 'COMPLETED' || schedule.status === 'CANCELLED'

  const handleClose = () => {
    setDrawerError(null)
    setEditError(null)
    setEditConflictResult(null)
    setSubError(null)
    setModalError(null)
    onClose?.()
  }

  return (
    <Drawer
      title={
        <Space wrap>
          <span>{vehicleLabel ? `Bảo dưỡng: ${vehicleLabel}` : 'Chi tiết lịch bảo dưỡng'}</span>
          <Tag color={statusMeta.color}>{statusMeta.label}</Tag>
          <Tag color={priorityMeta.color}>{priorityMeta.label}</Tag>
        </Space>
      }
      width={780}
      open={open}
      onClose={handleClose}
      focusable={{ trap: false }}
      extra={
        <Space wrap>
          <Popconfirm
            title="Bắt đầu thực hiện bảo dưỡng và chuyển trạng thái xe sang Đang bảo dưỡng?"
            onConfirm={handleStart}
            disabled={!canStartOrCancel}
          >
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              disabled={!canStartOrCancel}
              loading={actionLoading}
            >
              Bắt đầu
            </Button>
          </Popconfirm>
          <Button
            icon={<CheckCircleOutlined />}
            disabled={!canComplete}
            loading={actionLoading}
            onClick={() => {
              setModalError(null)
              actionForm.resetFields()
              setModalMode('complete')
            }}
          >
            Hoàn tất
          </Button>
          <Button
            danger
            icon={<CloseCircleOutlined />}
            disabled={!canStartOrCancel}
            loading={actionLoading}
            onClick={() => {
              setModalError(null)
              actionForm.resetFields()
              setModalMode('cancel')
            }}
          >
            Hủy lịch
          </Button>
        </Space>
      }
    >
      <ErrorAlertBanner
        error={drawerError}
        onClose={() => setDrawerError(null)}
        style={{ marginBottom: 16 }}
      />

      <Tabs
        items={[
          {
            key: 'info',
            label: 'Thông tin & Cập nhật',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {schedule.lastSyncError && (
                  <Alert
                    type="warning"
                    showIcon
                    message="Lỗi đồng bộ gần nhất với Quản lý xe"
                    description={schedule.lastSyncError}
                  />
                )}

                <Descriptions bordered size="small" column={2}>
                  {vehicleLabel && (
                    <Descriptions.Item label="Xe bảo dưỡng" span={2}>
                      <strong>{vehicleLabel}</strong>
                    </Descriptions.Item>
                  )}
                  <Descriptions.Item label="Mã lịch bảo dưỡng">
                    <Text code copyable>{schedule.id}</Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Vehicle UUID">
                    <Text code copyable>{schedule.vehicleId}</Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Thời gian bắt đầu">
                    {formatDateTime(schedule.scheduledStartAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Thời gian kết thúc">
                    {formatDateTime(schedule.scheduledEndAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Chi phí dự kiến">
                    {formatCurrency(schedule.estimatedCost)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Chi phí thực tế">
                    {formatCurrency(schedule.actualCost)}
                  </Descriptions.Item>
                  <Descriptions.Item label="Trạng thái xe đồng bộ">
                    {schedule.resolvedVehicleStatus || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Số lần đồng bộ">
                    {schedule.syncRetryCount ?? 0} {schedule.syncExhausted ? '(Hết lượt thử)' : ''}
                  </Descriptions.Item>
                  {schedule.cancellationReason && (
                    <Descriptions.Item label="Lý do hủy" span={2}>
                      {schedule.cancellationReason}
                    </Descriptions.Item>
                  )}
                </Descriptions>

                <Card
                  size="small"
                  title="Chỉnh sửa lịch bảo dưỡng"
                  extra={
                    !canStartOrCancel && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Chỉ chỉnh sửa khi ở trạng thái Đã lên lịch / Quá hạn
                      </Text>
                    )
                  }
                >
                  <Form form={updateForm} layout="vertical" onFinish={handleUpdateSchedule}>
                    <Space style={{ width: '100%' }} wrap>
                      <Form.Item label="Thời gian bắt đầu" name="scheduledStartAt" rules={[{ required: true }]}>
                        <DatePicker showTime style={{ width: 230 }} format="DD/MM/YYYY HH:mm" />
                      </Form.Item>
                      <Form.Item label="Thời gian kết thúc" name="scheduledEndAt" rules={[{ required: true }]}>
                        <DatePicker showTime style={{ width: 230 }} format="DD/MM/YYYY HH:mm" />
                      </Form.Item>
                    </Space>
                    <Space style={{ width: '100%' }} wrap>
                      <Form.Item label="Mức ưu tiên" name="priority">
                        <Select
                          style={{ width: 140 }}
                          options={PRIORITY_OPTIONS.map((p) => ({
                            value: p,
                            label: PRIORITY_META[p]?.label || p,
                          }))}
                        />
                      </Form.Item>
                      <Form.Item label="Số km dự kiến" name="scheduledAtKm">
                        <InputNumber min={0} />
                      </Form.Item>
                      <Form.Item label="Chi phí dự kiến" name="estimatedCost">
                        <InputNumber min={0} />
                      </Form.Item>
                      <Form.Item label="Xưởng phụ trách" name="assignedTo">
                        <Input />
                      </Form.Item>
                    </Space>
                    <Form.Item label="Ghi chú" name="notes">
                      <Input.TextArea rows={2} />
                    </Form.Item>
                    <ErrorAlertBanner
                      error={editError}
                      onClose={() => setEditError(null)}
                      style={{ marginBottom: 12 }}
                    />
                    <ConflictSummaryAlert
                      conflictResult={editConflictResult}
                      onClose={() => setEditConflictResult(null)}
                      style={{ marginBottom: 12 }}
                    />
                    <Space wrap>
                      <Button
                        icon={<SearchOutlined />}
                        disabled={!canStartOrCancel}
                        loading={checkingEditConflict}
                        onClick={handleCheckEditConflict}
                      >
                        Kiểm tra trùng lịch
                      </Button>
                      <Button type="primary" htmlType="submit" disabled={!canStartOrCancel} loading={actionLoading}>
                        Lưu thay đổi
                      </Button>
                    </Space>
                  </Form>
                </Card>
              </Space>
            ),
          },
          {
            key: 'records',
            label: `Biên bản dịch vụ (${records.length})`,
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <ErrorAlertBanner error={subError} onClose={() => setSubError(null)} />
                {!isFinished && (
                  <Card size="small" title="Thêm biên bản công việc đã thực hiện">
                    <Form
                      form={recordForm}
                      layout="vertical"
                      initialValues={{
                        serviceType: 'OIL_CHANGE',
                        serviceDate: dayjs(),
                        partsCost: 0,
                        laborCost: 0,
                      }}
                      onFinish={handleAddRecord}
                    >
                      <Space wrap style={{ width: '100%' }}>
                        <Form.Item label="Loại dịch vụ" name="serviceType" rules={[{ required: true }]}>
                          <Select
                            style={{ width: 200 }}
                            options={SERVICE_TYPE_OPTIONS.map((t) => ({
                              value: t,
                              label: SERVICE_TYPE_LABELS[t] || t,
                            }))}
                          />
                        </Form.Item>
                        <Form.Item label="Ngày thực hiện" name="serviceDate" rules={[{ required: true }]}>
                          <DatePicker format="DD/MM/YYYY" />
                        </Form.Item>
                        <Form.Item label="Tiền phụ tùng" name="partsCost">
                          <InputNumber min={0} />
                        </Form.Item>
                        <Form.Item label="Tiền công" name="laborCost">
                          <InputNumber min={0} />
                        </Form.Item>
                      </Space>
                      <Form.Item
                        label="Mô tả công việc"
                        name="description"
                        rules={[{ required: true, message: 'Vui lòng nhập mô tả công việc' }]}
                      >
                        <Input placeholder="Thay nhớt động cơ, thay lọc dầu..." />
                      </Form.Item>
                      <Space wrap>
                        <Form.Item label="Kỹ thuật viên" name="mechanicName">
                          <Input />
                        </Form.Item>
                        <Form.Item label="Xưởng" name="workshopName">
                          <Input />
                        </Form.Item>
                        <Form.Item label="Bảo hành đến" name="warrantyUntil">
                          <DatePicker format="DD/MM/YYYY" />
                        </Form.Item>
                      </Space>
                      <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={loadingSub}>
                        Thêm biên bản công việc
                      </Button>
                    </Form>
                  </Card>
                )}

                <Table
                  size="small"
                  rowKey="id"
                  loading={loadingSub}
                  dataSource={records}
                  pagination={false}
                  locale={{ emptyText: <Empty description="Chưa có biên bản công việc nào." /> }}
                  columns={[
                    {
                      title: 'Dịch vụ',
                      dataIndex: 'serviceType',
                      key: 'serviceType',
                      render: (t) => SERVICE_TYPE_LABELS[t] || t,
                    },
                    { title: 'Mô tả', dataIndex: 'description', key: 'description' },
                    { title: 'Ngày', dataIndex: 'serviceDate', key: 'serviceDate' },
                    {
                      title: 'Tổng tiền',
                      dataIndex: 'totalCost',
                      key: 'totalCost',
                      render: (v) => formatCurrency(v),
                    },
                  ]}
                />
              </Space>
            ),
          },
          {
            key: 'checklists',
            label: `Kiểm định an toàn (${checklists.length})`,
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <ErrorAlertBanner error={subError} onClose={() => setSubError(null)} />
                {!isFinished && (
                  <Card size="small" title="Thêm hạng mục kiểm định">
                    <Form
                      form={checklistForm}
                      layout="vertical"
                      initialValues={{ result: 'NOT_CHECKED' }}
                      onFinish={handleAddChecklist}
                    >
                      <Space wrap>
                        <Form.Item
                          label="Hạng mục"
                          name="checkItem"
                          rules={[{ required: true, message: 'Nhập tên hạng mục' }]}
                        >
                          <Input placeholder="Phanh trước / Áp suất lốp" />
                        </Form.Item>
                        <Form.Item label="Nhóm kiểm tra" name="checkCategory">
                          <Input placeholder="Phanh / Lốp / Đèn" />
                        </Form.Item>
                        <Form.Item label="Kết quả ban đầu" name="result">
                          <Select
                            style={{ width: 150 }}
                            options={INSPECTION_RESULT_OPTIONS.map((r) => ({
                              value: r,
                              label: INSPECTION_RESULT_META[r]?.label || r,
                            }))}
                          />
                        </Form.Item>
                        <Form.Item label="Thông số đo" name="measuredValue">
                          <Input placeholder="32 PSI" />
                        </Form.Item>
                        <Form.Item label="Ngưỡng chuẩn" name="acceptableRange">
                          <Input placeholder="30-35 PSI" />
                        </Form.Item>
                      </Space>
                      <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={loadingSub}>
                        Thêm hạng mục kiểm định
                      </Button>
                    </Form>
                  </Card>
                )}

                <Table
                  size="small"
                  rowKey="id"
                  loading={loadingSub}
                  dataSource={checklists}
                  pagination={false}
                  locale={{ emptyText: <Empty description="Chưa có hạng mục kiểm định nào." /> }}
                  columns={[
                    { title: 'Hạng mục', dataIndex: 'checkItem', key: 'checkItem' },
                    { title: 'Nhóm', dataIndex: 'checkCategory', key: 'checkCategory' },
                    {
                      title: 'Kết quả',
                      dataIndex: 'result',
                      key: 'result',
                      render: (val) => {
                        const meta = INSPECTION_RESULT_META[val] || { label: val, color: 'default' }
                        return <Tag color={meta.color}>{meta.label}</Tag>
                      },
                    },
                    {
                      title: 'Đánh giá nhanh',
                      key: 'quick',
                      render: (_, item) => (
                        <Space size={4}>
                          {[
                            ['PASS', 'Đạt'],
                            ['WARNING', 'Lưu ý'],
                            ['FAIL', 'Không đạt'],
                          ].map(([res, text]) => (
                            <Button
                              key={res}
                              size="small"
                              disabled={isFinished}
                              onClick={() => handleQuickChecklistResult(item, res)}
                            >
                              {text}
                            </Button>
                          ))}
                        </Space>
                      ),
                    },
                  ]}
                />
              </Space>
            ),
          },
        ]}
      />

      <Modal
        title={modalMode === 'complete' ? 'Hoàn tất bảo dưỡng xe' : 'Hủy lịch bảo dưỡng'}
        open={Boolean(modalMode)}
        confirmLoading={actionLoading}
        onOk={handleModalSubmit}
        onCancel={() => {
          setModalMode(null)
          setModalError(null)
        }}
      >
        <ErrorAlertBanner
          error={modalError}
          onClose={() => setModalError(null)}
          style={{ marginBottom: 16 }}
        />
        <Form form={actionForm} layout="vertical">
          {modalMode === 'complete' ? (
            <>
              <Form.Item label="Chi phí thực tế (VNĐ)" name="actualCost">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="Số km lúc hoàn thành" name="completedAtKm">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="Ghi chú nghiệm thu" name="notes">
                <Input.TextArea rows={2} />
              </Form.Item>
            </>
          ) : (
            <Form.Item
              label="Lý do hủy"
              name="cancellationReason"
              rules={[{ required: true, message: 'Vui lòng nhập lý do hủy lịch' }]}
            >
              <Input.TextArea rows={3} placeholder="Hoãn lịch bảo dưỡng sang tuần sau..." />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Drawer>
  )
}
