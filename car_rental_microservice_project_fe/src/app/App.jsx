import {
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useSearchParams,
} from 'react-router-dom'
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
} from 'antd'
import {
  BellOutlined,
  CalendarOutlined,
  CarOutlined,
  CreditCardOutlined,
  HomeOutlined,
  LoginOutlined,
  LogoutOutlined,
  ToolOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuth } from './providers/auth-context'
import { PrivateRoute } from './routes/PrivateRoute'
import { BookingListPage } from '../features/bookings/pages/BookingListPage'
import { VehicleManagementLayout } from '../features/vehicles/pages/VehicleManagementLayout'
import { VehicleCategoryPage } from '../features/vehicles/pages/VehicleCategoryPage'
import { VehicleModelPage } from '../features/vehicles/pages/VehicleModelPage'
import { VehicleDetailPage } from '../features/vehicles/pages/VehicleDetailPage'
import { VehicleFormPage } from '../features/vehicles/pages/VehicleFormPage'
import { VehicleListPage } from '../features/vehicles/pages/VehicleListPage'
import { HomePage } from '../features/home/HomePage'
import '../style/home.css'
import '../style/admin.css'

const { Header, Content, Footer } = Layout
const { Text } = Typography

const ModulePlaceholder = ({ title, owner, serviceName }) => {
  const [searchParams] = useSearchParams()
  const bookingId = searchParams.get('bookingId')
  const bookingCode = searchParams.get('code')
  const deposit = searchParams.get('deposit')

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
              style={{ maxWidth: 520, margin: '0 auto', textAlign: 'left' }}
              items={[
                {
                  key: 'code',
                  label: 'Mã đơn chuyển sang từ Booking',
                  children: <Text strong>{bookingCode || bookingId}</Text>,
                },
                {
                  key: 'bookingId',
                  label: 'Booking UUID',
                  children: <Text code>{bookingId}</Text>,
                },
                {
                  key: 'deposit',
                  label: 'Số tiền cọc cần thanh toán',
                  children: (
                    <Text type="danger" strong>
                      {Number(deposit || 0).toLocaleString('vi-VN')} ₫
                    </Text>
                  ),
                },
              ]}
            />
          )
        }
      />
    </Card>
  )
}

function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { authenticated, authError, user, roles, canManageFleet, login, register, logout } =
    useAuth()

  const selectedKey =
    [
      '/bookings',
      '/vehicles',
      '/payments',
      '/maintenance',
      '/notifications',
      '/profile',
    ].find((path) => location.pathname.startsWith(path)) || '/bookings'

  const menuItems = [
    {
      key: '/',
      icon: <HomeOutlined />,
      label: 'Trang Chủ',
    },
    {
      key: '/bookings',
      icon: <CalendarOutlined />,
      label: 'Đặt Xe',
    },
    ...(canManageFleet
      ? [
          {
            key: '/vehicles',
            icon: <CarOutlined />,
            label: 'Quản Trị Đội Xe',
          },
        ]
      : []),
    {
      key: '/payments',
      icon: <CreditCardOutlined />,
      label: 'Thanh Toán',
    },
    {
      key: '/maintenance',
      icon: <ToolOutlined />,
      label: 'Bảo Dưỡng',
    },
    {
      key: '/notifications',
      icon: <BellOutlined />,
      label: 'Thông Báo',
    },
    {
      key: '/profile',
      icon: <UserOutlined />,
      label: 'Hồ Sơ',
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          background: '#001529',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 24,
            flex: 1,
            minWidth: 0,
          }}
        >
          <div
            onClick={() => navigate('/')}
            style={{
              color: '#fff',
              fontWeight: 700,
              fontSize: 18,
              cursor: 'pointer',
              whiteSpace: 'nowrap',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <CarOutlined style={{ color: '#4096ff', fontSize: 22 }} />
            <span>CarRental MS</span>
          </div>

          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[selectedKey]}
            items={menuItems}
            onClick={({ key }) => navigate(key)}
            style={{ flex: 1, minWidth: 0, borderBottom: 'none' }}
          />
        </div>

        <Space size="middle">
          {authenticated ? (
            <>
              <Space>
                <UserOutlined style={{ color: '#fff' }} />
                <Text style={{ color: '#fff' }}>
                  {user?.fullName || user?.username || 'Người dùng'}
                </Text>
                {roles && roles.length > 0 && (
                  <Tag color="blue">{roles[0]}</Tag>
                )}
              </Space>
              <Button
                size="small"
                danger
                icon={<LogoutOutlined />}
                onClick={() => logout && logout()}
              >
                Đăng xuất
              </Button>
            </>
          ) : (
            <>
              <Button
                size="small"
                icon={<UserAddOutlined />}
                onClick={() => register && register()}
              >
                Đăng ký
              </Button>
              <Button
                size="small"
                type="primary"
                icon={<LoginOutlined />}
                onClick={() => login && login()}
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
          width: '100%',
          margin: '0 auto',
          padding: '24px',
        }}
      >
        {authError && (
          <Alert
            type="info"
            showIcon
            closable
            style={{ marginBottom: 16 }}
            message={authError}
          />
        )}
        <Outlet />
      </Content>

      <Footer style={{ textAlign: 'center', color: '#8c8c8c' }}>
        Car Rental Management Microservice System • Keycloak SSO • Spring Cloud
        Gateway • Feign Client Architecture
      </Footer>
    </Layout>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/vehicles" element={<VehicleManagementLayout />}>
        <Route index element={<VehicleListPage />} />
        <Route path="categories" element={<VehicleCategoryPage />} />
        <Route path="models" element={<VehicleModelPage />} />
        <Route path="new" element={<VehicleFormPage />} />
        <Route path=":id" element={<VehicleDetailPage />} />
        <Route path=":id/edit" element={<VehicleFormPage />} />
      </Route>
      <Route element={<MainLayout />}>
        <Route path="/bookings" element={<BookingListPage />} />
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
            <PrivateRoute allowedRoles={['ADMIN', 'VEHICLE_OWNER']}>
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
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
