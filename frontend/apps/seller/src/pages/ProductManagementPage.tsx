export default function ProductManagementPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Sản phẩm của tôi</h1>
          <p className="text-sm text-gray-500 mt-1">Quản lý toàn bộ sản phẩm trong cửa hàng</p>
        </div>
        <button className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-colors shadow-sm">
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Thêm sản phẩm
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap gap-2 mb-5">
        {['Tất cả', 'Đang bán', 'Hết hàng', 'Nháp'].map((tab, i) => (
          <button key={tab} className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            i === 0 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}>{tab}</button>
        ))}
        <div className="ml-auto">
          <input type="text" placeholder="Tìm sản phẩm..." className="px-4 py-1.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        </div>
      </div>

      {/* Empty state */}
      <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
        <div className="w-20 h-20 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-5 text-4xl">📦</div>
        <h3 className="text-lg font-semibold text-gray-900 mb-2">Chưa có sản phẩm nào</h3>
        <p className="text-gray-500 text-sm mb-6 max-w-sm mx-auto">
          Hãy thêm sản phẩm đầu tiên để bắt đầu bán hàng trên nền tảng
        </p>
        <button className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-colors">
          Thêm sản phẩm đầu tiên
        </button>
      </div>
    </div>
  );
}
