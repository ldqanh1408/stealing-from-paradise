import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

/** Seller stats for dashboard */
export interface SellerDashboardStats {
  totalProducts: number;
  ordersToday: number;
  revenueMonth: number;
  pendingOrders: number;
  activeProducts: number;
}

/** Stripe onboarding status — matches StripeOnboardingStatusResponse from backend */
export interface StripeOnboardingStatus {
  stripeAccountId?: string;
  accountStatus?: string;
  detailsSubmitted?: boolean;
  chargesEnabled?: boolean;
  payoutsEnabled?: boolean;
  onboardingStatus?: 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';
  onboardingUrl?: string | null;
  expressDashboardUrl?: string | null;
}

/** Stripe dashboard link response */
export interface StripeDashboardLink {
  dashboardUrl: string;
  stripeAccountId: string;
  accountStatus: string;
}

/** Seller earnings summary */
export interface SellerEarnings {
  totalEarnings: number;
  availableBalance: number;
  pendingBalance: number;
  platformFeePercentage: number;
  totalOrders: number;
  transfers: SellerTransferItem[];
}

/** A single seller transfer / earnings record */
export interface SellerTransferItem {
  id: number;
  orderId: number;
  orderCode?: string;
  transferAmount: number;
  feeAmount: number;
  netAmount: number;
  stripeTransferId: string | null;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'REVERSED';
  createdAt: string;
  updatedAt: string;
}

/** Product variant response */
export interface SellerVariant {
  variantId: string;
  skuCode: string;
  variantName: string;
  price: number;
  stock: number;
}

/** Seller product list item */
export interface SellerProduct {
  productId: string;
  name: string;
  category: string;
  price: number;
  originalPrice?: number;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'UNPUBLISHED' | 'PUBLISHED';
  stockAvailable: number;
  images?: string[];
  variantsCount: number;
  variants?: SellerVariant[];
  createdAt: string;
  updatedAt?: string;
}

export const sellerApi = {
  /** Seller dashboard stats */
  getDashboardStats: () =>
    apiClient.get<ApiResponse<SellerDashboardStats>>('/sellers/me/dashboard'),

  /** Start Stripe onboarding */
  startStripeOnboarding: () =>
    apiClient.post<ApiResponse<{ onboardingUrl: string; expiresAt: string }>>('/stripe/onboarding/start'),

  /** Get Stripe onboarding status */
  getStripeStatus: () =>
    apiClient.get<ApiResponse<StripeOnboardingStatus>>('/stripe/onboarding/status'),

  /** Refresh expired Stripe onboarding link */
  refreshStripeLink: () =>
    apiClient.post<ApiResponse<{ onboardingUrl: string; expiresAt: string }>>('/stripe/onboarding/refresh-link'),

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
  createVariant: (productId: string, data: { skuCode: string; variantName: string; price: number; stock: number }) =>
    apiClient.post<ApiResponse<SellerVariant>>(`/seller/products/${productId}/variants`, data),

  /** Update a variant */
  updateVariant: (variantId: string, data: { skuCode?: string; variantName?: string; price?: number; stock?: number }) =>
    apiClient.put<ApiResponse<SellerVariant>>(`/seller/variants/${variantId}`, data),

  /** Delete a variant */
  deleteVariant: (variantId: string) =>
    apiClient.delete<ApiResponse<void>>(`/seller/variants/${variantId}`),

  /** Get presigned URL for image upload */
  getPresignedUrl: (productId: string, fileName: string, contentType: string) =>
    apiClient.get<ApiResponse<PresignedUrlResponse>>(`/products/${productId}/presigned-url`, {
      params: { fileName, contentType },
    }),

  /** Get seller earnings summary with all transfer records */
  getEarnings: () =>
    apiClient.get<ApiResponse<SellerEarnings>>('/seller/payments/earnings'),

  /** Get Stripe Dashboard single-use login link (valid 30 min) */
  getStripeDashboardLink: () =>
    apiClient.get<ApiResponse<StripeDashboardLink>>('/seller/payments/stripe-dashboard'),

  /** Create a new product */
  createProduct: (data: { name: string; description: string; categoryId: string; price: number; stock: number; images?: string[] }) =>
    apiClient.post<ApiResponse<SellerProduct>>('/products', data),

  /** Delete a product (seller owner) */
  deleteProduct: (productId: string) =>
    apiClient.delete<ApiResponse<void>>(`/products/${productId}`),

  /** Update a product */
  updateProduct: (productId: string, data: { name: string; description: string; categoryId: string; images?: string[] }) =>
    apiClient.put<ApiResponse<SellerProduct>>(`/products/${productId}`, data),

  // ─── Inventory ──────────────────────────────────────────────────────────────

  /** Adjust inventory by SKU */
  adjustInventory: (data: { skuCode: string; delta: number; reason: string }) =>
    apiClient.post<ApiResponse<{ skuCode: string; stockAvailable: number }>>('/seller/inventory/adjust', data),

  /** Get inventory adjustment logs for a SKU */
  getInventoryLogs: (skuCode: string) =>
    apiClient.get<ApiResponse<InventoryLogEntry[]>>(`/seller/inventory/${skuCode}/logs`),

  /** Restock inventory by SKU */
  restockInventory: (skuCode: string, data: { quantity: number; reason: string; note?: string }) =>
    apiClient.put<ApiResponse<{ skuCode: string; stockAvailable: number }>>(`/inventory/${skuCode}/restock`, data),
};

/** Inventory log entry */
export interface InventoryLogEntry {
  logId: number;
  skuCode: string;
  delta: number;
  stockBefore: number;
  stockAfter: number;
  reason: string;
  note?: string;
  changedBy: string;
  createdAt: string;
}

/** Presigned URL response from MinIO */
export interface PresignedUrlResponse {
  presignedUrl: string;
  objectUrl: string;
  expiresIn: number;
}
