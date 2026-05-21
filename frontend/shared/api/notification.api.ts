import apiClient from '../lib/axios';

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
  /** Get notifications (raw array) */
  getNotifications: (params?: { page?: number; size?: number }) =>
    apiClient.get<Notification[]>('/notifications', { params }),

  /** Mark single notification as read (raw Notification object) */
  markAsRead: (notifId: string) =>
    apiClient.patch<Notification>(`/notifications/${notifId}/read`),

  /** Mark all notifications as read */
  markAllAsRead: () =>
    apiClient.patch<{ success: boolean; updated_count: number; user_id: number }>('/notifications/read-all'),

  /** Get unread notification count */
  getUnreadCount: () =>
    apiClient.get<{ user_id: number; unread_count: number }>('/notifications/unread-count'),
};
