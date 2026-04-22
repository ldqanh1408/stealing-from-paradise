import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

// ─── Refund Types ────────────────────────────────────────────────────────────

export interface RefundItemRequest {
  order_item_id: number;
  quantity: number;
  item_reason?: string;
}

export interface FullRefundRequest {
  reason: string;
  evidence_images?: string[];
}

export interface PartialRefundRequest {
  reason: string;
  items: RefundItemRequest[];
  evidence_images?: string[];
}

export interface RefundItemResponse {
  order_item_id: number;
  quantity: number;
  refund_amount: number;
  item_reason?: string;
  status: string;
  tracking_number?: string;
  returned_at?: string;
}

export interface RefundResponse {
  refund_id: number;
  order_id: number;
  group_ref: string;
  type: string;
  status: string;
  amount: number;
  reason: string;
  initiated_by: string;
  refund_reason_type?: string;
  evidence_images?: string[];
  admin_note?: string;
  reject_reason?: string;
  adjust_amount?: number;
  reviewed_by?: number;
  reviewed_at?: string;
  stripe_refund_id?: string;
  items?: RefundItemResponse[];
  created_at: string;
  updated_at?: string;
}

export interface FullRefundCreatedResponse {
  group_ref: string;
  type: string;
  total_amount: number;
  status: string;
  refunds: {
    refund_id: number;
    order_id: number;
    seller_id: number;
    amount: number;
    item_count: number;
  }[];
  loyalty_points_to_return?: number;
  estimated_days: number;
}

export interface FullRefundStatus {
  group_ref: string;
  type: string;
  overall_status: string;
  total_amount: number;
  refunds: {
    refund_id: number;
    order_id: number;
    status: string;
    refund_ref?: string;
  }[];
}

// ─── Admin Refund Types ──────────────────────────────────────────────────────

export interface AdminRefundApproveRequest {
  admin_note: string;
  adjust_amount?: number;
  caused_by?: 'SELLER' | 'BUYER';
  tracking_number?: string;
}

export interface AdminRefundRejectRequest {
  reject_reason: string;
  fraud_evidence?: boolean;
}

export interface AdminRefundApproveResponse {
  refund_id: number;
  status: string;
  amount: number;
  tracking_number?: string;
  reviewed_by: string;
  reviewed_at: string;
}

// ─── Admin Refund API ────────────────────────────────────────────────────────

export const adminRefundApi = {
  /** List all refunds with filters */
  list: (params?: {
    status?: string;
    type?: string;
    seller_id?: number;
    group_ref?: string;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<{ content: RefundResponse[]; total_elements: number; total_pages: number }>>('/admin/refunds', { params }),

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
    apiClient.get<ApiResponse<{ content: RefundResponse[]; total_elements: number; total_pages: number }>>('/orders/refunds', { params }),
};
