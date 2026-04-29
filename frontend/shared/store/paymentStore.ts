import { create } from 'zustand';
import {
  paymentApi,
  type PaymentDetail,
  type ClientSecretResponse,
} from '../api/payment.api';

interface PaymentState {
  payment: PaymentDetail | null;
  clientSecret: ClientSecretResponse | null;
  isLoading: boolean;
  error: string | null;

  fetchPayment: (parentOrderId: number) => Promise<void>;
  fetchClientSecret: (parentOrderId: number) => Promise<ClientSecretResponse>;
  fetchByPaymentIntent: (paymentIntentId: string) => Promise<void>;
  clearPayment: () => void;
  clearClientSecret: () => void;
}

export const usePaymentStore = create<PaymentState>((set) => ({
  payment: null,
  clientSecret: null,
  isLoading: false,
  error: null,

  fetchPayment: async (parentOrderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await paymentApi.getPayment(parentOrderId);
      set({ payment: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch payment',
        isLoading: false,
      });
    }
  },

  fetchClientSecret: async (parentOrderId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await paymentApi.getClientSecret(parentOrderId);
      const secret = data.data!;
      set({ clientSecret: secret, isLoading: false });
      return secret;
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to get client secret',
        isLoading: false,
      });
      throw err;
    }
  },

  fetchByPaymentIntent: async (paymentIntentId) => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await paymentApi.getByPaymentIntent(paymentIntentId);
      set({ payment: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch payment',
        isLoading: false,
      });
    }
  },

  clearPayment: () => set({ payment: null }),
  clearClientSecret: () => set({ clientSecret: null }),
}));
