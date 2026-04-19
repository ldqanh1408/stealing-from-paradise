import { Link } from 'react-router-dom';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

// Placeholder cart items — sẽ được thay bằng API sau
const CART_ITEMS: { id: number; name: string; price: number; qty: number; category: string }[] = [];

export default function CartPage() {
  const total = CART_ITEMS.reduce((sum, i) => sum + i.price * i.qty, 0);

  if (CART_ITEMS.length === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <div className="w-24 h-24 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-6 text-5xl">
          🛒
        </div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Giỏ hàng trống</h2>
        <p className="text-gray-500 mb-8">Bạn chưa thêm sản phẩm nào vào giỏ hàng</p>
        <Link
          to="/products"
          className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Tiếp tục mua sắm
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Giỏ hàng ({CART_ITEMS.length} sản phẩm)</h1>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Items */}
        <div className="lg:col-span-2 space-y-3">
          {CART_ITEMS.map((item) => (
            <div key={item.id} className="bg-white rounded-2xl border border-gray-100 p-4 flex items-center gap-4">
              <div className="w-20 h-20 rounded-xl bg-gray-100 flex items-center justify-center text-3xl shrink-0">🛍️</div>
              <div className="flex-1 min-w-0">
                <p className="text-xs text-gray-400">{item.category}</p>
                <h3 className="font-medium text-gray-900 truncate">{item.name}</h3>
                <p className="text-red-600 font-bold mt-1">{fmt(item.price)}</p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <button className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 text-gray-600 font-bold">−</button>
                <span className="w-8 text-center text-sm font-medium">{item.qty}</span>
                <button className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 text-gray-600 font-bold">+</button>
              </div>
              <button className="text-gray-300 hover:text-red-400 transition-colors shrink-0">
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="bg-white rounded-2xl border border-gray-100 p-6 h-fit sticky top-24">
          <h3 className="font-bold text-gray-900 mb-4">Tóm tắt đơn hàng</h3>
          <div className="space-y-3 text-sm mb-6">
            <div className="flex justify-between text-gray-600">
              <span>Tạm tính</span><span>{fmt(total)}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Phí vận chuyển</span><span className="text-green-600 font-medium">Miễn phí</span>
            </div>
            <div className="h-px bg-gray-100" />
            <div className="flex justify-between font-bold text-gray-900 text-base">
              <span>Tổng cộng</span><span className="text-red-600">{fmt(total)}</span>
            </div>
          </div>
          <Link to="/checkout" className="block w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white font-semibold text-center rounded-xl transition-all">
            Thanh toán ngay
          </Link>
        </div>
      </div>
    </div>
  );
}
