import { useState } from 'react';

// Placeholder sessions — sẽ được thay bằng API sau
const SESSIONS: { id: number; name: string; startTime: string; endTime: string; status: string; productCount: number }[] = [];

const STATUS_COLORS: Record<string, string> = {
  UPCOMING: 'bg-blue-100 text-blue-700',
  ACTIVE:   'bg-green-100 text-green-700',
  ENDED:    'bg-gray-100 text-gray-600',
};

export default function FlashSaleConfigPage() {
  const [showForm, setShowForm] = useState(false);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Cấu hình Flash Sale</h1>
          <p className="text-sm text-gray-500 mt-1">Tạo và quản lý các phiên flash sale</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 px-4 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors shadow-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Tạo phiên mới
        </button>
      </div>

      {/* Create form (toggle) */}
      {showForm && (
        <div className="bg-white rounded-2xl border border-violet-100 p-6 mb-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4">Tạo phiên Flash Sale mới</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên phiên</label>
              <input type="text" placeholder="vd: Flash Sale 18:00 Thứ Sáu" className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian bắt đầu</label>
              <input type="datetime-local" className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian kết thúc</label>
              <input type="datetime-local" className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500" />
            </div>
          </div>
          <div className="flex gap-3 mt-5">
            <button className="px-5 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors">
              Tạo phiên
            </button>
            <button onClick={() => setShowForm(false)} className="px-5 py-2.5 border border-gray-200 text-gray-700 font-semibold text-sm rounded-xl hover:bg-gray-50 transition-colors">
              Huỷ
            </button>
          </div>
        </div>
      )}

      {/* Sessions list */}
      {SESSIONS.length === 0 ? (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <span className="text-5xl block mb-4">⚡</span>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Chưa có phiên Flash Sale nào</h3>
          <p className="text-sm text-gray-500 mb-6 max-w-sm mx-auto">
            Tạo phiên flash sale đầu tiên để thu hút khách hàng với giá ưu đãi đặc biệt
          </p>
          <button onClick={() => setShowForm(true)} className="px-6 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors">
            Tạo phiên đầu tiên
          </button>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Tên phiên', 'Bắt đầu', 'Kết thúc', 'Sản phẩm', 'Trạng thái', 'Thao tác'].map((h) => (
                    <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {SESSIONS.map((s) => (
                  <tr key={s.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-4 font-medium text-gray-900">{s.name}</td>
                    <td className="px-5 py-4 text-gray-500">{s.startTime}</td>
                    <td className="px-5 py-4 text-gray-500">{s.endTime}</td>
                    <td className="px-5 py-4 text-gray-700">{s.productCount} sản phẩm</td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[s.status] ?? 'bg-gray-100 text-gray-600'}`}>
                        {s.status === 'UPCOMING' ? 'Sắp diễn ra' : s.status === 'ACTIVE' ? 'Đang chạy' : 'Đã kết thúc'}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex gap-2">
                        <button className="text-xs text-blue-600 hover:text-blue-700 font-medium">Chỉnh sửa</button>
                        <button className="text-xs text-red-500 hover:text-red-600 font-medium">Xoá</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
