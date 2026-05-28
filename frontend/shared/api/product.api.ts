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

/** Matches backend ProductCard (used in product grids from search service) */
export interface ProductListItem {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
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

/** Backend SearchService ProductCard shape */
interface SearchProductCard {
  productId: string;
  name: string;
  sellerId: number;
  sellerName: string;
  categoryId: string;
  categoryName: string;
  priceMin: number | null;
  priceMax: number | null;
  images: string[];
  stockAvailable: number;
  isFlash: boolean;
  thumbnailUrl: string;
}

interface SearchResponse {
  totalResults: number;
  page: number;
  size: number;
  totalPages: number;
  products: SearchProductCard[];
}

function mapProductCard(card: SearchProductCard): ProductListItem {
  return {
    productId: card.productId,
    sellerId: card.sellerId,
    sellerName: card.sellerName,
    name: card.name,
    price: card.priceMin ?? undefined,
    originalPrice: card.priceMax ?? undefined,
    categoryName: card.categoryName,
    images: card.images,
    stock: card.stockAvailable,
    isFlash: card.isFlash,
  };
}

export const productApi = {
  /**
   * List products via search service (the only product listing endpoint).
   * Params: category → category_id, search → q
   */
  getProducts: async (params?: {
    category?: string;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) => {
    const res = await apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: params?.search || undefined,
        category_id: params?.category || undefined,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        sort: params?.sort || undefined,
      },
    });
    const body = res.data.data;
    return {
      ...res,
      data: {
        ...res.data,
        data: {
          content: (body?.products ?? []).map(mapProductCard),
          totalElements: body?.totalResults ?? 0,
          totalPages: body?.totalPages ?? 0,
        },
      },
    };
  },

  /** Get product by ID — returns full ProductDetail from product service */
  getProductById: (productId: string) =>
    apiClient.get<ApiResponse<ProductDetail>>(`/products/${productId}`),

  /** Search products (delegates to search service) */
  searchProducts: (query: string, params?: {
    category?: string;
    page?: number;
    size?: number;
  }) =>
    apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: query,
        category_id: params?.category || undefined,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
      },
    }).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: {
          content: (res.data.data?.products ?? []).map(mapProductCard),
          totalElements: res.data.data?.totalResults ?? 0,
          totalPages: res.data.data?.totalPages ?? 0,
        },
      },
    })),
};
