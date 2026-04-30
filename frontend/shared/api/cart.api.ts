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
  sellerTrustScore?: number;
  items: CartItem[];
  sellerSubtotal?: number;
}

export interface Cart {
  cartId?: string;
  userId?: number;
  sellers: CartSeller[];
  totalItems: number;
  subtotal: number;
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
};

