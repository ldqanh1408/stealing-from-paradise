import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

/** Flash sale item — one SKU on sale within a session */
export interface FlashSaleItem {
  id: number;
  session_id: number;
  sku_code: string;
  product_name: string;
  product_description?: string;
  image_url?: string;
  flash_price: number;
  original_price: number;
  flash_stock: number;
  sold_qty: number;
  limit_per_user: number;
  status: 'PENDING' | 'ACTIVE' | 'SOLD_OUT' | 'ENDED';
}

/** Flash sale session — a time window with multiple items */
export interface FlashSaleSession {
  id: number;
  name: string;
  start_time: string;
  end_time: string;
  status: 'UPCOMING' | 'ACTIVE' | 'ENDED';
  items?: FlashSaleItem[];
}

export const flashSaleApi = {
  /** Get all flash sale sessions (public) */
  getSessions: (params?: { status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<FlashSaleSession>>>('/flash-sales', { params }),

  /** Get one session with its items */
  getSession: (sessionId: number) =>
    apiClient.get<ApiResponse<FlashSaleSession>>(`/flash-sales/${sessionId}`),

  /** Buy a flash sale item */
  buy: (sessionId: number, skuCode: string, quantity: number) =>
    apiClient.post<ApiResponse<{ order_id: number; order_code: string; amount: number }>>(
      `/flash-sales/${sessionId}/buy`,
      { sku_code: skuCode, quantity }
    ),

  /** Create a flash sale session (admin) */
  createSession: (data: { name: string; start_time: string; end_time: string; description?: string }) =>
    apiClient.post<ApiResponse<{ session_id: number; status: string }>>('/flash-sales', data),
};
