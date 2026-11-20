/**
 * 认证相关的类型定义。
 * 与后端 interview.guide.modules.auth.dto 下的 LoginRequest / LoginResponse 一一对应。
 */

/** POST /api/auth/login 响应里的 data */
export interface LoginResult {
  /** JWT 字符串，三段式 */
  token: string;
  /** 固定为 Bearer，拼 Authorization 头时用 */
  tokenType: string;
  /** 有效期（秒） */
  expiresIn: number;
  userId: number;
  username: string;
}

/** 本地缓存的最小用户信息，只用于界面展示，不含任何凭证 */
export interface AuthUser {
  userId: number;
  username: string;
}
