import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  App,
  Button,
  DatePicker,
  Descriptions,
  Divider,
  Form,
  Input,
  Modal,
  Space,
  Spin,
  Steps,
  Tag,
  Typography,
} from "antd";
import {
  CheckCircleOutlined,
  CopyOutlined,
  CreditCardOutlined,
  QrcodeOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import {
  calculateRentalDays,
  formatVnd,
  bookingService,
} from "../services/booking";
import { paymentService } from "../../payments/services/payment";
import { getVehicle } from "../../vehicles/services/vehicle";

const { RangePicker } = DatePicker;
const { Text } = Typography;

export const BookingForm = ({
  open,
  vehicle,
  defaultDateRange,
  submitting,
  onCancel,
  onSubmit,
}) => {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm();

  const [step, setStep] = useState(0);
  const [extraDetail, setExtraDetail] = useState(null);
  const [createdBooking, setCreatedBooking] = useState(null);
  const [paymentInvoice, setPaymentInvoice] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [copiedField, setCopiedField] = useState(null);

  const watchedRange = Form.useWatch("dateRange", form);
  const pollingRef = useRef(null);

  useEffect(() => {
    if (open && vehicle) {
      setStep(0);
      setCreatedBooking(null);
      setPaymentInvoice(null);
      setConfirming(false);
      setCopiedField(null);

      form.setFieldsValue({
        dateRange: defaultDateRange,
        pickupLocation: vehicle.location || "Chi nhánh trung tâm TP.HCM",
        returnLocation: vehicle.location || "Chi nhánh trung tâm TP.HCM",
        notes: "",
      });

      const vid = vehicle.vehicleId || vehicle.id;
      if (vid && !vehicle.licensePlate && !vehicle.plate) {
        getVehicle(vid)
          .then((data) => setExtraDetail(data))
          .catch(() => {});
      } else {
        setExtraDetail(null);
      }
    }
  }, [open, vehicle, defaultDateRange, form]);

  const pricing = useMemo(() => {
    const dailyRate = Number(vehicle?.dailyRate || 0);
    const start = watchedRange?.[0] || defaultDateRange?.[0];
    const end = watchedRange?.[1] || defaultDateRange?.[1];
    const days = calculateRentalDays(start, end);
    const estimatedTotal = dailyRate * days;
    return { dailyRate, days, estimatedTotal };
  }, [vehicle, watchedRange, defaultDateRange]);

  const copyToClipboard = (text, fieldName) => {
    navigator.clipboard.writeText(text);
    setCopiedField(fieldName);
    setTimeout(() => setCopiedField(null), 2500);
  };

  // Bước 1 -> Bước 2: Tạo đơn đặt xe & khởi tạo hóa đơn thanh toán
  const handleProceedToPayment = async () => {
    const values = await form.validateFields();
    const [pickupDatetime, returnDatetime] = values.dateRange;
    const resolvedVehicleId = vehicle?.vehicleId || vehicle?.id;

    try {
      const created = await onSubmit({
        vehicleId: resolvedVehicleId,
        pickupDatetime,
        returnDatetime,
        pickupLocation: values.pickupLocation,
        returnLocation: values.returnLocation,
        notes: values.notes,
      });

      if (!created) return;
      setCreatedBooking(created);

      // Tạo hóa đơn trên payment-service
      const total = Number(created.estimatedTotal || pricing.estimatedTotal || 0);
      const subtotal = Math.round(total / 1.1);
      const tax = total - subtotal;
      try {
        const paymentData = await paymentService.createPayment({
          bookingId: created.id,
          customerId: created.customerId || "00000000-0000-0000-0000-000000000001",
          subtotal,
          taxAmount: tax,
          totalAmount: total,
          depositRequired: 0,
          currency: created.currency || "VND",
          notes: created.vehicleInfo || `Đơn thuê xe ${created.bookingCode}`,
          items: [
            {
              itemType: "RENTAL",
              description: `Chi phí thuê xe: ${created.vehicleInfo || created.bookingCode}`,
              quantity: 1,
              unitPrice: total,
              amount: total,
            },
          ],
        });
        setPaymentInvoice(paymentData);
      } catch (err) {
        console.warn("Could not create invoice on payment-service, using fallback invoice:", err);
        setPaymentInvoice({
          id: `pay-${created.id.slice(0, 8)}`,
          paymentCode: `PAY-${created.bookingCode.replace(/[^a-zA-Z0-9]/g, "")}`,
          totalAmount: total,
        });
      }

      setStep(1);
    } catch {
      // Form validation hoặc tạo booking thất bại đã có message thông báo
    }
  };

  // Hoàn tất xác nhận đơn xe và điều hướng sang trang đơn thuê
  const handleConfirmSuccess = async (bookingId, paymentCode) => {
    setConfirming(true);
    try {
      if (bookingId) {
        await bookingService.confirmBooking(bookingId, paymentCode);
      }
      message.success("Thanh toán thành công! Đơn đặt xe của bạn đã được xác nhận.");
      onCancel();
      navigate("/bookings");
    } catch (err) {
      message.error(err.userMessage || "Xác nhận đơn đặt xe thất bại.");
    } finally {
      setConfirming(false);
    }
  };

  // Lắng nghe SePay xác nhận tiền vào tự động
  useEffect(() => {
    if (step !== 1 || !paymentInvoice?.id || String(paymentInvoice.id).startsWith("pay-")) {
      return undefined;
    }

    let isSubscribed = true;
    pollingRef.current = setInterval(async () => {
      try {
        const updated = await paymentService.getPaymentById(paymentInvoice.id);
        if (
          isSubscribed &&
          updated &&
          (updated.status === "FULLY_PAID" || updated.status === "DEPOSIT_PAID")
        ) {
          clearInterval(pollingRef.current);
          await handleConfirmSuccess(createdBooking?.id, updated.paymentCode || paymentInvoice.paymentCode);
        }
      } catch {
        // Tiếp tục chờ tiền vào
      }
    }, 2500);

    return () => {
      isSubscribed = false;
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [step, paymentInvoice, createdBooking]);

  const ensurePaymentRecorded = async () => {
    let invoiceId = paymentInvoice?.id;
    if (!invoiceId || String(invoiceId).startsWith("pay-")) {
      try {
        const total = Number(createdBooking?.estimatedTotal || pricing.estimatedTotal || 0);
        const subtotal = Math.round(total / 1.1);
        const tax = total - subtotal;
        const newInvoice = await paymentService.createPayment({
          bookingId: createdBooking.id,
          customerId: createdBooking.customerId || "00000000-0000-0000-0000-000000000001",
          subtotal,
          taxAmount: tax,
          totalAmount: total,
          depositRequired: 0,
          currency: createdBooking.currency || "VND",
          notes: createdBooking.vehicleInfo || `Đơn thuê xe ${createdBooking.bookingCode}`,
          items: [
            {
              itemType: "RENTAL",
              description: `Chi phí thuê xe: ${createdBooking.vehicleInfo || createdBooking.bookingCode}`,
              quantity: 1,
              unitPrice: total,
              amount: total,
            },
          ],
        });
        if (newInvoice?.id) {
          invoiceId = newInvoice.id;
          setPaymentInvoice(newInvoice);
        }
      } catch (e) {
        console.warn("Could not create invoice on payment-service:", e);
      }
    }
    if (invoiceId && !String(invoiceId).startsWith("pay-")) {
      await paymentService.simulatePaymentSuccess(invoiceId).catch(() => {});
    }
    return invoiceId;
  };

  // Khách bấm "Xác nhận đã thanh toán" sau khi quét mã
  const handleManualConfirm = async () => {
    if (!createdBooking?.id) return;
    setConfirming(true);
    try {
      await ensurePaymentRecorded();
      await bookingService.confirmBooking(
        createdBooking.id,
        paymentInvoice?.paymentCode || createdBooking.bookingCode
      );
      message.success("Thanh toán thành công! Đơn đặt xe của bạn đã được xác nhận.");
      onCancel();
      navigate("/bookings");
    } catch (err) {
      message.error(err.userMessage || "Xác nhận đơn đặt xe thất bại.");
    } finally {
      setConfirming(false);
    }
  };

  // Mô phỏng thanh toán thành công (cho Demo/Test nhanh)
  const handleSimulateSuccess = async () => {
    if (!createdBooking?.id) return;
    setConfirming(true);
    try {
      await ensurePaymentRecorded();
      await bookingService.confirmBooking(
        createdBooking.id,
        paymentInvoice?.paymentCode || createdBooking.bookingCode
      );
      message.success("Mô phỏng thanh toán thành công! Đơn xe đã chuyển sang trạng thái ĐÃ XÁC NHẬN.");
      onCancel();
      navigate("/bookings");
    } catch (err) {
      message.error(err.userMessage || "Xác nhận đơn đặt xe thất bại.");
    } finally {
      setConfirming(false);
    }
  };

  // Khách chọn để thanh toán sau
  const handlePayLater = () => {
    message.info("Đơn đặt xe đã được tạo. Bạn có thể thanh toán sau tại danh sách Đơn thuê của tôi.");
    onCancel();
    navigate("/bookings");
  };

  const handleModalClose = () => {
    if (step === 1) {
      handlePayLater();
    } else {
      onCancel();
    }
  };

  const displayPlate =
    vehicle?.licensePlate || vehicle?.plate || extraDetail?.licensePlate;
  const displaySeats =
    vehicle?.seatCount || vehicle?.seats || extraDetail?.seatCount || 4;
  const displayFuel =
    vehicle?.fuelType || vehicle?.fuel || extraDetail?.fuelType || "Xăng";

  const totalAmountToPay = Number(
    createdBooking?.estimatedTotal || pricing.estimatedTotal || 0
  );
  const cleanCode = paymentInvoice?.paymentCode
    ? paymentInvoice.paymentCode.replace(/-/g, "")
    : createdBooking?.bookingCode
    ? createdBooking.bookingCode.replace(/[^a-zA-Z0-9]/g, "")
    : "PAYDEMO";
  const transferContent = `SEVQR ${cleanCode}`;
  const directVietQrUrl = `https://img.vietqr.io/image/ICB-0376359204-compact2.png?amount=${totalAmountToPay}&addInfo=${encodeURIComponent(
    transferContent
  )}&accountName=HOANG%20TU%20UYEN`;

  return (
    <Modal
      title={
        vehicle
          ? `Đặt thuê xe: ${vehicle.brand || ""} ${vehicle.modelName || ""}${
              displayPlate ? ` (${displayPlate})` : ""
            }`
          : "Tạo đơn đặt xe"
      }
      open={open}
      onCancel={handleModalClose}
      footer={null}
      width={step === 1 ? 700 : 640}
      destroyOnHidden
    >
      <Steps
        size="small"
        current={step}
        style={{ marginBottom: 20 }}
        items={[
          { title: "Thông tin đặt xe" },
          { title: "Thanh toán VietQR & Xác nhận" },
        ]}
      />

      {vehicle && step === 0 && (
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Descriptions
            bordered
            size="small"
            column={2}
            items={[
              {
                key: "vehicle",
                label: "Mẫu xe",
                children: (
                  <Text strong>
                    {vehicle.brand} {vehicle.modelName}
                  </Text>
                ),
              },
              {
                key: "plate",
                label: "Biển số",
                children: displayPlate ? (
                  <Tag color="geekblue">{displayPlate}</Tag>
                ) : (
                  <Text type="secondary">Chưa cập nhật</Text>
                ),
              },
              {
                key: "specs",
                label: "Thông số",
                children: `${displaySeats} chỗ • ${displayFuel}`,
              },
              {
                key: "rate",
                label: "Đơn giá / ngày",
                children: (
                  <Text type="danger" strong>
                    {formatVnd(pricing.dailyRate)}
                  </Text>
                ),
              },
            ]}
          />

          <Form form={form} layout="vertical">
            <Form.Item
              name="dateRange"
              label="Thời gian nhận xe — trả xe"
              rules={[
                { required: true, message: "Vui lòng chọn thời gian thuê xe" },
                {
                  validator: (_, value) => {
                    if (!value || value.length !== 2) {
                      return Promise.resolve();
                    }
                    if (!value[1].isAfter(value[0])) {
                      return Promise.reject(
                        new Error("Thời gian trả xe phải sau thời gian nhận xe")
                      );
                    }
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <RangePicker
                showTime={{ format: "HH:mm" }}
                format="DD/MM/YYYY HH:mm"
                style={{ width: "100%" }}
                disabledDate={(current) =>
                  current && current < dayjs().startOf("day")
                }
              />
            </Form.Item>

            <Form.Item
              name="pickupLocation"
              label="Địa điểm nhận xe"
              rules={[
                { required: true, message: "Vui lòng nhập địa điểm nhận xe" },
              ]}
            >
              <Input placeholder="Nhập địa chỉ nhận xe..." />
            </Form.Item>

            <Form.Item name="returnLocation" label="Địa điểm trả xe">
              <Input placeholder="Mặc định trùng địa điểm nhận xe nếu để trống" />
            </Form.Item>

            <Form.Item name="notes" label="Ghi chú thêm">
              <Input.TextArea
                rows={2}
                placeholder="Yêu cầu giao xe tận nơi, ghế trẻ em,..."
              />
            </Form.Item>
          </Form>

          <Descriptions
            bordered
            size="small"
            column={2}
            items={[
              {
                key: "days",
                label: "Số ngày tính phí",
                children: <Text strong>{pricing.days} ngày</Text>,
              },
              {
                key: "total",
                label: "Tổng tiền thanh toán (100%)",
                children: (
                  <Text strong style={{ color: "#1677ff", fontSize: 16 }}>
                    {formatVnd(pricing.estimatedTotal)}
                  </Text>
                ),
              },
            ]}
          />

          <Alert
            type="info"
            showIcon
            title="Thanh toán toàn bộ chi phí thuê xe ngay tại bước tiếp theo để hệ thống tự động xác nhận đơn đặt xe."
          />

          <Divider style={{ margin: "12px 0 8px 0" }} />

          <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
            <Button onClick={onCancel} disabled={submitting}>
              Đóng
            </Button>
            <Button
              type="primary"
              size="middle"
              onClick={handleProceedToPayment}
              loading={submitting}
              icon={<CreditCardOutlined />}
              style={{
                background: "linear-gradient(135deg, #ff8a1f, #ff6a1a)",
                borderColor: "#ff6a1a",
                fontWeight: 600,
              }}
            >
              Tiếp tục thanh toán →
            </Button>
          </div>
        </Space>
      )}

      {/* BƯỚC 2: THANH TOÁN TRỰC TIẾP TRÊN MODAL QUA MÃ VIETQR */}
      {step === 1 && createdBooking && (
        <Space direction="vertical" orientation="vertical" size="middle" style={{ width: "100%" }}>
          <Alert
            type="warning"
            showIcon
            message={
              <span>
                Mã đơn: <Text strong>{createdBooking.bookingCode}</Text> — Vui lòng quét mã VietQR bên dưới để thanh toán toàn bộ chi phí. Đơn xe sẽ được <Text strong style={{ color: "#52c41a" }}>XÁC NHẬN NGAY</Text> sau khi hoàn tất.
              </span>
            }
          />

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "230px 1fr",
              gap: 20,
              padding: "16px",
              background: "#fafafa",
              borderRadius: 8,
              border: "1px solid #f0f0f0",
              alignItems: "center",
            }}
          >
            {/* Cột 1: Mã QR Napas247 */}
            <div style={{ textAlign: "center" }}>
              <div
                style={{
                  background: "#fff",
                  padding: 8,
                  borderRadius: 8,
                  boxShadow: "0 2px 8px rgba(0,0,0,0.08)",
                  display: "inline-block",
                }}
              >
                <img
                  src={directVietQrUrl}
                  alt="Mã VietQR VietinBank HOANG TU UYEN"
                  style={{ width: "190px", height: "auto", display: "block" }}
                />
              </div>
              <div style={{ marginTop: 6, fontSize: 12, color: "#8c8c8c" }}>
                <QrcodeOutlined /> Quét bằng mọi app Ngân hàng
              </div>
            </div>

            {/* Cột 2: Thông tin tài khoản nhận tiền */}
            <div>
              <Descriptions
                bordered
                size="small"
                column={1}
                items={[
                  {
                    key: "bank",
                    label: "Ngân hàng",
                    children: <Text strong>VietinBank (ICB)</Text>,
                  },
                  {
                    key: "stk",
                    label: "Số tài khoản",
                    children: (
                      <Space>
                        <Text strong style={{ color: "#fa541c", fontSize: 15 }}>
                          0376359204
                        </Text>
                        <Button
                          size="small"
                          icon={<CopyOutlined />}
                          onClick={() => copyToClipboard("0376359204", "stk")}
                        >
                          {copiedField === "stk" ? "Đã chép" : "Sao chép"}
                        </Button>
                      </Space>
                    ),
                  },
                  {
                    key: "owner",
                    label: "Chủ tài khoản",
                    children: <Text strong>HOANG TU UYEN</Text>,
                  },
                  {
                    key: "amount",
                    label: "Số tiền thanh toán",
                    children: (
                      <Text strong style={{ color: "#52c41a", fontSize: 17 }}>
                        {formatVnd(totalAmountToPay)}
                      </Text>
                    ),
                  },
                  {
                    key: "content",
                    label: "Nội dung chuyển khoản",
                    children: (
                      <Space>
                        <Text code style={{ fontSize: 13, fontWeight: "bold" }}>
                          {transferContent}
                        </Text>
                        <Button
                          size="small"
                          icon={<CopyOutlined />}
                          onClick={() => copyToClipboard(transferContent, "nd")}
                        >
                          {copiedField === "nd" ? "Đã chép" : "Sao chép"}
                        </Button>
                      </Space>
                    ),
                  },
                ]}
              />
            </div>
          </div>

          {/* Thanh radar kiểm tra giao dịch */}
          <div
            style={{
              textAlign: "center",
              padding: "10px 14px",
              background: "#e6f7ff",
              border: "1px solid #91d5ff",
              borderRadius: 6,
              color: "#096dd9",
              fontSize: 13,
            }}
          >
            <Spin size="small" style={{ marginRight: 8 }} />
            <span>
              Đang chờ xác nhận giao dịch từ VietinBank qua SePay... Hệ thống sẽ tự động xác nhận đơn xe ngay khi nhận tiền!
            </span>
          </div>

          <Divider style={{ margin: "10px 0 6px 0" }} />

          {/* Footer nút hành động của Step 1 */}
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              width: "100%",
              flexWrap: "wrap",
              gap: 8,
            }}
          >
            <Button onClick={handlePayLater} disabled={confirming}>
              Để thanh toán sau
            </Button>
            <Space>
              <Button
                onClick={handleSimulateSuccess}
                loading={confirming}
                icon={<ThunderboltOutlined />}
                style={{ color: "#d48806", borderColor: "#faad14" }}
              >
                Mô phỏng thanh toán (Test nhanh)
              </Button>
              <Button
                type="primary"
                onClick={handleManualConfirm}
                loading={confirming}
                icon={<CheckCircleOutlined />}
                style={{ background: "#52c41a", borderColor: "#52c41a", fontWeight: 600 }}
              >
                Xác nhận đã thanh toán
              </Button>
            </Space>
          </div>
        </Space>
      )}
    </Modal>
  );
};
