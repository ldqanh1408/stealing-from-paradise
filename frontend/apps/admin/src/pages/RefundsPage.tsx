const STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING:  { label: 'Chờ xử lý',  color: 'bg-yellow-100 text-yellow-700' },
  APPROVED: { label: 'Đã duyệt',   color: 'bg-green-100 text-green-700' },
  REJECTED: { label: 'Từ chối',    color: 'bg-red-100 text-red-700' },
  REFUNDED: { label: 'Đã hoàn',    color: 'bg-blue-100 text-blue-700' },
};

// Placeholder — sẽ được thay bằng API sau
const REFUNDS: { id: string; orderId: string; customer: string; amount: number; reason: string; status: string; date: string }[] = [];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function RefundsPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý hoàn tiền</h1>
        <p className="text-sm text-gray-500 mt-1">Xem xét và xử lý các yêu cầu hoàn tiền từ khách hàng</p>
      </div>

      {/* Status filter */}
      <div className="flex gap-2 mb-5 flex-wrap">
        {['Tất cả', ...Object.values(STATUS_MAP).map((s) => s.label)].map((label, i) => (
          <button key={label} className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
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
                {['Mã YC', 'Đơn hàng', 'Khách hàng', 'Số tiền', 'Lý do', 'Trạng thái', 'Ngày', 'Thao tác'].map((h) => (
                  <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {REFUNDS.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-16 text-center text-gray-400">
                    <span className="text-4xl block mb-3">💸</span>
                    Không có yêu cầu hoàn tiền nào
                  </td>
                </tr>
              ) : (
                REFUNDS.map((r) => {
                  const st = STATUS_MAP[r.status] ?? { label: r.status, color: 'bg-gray-100 text-gray-600' };
                  return (
                    <tr key={r.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4 font-mono text-gray-900">#{r.id}</td>
                      <td className="px-5 py-4 font-mono text-gray-500">#{r.orderId}</td>
                      <td className="px-5 py-4 text-gray-700">{r.customer}</td>
                      <td className="px-5 py-4 font-semibold text-gray-900">{fmt(r.amount)}</td>
                      <td className="px-5 py-4 text-gray-500 max-w-xs truncate">{r.reason}</td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.color}`}>{st.label}</span>
                      </td>
                      <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{r.date}</td>
                      <td className="px-5 py-4">
                        {r.status === 'PENDING' ? (
                          <div className="flex gap-2">
                            <button className="text-xs text-green-600 hover:text-green-700 font-medium">Duyệt</button>
                            <button className="text-xs text-red-500 hover:text-red-600 font-medium">Từ chối</button>
                          </div>
                        ) : (
                          <button className="text-xs text-blue-600 hover:text-blue-700 font-medium">Xem</button>
                        )}
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
