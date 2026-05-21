import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Refund Types ────────────────────────────────────────────────────────────

export interface RefundItemRequest {
  orderItemId: number;
  quantity: number;
  itemReason?: string;
}

export interface FullRefundRequest {
  reason: string;
  evidenceImages?: string[];
}

export interface PartialRefundRequest {
  reason: string;
  items: RefundItemRequest[];
  evidenceImages?: string[];
}

export interface RefundItemResponse {
  orderItemId: number;
  quantity: number;
  refundAmount: number;
  itemReason?: string;
  status: string;
  trackingNumber?: string;
  returnedAt?: string;
}

export interface RefundResponse {
  refundId: number;
  orderId: number;
  groupRef: string;
  type: string;
  status: string;
  amount: number;
  reason: string;
  initiatedBy: string;
  refundReasonType?: string;
  evidenceImages?: string[];
  adminNote?: string;
  rejectReason?: string;
  adjustAmount?: number;
  reviewedBy?: number;
  reviewedAt?: string;
  stripeRefundId?: string;
  items?: RefundItemResponse[];
  createdAt: string;
  updatedAt?: string;
}

export interface FullRefundCreatedResponse {
  groupRef: string;
  type: string;
  totalAmount: number;
  status: string;
  refunds: {
    refundId: number;
    orderId: number;
    sellerId: number;
    amount: number;
    itemCount: number;
  }[];
  estimatedDays: number;
}

export interface FullRefundStatus {
  groupRef: string;
  type: string;
  overallStatus: string;
  totalAmount: number;
  refunds: {
    refundId: number;
    orderId: number;
    status: string;
    refundRef?: string;
  }[];
}

// ─── Admin Refund Types ──────────────────────────────────────────────────────

export interface AdminRefundApproveRequest {
  adminNote: string;
  adjustAmount?: number;
  causedBy?: 'SELLER' | 'BUYER';
  trackingNumber?: string;
}

export interface AdminRefundRejectRequest {
  rejectReason: string;
  fraudEvidence?: boolean;
}

export interface AdminRefundApproveResponse {
  refundId: number;
  status: string;
  amount: number;
  trackingNumber?: string;
  reviewedBy: string;
  reviewedAt: string;
}

// ─── Admin Refund API ────────────────────────────────────────────────────────

export const adminRefundApi = {
  /** List all refunds with filters */
  list: (params?: {
    status?: string;
    type?: string;
    sellerId?: number;
    groupRef?: string;
    fromDate?: string;
    toDate?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<{ content: RefundResponse[]; totalElements: number; totalPages: number }>>('/admin/refunds', { params }),

  /** Get refund detail */
  getById: (refundId: number) =>
    apiClient.get<ApiResponse<RefundResponse>>(`/admin/refunds/${refundId}`),

  /** Approve a refund */
  approve: (refundId: number, body: AdminRefundApproveRequest) =>
    apiClient.post<ApiResponse<AdminRefundApproveResponse>>(`/admin/refunds/${refundId}/approve`, body),

  /** Reject a refund */
  reject: (refundId: number, body: AdminRefundRejectRequest) =>
    apiClient.post<ApiResponse<{ refund_id: number; status: string }>>(`/admin/refunds/${refundId}/reject`, body),
};

// ─── Order-level Refund API ──────────────────────────────────────────────────

export const refundApi = {
  /** Full refund: all sub-orders in a parent order */
  requestFullRefund: (parentOrderId: number, body: FullRefundRequest) =>
    apiClient.post<ApiResponse<FullRefundCreatedResponse>>(`/orders/parent/${parentOrderId}/refund`, body),

  /** Full refund status check */
  getFullRefundStatus: (parentOrderId: number) =>
    apiClient.get<ApiResponse<FullRefundStatus>>(`/orders/parent/${parentOrderId}/refund`),

  /** Partial refund: items in ONE sub-order */
  requestPartialRefund: (orderId: number, body: PartialRefundRequest) =>
    apiClient.post<ApiResponse<RefundResponse>>(`/orders/${orderId}/refunds`, body),

  /** Partial refund across MULTIPLE sub-orders (different sellers) */
  requestMultiPartialRefund: (parentOrderId: number, body: PartialRefundRequest) =>
    apiClient.post<ApiResponse<FullRefundCreatedResponse>>(`/orders/parent/${parentOrderId}/refunds/partial`, body),

  /** Get refund history for ONE sub-order */
  getRefundsByOrder: (orderId: number) =>
    apiClient.get<ApiResponse<RefundResponse[]>>(`/orders/${orderId}/refunds`),

  /** Get specific refund within an order */
  getRefundById: (orderId: number, refundId: number) =>
    apiClient.get<ApiResponse<RefundResponse>>(`/orders/${orderId}/refunds/${refundId}`),

  /** List all buyer's refunds */
  getMyRefunds: (params?: { status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<{ content: RefundResponse[]; totalElements: number; totalPages: number }>>('/orders/refunds', { params }),

  /** Get presigned URL for refund evidence upload */
  getRefundPresignedUrl: (orderId: number, fileName: string, contentType: string) =>
    apiClient.get<ApiResponse<{ url: string; fileName: string; contentType: string; expiresAt: string }>>(
      `/orders/${orderId}/refunds/presigned-url`,
      { params: { file_name: fileName, content_type: contentType } }
    ),
};
