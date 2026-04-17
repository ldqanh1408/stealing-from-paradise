const STATUSES: Record<string, { label: string; color: string }> = {
  PENDING:   { label: 'Chờ xác nhận', color: 'bg-yellow-100 text-yellow-700' },
  CONFIRMED: { label: 'Đã xác nhận',  color: 'bg-blue-100 text-blue-700' },
  SHIPPING:  { label: 'Đang giao',    color: 'bg-purple-100 text-purple-700' },
  DELIVERED: { label: 'Đã nhận',      color: 'bg-green-100 text-green-700' },
  CANCELLED: { label: 'Đã huỷ',       color: 'bg-red-100 text-red-700' },
};

// Placeholder — sẽ được thay bằng API sau
const ORDERS: { id: string; date: string; total: number; status: string; items: number }[] = [];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function OrderHistoryPage() {
  if (ORDERS.length === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <div className="w-24 h-24 rounded-full bg-purple-50 flex items-center justify-center mx-auto mb-6 text-5xl">
          📦
        </div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Chưa có đơn hàng nào</h2>
        <p className="text-gray-500 mb-8">Các đơn hàng của bạn sẽ xuất hiện ở đây sau khi đặt mua</p>
        <a
          href="/products"
          className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
        >
          Khám phá sản phẩm
        </a>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Đơn hàng của tôi</h1>
      <div className="space-y-4">
        {ORDERS.map((order) => {
          const st = STATUSES[order.status] ?? { label: order.status, color: 'bg-gray-100 text-gray-700' };
          return (
            <div key={order.id} className="bg-white rounded-2xl border border-gray-100 p-5 hover:shadow-sm transition-shadow">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-bold text-gray-900">#{order.id}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${st.color}`}>{st.label}</span>
                  </div>
                  <p className="text-sm text-gray-500">{order.items} sản phẩm · {order.date}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="font-bold text-gray-900">{fmt(order.total)}</p>
                  <button className="text-sm text-blue-600 hover:text-blue-700 font-medium mt-1">Xem chi tiết</button>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
