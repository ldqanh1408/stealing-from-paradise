import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  id: string;
  skuCode: string;
  variantName: string;
  stockQuantity: number;
  isFlash: boolean;
  price?: number;
  originalPrice?: number;
}

/** Matches backend ProductResponse: GET /products/{productId} */
export interface ProductDetail {
  productId: string;
  sellerId: number;
  sellerName?: string;
  name: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  categorySlug?: string;
  attributes?: Record<string, unknown>;
  images?: string[];
  status?: string;
  rejectReason?: string;
  variants?: ProductVariant[];
  rating?: number;
  reviewsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** Backend VariantResponse shape */
interface BackendVariant {
  id: string;
  productId: string;
  skuCode: string;
  variantName: string;
  price: number;
  originalPrice?: number;
  stockQuantity: number;
  isFlash: boolean;
  status: string;
  imageUrl?: string;
  variantAttributes?: Record<string, unknown>;
}

/** Backend ProductResponse shape */
interface BackendProductResponse {
  id: string;
  name: string;
  slug: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  sellerId: number;
  sellerName?: string;
  status: string;
  attributes?: Record<string, unknown>;
  variants: BackendVariant[];
  images: string[];
  rejectReason?: string;
  rejectCount?: number;
  createdAt: string;
  updatedAt?: string;
  publishedAt?: string;
}

function mapBackendProduct(raw: BackendProductResponse): ProductDetail {
  return {
    productId: raw.id,
    sellerId: raw.sellerId,
    sellerName: raw.sellerName,
    name: raw.name,
    slug: raw.slug,
    description: raw.description,
    categoryId: raw.categoryId,
    categoryName: raw.categoryName,
    status: raw.status,
    attributes: raw.attributes,
    images: raw.images ?? [],
    rejectReason: raw.rejectReason,
    variants: (raw.variants ?? []).map(v => ({
      id: v.id,
      skuCode: v.skuCode,
      variantName: v.variantName,
      stockQuantity: v.stockQuantity ?? 0,
      isFlash: v.isFlash ?? false,
      price: v.price,
      originalPrice: v.originalPrice,
    })),
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
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
    apiClient.get<ApiResponse<BackendProductResponse>>(`/products/${productId}`)
      .then(res => ({
        ...res,
        data: {
          ...res.data,
          data: mapBackendProduct(res.data.data),
        },
      })),

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
