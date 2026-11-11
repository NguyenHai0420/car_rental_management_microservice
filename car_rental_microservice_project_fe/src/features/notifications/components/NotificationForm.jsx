import { useState } from 'react'
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
  Radio,
  Row,
  Space,
  Switch,
} from 'antd'
import { MailOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { extractApiError, newUuid } from '../../../services/api-helpers.js'
import { notificationService } from '../services/notification.js'

const SUBMIT_LABELS = {
  booking: 'Gửi email xác nhận đặt xe',
  payment: 'Gửi biên nhận thanh toán',
  generic: 'Gửi email thông báo',
}

function buildInitialValues(mode) {
  const refId = newUuid()
  const now = dayjs()
  if (mode === 'booking') {
    return {
      idempotencyKey: `booking-web-${Date.now()}`,
      bookingId: refId,
      bookingCode: `BK-${now.format('YYYYMMDD')}-001`,
      customerEmail: '',
      customerName: 'Nguyen Van A',
      vehicleName: 'Toyota Camry 2.5Q',
      licensePlate: '30A-999.88',
      pickupDatetime: now.add(1, 'day').hour(9).minute(0).second(0),
      returnDatetime: now.add(3, 'day').hour(18).minute(0).second(0),
      pickupLocation: 'Chi nhánh Cầu Giấy, Hà Nội',
      estimatedTotal: 2400000,
      depositRequired: 720000,
      currency: 'VND',
    }
  }
  if (mode === 'payment') {
    return {
      idempotencyKey: `payment-web-${Date.now()}`,
      paymentId: refId,
      bookingId: newUuid(),
      paymentCode: `PAY-${now.format('YYYYMMDD')}-001`,
      bookingCode: `BK-${now.format('YYYYMMDD')}-001`,
      customerEmail: '',
      customerName: 'Nguyen Van A',
      amountPaid: 720000,
      totalAmount: 2400000,
      currency: 'VND',
      paymentMethod: 'PAYOS',
      paidAt: now,
    }
  }
  return {
    idempotencyKey: `email-web-${Date.now()}`,
    recipientEmail: '',
    recipientName: 'Khách hàng',
    subject: '[CarRental] Thông báo từ bộ phận chăm sóc khách hàng',
    body: '<h3>Xin chào Quý khách,</h3><p>Đây là email thông báo chính thức từ hệ thống CarRental.</p>',
    html: true,
    sourceService: 'admin-console',
    sourceEvent: 'ManualTestEmail',
    referenceId: refId,
    maxRetries: 3,
  }
}

function toPayload(mode, values) {
  if (mode === 'booking') {
    return {
      ...values,
      pickupDatetime: values.pickupDatetime ? values.pickupDatetime.format('YYYY-MM-DDTHH:mm:ss') : null,
      returnDatetime: values.returnDatetime ? values.returnDatetime.format('YYYY-MM-DDTHH:mm:ss') : null,
    }
  }
  if (mode === 'payment') {
    return {
      ...values,
      bookingId: values.bookingId?.trim() || null,
      paidAt: values.paidAt ? values.paidAt.format('YYYY-MM-DDTHH:mm:ss') : null,
    }
  }
  return {
    ...values,
    referenceId: values.referenceId?.trim() || null,
  }
}

export function NotificationForm({ onSubmitted }) {
  const { message } = App.useApp()
  const [mode, setMode] = useState('booking')
  const [submitting, setSubmitting] = useState(false)
  const [errorText, setErrorText] = useState('')
  const [form] = Form.useForm()

  const handleModeChange = (e) => {
    const nextMode = e.target.value
    const currentEmail = form.getFieldValue('customerEmail') || form.getFieldValue('recipientEmail') || ''
    const nextInitial = buildInitialValues(nextMode)
    if (nextMode === 'generic') {
      nextInitial.recipientEmail = currentEmail
    } else {
      nextInitial.customerEmail = currentEmail
    }
    setMode(nextMode)
    setErrorText('')
    form.setFieldsValue(nextInitial)
  }

  const handleRegenerateKey = () => {
    const prefix = mode === 'booking' ? 'booking' : mode === 'payment' ? 'payment' : 'email'
    const updates = { idempotencyKey: `${prefix}-web-${Date.now()}` }
    if (mode === 'booking') updates.bookingId = newUuid()
    else if (mode === 'payment') updates.paymentId = newUuid()
    else updates.referenceId = newUuid()
    form.setFieldsValue(updates)
    setErrorText('')
  }

  const handleFinish = async (values) => {
    setSubmitting(true)
    setErrorText('')
    const merged = { ...form.getFieldsValue(true), ...values }
    try {
      const payload = toPayload(mode, merged)
      let result
      if (mode === 'booking') {
        result = await notificationService.sendBookingConfirmation(payload)
      } else if (mode === 'payment') {
        result = await notificationService.sendPaymentConfirmation(payload)
      } else {
        result = await notificationService.sendGenericEmail(payload)
      }
      if (result?.data?.idempotentReplay) {
        message.info('Yêu cầu trùng Idempotency Key — hệ thống trả lại bản ghi hiện có và không gửi trùng.')
      } else {
        message.success('Đã tiếp nhận yêu cầu gửi email. Vui lòng kiểm tra trạng thái sau vài giây.')
      }
      onSubmitted?.(result)
    } catch (err) {
      const msg = extractApiError(err, 'Gửi thông báo thất bại.')
      setErrorText(msg)
      message.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  const advancedItems = [
    {
      key: 'advanced',
      forceRender: true,
      label: 'Thông tin tham chiếu & Idempotency (nâng cao)',
      children: (
        <Row gutter={12}>
          <Col xs={24} md={16}>
            <Form.Item
              label="Idempotency Key"
              name="idempotencyKey"
              rules={[{ required: true, message: 'Vui lòng nhập Idempotency Key' }]}
            >
              <Input placeholder="booking-web-..." />
            </Form.Item>
          </Col>
          <Col xs={24} md={8} style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 24 }}>
            <Button icon={<ReloadOutlined />} onClick={handleRegenerateKey}>
              Tạo Key mới
            </Button>
          </Col>

          {mode === 'booking' && (
            <Col xs={24}>
              <Form.Item label="Booking UUID" name="bookingId" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
          )}

          {mode === 'payment' && (
            <>
              <Col xs={24} md={12}>
                <Form.Item label="Payment UUID" name="paymentId" rules={[{ required: true }]}>
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label="Booking UUID (tùy chọn)" name="bookingId">
                  <Input />
                </Form.Item>
              </Col>
            </>
          )}

          {mode === 'generic' && (
            <>
              <Col xs={24} md={8}>
                <Form.Item label="Reference UUID" name="referenceId">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item label="Source Service" name="sourceService">
                  <Input />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item label="Source Event" name="sourceEvent">
                  <Input />
                </Form.Item>
              </Col>
            </>
          )}
        </Row>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Radio.Group value={mode} onChange={handleModeChange} optionType="button" buttonStyle="solid">
          <Radio.Button value="booking">Xác nhận đặt xe</Radio.Button>
          <Radio.Button value="payment">Biên nhận thanh toán</Radio.Button>
          <Radio.Button value="generic">Email tùy chỉnh</Radio.Button>
        </Radio.Group>
      </div>

      {errorText && (
        <Alert
          type="error"
          showIcon
          message={errorText}
          style={{ marginBottom: 16 }}
          closable
          onClose={() => setErrorText('')}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        initialValues={buildInitialValues('booking')}
        onFinish={handleFinish}
      >
        {mode === 'booking' && (
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item
                label="Email khách hàng"
                name="customerEmail"
                rules={[
                  { required: true, message: 'Vui lòng nhập email người nhận' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input prefix={<MailOutlined />} placeholder="khachhang@gmail.com" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Tên khách hàng"
                name="customerName"
                rules={[{ required: true, message: 'Vui lòng nhập tên khách hàng' }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Mã đơn đặt xe" name="bookingCode" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Tên xe" name="vehicleName">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item label="Biển số xe" name="licensePlate">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Thời gian nhận xe" name="pickupDatetime" rules={[{ required: true }]}>
                <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Thời gian trả xe" name="returnDatetime" rules={[{ required: true }]}>
                <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
              </Form.Item>
            </Col>
            <Col xs={24} md={10}>
              <Form.Item label="Địa điểm nhận xe" name="pickupLocation">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item label="Tổng tiền dự kiến" name="estimatedTotal" rules={[{ required: true }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={5}>
              <Form.Item label="Tiền cọc" name="depositRequired">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item label="Tiền tệ" name="currency">
                <Input />
              </Form.Item>
            </Col>
          </Row>
        )}

        {mode === 'payment' && (
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item
                label="Email khách hàng"
                name="customerEmail"
                rules={[
                  { required: true, message: 'Vui lòng nhập email người nhận' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input prefix={<MailOutlined />} placeholder="khachhang@gmail.com" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                label="Tên khách hàng"
                name="customerName"
                rules={[{ required: true, message: 'Vui lòng nhập tên khách hàng' }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Mã giao dịch" name="paymentCode" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Mã đơn đặt xe" name="bookingCode">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Số tiền đã trả" name="amountPaid" rules={[{ required: true }]}>
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Tổng giá trị đơn" name="totalAmount">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Phương thức" name="paymentMethod">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={12} md={6}>
              <Form.Item label="Thời điểm thanh toán" name="paidAt" rules={[{ required: true }]}>
                <DatePicker showTime style={{ width: '100%' }} format="DD/MM/YYYY HH:mm" />
              </Form.Item>
            </Col>
          </Row>
        )}

        {mode === 'generic' && (
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item
                label="Email người nhận"
                name="recipientEmail"
                rules={[
                  { required: true, message: 'Vui lòng nhập email người nhận' },
                  { type: 'email', message: 'Email không đúng định dạng' },
                ]}
              >
                <Input prefix={<MailOutlined />} placeholder="nguoinhan@gmail.com" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item label="Tên người nhận" name="recipientName">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={18}>
              <Form.Item label="Tiêu đề email" name="subject" rules={[{ required: true }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item label="Định dạng HTML" name="html" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={12} md={3}>
              <Form.Item label="Số lần thử tối đa" name="maxRetries">
                <InputNumber min={0} max={10} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24}>
              <Form.Item label="Nội dung email" name="body" rules={[{ required: true }]}>
                <Input.TextArea rows={4} />
              </Form.Item>
            </Col>
          </Row>
        )}

        <Collapse ghost size="small" style={{ marginBottom: 16 }} items={advancedItems} />

        <Space wrap>
          <Button type="primary" htmlType="submit" icon={<SendOutlined />} loading={submitting}>
            {SUBMIT_LABELS[mode]}
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleRegenerateKey} disabled={submitting}>
            Chuẩn bị key mới để gửi tiếp
          </Button>
        </Space>
      </Form>
    </div>
  )
}
