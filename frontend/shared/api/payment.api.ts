import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Payment Endpoints (payment-service) ───────────────────────────────────

export interface PaymentDetail {
  transactionId: number;
  parentOrderId: number;
  amount: number;
  method: string;
  status: string;
  stripePiId: string;
  applicationFee: number;
  transRef: string;
  paidAt: string | null;
  remainingSeconds: number | null;
}

export const paymentApi = {
  /** Get payment/transaction details for a parent order */
  getPayment: (parentOrderId: number) =>
    apiClient.get<ApiResponse<PaymentDetail>>(`/payments/parent-order/${parentOrderId}`),

  /** Get Stripe client_secret to render PaymentElement for a parent order */
  getClientSecret: (parentOrderId: number) =>
    apiClient.get<ApiResponse<string>>(`/payments/${parentOrderId}/client-secret`),
};
