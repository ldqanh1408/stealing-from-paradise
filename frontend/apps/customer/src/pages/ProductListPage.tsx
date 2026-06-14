import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { productApi, type ProductDetail } from '@shared/api/product.api';
import { categoryApi } from '@shared/api/category.api';
import { flashSaleApi } from '@shared/api/flashSale.api';
import { notify } from '@shared/lib/toast';
import ProductCard from '@/components/ProductCard';

// The "All" chip is always first. Real categories are loaded from the backend.
// The search service filters by category *slug* (not UUID), so chip values are slugs.
const ALL_CATEGORY = { value: '', label: 'Tất cả' };

// Emoji for the Shopee-style category strip, matched by keyword in the slug/name.
function categoryEmoji(value: string, label: string): string {
  const key = `${value} ${label}`.toLowerCase();
  if (/electron|phone|laptop|audio|tablet/.test(key)) return '📱';
  if (/home|living|kitchen|furnit/.test(key)) return '🏠';
  if (/sport|outdoor/.test(key)) return '⚽';
  if (/fashion|cloth|wear|shoe/.test(key)) return '👕';
  if (/accessor/.test(key)) return '🎧';
  if (value === '') return '🛍️';
  return '🏷️';
}

const SORT_OPTIONS = [
  { value: '', label: 'Mặc định' },
  { value: 'price_asc', label: 'Giá: Thấp → Cao' },
  { value: 'price_desc', label: 'Giá: Cao → Thấp' },
  { value: 'newest', label: 'Mới nhất' },
  { value: 'bestseller', label: 'Bán chạy' },
];

const PRICE_RANGES = [
  { label: 'Tất cả', min: undefined, max: undefined },
  { label: 'Dưới 100K', min: 0, max: 100000 },
  { label: '100K - 300K', min: 100000, max: 300000 },
  { label: '300K - 500K', min: 300000, max: 500000 },
  { label: '500K - 1M', min: 500000, max: 1000000 },
  { label: 'Trên 1M', min: 1000000, max: undefined },
];

const STOCK_FILTERS = [
  { value: 'in_stock', label: 'Còn hàng' },
  { value: 'all', label: 'Tất cả tồn kho' },
];

function mapSort(value: string) {
  switch (value) {
    case 'price_asc': return 'price_asc';
    case 'price_desc': return 'price_desc';
    case 'newest': return 'newest';
    case 'bestseller': return 'popular';
    default: return undefined;
  }
}

