import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface CartItem {
  cartItemId: number;
  skuCode: string;
  productId?: string;
  productName: string;
  variantName: string;
  unitPrice: number;
  quantity: number;
  stockAvailable: number;
  isFlash: boolean;
  fsItemId?: number | null;
  flashPrice?: number | null;
  flashExpiresAt?: string | null;
  maxQuantityPerUser?: number | null;
  subtotal?: number;
  addedAt?: string;
}

export interface CartSeller {
  sellerId: number;
  sellerName: string;
  items: CartItem[];
  sellerSubtotal?: number;
}

export interface Cart {
  cartId?: string;
  userId?: number;
  sellers: CartSeller[];
  totalItems: number;
  subtotal: number;
  hasPriceChanges?: boolean;
}

// ─── Checkout Preview / Submit ──────────────────────────────────────────────────

export interface CartChangeDetail {
  variantId: string;
  skuCode?: string;
  productName?: string;
  reason: 'PRICE_CHANGED' | 'OUT_OF_STOCK' | 'INSUFFICIENT_STOCK' | 'VARIANT_UNAVAILABLE' | 'VARIANT_INACTIVE';
  currentValue: string;
  expectedValue: string;
}

export interface CartChangeError {
  error: string;
  message: string;
  details: CartChangeDetail[];
}

export interface CheckoutPreviewSellerGroup {
  sellerId: number;
  sellerName?: string;
  items: CheckoutPreviewItem[];
  subtotal: number;
}

export interface CheckoutPreviewItem {
  variantId: string;
  skuCode: string;
  productName: string;
  variantName: string;
  priceSnapshot: number;
  quantity: number;
  imageUrl?: string;
  subtotal: number;
  fsItemId?: number | null;
  sellerId: number;
}

export interface CheckoutPreviewResponse {
  previewToken: string;
  expiresAt: string;
  customerId: number;
  sellers: CheckoutPreviewSellerGroup[];
  totalItems: number;
  totalAmount: number;
  allValid: boolean;
}

export interface CheckoutSubmitResponse {
  sessionId: string;
  parentOrderId: number;
  createdAt: string;
  totalItems: number;
  totalAmount: number;
  message: string;
}

export const cartApi = {
  // Get current cart
  getCart: () =>
    apiClient.get<ApiResponse<Cart>>('/cart'),

  // Add item to cart
  addItem: (skuCode: string, quantity: number, fsItemId?: number) =>
    apiClient.post<ApiResponse<CartItem>>('/cart/items', {
      skuCode,
      quantity,
      fsItemId,
    }),

  // Update item quantity
  updateItemQuantity: (itemId: number, quantity: number) =>
    apiClient.put<ApiResponse<CartItem>>(`/cart/items/${itemId}`, {
      quantity,
    }),

  // Remove item from cart
  removeItem: (itemId: number) =>
    apiClient.delete<ApiResponse<void>>(`/cart/items/${itemId}`),

  // Clear entire cart
  clearCart: () =>
    apiClient.delete<ApiResponse<void>>('/cart'),

  // Checkout preview — validates stock/price, returns preview token or error details
  checkoutPreview: (itemIds: string[]) =>
    apiClient.post<ApiResponse<CheckoutPreviewResponse>>('/cart/checkout/preview', { itemIds }),

  // Checkout submit — uses preview token, creates order
  checkoutSubmit: (previewToken: string, addressId: number, provinceId?: number, districtId?: number, fullAddress?: string) =>
    apiClient.post<ApiResponse<CheckoutSubmitResponse>>('/cart/checkout/submit', {
      previewToken,
      addressId,
      provinceId,
      districtId,
      fullAddress,
    }),
};

