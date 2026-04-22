import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

// ─── Order Status ────────────────────────────────────────────────────────────
export type OrderStatus =
  | 'PENDING'
  | 'PAID'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'RETURNED'
  | 'PARTIALLY_REFUNDED'
  | 'REFUNDED';

// ─── Order Item ───────────────────────────────────────────────────────────────
export interface OrderItem {
  order_item_id: number;
  sku_code: string;
  product_name: string;
  variant_name: string;
  image_snapshot?: string;
  price_snapshot: number;
  quantity: number;
  refunded_quantity: number;
  fs_item_id?: number | null;
}

// ─── Shipping Address ─────────────────────────────────────────────────────────
export interface ShippingAddress {
  full_address: string;
  province_id: number;
  district_id: number;
}

// ─── Order (sub-order) ───────────────────────────────────────────────────────
export interface Order {
  order_id: number;
  parent_order_id: number;
  order_code: string;
  seller_id: number;
  seller_name: string;
  buyer_id?: number;
  buyer_name?: string;
  status: OrderStatus;
  total_amt: number;
  final_amt: number;
  is_flash_sale?: boolean;
  item_count?: number;
  cancelled_by?: string | null;
  cancel_reason?: string | null;
  shipping_address?: ShippingAddress;
  tracking_number?: string | null;
  carrier?: string | null;
  shipping_deadline?: string | null;
  return_tracking_number?: string | null;
  items?: OrderItem[];
  created_at: string;
  updated_at?: string;
}

// ─── Checkout ─────────────────────────────────────────────────────────────────
export interface CheckoutSubOrder {
  order_id: number;
  order_code: string;
  seller_id: number;
  seller_name: string;
  total_amt: number;
  final_amt: number;
  status: string;
  item_count: number;
  created_at: string;
}

export interface CheckoutResponse {
  parent_order_id: number;
  order_code: string;
  orders: CheckoutSubOrder[];
  total_amount: number;
  loyalty_discount?: number;
  loyalty_points_used?: number;
  final_amount: number;
  items_count: number;
  payment_status?: string;
  timeout_at?: string;
  created_at: string;
}

export interface CheckoutRequest {
  address_id: number;
  item_ids: number[];
  use_loyalty_points?: boolean;
  loyalty_points_to_use?: number;
}

// ─── Parent Order ──────────────────────────────────────────────────────────────
export interface ParentOrderDetail {
  parent_order_id: number;
  order_code: string;
  status: string;
  total_amt: number;
  final_amt: number;
  shipping_address?: ShippingAddress;
  orders: Order[];
  created_at: string;
  updated_at?: string;
}

// ─── Cancel ───────────────────────────────────────────────────────────────────
export interface CancelOrderRequest {
  reason: string;
  note?: string;
}

export interface CancelOrderResponse {
  order_id: number;
  order_code: string;
  status: string;
  cancelled_by: string;
  cancelled_at: string;
}

// ─── Tracking ─────────────────────────────────────────────────────────────────
export interface UpdateTrackingRequest {
  tracking_number: string;
  carrier?: string;
  note?: string;
}

export interface TrackingUpdateResponse {
  order_id: number;
  order_code: string;
  status: string;
  tracking_number: string;
  carrier: string;
  shipping_deadline: string;
}

// ─── Order Summary (paginated list) ─────────────────────────────────────────
export interface OrderSummary {
  order_id: number;
  parent_order_id: number;
  order_code: string;
  seller_id: number;
  seller_name: string;
  status: OrderStatus;
  total_amt: number;
  final_amt: number;
  is_flash_sale?: boolean;
  item_count: number;
  created_at: string;
  updated_at?: string;
}

// ─── Seller Order (with buyer info) ─────────────────────────────────────────
export interface SellerOrderItem {
  order_item_id: number;
  sku_code: string;
  product_name: string;
  variant_name: string;
  image_snapshot?: string;
  price_snapshot: number;
  quantity: number;
  refunded_quantity: number;
}

export interface SellerOrderSummary {
  order_id: number;
  parent_order_id: number;
  order_code: string;
  buyer_id: number;
  buyer_name?: string;
  buyer_username?: string;
  status: OrderStatus;
  total_amt: number;
  final_amt: number;
  is_flash_sale?: boolean;
  item_count: number;
  shipping_address?: ShippingAddress;
  tracking_number?: string | null;
  carrier?: string | null;
  created_at: string;
  updated_at?: string;
}

// ─── API ─────────────────────────────────────────────────────────────────────
export const orderApi = {
  /** Create order from cart (BUYER) */
  checkout: (data: CheckoutRequest) =>
    apiClient.post<ApiResponse<CheckoutResponse>>('/orders/checkout', data),

  /** List buyer's orders */
  getOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<PageResponse<OrderSummary>>>('/orders', { params }),

  /** Get sub-order detail */
  getOrderById: (orderId: number) =>
    apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`),

  /** Get parent order with all sub-orders */
  getParentOrder: (parentOrderId: number) =>
    apiClient.get<ApiResponse<ParentOrderDetail>>(`/orders/parent/${parentOrderId}`),

  /** Cancel order (BUYER or SELLER) */
  cancelOrder: (orderId: number, body: CancelOrderRequest) =>
    apiClient.post<ApiResponse<CancelOrderResponse>>(`/orders/${orderId}/cancel`, body),

  /** Update tracking number (SELLER) */
  updateTracking: (orderId: number, body: UpdateTrackingRequest) =>
    apiClient.put<ApiResponse<TrackingUpdateResponse>>(`/orders/${orderId}/tracking`, body),

  /** Confirm receipt (BUYER) */
  confirmReceived: (orderId: number) =>
    apiClient.post<ApiResponse<{ order_id: number; status: string }>>(`/orders/${orderId}/confirm-received`),

  /** Return to sender (SELLER) */
  returnToSender: (orderId: number, formData: FormData) =>
    apiClient.post<ApiResponse<{
      order_id: number;
      order_code: string;
      order_status: string;
      refund_id: number;
      refund_status: string;
      refund_amount: number;
    }>>(`/orders/${orderId}/return-to-sender`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  /** List seller's orders */
  getSellerOrders: (params?: {
    status?: OrderStatus;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<PageResponse<SellerOrderSummary>>>('/sellers/me/orders', { params }),
};
