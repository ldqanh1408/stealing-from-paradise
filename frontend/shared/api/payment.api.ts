import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Payment Endpoints (payment-service) ───────────────────────────────────

export interface PaymentDetail {
  transaction_id: number;
  parent_order_id: number;
  amount: number;
  method: string;
  status: string;
  stripe_pi_id: string;
  application_fee: number;
  trans_ref: string;
  paid_at: string | null;
  remaining_seconds: number | null;
}

export interface ClientSecretResponse {
  parent_order_id: number;
  transaction_id: number;
  client_secret: string;
  status: string;
}

export const paymentApi = {
  /** Get payment/transaction details for a parent order */
  getPayment: (parentOrderId: number) =>
    apiClient.get<ApiResponse<PaymentDetail>>(`/payments/parent-order/${parentOrderId}`),

  /** Get Stripe PaymentIntent client secret for frontend Stripe.js */
  getClientSecret: (parentOrderId: number) =>
    apiClient.get<ApiResponse<ClientSecretResponse>>(`/payments/parent-order/${parentOrderId}/client-secret`),
};
