import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface CartItem {
  cart_item_id: number;
  sku_code: string;
  product_id?: string;
  product_name: string;
  variant_name: string;
  unit_price: number;
  quantity: number;
  stock_available: number;
  is_flash: boolean;
  fs_item_id?: number | null;
  flash_price?: number | null;
  flash_expires_at?: string | null;
  max_quantity_per_user?: number | null; // flash sale per-user limit
  subtotal?: number;
  added_at?: string;
}

export interface CartSeller {
  seller_id: number;
  seller_name: string;
  seller_trust_score?: number;
  items: CartItem[];
  seller_subtotal?: number;
}

export interface Cart {
  cart_id?: string;
  user_id?: number;
  sellers: CartSeller[];
  total_items: number;
  subtotal: number;
}

export const cartApi = {
  // Get current cart
  getCart: () =>
    apiClient.get<ApiResponse<Cart>>('/cart'),

  // Add item to cart
  addItem: (skuCode: string, quantity: number, fsItemId?: number) =>
    apiClient.post<ApiResponse<CartItem>>('/cart/items', {
      sku_code: skuCode,
      quantity,
      fs_item_id: fsItemId,
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

