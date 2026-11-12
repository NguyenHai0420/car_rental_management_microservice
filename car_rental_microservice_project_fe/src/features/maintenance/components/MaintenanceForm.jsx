import { useEffect, useRef, useState } from 'react'
import {
  App,
  Button,
  Col,
  Collapse,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Typography,
} from 'antd'
import { CalendarOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import {
  PRIORITY_META,
  PRIORITY_OPTIONS,
  formatVehicleDisplay,
  maintenanceService,
} from '../services/maintenance.js'
import { formatMaintenanceError, isConflictError } from '../services/maintenanceErrors.js'
import { ConflictSummaryAlert, ErrorAlertBanner } from './ConflictSummaryAlert.jsx'

export function MaintenanceForm({ vehicles = [], createdByDefault = '', onCreated }) {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const feedbackRef = useRef(null)
  const [submitting, setSubmitting] = useState(false)
  const [checking, setChecking] = useState(false)
  const [manualUuid, setManualUuid] = useState(false)
  const [formError, setFormError] = useState(null)
  const [conflictResult, setConflictResult] = useState(null)
  const watchedVehicleId = Form.useWatch('vehicleId', form)

  const scrollToFeedback = () => {
    requestAnimationFrame(() => {
      feedbackRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    })
  }

  const vehicleOptions = vehicles.map((v) => ({
    value: v.id,
    label: formatVehicleDisplay(v),
  }))

  useEffect(() => {
    const current = form.getFieldValue('vehicleId')
    if (!current && vehicles[0]?.id) {
      form.setFieldValue('vehicleId', vehicles[0].id)
    }
  }, [vehicles, form])

  const handleVehicleChange = () => {
    setConflictResult(null)
    setFormError(null)
  }

  const handleCheckConflict = async () => {
    setFormError(null)
    try {
      const values = await form.validateFields(['vehicleId', 'scheduledStartAt', 'scheduledEndAt'])
      if (!values.scheduledEndAt.isAfter(values.scheduledStartAt)) {
        const errObj = {
          title: 'Khung thời gian không hợp lệ',
          description: 'Thời gian kết thúc dự kiến phải sau thời gian bắt đầu dự kiến.',
        }
        setFormError(errObj)
        setConflictResult(null)
        message.error(errObj.description)
        scrollToFeedback()
        return
      }
      setChecking(true)
      const data = await maintenanceService.checkAllConflicts(values.vehicleId.trim(), {
        startAt: values.scheduledStartAt.toISOString(),
        endAt: values.scheduledEndAt.toISOString(),
      })
      setConflictResult(data)
      if (data.hasConflict) {
        const total = (data.conflictingSchedules?.length || 0) + (data.conflictingHolds?.length || 0)
        message.warning(
          total > 0
            ? `Phát hiện ${total} lịch / giữ chỗ bị trùng khung giờ.`
            : 'Xe đang không khả dụng trong trạng thái hiện tại.'
        )
      } else {
        message.success('Khung giờ hợp lệ, chưa phát hiện trùng lịch.')
      }
      scrollToFeedback()
    } catch (err) {
      if (err?.errorFields) return
      const parsed = formatMaintenanceError(err, 'Kiểm tra trùng lịch thất bại.')
      setFormError(parsed)
      message.error(parsed.description || parsed.title)
      scrollToFeedback()
    } finally {
      setChecking(false)
    }
  }

  const handleCreate = async (values) => {
    const merged = { ...form.getFieldsValue(true), ...values }
    if (!merged.scheduledEndAt?.isAfter(merged.scheduledStartAt)) {
      const errObj = {
        title: 'Khung thời gian không hợp lệ',
        description: 'Thời gian kết thúc dự kiến phải sau thời gian bắt đầu dự kiến.',
      }
      setFormError(errObj)
      message.error(errObj.description)
      scrollToFeedback()
      return
    }

    const vehicleId = merged.vehicleId.trim()
    const startAt = merged.scheduledStartAt.toISOString()
    const endAt = merged.scheduledEndAt.toISOString()

    setSubmitting(true)
    setFormError(null)
    try {
      const payload = {
        vehicleId,
        standardId: merged.standardId?.trim() || null,
        scheduledStartAt: startAt,
        scheduledEndAt: endAt,
        scheduledAtKm: merged.scheduledAtKm ?? null,
        estimatedCost: merged.estimatedCost ?? null,
        assignedTo: merged.assignedTo?.trim() || null,
        priority: merged.priority || 'MEDIUM',
        notes: merged.notes?.trim() || null,
        createdBy: merged.createdBy?.trim() || createdByDefault || null,
      }
      const created = await maintenanceService.createSchedule(payload)
      message.success('Đã tạo lịch bảo dưỡng thành công.')
      setConflictResult(null)
      setFormError(null)
      onCreated?.(created)
    } catch (err) {
      const parsed = formatMaintenanceError(err, 'Không thể tạo lịch bảo dưỡng.')
      setFormError(parsed)
      message.error(parsed.description || parsed.title)
      if (isConflictError(err)) {
        try {
          const conflicts = await maintenanceService.checkAllConflicts(vehicleId, { startAt, endAt })
          if (conflicts?.hasConflict) {
            setConflictResult(conflicts)
          }
        } catch {
          // Giữ lỗi 409 gốc nếu truy vấn chi tiết phụ không thành công
        }
      }
      scrollToFeedback()
    } finally {
      setSubmitting(false)
    }
  }

  const advancedItems = [
    {
      key: 'extra',
      forceRender: true,
      label: 'Thông tin kỹ thuật & định danh bổ sung (tùy chọn)',
      children: (
        <Row gutter={12}>
          <Col xs={24} md={12}>
            <Form.Item label="Standard UUID" name="standardId">
              <Input placeholder="Để trống nếu không gắn định mức chuẩn" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item label="Người tạo" name="createdBy">
              <Input placeholder="Tên người tạo (VD: owner1)" />
            </Form.Item>
          </Col>
        </Row>
      ),
    },
  ]

  return (
    <div>
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          vehicleId: vehicles[0]?.id || '',
          scheduledStartAt: dayjs().add(2, 'day').hour(8).minute(0).second(0),
          scheduledEndAt: dayjs().add(2, 'day').hour(17).minute(0).second(0),
          priority: 'MEDIUM',
          estimatedCost: 1500000,
          assignedTo: 'Xưởng Dịch Vụ Trung Tâm',
          createdBy: createdByDefault || '',
        }}
        onFinish={handleCreate}
      >
        <Row gutter={12}>
          <Col xs={24}>
            {vehicleOptions.length > 0 && !manualUuid ? (
              <Form.Item
                label={
                  <Space size={12} wrap>
                    <span>Chọn xe bảo dưỡng</span>
                    <Space size={6}>
                      <Switch size="small" checked={manualUuid} onChange={setManualUuid} />
                      <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
                        Nhập UUID thủ công
                      </Typography.Text>
                    </Space>
                  </Space>
                }
                name="vehicleId"
                extra={
                  watchedVehicleId ? (
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      Vehicle UUID: <Typography.Text code copyable>{watchedVehicleId}</Typography.Text>
                    </Typography.Text>
                  ) : null
                }
                rules={[{ required: true, message: 'Vui lòng chọn xe cần bảo dưỡng' }]}
              >
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="Tìm theo biển số, hãng hoặc mẫu xe..."
                  options={vehicleOptions}
                  onChange={handleVehicleChange}
                />
              </Form.Item>
            ) : (
              <Form.Item
                label={
                  <Space size={12} wrap>
                    <span>Mã định danh xe (Vehicle UUID)</span>
                    {vehicleOptions.length > 0 && (
                      <Space size={6}>
                        <Switch size="small" checked={manualUuid} onChange={setManualUuid} />
                        <Typography.Text type="secondary" style={{ fontSize: 12, fontWeight: 400 }}>
                          Nhập UUID thủ công
                        </Typography.Text>
                      </Space>
                    )}
                  </Space>
                }
                name="vehicleId"
                rules={[{ required: true, message: 'Vui lòng nhập Vehicle UUID' }]}
              >
                <Input placeholder="UUID của xe cần bảo dưỡng" onChange={handleVehicleChange} />
              </Form.Item>
            )}
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              label="Thời gian bắt đầu dự kiến"
              name="scheduledStartAt"
              rules={[{ required: true, message: 'Chọn thời điểm bắt đầu' }]}
            >
              <DatePicker showTime={{ format: 'HH:mm', minuteStep: 15 }} style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              label="Thời gian kết thúc dự kiến"
              name="scheduledEndAt"
              rules={[{ required: true, message: 'Chọn thời điểm kết thúc' }]}
            >
              <DatePicker showTime={{ format: 'HH:mm', minuteStep: 15 }} style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
            </Form.Item>
          </Col>

          <Col xs={12} md={6}>
            <Form.Item label="Mức ưu tiên" name="priority">
              <Select
                options={PRIORITY_OPTIONS.map((p) => ({
                  value: p,
                  label: PRIORITY_META[p]?.label || p,
                }))}
              />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Số km dự kiến" name="scheduledAtKm">
              <InputNumber min={0} step={100} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Chi phí dự kiến (VNĐ)" name="estimatedCost">
              <InputNumber min={0} step={50000} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} md={6}>
            <Form.Item label="Xưởng / Kỹ thuật viên" name="assignedTo">
              <Input />
            </Form.Item>
          </Col>

          <Col xs={24}>
            <Form.Item label="Ghi chú hạng mục bảo dưỡng" name="notes">
              <Input.TextArea rows={2} placeholder="Nội dung bảo dưỡng định kỳ, thay dầu, kiểm tra phanh..." />
            </Form.Item>
          </Col>
        </Row>

        <Collapse ghost size="small" style={{ marginBottom: 16 }} items={advancedItems} />

        <div ref={feedbackRef}>
          <ErrorAlertBanner
            error={formError}
            onClose={() => setFormError(null)}
            style={{ marginBottom: 12 }}
          />
          <ConflictSummaryAlert
            conflictResult={conflictResult}
            onClose={() => setConflictResult(null)}
            style={{ marginBottom: 16 }}
          />
        </div>

        <Space wrap>
          <Button icon={<SearchOutlined />} loading={checking} onClick={handleCheckConflict}>
            Kiểm tra trùng lịch xe
          </Button>
          <Button type="primary" htmlType="submit" icon={<PlusOutlined />} loading={submitting}>
            <CalendarOutlined /> Tạo lịch bảo dưỡng
          </Button>
        </Space>
      </Form>
    </div>
  )
}
