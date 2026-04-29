import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

/** Admin: pending product for moderation */
export interface PendingProduct {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  category?: string;
  price?: number;
  images?: string[];
  status: string;
  submittedAt: string;
}

/** Admin: user list */
export interface AdminUser {
  userId: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'BANNED' | 'PENDING';
  trustScore: number;
  createdAt: string;
}

/** Admin: flash sale session create request */
export interface CreateFlashSaleRequest {
  name: string;
  startTime: string;
  endTime: string;
  description?: string;
}

export const adminApi = {
  /** List pending products */
  getPendingProducts: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<PendingProduct>>>('/admin/products/pending', { params }),

  /** Approve a product */
  approveProduct: (productId: string, adminNote?: string) =>
    apiClient.post<ApiResponse<{ productId: string; status: string }>>(
      `/admin/products/${productId}/approve`,
      { adminNote }
    ),

  /** Reject a product */
  rejectProduct: (productId: string, reason: string) =>
    apiClient.post<ApiResponse<{ productId: string; status: string }>>(
      `/admin/products/${productId}/reject`,
      { reason }
    ),

  /** List all users */
  getUsers: (params?: { role?: string; status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AdminUser>>>('/users', { params }),

  /** Ban/unban user */
  updateUserStatus: (userId: number, status: 'ACTIVE' | 'BANNED') =>
    apiClient.put<ApiResponse<{ userId: number; status: string }>>(`/users/${userId}/status`, { status }),

  /** Create flash sale session (admin) */
  createFlashSaleSession: (data: CreateFlashSaleRequest) =>
    apiClient.post<ApiResponse<{ sessionId: number; status: string }>>('/flash-sales', data),

  /** Update flash sale session */
  updateFlashSaleSession: (sessionId: number, data: Partial<CreateFlashSaleRequest>) =>
    apiClient.put<ApiResponse<{ sessionId: number }>>(`/flash-sales/${sessionId}`, data),

  /** Delete flash sale session */
  deleteFlashSaleSession: (sessionId: number) =>
    apiClient.delete<ApiResponse<void>>(`/flash-sales/${sessionId}`),
};
