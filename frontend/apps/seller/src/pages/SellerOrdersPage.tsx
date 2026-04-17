const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING:   { label: 'Chờ xác nhận', color: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED: { label: 'Đã xác nhận',  color: 'bg-blue-100 text-blue-700' },
  SHIPPING:  { label: 'Đang giao',    color: 'bg-purple-100 text-purple-700' },
  DELIVERED: { label: 'Đã giao',      color: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Đã huỷ',       color: 'bg-red-100 text-red-700' },
};

// Placeholder — sẽ được thay bằng API sau
const ORDERS: { id: string; customer: string; total: number; status: string; date: string; items: number }[] = [];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function SellerOrdersPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-1">Quản lý và xử lý đơn hàng từ khách</p>
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap gap-2 mb-5">
        {Object.entries(STATUS_MAP).map(([key, { label }], i) => (
          <button key={key} className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            i === 0 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}>{label}</button>
        ))}
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['Mã đơn', 'Khách hàng', 'Sản phẩm', 'Tổng tiền', 'Trạng thái', 'Ngày đặt', ''].map((h) => (
                  <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {ORDERS.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-16 text-center text-gray-400">
                    <span className="text-4xl block mb-3">📋</span>
                    Chưa có đơn hàng nào
                  </td>
                </tr>
              ) : (
                ORDERS.map((order) => {
                  const st = STATUS_MAP[order.status] ?? { label: order.status, color: 'bg-gray-100 text-gray-700' };
                  return (
                    <tr key={order.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4 font-mono font-medium text-gray-900">#{order.id}</td>
                      <td className="px-5 py-4 text-gray-700">{order.customer}</td>
                      <td className="px-5 py-4 text-gray-500">{order.items} sản phẩm</td>
                      <td className="px-5 py-4 font-semibold text-gray-900">{fmt(order.total)}</td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.color}`}>{st.label}</span>
                      </td>
                      <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{order.date}</td>
                      <td className="px-5 py-4">
                        <button className="text-blue-600 hover:text-blue-700 font-medium text-xs">Xem</button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
