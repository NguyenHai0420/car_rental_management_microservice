import { useEffect, useMemo } from "react";
import {
  Alert,
  DatePicker,
  Descriptions,
  Form,
  Input,
  Modal,
  Space,
  Tag,
  Typography,
} from "antd";
import dayjs from "dayjs";
import {
  calculateRentalDays,
  formatVnd,
} from "../services/booking";

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
  const [form] = Form.useForm();
  const watchedRange = Form.useWatch("dateRange", form);

  useEffect(() => {
    if (open && vehicle) {
      form.setFieldsValue({
        dateRange: defaultDateRange,
        pickupLocation: vehicle.location || "Chi nhánh trung tâm TP.HCM",
        returnLocation: vehicle.location || "Chi nhánh trung tâm TP.HCM",
        notes: "",
      });
    }
  }, [open, vehicle, defaultDateRange, form]);

  const pricing = useMemo(() => {
    const dailyRate = Number(vehicle?.dailyRate || 0);
    const start = watchedRange?.[0] || defaultDateRange?.[0];
    const end = watchedRange?.[1] || defaultDateRange?.[1];
    const days = calculateRentalDays(start, end);
    const estimatedTotal = dailyRate * days;
    const depositRequired = Math.round(estimatedTotal * 0.3);
    return { dailyRate, days, estimatedTotal, depositRequired };
  }, [vehicle, watchedRange, defaultDateRange]);

  const handleOk = async () => {
    const values = await form.validateFields();
    const [pickupDatetime, returnDatetime] = values.dateRange;
    await onSubmit({
      vehicleId: vehicle.vehicleId,
      pickupDatetime,
      returnDatetime,
      pickupLocation: values.pickupLocation,
      returnLocation: values.returnLocation,
      notes: values.notes,
    });
  };

  return (
    <Modal
      title={
        vehicle
          ? `Đặt thuê xe: ${vehicle.brand || ""} ${vehicle.modelName || ""} (${vehicle.licensePlate || ""})`
          : "Tạo đơn đặt xe"
      }
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={submitting}
      okText="Xác nhận đặt xe"
      cancelText="Đóng"
      width={640}
      destroyOnHidden
    >
      {vehicle && (
        <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
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
                children: <Tag color="geekblue">{vehicle.licensePlate}</Tag>,
              },
              {
                key: "specs",
                label: "Thông số",
                children: `${vehicle.seatCount || 4} chỗ • ${vehicle.fuelType || "Xăng"}`,
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
            column={3}
            items={[
              {
                key: "days",
                label: "Số ngày tính phí",
                children: <Text strong>{pricing.days} ngày</Text>,
              },
              {
                key: "total",
                label: "Tạm tính tổng tiền",
                children: (
                  <Text strong style={{ color: "#1677ff" }}>
                    {formatVnd(pricing.estimatedTotal)}
                  </Text>
                ),
              },
              {
                key: "deposit",
                label: "Tiền cọc (30%)",
                children: (
                  <Text strong type="warning">
                    {formatVnd(pricing.depositRequired)}
                  </Text>
                ),
              },
            ]}
          />

          <Alert
            type="info"
            showIcon
            title="Sau khi tạo đơn, xe sẽ được giữ chỗ trong 15 phút để bạn hoàn tất thanh toán tiền cọc 30%."
          />
        </Space>
      )}
    </Modal>
  );
};
