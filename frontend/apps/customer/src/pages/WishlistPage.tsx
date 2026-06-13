import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useWishlistStore } from '@shared/store/wishlistStore';
import { fmtVnd } from '@shared/utils/format';

export default function WishlistPage() {
  const { items, isLoading, error, fetchWishlist, remove } = useWishlistStore();

  useEffect(() => {
    fetchWishlist();
  }, [fetchWishlist]);

  return (
    <div className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">❤️ Sản phẩm yêu thích</h1>

      {error && (
        <div className="mb-4 p-3 rounded-xl bg-red-50 text-red-600 text-sm">{error}</div>
      )}

      {isLoading ? (
        <div className="py-16 text-center text-gray-400">⏳ Đang tải...</div>
      ) : items.length === 0 ? (
        <div className="py-16 text-center">
          <div className="text-5xl mb-3">🤍</div>
          <p className="text-gray-500 mb-4">Bạn chưa thích sản phẩm nào.</p>
          <Link
            to="/products"
            className="inline-block px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold rounded-xl transition-colors"
          >
            Khám phá sản phẩm
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {items.map((item) => (
            <div
              key={item.productId}
              className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg transition-all flex flex-col"
            >
              <Link to={`/products/${item.productId}`} className="block">
                <div className="relative bg-gradient-to-br from-gray-100 to-gray-200 aspect-square flex items-center justify-center overflow-hidden">
                  {item.thumbnailUrl ? (
                    <img src={item.thumbnailUrl} alt={item.productName} className="w-full h-full object-cover" />
                  ) : (
                    <span className="text-4xl">🛍️</span>
                  )}
                  {item.available === false && (
                    <span className="absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-bold text-white bg-gray-500">
                      Ngừng bán
                    </span>
                  )}
                </div>
              </Link>
              <div className="p-3 flex flex-col flex-1">
                <Link to={`/products/${item.productId}`} className="flex-1">
                  <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 hover:text-blue-600 transition-colors">
                    {item.productName}
                  </h3>
                </Link>
                {item.minPrice != null && (
                  <p className="text-base font-bold text-red-600 mb-2">{fmtVnd(item.minPrice)}</p>
                )}
                <div className="flex gap-1.5 mt-auto">
                  <Link
                    to={`/products/${item.productId}`}
                    className="flex-1 py-2 border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold rounded-xl transition-colors text-center"
                  >
                    Xem
                  </Link>
                  <button
                    onClick={() => remove(item.productId)}
                    className="flex-1 py-2 border border-red-200 text-red-500 hover:bg-red-50 text-xs font-semibold rounded-xl transition-colors"
                  >
                    Bỏ thích
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
