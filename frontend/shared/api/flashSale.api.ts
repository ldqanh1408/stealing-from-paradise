import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

/** Flash sale item — one SKU on sale within a session */
export interface FlashSaleItem {
  id: number;
  sessionId: number;
  skuCode: string;
  productName: string;
  productDescription?: string;
  imageUrl?: string;
  flashPrice: number;
  originalPrice: number;
  flashStock: number;
  soldQty: number;
  limitPerUser: number;
  status: 'PENDING' | 'ACTIVE' | 'SOLD_OUT' | 'ENDED';
}

/** Flash sale session — a time window with multiple items */
export interface FlashSaleSession {
  id: number;
  name: string;
  startTime: string;
  endTime: string;
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
    apiClient.post<ApiResponse<{ orderId: number; orderCode: string; amount: number }>>(
      `/flash-sales/${sessionId}/buy`,
      { skuCode, quantity }
    ),

  /** Create a flash sale session (admin) */
  createSession: (data: { name: string; startTime: string; endTime: string; description?: string }) =>
    apiClient.post<ApiResponse<{ sessionId: number; status: string }>>('/flash-sales', data),
};
