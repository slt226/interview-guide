import request from './request';
import type { DeadLetterItem, DeadLetterStatus } from '../types/deadLetter';

export const deadLetterApi = {
  /** 列表：status 为空返回全部，否则按状态过滤 */
  list: (status?: DeadLetterStatus) =>
    request.get<DeadLetterItem[]>(
      status ? `/api/async/dead-letter?status=${status}` : '/api/async/dead-letter'
    ),

  /** 重投：把死信原样重新投递回对应 Stream */
  replay: (id: number) =>
    request.post<void>(`/api/async/dead-letter/${id}/replay`),

  /** 丢弃：标记 RESOLVED，不再重投 */
  discard: (id: number) =>
    request.post<void>(`/api/async/dead-letter/${id}/discard`),
};
