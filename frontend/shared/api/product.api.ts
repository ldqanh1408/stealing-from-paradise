import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  skuCode: string;
  variantName: string;
  stock: number;
}

/** Matches backend ProductResponse: GET /products/{productId} */
export interface ProductDetail {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  price?: number;
  originalPrice?: number;
  categoryId?: string;
  categoryName?: string;
  categorySlug?: string;
  attributes?: Record<string, unknown>;
  images?: string[];
  isFlash?: boolean;
  status?: string;
  rejectReason?: string;
  stockAvailable: number;
  variants?: ProductVariant[];
  rating?: number;
  reviewsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** Matches backend ProductResponse (used in product grids) */
export interface ProductListItem {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  price?: number;
  originalPrice?: number;
  categoryName?: string;
  images?: string[];
  stock?: number;
  rating?: number;
  reviewsCount?: number;
  isFlash?: boolean;
  createdAt?: string;
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

