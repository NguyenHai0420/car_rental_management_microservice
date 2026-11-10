import { useAuth } from '../providers/auth-context'

export function PrivateRoute({ children }) {
  const auth = useAuth()
  if (!auth.ready) return <p role="status">Đang kiểm tra phiên đăng nhập…</p>
  if (!auth.authenticated) return <p>Vui lòng đăng nhập để tiếp tục.</p>
  return children
}
