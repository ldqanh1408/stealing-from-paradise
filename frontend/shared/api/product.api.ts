import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  sku_code: string;
  variant_name: string;
  stock: number;
}

/** Matches backend ProductResponse: GET /products/{productId} */
export interface ProductDetail {
  product_id: string;
  seller_id: number;
  seller_name?: string;
  name: string;
  description?: string;
  price?: number;
  original_price?: number;
  category_id: string;
  category?: string;
  attributes?: Record<string, unknown>;
  images?: string[];
  is_flash?: boolean;
  status?: string;
  reject_reason?: string;
  stock_available: number;
  variants?: ProductVariant[];
  rating?: number;
  reviews_count?: number;
  created_at?: string;
  updated_at?: string;
}

/** Matches backend ProductListItem or similar (used in product grids) */
export interface ProductListItem {
  product_id: string;
  seller_id: number;
  seller_name?: string;
  name: string;
  description?: string;
  price?: number;
  original_price?: number;
  category?: string;
  images?: string[];
  stock?: number;
  rating?: number;
  reviews_count?: number;
  is_flash?: boolean;
  created_at?: string;
}

export const productApi = {
  /** Get all products with optional filters */
  getProducts: (params?: {
    category?: string;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    apiClient.get<ApiResponse<ProductDetail[]>>('/products', { params }),

  /** Get product by ID — returns full ProductDetail */
  getProductById: (productId: string) =>
    apiClient.get<ApiResponse<ProductDetail>>(`/products/${productId}`),

  /** Search products */
  searchProducts: (query: string, params?: {
    category?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<ProductDetail[]>>('/search', {
      params: { q: query, ...params },
    }),
};

