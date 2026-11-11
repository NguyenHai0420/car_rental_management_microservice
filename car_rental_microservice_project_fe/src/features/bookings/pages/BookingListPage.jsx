import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Steps,
  Table,
  Tabs,
  Tag,
  Timeline,
  Typography,
} from "antd";
import {
  CarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CreditCardOutlined,
  EyeOutlined,
  ReloadOutlined,
  SwapRightOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import { useAuth } from "../../../app/providers/auth-context";
import { useBookingOperations } from "../hooks/bookingDetail";
import {
  BOOKING_STATUS_CONFIG,
  bookingService,
  formatDateTime,
  formatVnd,
} from "../services/booking";

const { Title, Text } = Typography;

export const BookingListPage = () => {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { authenticated, hasRole, login } = useAuth();

  const isStaff = authenticated && hasRole("ADMIN", "VEHICLE_OWNER");
  const activeTab = searchParams.get("tab") || "my-bookings";

  const [staffStatusFilter, setStaffStatusFilter] = useState("ALL");
  const [drawerOpen, setDrawerOpen] = useState(false);

  const [actionModal, setActionModal] = useState({
    type: null,
    booking: null,
    submitting: false,
  });
  const [actionForm] = Form.useForm();

  const {
    myBookings,
    myLoading,
    loadMyBookings,
    staffPage,
    staffLoading,
    loadStaffBookings,
    selectedBooking,
    detailLoading,
    loadBookingDetail,
  } = useBookingOperations();

  useEffect(() => {
    if (authenticated && activeTab === "my-bookings") {
      loadMyBookings().catch((err) =>
        message.error(err.userMessage || "Không tải được danh sách đơn của bạn")
      );
    }
  }, [authenticated, activeTab, loadMyBookings, message]);

  useEffect(() => {
    if (isStaff && activeTab === "manage") {
      loadStaffBookings({
        status: staffStatusFilter,
        page: 0,
        size: 10,
      }).catch((err) =>
        message.error(err.userMessage || "Không tải được danh sách quản lý đơn")
      );
    }
  }, [isStaff, activeTab, staffStatusFilter, loadStaffBookings, message]);

  const handleTabChange = (key) => {
    setSearchParams({ tab: key });
  };

  const openDetailDrawer = async (record) => {
    setDrawerOpen(true);
    try {
      await loadBookingDetail(record.id);
    } catch (err) {
      message.error(err.userMessage || "Không thể tải chi tiết đơn đặt xe.");
    }
  };

  const openActionModal = (type, booking) => {
    actionForm.resetFields();
    if (type === "pickup") {
      actionForm.setFieldsValue({ pickupMileage: 0, notes: "" });
    } else if (type === "return") {
      actionForm.setFieldsValue({
        returnMileage: Number(booking.pickupMileage || 0),
        notes: "",
      });
    } else if (type === "cancel") {
      actionForm.setFieldsValue({ reason: "" });
    }
    setActionModal({ type, booking, submitting: false });
  };

  const handleConfirmAction = async () => {
    const values = await actionForm.validateFields();
    const { type, booking } = actionModal;
    setActionModal((prev) => ({ ...prev, submitting: true }));
    try {
      if (type === "cancel") {
        await bookingService.cancelBooking(booking.id, values.reason);
        message.success(`Đã hủy đơn ${booking.bookingCode} và giải phóng lịch xe.`);
      } else if (type === "pickup") {
        await bookingService.pickupVehicle(booking.id, values);
        message.success(`Đã xác nhận bàn giao xe cho đơn ${booking.bookingCode}.`);
      } else if (type === "return") {
        await bookingService.returnVehicle(booking.id, {
          ...values,
          actualReturnDatetime: dayjs(),
        });
        message.success(`Đã xác nhận nhận lại xe cho đơn ${booking.bookingCode}.`);
      }
      setActionModal({ type: null, booking: null, submitting: false });
      if (activeTab === "my-bookings") {
        await loadMyBookings();
      } else if (activeTab === "manage") {
        await loadStaffBookings({
          status: staffStatusFilter,
          page: staffPage.number,
          size: staffPage.size,
        });
      }
      if (drawerOpen && selectedBooking?.id === booking.id) {
        await loadBookingDetail(booking.id);
      }
    } catch (err) {
      message.error(err.userMessage || "Thao tác cập nhật đơn thất bại.");
      setActionModal((prev) => ({ ...prev, submitting: false }));
    }
  };

  const renderStatusTag = (status) => {
    const cfg = BOOKING_STATUS_CONFIG[status] || {
      label: status,
      color: "default",
    };
    return <Tag color={cfg.color}>{cfg.label}</Tag>;
  };

  const myBookingColumns = [
    {
      title: "Mã đơn",
      dataIndex: "bookingCode",
      key: "bookingCode",
      render: (code) => <Text strong>{code}</Text>,
    },
    {
      title: "Xe thuê",
      dataIndex: "vehicleInfo",
      key: "vehicleInfo",
      render: (info, record) => info || record.vehicleId,
    },
    {
      title: "Nhận xe — Trả xe",
      key: "schedule",
      render: (_, record) => (
        <Space size={4}>
          <Text>{formatDateTime(record.pickupDatetime)}</Text>
          <SwapRightOutlined style={{ color: "#8c8c8c" }} />
          <Text>{formatDateTime(record.returnDatetime)}</Text>
        </Space>
      ),
    },
    {
      title: "Tổng tiền / Cọc (30%)",
      key: "pricing",
      render: (_, record) => (
        <div>
          <div>
            <Text strong>{formatVnd(record.estimatedTotal, record.currency)}</Text>
          </div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Cọc: {formatVnd(record.depositRequired, record.currency)}
          </Text>
        </div>
      ),
    },
    {
      title: "Trạng thái",
      dataIndex: "status",
      key: "status",
      render: renderStatusTag,
    },
    {
      title: "Thao tác",
      key: "actions",
      render: (_, record) => (
        <Space wrap>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetailDrawer(record)}
          >
            Chi tiết
          </Button>
          {record.status === "PENDING" && (
            <Button
              size="small"
              type="primary"
              icon={<CreditCardOutlined />}
              onClick={() =>
                navigate(
                  `/payments?bookingId=${record.id}&code=${record.bookingCode}&deposit=${record.depositRequired || 0}`
                )
              }
            >
              Thanh toán cọc
            </Button>
          )}
          {(record.status === "PENDING" || record.status === "CONFIRMED") && (
            <Button
              size="small"
              danger
              icon={<CloseCircleOutlined />}
              onClick={() => openActionModal("cancel", record)}
            >
              Hủy đơn
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const staffColumns = [
    {
      title: "Mã đơn",
      dataIndex: "bookingCode",
      key: "bookingCode",
      render: (code) => <Text strong>{code}</Text>,
    },
    {
      title: "Khách hàng",
      key: "customer",
      render: (_, record) => (
        <div>
          <div>{record.customerName || "Khách hàng"}</div>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.customerEmail || ""}
          </Text>
        </div>
      ),
    },
    {
      title: "Xe thuê",
      dataIndex: "vehicleInfo",
      key: "vehicleInfo",
    },
    {
      title: "Lịch thuê",
      key: "schedule",
      render: (_, record) => (
        <div style={{ fontSize: 13 }}>
          <div>Nhận: {formatDateTime(record.pickupDatetime)}</div>
          <div>Trả: {formatDateTime(record.returnDatetime)}</div>
        </div>
      ),
    },
    {
      title: "Tổng tạm tính",
      dataIndex: "estimatedTotal",
      key: "estimatedTotal",
      render: (val, record) => formatVnd(val, record.currency),
    },
    {
      title: "Trạng thái",
      dataIndex: "status",
      key: "status",
      render: renderStatusTag,
    },
    {
      title: "Vận hành",
      key: "ops",
      render: (_, record) => (
        <Space wrap>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => openDetailDrawer(record)}
          >
            Chi tiết
          </Button>
          {record.status === "CONFIRMED" && (
            <Button
              size="small"
              type="primary"
              icon={<CarOutlined />}
              onClick={() => openActionModal("pickup", record)}
            >
              Giao xe
            </Button>
          )}
          {record.status === "PICKED_UP" && (
            <Button
              size="small"
              type="primary"
              style={{ backgroundColor: "#13c2c2" }}
              icon={<CheckCircleOutlined />}
              onClick={() => openActionModal("return", record)}
            >
              Nhận trả xe
            </Button>
          )}
          {(record.status === "PENDING" || record.status === "CONFIRMED") && (
            <Button
              size="small"
              danger
              onClick={() => openActionModal("cancel", record)}
            >
              Hủy
            </Button>
          )}
        </Space>
      ),
    },
  ];

  if (!authenticated) {
    return (
      <div style={{ padding: "40px 0" }}>
        <Card
          style={{
            maxWidth: 500,
            margin: "0 auto",
            textAlign: "center",
            borderRadius: 12,
            boxShadow: "0 4px 12px rgba(0,0,0,0.05)",
          }}
        >
          <Empty
            description={
              <div>
                <Title level={4} style={{ marginBottom: 8 }}>
                  Đơn thuê của tôi
                </Title>
                <Text type="secondary">
                  Vui lòng đăng nhập để xem lịch sử đặt xe và quản lý các đơn thuê của bạn.
                </Text>
              </div>
            }
          >
            <Button
              type="primary"
              size="large"
              onClick={login}
              style={{
                marginTop: 16,
                background: "linear-gradient(135deg, #ff8a1f, #ff6a1a)",
                borderColor: "#ff6a1a",
              }}
            >
              Đăng nhập ngay
            </Button>
          </Empty>
        </Card>
      </div>
    );
  }

  const tabItems = [
    {
      key: "my-bookings",
      label: "Đơn Đặt Xe Của Tôi",
      children: (
        <Card
          title="Lịch sử đặt thuê xe của bạn"
          extra={
            <Button
              icon={<ReloadOutlined />}
              onClick={loadMyBookings}
              loading={myLoading}
            >
              Làm mới
            </Button>
          }
        >
          <Table
            rowKey="id"
            columns={myBookingColumns}
            dataSource={myBookings}
            loading={myLoading}
            pagination={{ pageSize: 8 }}
          />
        </Card>
      ),
    },
  ];

  if (isStaff) {
    tabItems.push({
      key: "manage",
      label: "Quản Lý Vận Hành (Owner / Admin)",
      children: (
        <Card
          title="Điều phối & Bàn giao xe toàn hệ thống"
          extra={
            <Space>
              <Select
                value={staffStatusFilter}
                onChange={setStaffStatusFilter}
                style={{ width: 180 }}
                options={[
                  { value: "ALL", label: "Tất cả trạng thái" },
                  ...Object.entries(BOOKING_STATUS_CONFIG).map(
                    ([key, cfg]) => ({
                      value: key,
                      label: cfg.label,
                    })
                  ),
                ]}
              />
              <Button
                icon={<ReloadOutlined />}
                loading={staffLoading}
                onClick={() =>
                  loadStaffBookings({
                    status: staffStatusFilter,
                    page: staffPage.number,
                    size: staffPage.size,
                  })
                }
              >
                Làm mới
              </Button>
            </Space>
          }
        >
          <Table
            rowKey="id"
            columns={staffColumns}
            dataSource={staffPage.content}
            loading={staffLoading}
            pagination={{
              current: staffPage.number + 1,
              pageSize: staffPage.size,
              total: staffPage.totalElements,
              onChange: (page, pageSize) =>
                loadStaffBookings({
                  status: staffStatusFilter,
                  page: page - 1,
                  size: pageSize,
                }),
            }}
          />
        </Card>
      ),
    });
  }

  const currentStep =
    selectedBooking && BOOKING_STATUS_CONFIG[selectedBooking.status]
      ? BOOKING_STATUS_CONFIG[selectedBooking.status].step
      : 0;

  return (
    <div>
      {isStaff && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="Khu vực Quản trị & Điều phối"
          description={
            <span>
              Bạn đang đăng nhập với tài khoản Quản trị / Nhân viên. Để duyệt và điều phối đơn xe toàn hệ thống, vui lòng truy cập{" "}
              <Link to="/vehicles/bookings" style={{ fontWeight: 600 }}>
                Trang Quản lý đơn thuê xe ➔
              </Link>
            </span>
          }
        />
      )}

      <Tabs
        activeKey={activeTab}
        onChange={handleTabChange}
        items={tabItems}
        size="large"
      />

      <Modal
        title={
          actionModal.type === "cancel"
            ? `Hủy đơn đặt xe ${actionModal.booking?.bookingCode || ""}`
            : actionModal.type === "pickup"
              ? `Bàn giao xe (Pick-up) — ${actionModal.booking?.bookingCode || ""}`
              : `Nhận trả xe (Return) — ${actionModal.booking?.bookingCode || ""}`
        }
        open={Boolean(actionModal.type)}
        confirmLoading={actionModal.submitting}
        onCancel={() =>
          setActionModal({ type: null, booking: null, submitting: false })
        }
        onOk={handleConfirmAction}
        okText="Xác nhận"
        cancelText="Đóng"
        destroyOnHidden
      >
        <Form form={actionForm} layout="vertical">
          {actionModal.type === "cancel" && (
            <Form.Item
              name="reason"
              label="Lý do hủy đơn"
              rules={[{ required: true, message: "Vui lòng nhập lý do hủy đơn" }]}
            >
              <Input.TextArea
                rows={3}
                placeholder="Nhập lý do hủy để lưu lịch sử trạng thái..."
              />
            </Form.Item>
          )}

          {actionModal.type === "pickup" && (
            <>
              <Form.Item
                name="pickupMileage"
                label="Số ODO đồng hồ khi giao xe (km)"
                rules={[
                  { required: true, message: "Vui lòng nhập số km khi giao xe" },
                ]}
              >
                <InputNumber min={0} style={{ width: "100%" }} />
              </Form.Item>
              <Form.Item name="notes" label="Ghi chú tình trạng xe khi bàn giao">
                <Input.TextArea rows={2} placeholder="Xăng đầy bình, ngoại thất tốt..." />
              </Form.Item>
            </>
          )}

          {actionModal.type === "return" && (
            <>
              <Form.Item
                name="returnMileage"
                label="Số ODO đồng hồ khi khách trả xe (km)"
                rules={[
                  { required: true, message: "Vui lòng nhập số km khi nhận xe" },
                  {
                    validator: (_, val) => {
                      const pickupOdo = Number(
                        actionModal.booking?.pickupMileage || 0
                      );
                      if (val !== undefined && val !== null && val < pickupOdo) {
                        return Promise.reject(
                          new Error(
                            `Số km trả xe không được nhỏ hơn lúc giao (${pickupOdo} km)`
                          )
                        );
                      }
                      return Promise.resolve();
                    },
                  },
                ]}
              >
                <InputNumber min={0} style={{ width: "100%" }} />
              </Form.Item>
              <Form.Item name="notes" label="Biên bản nhận trả xe / Phụ phí phát sinh">
                <Input.TextArea rows={3} placeholder="Tình trạng xe khi nhận lại..." />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>

      <Drawer
        title={
          selectedBooking
            ? `Chi tiết đơn đặt xe: ${selectedBooking.bookingCode}`
            : "Chi tiết đơn đặt xe"
        }
        size="large"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {selectedBooking && (
            <Space orientation="vertical" size="large" style={{ width: "100%" }}>
              {selectedBooking.status === "CANCELLED" ? (
                <Alert
                  type="error"
                  showIcon
                  title="Đơn đặt xe đã bị hủy"
                  description={
                    selectedBooking.cancellationReason ||
                    "Lịch giữ xe đã được giải phóng."
                  }
                />
              ) : (
                <Steps
                  size="small"
                  current={currentStep}
                  items={[
                    { title: "Chờ đặt cọc" },
                    { title: "Đã xác nhận" },
                    { title: "Đang thuê" },
                    { title: "Đã trả xe" },
                    { title: "Hoàn tất" },
                  ]}
                />
              )}

              <Descriptions
                bordered
                size="small"
                column={2}
                items={[
                  {
                    key: "code",
                    label: "Mã đơn",
                    children: <Text strong>{selectedBooking.bookingCode}</Text>,
                  },
                  {
                    key: "status",
                    label: "Trạng thái",
                    children: renderStatusTag(selectedBooking.status),
                  },
                  {
                    key: "customer",
                    label: "Khách hàng",
                    children: selectedBooking.customerName || "—",
                  },
                  {
                    key: "email",
                    label: "Email",
                    children: selectedBooking.customerEmail || "—",
                  },
                  {
                    key: "vehicle",
                    label: "Thông tin xe",
                    span: 2,
                    children:
                      selectedBooking.vehicleInfo || selectedBooking.vehicleId,
                  },
                  {
                    key: "pickup",
                    label: "Nhận xe",
                    children: formatDateTime(selectedBooking.pickupDatetime),
                  },
                  {
                    key: "return",
                    label: "Trả xe dự kiến",
                    children: formatDateTime(selectedBooking.returnDatetime),
                  },
                  {
                    key: "pickupLoc",
                    label: "Điểm nhận xe",
                    children: selectedBooking.pickupLocation || "—",
                  },
                  {
                    key: "returnLoc",
                    label: "Điểm trả xe",
                    children: selectedBooking.returnLocation || "—",
                  },
                  {
                    key: "odo",
                    label: "ODO Giao / Trả",
                    children: `${selectedBooking.pickupMileage ?? "—"} km / ${selectedBooking.returnMileage ?? "—"} km`,
                  },
                  {
                    key: "actualReturn",
                    label: "Trả xe thực tế",
                    children: formatDateTime(
                      selectedBooking.actualReturnDatetime
                    ),
                  },
                  {
                    key: "total",
                    label: "Tổng tạm tính",
                    children: (
                      <Text strong style={{ color: "#1677ff" }}>
                        {formatVnd(
                          selectedBooking.estimatedTotal,
                          selectedBooking.currency
                        )}
                      </Text>
                    ),
                  },
                  {
                    key: "deposit",
                    label: "Tiền cọc (30%)",
                    children: (
                      <Text strong type="warning">
                        {formatVnd(
                          selectedBooking.depositRequired,
                          selectedBooking.currency
                        )}
                      </Text>
                    ),
                  },
                  {
                    key: "notes",
                    label: "Ghi chú",
                    span: 2,
                    children: selectedBooking.notes || "Không có ghi chú",
                  },
                ]}
              />

              <div>
                <Title level={5}>Lịch sử chuyển đổi trạng thái</Title>
                {selectedBooking.statusHistories?.length ? (
                  <Timeline
                    items={selectedBooking.statusHistories.map((h, idx) => ({
                      key: idx,
                      color:
                        h.newStatus === "CANCELLED" ? "red" : "blue",
                      children: (
                        <div>
                          <Space>
                            {h.oldStatus && (
                              <>
                                {renderStatusTag(h.oldStatus)}
                                <SwapRightOutlined />
                              </>
                            )}
                            {renderStatusTag(h.newStatus)}
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {formatDateTime(h.changedAt)}
                            </Text>
                          </Space>
                          {h.reason && (
                            <div style={{ marginTop: 4 }}>
                              <Text type="secondary">{h.reason}</Text>
                            </div>
                          )}
                        </div>
                      ),
                    }))}
                  />
                ) : (
                  <Text type="secondary">Chưa có lịch sử cập nhật.</Text>
                )}
              </div>
            </Space>
          )}
        </Spin>
      </Drawer>
    </div>
  );
};

export default BookingListPage;
