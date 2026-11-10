import {
  Navigate,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Layout,
  Menu,
  Result,
  Space,
  Tag,
  Typography,
} from "antd";
import {
  BellOutlined,
  CalendarOutlined,
  CarOutlined,
  CreditCardOutlined,
  LoginOutlined,
  LogoutOutlined,
  ToolOutlined,
  UserAddOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { useAuth } from "./providers/auth-context";
import { PrivateRoute } from "./routes/PrivateRoute";
import { BookingListPage } from "../features/bookings/pages/BookingListPage";

const { Header, Content, Footer } = Layout;
const { Text } = Typography;

const ModulePlaceholder = ({ title, owner, serviceName }) => {
  const [searchParams] = useSearchParams();
  const bookingId = searchParams.get("bookingId");
  const bookingCode = searchParams.get("code");
  const deposit = searchParams.get("deposit");

  return (
    <Card>
      <Result
        status="info"
        title={title}
        subTitle={`Phân hệ giao diện ${serviceName} (${owner} phụ trách) đã được định tuyến sẵn qua API Gateway (:8080) và bảo vệ bởi Keycloak JWT.`}
        extra={
          bookingId && (
            <Descriptions
              bordered
              size="small"
              column={1}
              style={{ maxWidth: 520, margin: "0 auto", textAlign: "left" }}
              items={[
                {
                  key: "code",
                  label: "Mã đơn chuyển sang từ Booking",
                  children: <Text strong>{bookingCode || bookingId}</Text>,
                },
                {
                  key: "bookingId",
                  label: "Booking UUID",
                  children: <Text code>{bookingId}</Text>,
                },
                {
                  key: "deposit",
                  label: "Số tiền cọc cần thanh toán",
                  children: (
                    <Text type="danger" strong>
                      {Number(deposit || 0).toLocaleString("vi-VN")} ₫
                    </Text>
                  ),
                },
              ]}
            />
          )
        }
      />
    </Card>
  );
};

function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const { authenticated, authError, user, roles, login, register, logout } =
    useAuth();

  const selectedKey =
    [
      "/bookings",
      "/vehicles",
      "/payments",
      "/maintenance",
      "/notifications",
      "/profile",
    ].find((path) => location.pathname.startsWith(path)) || "/bookings";

  const menuItems = [
    {
      key: "/bookings",
      icon: <CalendarOutlined />,
      label: "Đặt Xe",
    },
    {
      key: "/vehicles",
      icon: <CarOutlined />,
      label: "Đội Xe",
    },
    {
      key: "/payments",
      icon: <CreditCardOutlined />,
      label: "Thanh Toán",
    },
    {
      key: "/maintenance",
      icon: <ToolOutlined />,
      label: "Bảo Dưỡng",
    },
    {
      key: "/notifications",
      icon: <BellOutlined />,
      label: "Thông Báo",
    },
    {
      key: "/profile",
      icon: <UserOutlined />,
      label: "Hồ Sơ",
    },
  ];

  return (
    <Layout style={{ minHeight: "100vh" }}>
      <Header
        style={{
          position: "sticky",
          top: 0,
          zIndex: 100,
          width: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          padding: "0 24px",
          background: "#001529",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 24,
            flex: 1,
            minWidth: 0,
          }}
        >
          <div
            onClick={() => navigate("/bookings")}
            style={{
              color: "#fff",
              fontWeight: 700,
              fontSize: 18,
              cursor: "pointer",
              whiteSpace: "nowrap",
              display: "flex",
              alignItems: "center",
              gap: 8,
            }}
          >
            <CarOutlined style={{ color: "#4096ff", fontSize: 22 }} />
            <span>CarRental MS</span>
          </div>

          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ flex: 1, minWidth: 0 }}
          />
        </div>

        <Space size="small">
          {authenticated && user ? (
            <>
              <Text style={{ color: "#fff" }}>{user.fullName}</Text>
              {roles.map((role) => (
                <Tag
                  key={role}
                  color={
                    role === "ADMIN"
                      ? "red"
                      : role === "VEHICLE_OWNER"
                        ? "gold"
                        : "blue"
                  }
                >
                  {role}
                </Tag>
              ))}
              <Button
                size="small"
                icon={<LogoutOutlined />}
                onClick={() => logout()}
              >
                Đăng xuất
              </Button>
            </>
          ) : (
            <>
              <Button
                size="small"
                icon={<UserAddOutlined />}
                onClick={() => register()}
              >
                Đăng ký
              </Button>
              <Button
                size="small"
                type="primary"
                icon={<LoginOutlined />}
                onClick={() => login()}
              >
                Đăng nhập SSO
              </Button>
            </>
          )}
        </Space>
      </Header>

      <Content
        style={{
          maxWidth: 1280,
          width: "100%",
          margin: "0 auto",
          padding: "24px",
        }}
      >
        {authError && (
          <Alert
            type="info"
            showIcon
            closable
            style={{ marginBottom: 16 }}
            title={authError}
          />
        )}

        <Routes>
          <Route path="/" element={<Navigate to="/bookings" replace />} />
          <Route path="/bookings" element={<BookingListPage />} />
          <Route
            path="/vehicles"
            element={
              <ModulePlaceholder
                title="Quản lý Đội Xe (Vehicle Service)"
                owner="Quốc"
                serviceName="vehicle-service (:8081)"
              />
            }
          />
          <Route
            path="/payments"
            element={
              <PrivateRoute>
                <ModulePlaceholder
                  title="Cổng Thanh Toán & Đặt Cọc (Payment Service)"
                  owner="Uyên"
                  serviceName="payment-service (:8084)"
                />
              </PrivateRoute>
            }
          />
          <Route
            path="/maintenance"
            element={
              <PrivateRoute allowedRoles={["ADMIN", "VEHICLE_OWNER"]}>
                <ModulePlaceholder
                  title="Quản lý Bảo Dưỡng & Đăng Kiểm (Maintenance Service)"
                  owner="Phong"
                  serviceName="maintenance-service (:8085)"
                />
              </PrivateRoute>
            }
          />
          <Route
            path="/notifications"
            element={
              <PrivateRoute>
                <ModulePlaceholder
                  title="Trung Tâm Thông Báo (Notification Service)"
                  owner="Phong"
                  serviceName="notification-service (:8086)"
                />
              </PrivateRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <PrivateRoute>
                <ModulePlaceholder
                  title="Hồ Sơ Khách Hàng & GPLX (Customer Service)"
                  owner="DA"
                  serviceName="customer-service (:8082)"
                />
              </PrivateRoute>
            }
          />
          <Route path="*" element={<Navigate to="/bookings" replace />} />
        </Routes>
      </Content>

      <Footer style={{ textAlign: "center", color: "#8c8c8c" }}>
        Car Rental Management Microservice System • Keycloak SSO • Spring Cloud
        Gateway • CQRS Booking Engine
      </Footer>
    </Layout>
  );
}

export default App;
