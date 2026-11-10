import { Button, Result, Spin } from 'antd'
import { useAuth } from '../providers/auth-context'

export function PrivateRoute({ children, allowedRoles = [] }) {
  const { initialized, ready, authenticated, hasRole, login } = useAuth()
  const isReady = initialized ?? ready

  if (!isReady) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
        <Spin size="large" description="Đang kiểm tra phiên đăng nhập..." />
      </div>
    )
  }

  if (!authenticated) {
    return (
      <Result
        status="403"
        title="Yêu cầu đăng nhập"
        subTitle="Vui lòng đăng nhập qua Keycloak SSO để truy cập chức năng này."
        extra={
          <Button type="primary" onClick={() => login && login()}>
            Đăng nhập ngay
          </Button>
        }
      />
    )
  }

  if (allowedRoles.length > 0 && hasRole && !hasRole(...allowedRoles)) {
    return (
      <Result
        status="403"
        title="Không đủ quyền truy cập"
        subTitle={`Chức năng này yêu cầu một trong các vai trò: ${allowedRoles.join(', ')}.`}
      />
    )
  }

  return children
}

export default PrivateRoute
