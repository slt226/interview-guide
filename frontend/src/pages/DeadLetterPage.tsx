import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Inbox, Loader2, RefreshCw, Trash2 } from 'lucide-react';
import { deadLetterApi } from '../api/deadLetter';
import { getErrorMessage } from '../api/request';
import type { DeadLetterItem, DeadLetterStatus } from '../types/deadLetter';

const STATUS_META: Record<DeadLetterStatus, { label: string; className: string }> = {
  DEAD: { label: '待处理', className: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300' },
  REPLAYED: { label: '已重投', className: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300' },
  RESOLVED: { label: '已解决', className: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' },
};

const FILTERS: { value: DeadLetterStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'DEAD', label: '待处理' },
  { value: 'REPLAYED', label: '已重投' },
  { value: 'RESOLVED', label: '已解决' },
];

function formatTime(value: string | null): string {
  if (!value) return '-';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false });
}

export default function DeadLetterPage() {
  const [filter, setFilter] = useState<DeadLetterStatus | 'ALL'>('ALL');
  const [items, setItems] = useState<DeadLetterItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await deadLetterApi.list(filter === 'ALL' ? undefined : filter);
      setItems(data ?? []);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleReplay = async (item: DeadLetterItem) => {
    if (!window.confirm(`确认重投死信 #${item.id}（${item.payloadIdentifier}）？\n该任务将重新投递回对应 Stream 重新执行。`)) {
      return;
    }
    setBusyId(item.id);
    try {
      await deadLetterApi.replay(item.id);
      await load();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  const handleDiscard = async (item: DeadLetterItem) => {
    if (!window.confirm(`确认丢弃死信 #${item.id}（${item.payloadIdentifier}）？\n标记后不再重投，仅保留审计记录。`)) {
      return;
    }
    setBusyId(item.id);
    try {
      await deadLetterApi.discard(item.id);
      await load();
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white">
          <Inbox className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-900 dark:text-white">死信管理</h1>
          <p className="text-sm text-slate-400 dark:text-slate-500">
            异步任务消费失败归档与重投（死信队列）
          </p>
        </div>
      </div>

      {/* 过滤 + 刷新 */}
      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="flex rounded-lg overflow-hidden border border-slate-200 dark:border-slate-700">
          {FILTERS.map((f) => (
            <button
              key={f.value}
              onClick={() => setFilter(f.value)}
              className={`px-4 py-2 text-sm font-medium transition-colors ${
                filter === f.value
                  ? 'bg-primary-500 text-white'
                  : 'bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
        <button
          onClick={() => void load()}
          disabled={loading}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          刷新
        </button>
      </div>

      {error && (
        <div className="mb-4 flex items-center gap-2 px-4 py-3 rounded-lg bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-300 text-sm">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          {error}
        </div>
      )}

      <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-700 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-400 dark:text-slate-500 border-b border-slate-100 dark:border-slate-700">
                <th className="px-4 py-3 font-medium">ID</th>
                <th className="px-4 py-3 font-medium">任务类型</th>
                <th className="px-4 py-3 font-medium">业务标识</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">失败原因</th>
                <th className="px-4 py-3 font-medium text-center">重试/重投</th>
                <th className="px-4 py-3 font-medium">归档时间</th>
                <th className="px-4 py-3 font-medium text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="px-4 py-16 text-center text-slate-400">
                    <Loader2 className="w-6 h-6 mx-auto mb-2 animate-spin" />
                    加载中…
                  </td>
                </tr>
              ) : items.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-16 text-center text-slate-400 dark:text-slate-500">
                    暂无死信记录
                  </td>
                </tr>
              ) : (
                items.map((item) => {
                  const meta = STATUS_META[item.status];
                  const isResolved = item.status === 'RESOLVED';
                  return (
                    <tr
                      key={item.id}
                      className="border-b border-slate-50 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800/50"
                    >
                      <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{item.id}</td>
                      <td className="px-4 py-3 font-medium text-slate-800 dark:text-slate-200">
                        {item.taskType || item.streamKey}
                      </td>
                      <td className="px-4 py-3 text-slate-600 dark:text-slate-300 max-w-xs truncate" title={item.payloadIdentifier}>
                        {item.payloadIdentifier}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${meta.className}`}>
                          {meta.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-500 dark:text-slate-400 max-w-xs truncate" title={item.errorMessage ?? ''}>
                        {item.errorMessage ?? '-'}
                      </td>
                      <td className="px-4 py-3 text-center text-slate-500 dark:text-slate-400">
                        {item.retryCount}/{item.replayCount}
                      </td>
                      <td className="px-4 py-3 text-slate-500 dark:text-slate-400 whitespace-nowrap">
                        {formatTime(item.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => void handleReplay(item)}
                            disabled={isResolved || busyId === item.id}
                            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 hover:bg-primary-100 dark:hover:bg-primary-900/50 text-xs font-medium disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            {busyId === item.id ? (
                              <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            ) : (
                              <RefreshCw className="w-3.5 h-3.5" />
                            )}
                            重投
                          </button>
                          <button
                            onClick={() => void handleDiscard(item)}
                            disabled={isResolved || busyId === item.id}
                            className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 hover:bg-red-50 dark:hover:bg-red-900/30 hover:text-red-600 dark:hover:text-red-300 text-xs font-medium disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            丢弃
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
