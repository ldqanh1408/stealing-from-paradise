import apiClient from '../lib/axios';

/** Matches NotificationService backend: raw Notification objects, no ApiResponse wrapper */
export interface Notification {
  id: string;
  userId: number;
  type: string;
  title: string;
  message: string;
  data?: Record<string, any>;
  read: boolean;
  createdAt: string;
}

export const notificationApi = {
  /** Get notifications (raw array — no ApiResponse wrapper from reactive service) */
  getNotifications: async (params?: { page?: number; size?: number }): Promise<Notification[]> => {
    const res = await apiClient.get<Notification[]>('/notifications', { params });
    return res.data;
  },

  /** Mark single notification as read */
  markAsRead: (notifId: string) =>
    apiClient.patch<Notification>(`/notifications/${notifId}/read`),

  /** Mark all notifications as read */
  markAllAsRead: () =>
    apiClient.patch<{ success: boolean; updated_count: number; user_id: number }>('/notifications/read-all'),

  /** Get unread notification count */
  getUnreadCount: async (): Promise<number> => {
    const res = await apiClient.get<{ user_id: number; unread_count: number }>('/notifications/unread-count');
    return res.data.unread_count ?? 0;
  },
};
