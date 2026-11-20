import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { isLoggedIn } from '../utils/authStorage';

/**
 * 路由守卫：未登录时重定向到 /login，并记住原本要去的地址。
 * 登录成功后 LoginPage 会读 location.state.from 跳回去。
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();

  if (!isLoggedIn()) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }

  return <>{children}</>;
}
