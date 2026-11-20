export type DeadLetterStatus = 'DEAD' | 'REPLAYED' | 'RESOLVED';

export interface DeadLetterItem {
  id: number;
  streamKey: string;
  groupName: string;
  messageId: string;
  taskType: string;
  payloadIdentifier: string;
  status: DeadLetterStatus;
  errorMessage: string | null;
  retryCount: number;
  replayCount: number;
  createdAt: string;
  lastReplayAt: string | null;
  updatedAt: string;
}
