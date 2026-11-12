import { useEffect, useMemo, useState } from "react";
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
  HistoryOutlined,
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
    } else if (type === "complete") {
      actionForm.setFieldsValue({ notes: "" });
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
        message.success(`Đã xác nhận bàn giao / nhận xe cho đơn ${booking.bookingCode}.`);
      } else if (type === "return") {
        await bookingService.returnVehicle(booking.id, {
          ...values,
          actualReturnDatetime: dayjs(),
        });
        message.success(`Đã xác nhận trả xe cho đơn ${booking.bookingCode}.`);
      } else if (type === "complete") {
        await bookingService.completeBooking(booking.id, values.notes);
        message.success(`Đã hoàn tất chuyến đi ${booking.bookingCode}. Đơn đã được chuyển sang Lịch sử chuyến đi.`);
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
      title: "Tổng tiền thanh toán",
      key: "pricing",
      render: (_, record) => (
        <div>
          <Text strong>{formatVnd(record.estimatedTotal, record.currency)}</Text>
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
                  `/payments?bookingId=${record.id}&code=${record.bookingCode}&amount=${record.estimatedTotal || 0}`
                )
              }
            >
              Thanh toán ngay
            </Button>
          )}
          {record.status === "CONFIRMED" && (
            <Button
              size="small"
              type="primary"
              icon={<CarOutlined />}
              onClick={() => openActionModal("pickup", record)}
            >
              Nhận xe
            </Button>
          )}
          {record.status === "PICKED_UP" && (
            <Button
              size="small"
              type="primary"
              style={{ backgroundColor: "#13c2c2", borderColor: "#13c2c2" }}
              icon={<CheckCircleOutlined />}
              onClick={() => openActionModal("return", record)}
            >
              Trả xe
            </Button>
          )}
          {record.status === "RETURNED" && (
            <Button
              size="small"
              type="primary"
              style={{ backgroundColor: "#52c41a", borderColor: "#52c41a" }}
              icon={<CheckCircleOutlined />}
              onClick={() => openActionModal("complete", record)}
            >
              Hoàn tất
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
          {record.status === "RETURNED" && (
            <Button
              size="small"
              type="primary"
              style={{ backgroundColor: "#52c41a", borderColor: "#52c41a" }}
              icon={<CheckCircleOutlined />}
              onClick={() => openActionModal("complete", record)}
            >
              Hoàn tất
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

  const activeMyBookings = useMemo(() => {
    return (myBookings || []).filter(
      (b) =>
        b.status !== "CANCELLED" &&
        b.status !== "COMPLETED"
    );
  }, [myBookings]);

  const tabItems = [
    {
      key: "my-bookings",
      label: (
        <span>
          Đơn Đặt Xe Của Tôi {activeMyBookings.length > 0 ? `(${activeMyBookings.length})` : ""}
        </span>
      ),
      children: (
        <Card
          title="Đơn thuê xe đang thực hiện"
          extra={
            <Space>
              <Link to="/profile">
                <Button icon={<HistoryOutlined />}>
                  Lịch sử chuyến đi
                </Button>
              </Link>
              <Button
                icon={<ReloadOutlined />}
                onClick={loadMyBookings}
                loading={myLoading}
              >
                Làm mới
              </Button>
            </Space>
          }
        >
          <Table
            rowKey="id"
            columns={myBookingColumns}
            dataSource={activeMyBookings}
            loading={myLoading}
            pagination={{ pageSize: 8 }}
            locale={{
              emptyText: (
                <Empty
                  description={
                    <div>
                      <Text strong style={{ display: "block", marginBottom: 4 }}>
                        Hiện bạn không có đơn thuê xe nào đang hoạt động
                      </Text>
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        Các chuyến đi đã hoàn thành hoặc bị hủy được lưu trữ tại{" "}
                        <Link to="/profile">Lịch sử chuyến đi</Link>. Bạn có thể tìm và đặt xe mới tại{" "}
                        <Link to="/">Trang chủ</Link>.
                      </Text>
                    </div>
                  }
                />
              ),
            }}
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
              ? `Xác nhận nhận xe (Pick-up) — ${actionModal.booking?.bookingCode || ""}`
              : actionModal.type === "return"
                ? `Xác nhận trả xe (Return) — ${actionModal.booking?.bookingCode || ""}`
                : `Hoàn tất chuyến đi — ${actionModal.booking?.bookingCode || ""}`
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

          {actionModal.type === "complete" && (
            <>
              <Alert
                type="success"
                showIcon
                style={{ marginBottom: 16 }}
                message="Xác nhận hoàn tất chuyến đi"
                description="Sau khi hoàn tất, chuyến đi sẽ kết thúc và tự động được lưu trữ vào mục Lịch sử chuyến đi của bạn."
              />
              <Form.Item name="notes" label="Ghi chú hoàn tất / Phản hồi trải nghiệm">
                <Input.TextArea
                  rows={3}
                  placeholder="Nhập ghi chú phản hồi (nếu có): chuyến đi an toàn, xe chạy tốt..."
                />
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
                    { title: "Chờ thanh toán" },
                    { title: "Đã xác nhận" },
                    { title: "Đang thuê" },
                    { title: "Đã trả xe" },
                    { title: "Hoàn tất" },
                  ]}
                />
              )}

              {/* Thao tác cập nhật trạng thái đơn */}
              {selectedBooking.status !== "CANCELLED" && selectedBooking.status !== "COMPLETED" && (
                <Card
                  size="small"
                  style={{
                    backgroundColor: "#f6ffed",
                    borderColor: "#b7eb8f",
                    borderRadius: 8,
                  }}
                >
                  <Space style={{ width: "100%", justifyContent: "space-between" }} wrap>
                    <div>
                      <Text strong style={{ color: "#237804" }}>
                        Cập nhật tiến độ chuyến đi:
                      </Text>{" "}
                      <Text type="secondary">
                        {selectedBooking.status === "CONFIRMED" && "Xe đã sẵn sàng bàn giao. Hãy bấm nhận xe khi đã nhận chìa khóa."}
                        {selectedBooking.status === "PICKED_UP" && "Bạn đang trong chuyến đi. Bấm trả xe khi hoàn thành lộ trình."}
                        {selectedBooking.status === "RETURNED" && "Đã nhận lại xe. Bấm hoàn tất để kết thúc và lưu lịch sử."}
                      </Text>
                    </div>
                    <Space wrap>
                      {selectedBooking.status === "CONFIRMED" && (
                        <Button
                          type="primary"
                          icon={<CarOutlined />}
                          onClick={() => openActionModal("pickup", selectedBooking)}
                        >
                          Xác nhận nhận xe
                        </Button>
                      )}
                      {selectedBooking.status === "PICKED_UP" && (
                        <Button
                          type="primary"
                          style={{ backgroundColor: "#13c2c2", borderColor: "#13c2c2" }}
                          icon={<CheckCircleOutlined />}
                          onClick={() => openActionModal("return", selectedBooking)}
                        >
                          Xác nhận trả xe
                        </Button>
                      )}
                      {selectedBooking.status === "RETURNED" && (
                        <Button
                          type="primary"
                          style={{ backgroundColor: "#52c41a", borderColor: "#52c41a" }}
                          icon={<CheckCircleOutlined />}
                          onClick={() => openActionModal("complete", selectedBooking)}
                        >
                          Hoàn tất chuyến đi
                        </Button>
                      )}
                    </Space>
                  </Space>
                </Card>
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
                    label: "Tổng tiền thanh toán",
                    children: (
                      <Text strong style={{ color: "#1677ff", fontSize: 15 }}>
                        {formatVnd(
                          selectedBooking.estimatedTotal,
                          selectedBooking.currency
                        )}
                      </Text>
                    ),
                  },
                  {
                    key: "paymentAction",
                    label: "Thanh toán",
                    children: selectedBooking.status === "PENDING" ? (
                      <Button
                        type="primary"
                        size="small"
                        icon={<CreditCardOutlined />}
                        onClick={() => {
                          setDrawerOpen(false);
                          navigate(
                            `/payments?bookingId=${selectedBooking.id}&code=${selectedBooking.bookingCode}&amount=${selectedBooking.estimatedTotal || 0}`
                          );
                        }}
                      >
                        Thanh toán ngay để xác nhận
                      </Button>
                    ) : (
                      <Tag color="success">Đã thanh toán đủ</Tag>
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
