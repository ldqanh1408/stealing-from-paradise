import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  sku_code: string;
  variant_name: string;
  stock: number;
}

export interface Product {
  product_id: string;
  seller_id: number;
  seller_name: string;
  product_name: string;
  description?: string;
  price: number;
  original_price?: number;
  category: string;
  variants: ProductVariant[];
  images?: string[];
  stock: number;
  rating?: number;
  reviews_count?: number;
  created_at?: string;
}

export const productApi = {
  // Get all products with optional filters
  getProducts: (params?: {
    category?: string;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    apiClient.get<ApiResponse<any>>('/products', { params }),

  // Get product by ID
  getProductById: (productId: string) =>
    apiClient.get<ApiResponse<Product>>(`/products/${productId}`),

  // Search products
  searchProducts: (query: string, params?: any) =>
    apiClient.get<ApiResponse<any>>('/search', {
      params: { q: query, ...params },
    }),
};

