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

export interface ClientSecretResponse {
  parentOrderId: number;
  transactionId: number;
  clientSecret: string;
  status: string;
}

export const paymentApi = {
  /** Get payment/transaction details for a parent order */
  getPayment: (parentOrderId: number) =>
    apiClient.get<ApiResponse<PaymentDetail>>(`/payments/parent-order/${parentOrderId}`),

  /** Get Stripe PaymentIntent client secret for frontend Stripe.js */
  getClientSecret: (parentOrderId: number) =>
    apiClient.get<ApiResponse<ClientSecretResponse>>(`/payments/parent-order/${parentOrderId}/client-secret`),

  /** Look up a payment by Stripe PaymentIntent ID (for direct URL access after Stripe redirect) */
  getByPaymentIntent: (paymentIntentId: string) =>
    apiClient.get<ApiResponse<PaymentDetail>>(`/payments/by-intent/${paymentIntentId}`),
};
