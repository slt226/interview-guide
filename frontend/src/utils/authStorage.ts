import type { AuthUser, LoginResult } from '../types/auth';

const TOKEN_KEY = 'ig_token';
const USER_KEY = 'ig_user';

/**
 * 认证凭证的本地存储。
 *
 * 单独成文件而不是放进 api/auth.ts：request.ts 需要读 token，
 * 而 api/auth.ts 又依赖 request.ts，放一起会形成循环依赖。
 * 现在两边都只单向依赖这个模块。
 */
export function saveAuth(result: LoginResult): void {
  localStorage.setItem(TOKEN_KEY, result.token);
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({ userId: result.userId, username: result.username }),
  );
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getCurrentUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    // 手工改坏了就当没登录，不能让整个应用崩在 JSON.parse
    return null;
  }
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function isLoggedIn(): boolean {
  return Boolean(getToken());
}
