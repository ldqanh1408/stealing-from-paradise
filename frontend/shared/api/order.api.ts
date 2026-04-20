import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface OrderItem {
  order_item_id?: number;
  sku_code: string;
  product_name: string;
  variant_name: string;
  unit_price: number;
  quantity: number;
}

export interface Order {
  order_id: number;
  parent_order_id?: number;
  order_code: string;
  seller_id: number;
  seller_name: string;
  total_amt: number;
  final_amt: number;
  status: 'PENDING' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'RETURNED' | 'CANCELLED' | 'PARTIALLY_REFUNDED' | 'REFUNDED';
  is_flash_sale?: boolean;
  item_count?: number;
  items?: OrderItem[];
  created_at: string;
  updated_at?: string;
}

export interface CheckoutResponse {
  parent_order_id: number;
  order_code: string;
  orders: Order[];
  total_amount: number;
  loyalty_discount?: number;
  loyalty_points_used?: number;
  final_amount: number;
  items_count: number;
  created_at: string;
}

export interface CheckoutRequest {
  address_id: number;
  item_ids: number[];
  use_loyalty_points?: boolean;
  loyalty_points_to_use?: number;
}

export const orderApi = {
  // Create order from cart
  checkout: (data: CheckoutRequest) =>
    apiClient.post<ApiResponse<CheckoutResponse>>('/orders/checkout', data),

  // Get user orders
  getOrders: (params?: {
    status?: string;
    from_date?: string;
    to_date?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<any>>('/orders', { params }),

  // Get order details
  getOrderById: (orderId: number) =>
    apiClient.get<ApiResponse<Order>>(`/orders/${orderId}`),
};

