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
  variantId?: string;
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

// ─── Backend cart response (flat) ───────────────────────────────────────────────

interface BackendCartItem {
  variantId: string;
  variantCode: string;
  variantName: string;
  productName?: string;
  priceSnapshot: number;
  currentPrice?: number;
  priceChanged?: boolean;
  quantity: number;
  stockAvailable: number;
  variantImageSnapshot?: string;
  subtotal: number;
  outOfStock?: boolean;
  unavailable?: boolean;
  insufficientStock?: boolean;
  sellerId: number;
}

interface BackendCartResponse {
  customerId: number;
  items: BackendCartItem[];
  totalItems: number;
  subtotal: number;
  hasPriceChanges: boolean;
  groupedBySeller: Record<string, BackendCartItem[]>;
}

function mapBackendCart(raw: BackendCartResponse): Cart {
  const sellerGroups = Object.entries(raw.groupedBySeller ?? {});
  const sellers: CartSeller[] = sellerGroups.map(([sellerIdStr, items]) => {
    const sellerId = Number(sellerIdStr);
    const cartItems: CartItem[] = items.map((item) => ({
      cartItemId: generateCartItemId(sellerId, item.variantId),
      skuCode: item.variantCode ?? '',
      variantId: item.variantId,
      productName: item.productName ?? '',
      variantName: item.variantName ?? '',
      unitPrice: item.priceSnapshot,
      quantity: item.quantity,
      stockAvailable: item.stockAvailable ?? 0,
      isFlash: false,
      subtotal: item.subtotal,
      priceChanged: item.priceChanged,
      outOfStock: item.outOfStock,
      insufficientStock: item.insufficientStock,
      unavailable: item.unavailable,
    }));
    return {
      sellerId,
      sellerName: `Seller ${sellerId}`,
      items: cartItems,
      sellerSubtotal: cartItems.reduce((sum, item) => sum + (item.subtotal ?? 0), 0),
    };
  });

  return {
    sellers,
    totalItems: raw.totalItems ?? 0,
    subtotal: raw.subtotal ?? 0,
    hasPriceChanges: raw.hasPriceChanges ?? false,
  };
}

function generateCartItemId(sellerId: number, variantId: string): number {
  let hash = sellerId;
  for (let i = 0; i < variantId.length; i++) {
    hash = ((hash << 5) - hash + variantId.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
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
  getCart: () =>
    apiClient.get<ApiResponse<BackendCartResponse>('/cart').then(res => ({
      ...res,
      data: {
        ...res.data,
        data: mapBackendCart(res.data.data),
      },
    })),

  addItem: (skuCode: string, quantity: number) =>
    apiClient.post<ApiResponse<CartItem>('/cart/items', { skuCode, quantity }),

  updateItemQuantity: (variantId: string, quantity: number) =>
    apiClient.put<ApiResponse<CartItem>(`/cart/items/${variantId}`, { quantity }),

  removeItem: (variantId: string) =>
    apiClient.delete<ApiResponse<void>(`/cart/items/${variantId}`),

  clearCart: () =>
    apiClient.delete<ApiResponse<void>('/cart'),

  checkoutPreview: (itemIds: string[]) =>
    apiClient.post<ApiResponse<CheckoutPreviewResponse>('/cart/checkout/preview', { itemIds }),

  checkoutSubmit: (previewToken: string, addressId: number) =>
    apiClient.post<ApiResponse<CheckoutSubmitResponse>('/cart/checkout/submit', {
      previewToken,
      addressId,
    }),
};
