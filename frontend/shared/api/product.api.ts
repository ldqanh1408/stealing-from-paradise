import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface ProductVariant {
  variantId: string;
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

/** Backend product-service ProductResponse shape: GET /products/{productId} */
interface RawVariantResponse {
  id: string;
  productId: string;
  variantCode: string;
  variantName: string;
  variantAttributes?: Record<string, unknown>;
  price: number;
  originalPrice?: number;
  stockQuantity: number;
  status: string;
  imageUrl?: string;
}

interface RawProductResponse {
  id: string;
  name: string;
  slug?: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  sellerId: number;
  status?: string;
  attributes?: Record<string, unknown>;
  variants?: RawVariantResponse[];
  images?: { url: string; sortOrder?: number; variantId?: string | null }[];
  rejectReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

function mapProductDetail(raw: RawProductResponse): ProductDetail {
  const activeVariants = (raw.variants ?? []).filter(v => v.status === 'ACTIVE');
  const cheapest = activeVariants.length
    ? activeVariants.reduce((min, v) => (v.price < min.price ? v : min))
    : undefined;
  const images = (raw.images ?? [])
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map(img => img.url);
  return {
    productId: raw.id,
    sellerId: raw.sellerId,
    name: raw.name,
    description: raw.description,
    price: cheapest?.price,
    originalPrice: cheapest?.originalPrice,
    categoryId: raw.categoryId,
    categoryName: raw.categoryName,
    attributes: raw.attributes,
    images,
    status: raw.status,
    rejectReason: raw.rejectReason,
    stockAvailable: activeVariants.reduce((sum, v) => sum + (v.stockQuantity ?? 0), 0),
    variants: activeVariants.map(v => ({
      variantId: v.id,
      skuCode: v.variantCode,
      variantName: v.variantName,
      stock: v.stockQuantity ?? 0,
    })),
    createdAt: raw.createdAt,
    updatedAt: raw.updatedAt,
  };
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

/** Variant lookup by SKU: GET /products/variants/sku/{skuCode} */
export interface VariantBySku {
  id: string;
  variantCode: string;
  variantName: string;
  imageUrl?: string;
  productId: string;
  productName: string;
  sellerId: number;
  price: number;
}

export const productApi = {
  /**
   * List products via search service (the only product listing endpoint).
   * Params: category → category_id, search → q
   */
  getProducts: async (params?: {
    category?: string;
    search?: string;
    priceMin?: number;
    priceMax?: number;
    inStock?: boolean;
    isFlash?: boolean;
    page?: number;
    size?: number;
    sort?: string;
  }) => {
    const res = await apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: params?.search || undefined,
        category_id: params?.category || undefined,
        price_min: params?.priceMin,
        price_max: params?.priceMax,
        in_stock: params?.inStock,
        is_flash: params?.isFlash,
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

  /** Look up a single variant (with product name/image/price) by its SKU code. */
  getVariantBySku: (skuCode: string) =>
    apiClient
      .get<ApiResponse<VariantBySku>>(`/products/variants/sku/${encodeURIComponent(skuCode)}`)
      .then(res => res.data.data),

  /** Get product by ID — maps backend ProductResponse to ProductDetail */
  getProductById: (productId: string) =>
    apiClient.get<ApiResponse<RawProductResponse>>(`/products/${productId}`).then(res => ({
      ...res,
      data: {
        ...res.data,
        data: res.data.data ? mapProductDetail(res.data.data) : undefined,
      } as ApiResponse<ProductDetail>,
    })),

  /** Search products (delegates to search service) */
  searchProducts: (query: string, params?: {
    category?: string;
    priceMin?: number;
    priceMax?: number;
    inStock?: boolean;
    isFlash?: boolean;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    apiClient.get<ApiResponse<SearchResponse>>('/search/products', {
      params: {
        q: query,
        category_id: params?.category || undefined,
        price_min: params?.priceMin,
        price_max: params?.priceMax,
        in_stock: params?.inStock,
        is_flash: params?.isFlash,
        page: params?.page ?? 0,
        size: params?.size ?? 20,
        sort: params?.sort || undefined,
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
