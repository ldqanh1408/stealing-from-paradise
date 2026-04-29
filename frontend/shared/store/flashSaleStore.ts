import { create } from 'zustand';
import {
  flashSaleApi,
  type FlashSaleSession,
  type FlashSaleItem,
} from '../api/flashSale.api';
import type { PageResponse } from '../types/api';

interface FlashSaleState {
  sessions: FlashSaleSession[];
  currentSession: FlashSaleSession | null;
  activeSessions: FlashSaleSession[];
  upcomingSessions: FlashSaleSession[];
  isLoading: boolean;
  error: string | null;

  fetchSessions: (params?: { status?: string; page?: number; size?: number }) => Promise<void>;
  fetchSession: (sessionId: number) => Promise<void>;
  buyFlashSaleItem: (
    sessionId: number,
    skuCode: string,
    quantity: number
  ) => Promise<{ orderId: number; orderCode: string; amount: number }>;
  clearCurrentSession: () => void;
  getActiveSessions: () => FlashSaleSession[];
  getUpcomingSessions: () => FlashSaleSession[];
}

export const useFlashSaleStore = create<FlashSaleState>((set, get) => ({
  sessions: [],
  currentSession: null,
  activeSessions: [],
  upcomingSessions: [],
  isLoading: false,
  error: null,

  fetchSessions: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await flashSaleApi.getSessions(params);
      const page: PageResponse<FlashSaleSession> | undefined = data.data;
      const sessions = page?.content || [];

      const now = new Date();
      const active = sessions.filter(
        (s) => s.status === 'ACTIVE' || (new Date(s.startTime) <= now && new Date(s.endTime) >= now)
      );
      const upcoming = sessions.filter((s) => s.status === 'UPCOMING' || new Date(s.startTime) > now);

      set({
        sessions,
        activeSessions: active,
        upcomingSessions: upcoming,
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch flash sale sessions',
        isLoading: false,
      });
    }
  },

  fetchSession: async (sessionId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await flashSaleApi.getSession(sessionId);
      set({ currentSession: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch flash sale session',
        isLoading: false,
      });
    }
  },

  buyFlashSaleItem: async (sessionId, skuCode, quantity) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await flashSaleApi.buy(sessionId, skuCode, quantity);
      set({ isLoading: false });
      return data.data!;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to purchase flash sale item',
        isLoading: false,
      });
      throw err;
    }
  },

  clearCurrentSession: () => set({ currentSession: null }),

  getActiveSessions: () => get().activeSessions,
  getUpcomingSessions: () => get().upcomingSessions,
}));