export default function ProductListPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { addToCart } = useCartStore();

  const [selectedCategory, setSelectedCategory] = useState('');
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') ?? '');
  const [page, setPage] = useState(0);

  // Sync with searches submitted from the global header (?q=...).
  useEffect(() => {
    setSearchQuery(searchParams.get('q') ?? '');
    setPage(0);
  }, [searchParams]);
  const [sort, setSort] = useState('');
  const [priceRange, setPriceRange] = useState(0);
  const [stockFilter, setStockFilter] = useState<'in_stock' | 'all'>('in_stock');
  const [flashOnly, setFlashOnly] = useState(false);
  const [showFilters, setShowFilters] = useState(false);

  const selectedPriceRange = PRICE_RANGES[priceRange];

  const { data: categoryData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoryApi.getCategories().then(r => r.data.data ?? []),
    staleTime: 1000 * 60 * 10,
  });

  // QA fixture categories (slugs prefixed fe-/e2e-) are seeded for end-to-end
  // tests, not real catalog — hide them from shoppers.
  const isFixtureCategory = (slug: string) => /^(fe|e2e)-/.test(slug);
  const categories = [
    ALL_CATEGORY,
    ...(categoryData ?? [])
      .filter(c => !isFixtureCategory(c.slug))
      .map(c => ({ value: c.slug, label: c.name })),
  ];

  // Live flash-sale session powers the homepage teaser (backend uses "LIVE").
  const { data: flashSessions } = useQuery({
    queryKey: ['flash-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data.content),
    staleTime: 1000 * 60,
  });
  const liveSession = (flashSessions ?? []).find(
    s => (s.status as string) === 'LIVE' || s.status === 'ACTIVE',
  );

  const { data, isLoading, error } = useQuery({
    queryKey: ['products', selectedCategory, page, searchQuery, sort, priceRange, stockFilter, flashOnly],
    queryFn: () =>
      productApi.getProducts({
        category: selectedCategory || undefined,
        search: searchQuery || undefined,
        priceMin: selectedPriceRange.min,
        priceMax: selectedPriceRange.max,
        inStock: stockFilter === 'in_stock',
        isFlash: flashOnly ? true : undefined,
        page,
        size: 20,
        sort: mapSort(sort),
      }).then(r => r.data.data),
    staleTime: 1000 * 30,
  });

  const products: ProductDetail[] = (data as any)?.content ?? (Array.isArray(data) ? data : []);
  const totalPages = (data as any)?.totalPages ?? 1;

  const handleAddToCart = async (product: ProductDetail) => {
    try {
      // List items come from the search service and carry no variants, so we
      // fetch the full product to learn its variants before adding to the cart.
      let variants = product.variants;
      if (!variants?.length) {
        const detail = await productApi.getProductById(product.productId).then(r => r.data.data);
        variants = detail?.variants;
      }
      if (!variants?.length) return;
      // Multiple variants need a choice — send the user to the detail page.
      if (variants.length > 1) {
        navigate(`/products/${product.productId}`);
        return;
      }
      await addToCart(variants[0].variantId, 1, undefined);
      navigate('/cart');
    } catch (err: any) {
      notify.error(err?.response?.data?.message || 'Không thể thêm sản phẩm vào giỏ hàng');
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
  };

  const activeFiltersCount = [
    selectedCategory !== '',
    sort !== '',
    priceRange !== 0,
    stockFilter !== 'in_stock',
    flashOnly,
  ].filter(Boolean).length;

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Hero banner */}
      <div className="bg-gradient-to-r from-blue-600 to-violet-700 text-white py-12 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <p className="text-blue-200 text-sm font-medium uppercase tracking-widest mb-2">Khám phá ngay</p>
          <h1 className="text-4xl sm:text-5xl font-bold mb-4">Hàng ngàn sản phẩm</h1>
          <p className="text-blue-100 text-lg mb-8">Giá tốt nhất, giao hàng nhanh nhất toàn quốc</p>
          <form onSubmit={handleSearch} className="max-w-lg mx-auto flex gap-2">
            <input
              type="text"
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="Tìm kiếm sản phẩm..."
              className="flex-1 px-5 py-3 rounded-xl text-gray-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300"
            />
            <button
              type="submit"
              className="px-6 py-3 bg-white text-blue-600 font-semibold rounded-xl hover:bg-blue-50 transition-colors text-sm"
            >
              Tìm kiếm
            </button>
          </form>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

        {/* Flash Sale teaser — only when a session is live */}
        {liveSession && (
          <Link
            to="/flash-sales"
            className="flex items-center gap-4 mb-5 p-4 rounded-2xl bg-gradient-to-r from-red-500 to-orange-500 text-white hover:shadow-lg transition-shadow"
          >
            <span className="text-3xl shrink-0">⚡</span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-bold text-lg">FLASH SALE</span>
                <span className="px-2 py-0.5 rounded-full bg-white/25 text-[11px] font-bold uppercase tracking-wide">
                  Đang diễn ra
                </span>
              </div>
              <p className="text-sm text-white/90 truncate">{liveSession.name}</p>
            </div>
            <span className="shrink-0 px-4 py-2 rounded-xl bg-white text-red-600 font-semibold text-sm">
              Mua ngay →
            </span>
          </Link>
        )}

        {/* Category strip — Shopee-style icon cards */}
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-gray-700">Danh mục</h2>
          <button
            onClick={() => setShowFilters(f => !f)}
            className="flex items-center gap-1.5 px-3.5 py-1.5 bg-white border border-gray-200 rounded-xl text-sm font-medium text-gray-700 hover:border-gray-300 hover:text-gray-900 transition-colors shrink-0"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
            </svg>
            Bộ lọc
            {activeFiltersCount > 0 && (
              <span className="w-5 h-5 bg-blue-600 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                {activeFiltersCount}
              </span>
            )}
          </button>
        </div>
        <div className="flex gap-3 overflow-x-auto pb-2 mb-4 scrollbar-none">
          {categories.map(cat => (
            <button
              key={cat.value}
              onClick={() => { setSelectedCategory(cat.value); setPage(0); }}
              className="shrink-0 w-[88px] flex flex-col items-center gap-2 group"
            >
              <span
                className={`w-16 h-16 rounded-2xl flex items-center justify-center text-2xl border transition-all ${
                  selectedCategory === cat.value
                    ? 'bg-blue-600 border-blue-600 shadow-md scale-105'
                    : 'bg-white border-gray-100 group-hover:border-blue-300 group-hover:shadow-sm'
                }`}
              >
                {categoryEmoji(cat.value, cat.label)}
              </span>
              <span
                className={`text-xs text-center leading-tight line-clamp-2 ${
                  selectedCategory === cat.value ? 'text-blue-600 font-semibold' : 'text-gray-600'
                }`}
              >
                {cat.label}
              </span>
            </button>
          ))}
        </div>

        {/* Advanced filter panel */}
        {showFilters && (
          <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-4 space-y-5">
            {/* Sort */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Sắp xếp</p>
              <div className="flex flex-wrap gap-2">
                {SORT_OPTIONS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => { setSort(opt.value); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      sort === opt.value
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Price range */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Khoảng giá</p>
              <div className="flex flex-wrap gap-2">
                {PRICE_RANGES.map((range, i) => (
                  <button
                    key={i}
                    onClick={() => { setPriceRange(i); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      priceRange === i
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {range.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Stock + flash sale */}
            <div>
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">Tình trạng</p>
              <div className="flex flex-wrap gap-2">
                {STOCK_FILTERS.map(opt => (
                  <button
                    key={opt.value}
                    onClick={() => { setStockFilter(opt.value as 'in_stock' | 'all'); setPage(0); }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                      stockFilter === opt.value
                        ? 'bg-blue-600 text-white border-blue-600'
                        : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    {opt.label}
                  </button>
                ))}
                <button
                  onClick={() => { setFlashOnly(v => !v); setPage(0); }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-all ${
                    flashOnly
                      ? 'bg-red-600 text-white border-red-600'
                      : 'bg-gray-50 text-gray-700 border-gray-200 hover:border-red-300'
                  }`}
                >
                  Chỉ Flash Sale
                </button>
              </div>
            </div>

            {/* Active filter chips + clear */}
            {activeFiltersCount > 0 && (
              <div className="flex items-center gap-2 pt-2 border-t border-gray-100">
                <span className="text-xs text-gray-500">Đang lọc:</span>
                {selectedCategory && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    {categories.find(c => c.value === selectedCategory)?.label}
                    <button onClick={() => { setSelectedCategory(''); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {sort && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    {SORT_OPTIONS.find(s => s.value === sort)?.label}
                    <button onClick={() => { setSort(''); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {priceRange !== 0 && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    {PRICE_RANGES[priceRange].label}
                    <button onClick={() => { setPriceRange(0); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {stockFilter !== 'in_stock' && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded-full">
                    Tất cả tồn kho
                    <button onClick={() => { setStockFilter('in_stock'); setPage(0); }} className="hover:text-blue-900 ml-0.5">×</button>
                  </span>
                )}
                {flashOnly && (
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-red-50 text-red-700 text-xs rounded-full">
                    Flash Sale
                    <button onClick={() => { setFlashOnly(false); setPage(0); }} className="hover:text-red-900 ml-0.5">×</button>
                  </span>
                )}
                <button
                  onClick={() => { setSelectedCategory(''); setSort(''); setPriceRange(0); setStockFilter('in_stock'); setFlashOnly(false); setPage(0); }}
                  className="text-xs text-red-500 hover:text-red-700 font-medium ml-1"
                >
                  Xóa tất cả
                </button>
              </div>
            )}
          </div>
        )}

        {/* Results header */}
        <div className="flex items-center justify-between mb-3">
          <p className="text-base font-semibold text-gray-900">
            {searchQuery ? `Kết quả tìm kiếm cho "${searchQuery}"` : 'Gợi ý hôm nay'}
            {(selectedCategory || sort || priceRange !== 0 || stockFilter !== 'in_stock' || flashOnly) && (
              <span className="ml-1 text-sm font-normal text-gray-500">— đã lọc</span>
            )}
          </p>
          <p className="text-xs text-gray-400">
            {(data as any)?.totalElements > 0 && `${(data as any).totalElements} sản phẩm`}
          </p>
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 8 }).map((_, i) => (
              <div key={i} className="bg-white rounded-2xl border border-gray-100 overflow-hidden animate-pulse">
                <div className="aspect-square bg-gray-200" />
                <div className="p-3 space-y-2">
                  <div className="h-4 bg-gray-200 rounded w-3/4" />
                  <div className="h-4 bg-gray-200 rounded w-1/2" />
                  <div className="h-6 bg-gray-200 rounded w-2/3" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Error */}
        {error && !isLoading && (
          <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
            Không thể tải sản phẩm. Vui lòng thử lại.{' '}
            <button onClick={() => window.location.reload()} className="underline font-medium">Làm mới</button>
          </div>
        )}

        {/* Empty */}
        {!isLoading && !error && products.length === 0 && (
          <div className="text-center py-20">
            <span className="text-5xl block mb-4">🔍</span>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Không tìm thấy sản phẩm</h3>
            <p className="text-gray-500">Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.</p>
          </div>
        )}

        {/* Grid */}
        {!isLoading && !error && products.length > 0 && (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
              {products.map(p => (
                <ProductCard key={p.productId} product={p} onAddToCart={handleAddToCart} />
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex justify-center gap-2">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  ← Trước
                </button>
                <span className="px-4 py-2 text-sm text-gray-600">
                  Trang {page + 1} / {totalPages}
                </span>
                <button
                  onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  Sau →
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
