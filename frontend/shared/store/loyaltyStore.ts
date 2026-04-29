import { create } from 'zustand';
import {
  userApi,
  type LoyaltyBalanceResponse,
  type PointTransactionSummary,
  type LoyaltyEstimateResponse,
} from '../api/user.api';
import type { PageResponse } from '../types/api';

interface LoyaltyState {
  balance: LoyaltyBalanceResponse | null;
  transactions: PointTransactionSummary[];
  estimate: LoyaltyEstimateResponse | null;
  isLoading: boolean;
  error: string | null;
  pagination: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };

  fetchBalance: () => Promise<void>;
  fetchTransactions: (params?: { page?: number; size?: number }) => Promise<void>;
  estimatePoints: (orderAmount: number) => Promise<void>;
  clearEstimate: () => void;
}

export const useLoyaltyStore = create<LoyaltyState>((set) => ({
  balance: null,
  transactions: [],
  estimate: null,
  isLoading: false,
  error: null,
  pagination: {
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
  },

  fetchBalance: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await userApi.getLoyaltyBalance();
      set({ balance: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch loyalty balance',
        isLoading: false,
      });
    }
  },

  fetchTransactions: async (params) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await userApi.getLoyaltyTransactions(params);
      const page: PageResponse<PointTransactionSummary> | undefined = data.data;
      set({
        transactions: page?.content || [],
        pagination: {
          page: page?.page ?? params?.page ?? 0,
          size: page?.size ?? params?.size ?? 20,
          totalElements: page?.totalElements ?? 0,
          totalPages: page?.totalPages ?? 0,
        },
        isLoading: false,
      });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch transactions',
        isLoading: false,
      });
    }
  },

  estimatePoints: async (orderAmount) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await userApi.estimateLoyaltyPoints(orderAmount);
      set({ estimate: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to estimate points',
        isLoading: false,
      });
    }
  },

  clearEstimate: () => set({ estimate: null }),
}));
