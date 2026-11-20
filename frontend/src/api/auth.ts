import request from './request';
import type { LoginResult } from '../types/auth';

/**
 * 认证接口。
 * request.ts 的响应拦截器已经把 Result 解包，这里拿到的直接是 data。
 */
export const authApi = {
  login: (username: string, password: string) =>
    request.post<LoginResult>('/api/auth/login', { username, password }),

  register: (username: string, password: string) =>
    request.post<void>('/api/auth/register', { username, password }),
};
