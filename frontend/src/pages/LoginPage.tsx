import { useState } from 'react';
import type { FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Loader2, Lock, Sparkles, User } from 'lucide-react';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/request';
import { saveAuth } from '../utils/authStorage';

type Mode = 'login' | 'register';

interface LocationState {
  from?: string;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  // 被路由守卫踢回来时会带着原地址，登录后直接回去；直接访问则默认去简历库
  const from = (location.state as LocationState | null)?.from ?? '/history';

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  const switchMode = (next: Mode) => {
    setMode(next);
    setError('');
    setNotice('');
    setConfirmPassword('');
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setNotice('');

    if (mode === 'register' && password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }

    // 与后端 @Pattern 保持一致：注册密码必须含英文字母。
    // 登录不校验，否则历史弱密码账号会被自己的密码挡住。
    if (mode === 'register' && !/[A-Za-z]/.test(password)) {
      setError('密码必须包含至少一个英文字母');
      return;
    }

    setLoading(true);
    try {
      if (mode === 'login') {
        const result = await authApi.login(username.trim(), password);
        saveAuth(result);
        navigate(from, { replace: true });
      } else {
        await authApi.register(username.trim(), password);
        // 注册接口不返回 token，切回登录让用户登录一次
        setMode('login');
        setPassword('');
        setConfirmPassword('');
        setNotice('注册成功，请登录');
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800">
      <div className="w-full max-w-md">
        {/* 品牌区，与 Layout 侧边栏保持一致 */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white tracking-tight">AI Interview</h1>
            <p className="text-sm text-slate-500 dark:text-slate-400">智能面试助手</p>
          </div>
        </div>

        <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-xl p-8 border border-slate-100 dark:border-slate-700">
          {/* 登录 / 注册 切换 */}
          <div className="flex bg-slate-100 dark:bg-slate-800 rounded-xl p-1 mb-6">
            {(['login', 'register'] as const).map((item) => (
              <button
                key={item}
                type="button"
                onClick={() => switchMode(item)}
                className={`flex-1 py-2 text-sm font-medium rounded-lg transition-colors ${
                  mode === item
                    ? 'bg-white dark:bg-slate-700 text-primary-600 dark:text-primary-400 shadow-sm'
                    : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
                }`}
              >
                {item === 'login' ? '登录' : '注册'}
              </button>
            ))}
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                用户名
              </label>
              <div className="relative">
                <User className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  id="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="3~50 个字符"
                  autoComplete="username"
                  required
                  className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-800 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                密码
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                <input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder={mode === 'login' ? '请输入密码' : '6~64 位，需含英文字母'}
                  autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                  required
                  className="w-full pl-9 pr-3 py-2.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-800 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>
              {mode === 'register' && (
                <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">
                  6~64 位，至少包含一个英文字母
                </p>
              )}
            </div>

            {mode === 'register' && (
              <div>
                <label htmlFor="confirmPassword" className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                  确认密码
                </label>
                <input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="再输入一次"
                  autoComplete="new-password"
                  required
                  className="w-full px-3 py-2.5 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-800 text-slate-800 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </div>
            )}

            {error && <p className="text-sm text-red-500">{error}</p>}
            {notice && <p className="text-sm text-emerald-600 dark:text-emerald-400">{notice}</p>}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg bg-primary-500 hover:bg-primary-600 disabled:opacity-60 text-white font-medium flex items-center justify-center gap-2 transition-colors"
            >
              {loading && <Loader2 className="w-4 h-4 animate-spin" />}
              {mode === 'login' ? '登录' : '注册'}
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-slate-400 dark:text-slate-500 mt-6">
          {mode === 'login' ? '还没有账号？切换到「注册」' : '已有账号？切换到「登录」'}
        </p>
      </div>
    </div>
  );
}
