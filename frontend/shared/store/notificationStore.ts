import { create } from 'zustand';
import { notificationApi, type Notification } from '../api/notification.api';

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  isLoading: boolean;
  error: string | null;

  fetchNotifications: (params?: { page?: number; size?: number }) => Promise<void>;
  markAsRead: (notifId: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  fetchUnreadCount: () => Promise<void>;
  addNotification: (notif: Notification) => void;
  setUnreadCount: (count: number) => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  notifications: [],
  unreadCount: 0,
  isLoading: false,
  error: null,

  fetchNotifications: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await notificationApi.getNotifications(params);
      set({ notifications: data || [], isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch notifications',
        isLoading: false,
      });
    }
  },

  markAsRead: async (notifId) => {
    try {
      await notificationApi.markAsRead(notifId);
      set((state) => {
        const wasUnread = state.notifications.find((n) => n.id === notifId)?.read === false;
        return {
          notifications: state.notifications.map((n) =>
            n.id === notifId ? { ...n, read: true } : n
          ),
          unreadCount: wasUnread ? Math.max(0, state.unreadCount - 1) : state.unreadCount,
        };
      });
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Failed to mark notification as read' });
    }
  },

  markAllAsRead: async () => {
    try {
      await notificationApi.markAllAsRead();
      set((state) => ({
        notifications: state.notifications.map((n) => ({ ...n, read: true })),
        unreadCount: 0,
      }));
    } catch (err: any) {
      set({ error: err?.response?.data?.message || 'Failed to mark all notifications as read' });
    }
  },

  fetchUnreadCount: async () => {
    try {
      const { data } = await notificationApi.getUnreadCount();
      set({ unreadCount: data.unread_count || 0 });
    } catch (err: any) {
      console.error('Failed to fetch unread count:', err);
    }
  },

  addNotification: (notif) => {
    set((state) => {
      if (state.notifications.some((n) => n.id === notif.id)) {
        return state;
      }
      return {
        notifications: [notif, ...state.notifications],
        unreadCount: state.unreadCount + (notif.read ? 0 : 1),
      };
    });
  },

  setUnreadCount: (count) => set({ unreadCount: count }),
}));
