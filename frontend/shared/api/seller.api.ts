import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

/** Seller stats for dashboard */
export interface SellerDashboardStats {
  total_products: number;
  orders_today: number;
  revenue_month: number;
  trust_score: number;
  pending_orders: number;
  active_products: number;
}

/** Stripe onboarding status — matches StripeOnboardingStatusResponse from backend */
export interface StripeOnboardingStatus {
  stripe_account_id?: string;
  account_status?: string;
  details_submitted?: boolean;
  charges_enabled?: boolean;
  payouts_enabled?: boolean;
  onboarding_status?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';
  onboarding_url?: string | null;
}

/** Stripe dashboard link response */
export interface StripeDashboardLink {
  dashboard_url: string;
  stripe_account_id: string;
  account_status: string;
}

/** Seller earnings summary */
export interface SellerEarnings {
  total_earnings: number;
  available_balance: number;
  pending_balance: number;
  platform_fee_percentage: number;
  total_orders: number;
  transfers: SellerTransferItem[];
}

/** A single seller transfer / earnings record */
export interface SellerTransferItem {
  id: number;
  order_id: number;
  order_code?: string;
  transfer_amount: number;
  fee_amount: number;
  net_amount: number;
  stripe_transfer_id: string | null;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'REVERSED';
  created_at: string;
  updated_at: string;
}

/** Product variant response */
export interface SellerVariant {
  sku_code: string;
  variant_name: string;
  price: number;
  stock: number;
}

/** Seller product list item */
export interface SellerProduct {
  product_id: string;
  name: string;
  category: string;
  price: number;
  original_price?: number;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'UNPUBLISHED' | 'PUBLISHED';
  stock_available: number;
  images?: string[];
  variants_count: number;
  variants?: SellerVariant[];
  created_at: string;
  updated_at?: string;
}

export const sellerApi = {
  /** Seller dashboard stats */
  getDashboardStats: () =>
    apiClient.get<ApiResponse<SellerDashboardStats>>('/sellers/me/dashboard'),

  /** Start Stripe onboarding */
  startStripeOnboarding: () =>
    apiClient.post<ApiResponse<{ onboarding_url: string; expires_at: string }>>('/stripe/onboarding/start'),

  /** Get Stripe onboarding status */
  getStripeStatus: () =>
    apiClient.get<ApiResponse<StripeOnboardingStatus>>('/stripe/onboarding/status'),

  /** Refresh expired Stripe onboarding link */
  refreshStripeLink: () =>
    apiClient.post<ApiResponse<{ onboarding_url: string; expires_at: string }>>('/stripe/onboarding/refresh-link'),

  /** Submit a DRAFT product for review */
  submitForReview: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/submit`),

  /** Publish an APPROVED product */
  publishProduct: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/publish`),

  /** Unpublish a PUBLISHED/APPROVED product */
  unpublishProduct: (productId: string) =>
    apiClient.post<ApiResponse<SellerProduct>>(`/seller/products/${productId}/unpublish`),

  /** List variants for a product */
  getVariants: (productId: string) =>
    apiClient.get<ApiResponse<SellerVariant[]>>(`/seller/products/${productId}/variants`),

  /** Create a variant */
  createVariant: (productId: string, data: { sku_code: string; variant_name: string; price: number; stock: number }) =>
    apiClient.post<ApiResponse<SellerVariant>>(`/seller/products/${productId}/variants`, data),

  /** Update a variant */
  updateVariant: (variantId: string, data: { sku_code?: string; variant_name?: string; price?: number; stock?: number }) =>
    apiClient.put<ApiResponse<SellerVariant>>(`/seller/variants/${variantId}`, data),

  /** Delete a variant */
  deleteVariant: (variantId: string) =>
    apiClient.delete<ApiResponse<void>>(`/seller/variants/${variantId}`),

  /** Get presigned URL for image upload */
  getPresignedUrl: (productId: string, fileName: string, contentType: string) =>
    apiClient.get<ApiResponse<{ url: string; key: string }>>(`/products/${productId}/presigned-url`, {
      params: { fileName, contentType },
    }),

  /** Get seller earnings summary with all transfer records */
  getEarnings: () =>
    apiClient.get<ApiResponse<SellerEarnings>>('/seller/payments/earnings'),

  /** Get Stripe Dashboard single-use login link (valid 30 min) */
  getStripeDashboardLink: () =>
    apiClient.get<ApiResponse<StripeDashboardLink>>('/seller/payments/stripe-dashboard'),
};
