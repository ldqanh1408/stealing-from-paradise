import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { productApi, type ProductDetail } from '@shared/api/product.api';

const CATEGORIES = [
  { value: '', label: 'Tất cả' },
  { value: 'electronics', label: 'Điện tử' },
  { value: 'fashion', label: 'Thời trang' },
  { value: 'home', label: 'Gia dụng' },
  { value: 'accessories', label: 'Phụ kiện' },
  { value: 'books', label: 'Sách' },
  { value: 'footwear', label: 'Giày dép' },
  { value: 'bags', label: 'Túi xách' },
];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function discountPct(original?: number, price?: number) {
  if (!original || !price || original <= price) return null;
  return Math.round((1 - price / original) * 100);
}

function ProductCard({ product, onAddToCart }: { product: ProductDetail; onAddToCart: (p: ProductDetail) => void }) {
  const price = product.price ?? 0;
  const original = product.original_price ?? price;
  const disc = discountPct(original, price);
  const img = product.images?.[0];

  return (
    <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group flex flex-col">
      <Link to={`/products/${product.product_id}`} className="block">
        <div className="relative bg-gradient-to-br from-gray-100 to-gray-200 aspect-square flex items-center justify-center overflow-hidden">
          {img ? (
            <img src={img} alt={product.name} className="w-full h-full object-cover" />
          ) : (
            <span className="text-4xl">🛍️</span>
          )}
          {product.is_flash && (
            <span className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-bold text-white bg-red-500">
              ⚡ Flash Sale
            </span>
          )}
          {disc && (
            <span className="absolute top-2 right-2 bg-white/90 text-green-700 font-bold text-xs px-2 py-0.5 rounded-full">
              -{disc}%
            </span>
          )}
        </div>
      </Link>
      <div className="p-3 flex flex-col flex-1">
        <p className="text-xs text-gray-400 mb-1">{product.category || product.category_id || 'Sản phẩm'}</p>
        <Link to={`/products/${product.product_id}`} className="flex-1">
          <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-blue-600 transition-colors">
            {product.name}
          </h3>
        </Link>
        <div className="flex items-baseline gap-1.5 mb-1">
          <span className="text-base font-bold text-red-600">{fmt(price)}</span>
          {disc && (
            <span className="text-xs text-gray-400 line-through">{fmt(original)}</span>
          )}
        </div>
        {product.rating != null && (
          <div className="flex items-center gap-1 mb-2">
            <span className="text-yellow-400 text-xs">{'★'.repeat(Math.round(product.rating))}</span>
            <span className="text-xs text-gray-400">({product.reviews_count ?? 0})</span>
          </div>
        )}
        <div className="flex gap-1.5 mt-auto">
          <Link
            to={`/products/${product.product_id}`}
            className="flex-1 py-2 border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold rounded-xl transition-colors text-center"
          >
            Chi tiết
          </Link>
          <button
            onClick={(e) => { e.preventDefault(); onAddToCart(product); }}
            disabled={product.stock_available <= 0}
            className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white text-xs font-semibold rounded-xl transition-colors flex items-center justify-center gap-1"
          >
            ➕
          </button>
        </div>
      </div>
    </div>
  );
}

export default function ProductListPage() {
  const navigate = useNavigate();
  const { addToCart } = useCartStore();
  const [selectedCategory, setSelectedCategory] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['products', selectedCategory, page, searchQuery],
    queryFn: () =>
      productApi.getProducts({
        category: selectedCategory || undefined,
        search: searchQuery || undefined,
        page,
        size: 20,
      }).then(r => r.data.data),
    staleTime: 1000 * 30,
  });

  // API can return either a PageResponse or an array
  const products: ProductDetail[] = (data as any)?.content ?? (Array.isArray(data) ? data : []);
  const totalPages = (data as any)?.totalPages ?? 1;

  const handleAddToCart = async (product: ProductDetail) => {
    if (!product.variants?.length) return;
    try {
      const sku = product.variants[0].sku_code;
      await addToCart(sku, 1, undefined);
      navigate('/cart');
    } catch (err: any) {
      console.error('Add to cart failed:', err);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
  };

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Hero banner */}
      <div className="bg-gradient-to-r from-blue-600 to-violet-700 text-white py-12 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <p className="text-blue-200 text-sm font-medium uppercase tracking-widest mb-2">Khám phá ngay</p>
          <h1 className="text-4xl sm:text-5xl font-bold mb-4">Hàng ngàn sản phẩm</h1>
          <p className="text-blue-100 text-lg mb-8">Giá tốt nhất, giao hàng nhanh nhất toàn quốc</p>
          {/* Search bar */}
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
        {/* Category filter */}
        <div className="flex gap-2 overflow-x-auto pb-2 mb-6 scrollbar-none">
          {CATEGORIES.map(cat => (
            <button
              key={cat.value}
              onClick={() => { setSelectedCategory(cat.value); setPage(0); }}
              className={`shrink-0 px-4 py-2 rounded-full text-sm font-medium border transition-all ${
                selectedCategory === cat.value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-700 border-gray-200 hover:border-blue-300 hover:text-blue-600'
              }`}
            >
              {cat.label}
            </button>
          ))}
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
            <p className="text-gray-500">Thử thay đổi danh mục hoặc từ khóa tìm kiếm.</p>
          </div>
        )}

        {/* Grid */}
        {!isLoading && !error && products.length > 0 && (
          <>
            <p className="text-sm text-gray-500 mb-4">
              {searchQuery ? `Kết quả tìm kiếm cho "${searchQuery}"` : 'Tất cả sản phẩm'}
              {' — '}{products.length} sản phẩm
            </p>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4 mb-6">
              {products.map(p => (
                <ProductCard key={p.product_id} product={p} onAddToCart={handleAddToCart} />
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
