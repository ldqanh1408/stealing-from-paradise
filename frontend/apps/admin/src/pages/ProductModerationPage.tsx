// Placeholder — sẽ được thay bằng API sau
const PENDING_PRODUCTS: { id: number; name: string; seller: string; category: string; price: number; submittedAt: string }[] = [];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function ProductModerationPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Duyệt sản phẩm</h1>
          <p className="text-sm text-gray-500 mt-1">Kiểm duyệt sản phẩm mới từ người bán</p>
        </div>
        <span className="text-sm font-medium text-amber-700 bg-amber-100 px-3 py-1.5 rounded-full">
          {PENDING_PRODUCTS.length} chờ duyệt
        </span>
      </div>

      {/* Filter */}
      <div className="flex gap-2 mb-5">
        {['Chờ duyệt', 'Đã duyệt', 'Từ chối'].map((tab, i) => (
          <button key={tab} className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            i === 0 ? 'bg-amber-500 text-white border-amber-500' : 'bg-white text-gray-600 border-gray-200 hover:border-amber-300'
          }`}>{tab}</button>
        ))}
      </div>

      {/* Empty / list */}
      {PENDING_PRODUCTS.length === 0 ? (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <span className="text-5xl block mb-4">✅</span>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Không có sản phẩm nào chờ duyệt</h3>
          <p className="text-sm text-gray-500">Tất cả sản phẩm đã được kiểm duyệt</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {PENDING_PRODUCTS.map((p) => (
            <div key={p.id} className="bg-white rounded-2xl border border-gray-100 p-5 flex items-start gap-4 hover:shadow-sm transition-shadow">
              <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0">🛍️</div>
              <div className="flex-1 min-w-0">
                <h3 className="font-semibold text-gray-900 mb-0.5">{p.name}</h3>
                <p className="text-sm text-gray-500">Người bán: {p.seller} · {p.category}</p>
                <p className="text-sm text-gray-500">Gửi lúc: {p.submittedAt}</p>
                <p className="font-bold text-gray-900 mt-1">{fmt(p.price)}</p>
              </div>
              <div className="flex flex-col gap-2 shrink-0">
                <button className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-xl transition-colors">✓ Duyệt</button>
                <button className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 text-sm font-medium rounded-xl transition-colors border border-red-200">✗ Từ chối</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
