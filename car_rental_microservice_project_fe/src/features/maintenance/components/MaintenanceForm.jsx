import { useEffect, useState } from 'react'
import {
  Alert,
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
} from 'antd'
import { CalendarOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { extractApiError, formatDateTime } from '../../../services/api-helpers.js'
import {
  PRIORITY_META,
  PRIORITY_OPTIONS,
  formatVehicleDisplay,
  maintenanceService,
} from '../services/maintenance.js'

export function MaintenanceForm({ vehicles = [], createdByDefault = 'admin', onCreated }) {
  const { message } = App.useApp()
  const [form] = Form.useForm()
  const [submitting, setSubmitting] = useState(false)
  const [checking, setChecking] = useState(false)
  const [errorText, setErrorText] = useState('')
  const [conflictResult, setConflictResult] = useState(null)

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

  const handleSelectVehicle = (vehicleId) => {
    form.setFieldValue('vehicleId', vehicleId || '')
    setConflictResult(null)
  }

  const handleCheckConflict = async () => {
    setErrorText('')
    try {
      const values = await form.validateFields(['vehicleId', 'scheduledStartAt', 'scheduledEndAt'])
      setChecking(true)
      const data = await maintenanceService.checkConflicts(values.vehicleId.trim(), {
        startAt: values.scheduledStartAt.toISOString(),
        endAt: values.scheduledEndAt.toISOString(),
      })
      setConflictResult(data)
      if (data.hasConflict) {
        message.warning(`Phát hiện ${data.conflictingSchedules?.length || 0} lịch bảo dưỡng trùng khung giờ.`)
      } else {
        message.success('Khung giờ hợp lệ, chưa có lịch bảo dưỡng nào trùng.')
      }
    } catch (err) {
      if (err?.errorFields) return
      const msg = extractApiError(err, 'Kiểm tra trùng lịch thất bại.')
      setErrorText(msg)
      message.error(msg)
    } finally {
      setChecking(false)
    }
  }

  const handleCreate = async (values) => {
    setSubmitting(true)
    setErrorText('')
    const merged = { ...form.getFieldsValue(true), ...values }
    try {
      const payload = {
        vehicleId: merged.vehicleId.trim(),
        standardId: merged.standardId?.trim() || null,
        scheduledStartAt: merged.scheduledStartAt.toISOString(),
        scheduledEndAt: merged.scheduledEndAt.toISOString(),
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
      onCreated?.(created)
    } catch (err) {
      const msg = extractApiError(err, 'Không thể tạo lịch bảo dưỡng.')
      setErrorText(msg)
      message.error(msg)
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
              <Input placeholder="admin" />
            </Form.Item>
          </Col>
        </Row>
      ),
    },
  ]

  return (
    <div>
      {errorText && (
        <Alert
          type="error"
          showIcon
          message="Không thể thực hiện thao tác"
          description={errorText}
          style={{ marginBottom: 16 }}
          closable
          onClose={() => setErrorText('')}
        />
      )}

      {conflictResult && (
        <Alert
          type={conflictResult.hasConflict ? 'warning' : 'success'}
          showIcon
          style={{ marginBottom: 16 }}
          message={
            conflictResult.hasConflict
              ? `Phát hiện ${conflictResult.conflictingSchedules?.length || 0} lịch bảo dưỡng bị trùng`
              : 'Khung giờ khả dụng — không có lịch bảo dưỡng nào trùng'
          }
          description={`Khoảng kiểm tra: ${formatDateTime(conflictResult.requestedStartAt)} → ${formatDateTime(conflictResult.requestedEndAt)}`}
          closable
          onClose={() => setConflictResult(null)}
        />
      )}

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
          createdBy: createdByDefault || 'admin',
        }}
        onFinish={handleCreate}
      >
        <Row gutter={12}>
          {vehicleOptions.length > 0 && (
            <Col xs={24} md={13}>
              <Form.Item label="Chọn xe trong hệ thống">
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  placeholder="Tìm theo biển số, hãng hoặc mẫu xe..."
                  options={vehicleOptions}
                  defaultValue={vehicles[0]?.id}
                  onChange={handleSelectVehicle}
                />
              </Form.Item>
            </Col>
          )}
          <Col xs={24} md={vehicleOptions.length > 0 ? 11 : 24}>
            <Form.Item
              label="Mã định danh xe (Vehicle UUID)"
              name="vehicleId"
              rules={[{ required: true, message: 'Vui lòng chọn hoặc nhập Vehicle UUID' }]}
            >
              <Input placeholder="UUID của xe cần bảo dưỡng" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              label="Thời gian bắt đầu dự kiến"
              name="scheduledStartAt"
              rules={[{ required: true, message: 'Chọn thời điểm bắt đầu' }]}
            >
              <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              label="Thời gian kết thúc dự kiến"
              name="scheduledEndAt"
              rules={[{ required: true, message: 'Chọn thời điểm kết thúc' }]}
            >
              <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
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
