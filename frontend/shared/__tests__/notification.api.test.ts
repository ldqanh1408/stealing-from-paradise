import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

import apiClient from '../lib/axios';
import { notificationApi } from '../api/notification.api';

const client = apiClient as unknown as Record<'get' | 'patch', ReturnType<typeof vi.fn>>;

beforeEach(() => {
  vi.clearAllMocks();
  client.get.mockResolvedValue(Promise.resolve({ data: [] }));
  client.patch.mockResolvedValue(Promise.resolve({ data: {} }));
});

describe('notificationApi', () => {
  it('getNotifications → GET /notifications with paging params', async () => {
    await notificationApi.getNotifications({ page: 0, size: 20 });
    expect(client.get).toHaveBeenCalledWith('/notifications', { params: { page: 0, size: 20 } });
  });

  it('markAsRead → PATCH /notifications/{id}/read', async () => {
    await notificationApi.markAsRead('n1');
    expect(client.patch).toHaveBeenCalledWith('/notifications/n1/read');
  });

  it('markAllAsRead → PATCH /notifications/read-all', async () => {
    await notificationApi.markAllAsRead();
    expect(client.patch).toHaveBeenCalledWith('/notifications/read-all');
  });

  it('getUnreadCount → GET /notifications/unread-count', async () => {
    await notificationApi.getUnreadCount();
    expect(client.get).toHaveBeenCalledWith('/notifications/unread-count');
  });
});
