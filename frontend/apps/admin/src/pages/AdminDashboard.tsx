const STATS = [
  { label: 'Tổng người dùng', value: '0', icon: '👥', gradient: 'from-blue-500 to-cyan-500', trend: '+0 hôm nay' },
  { label: 'Sản phẩm chờ duyệt', value: '0', icon: '📦', gradient: 'from-amber-500 to-orange-500', trend: 'Cần xem xét' },
  { label: 'Yêu cầu hoàn tiền', value: '0', icon: '💸', gradient: 'from-red-500 to-pink-500', trend: 'Chờ xử lý' },
  { label: 'Flash Sale đang chạy', value: '0', icon: '⚡', gradient: 'from-violet-500 to-purple-600', trend: 'Phiên hoạt động' },
];

const QUICK_LINKS = [
  { label: 'Quản lý người dùng', icon: '👤', href: '/users', desc: 'Xem, khoá, mở khoá tài khoản', color: 'hover:border-blue-300 hover:bg-blue-50' },
  { label: 'Duyệt sản phẩm', icon: '✅', href: '/product-moderation', desc: 'Kiểm duyệt sản phẩm mới', color: 'hover:border-green-300 hover:bg-green-50' },
  { label: 'Xử lý hoàn tiền', icon: '💰', href: '/refunds', desc: 'Xem xét và phê duyệt hoàn tiền', color: 'hover:border-red-300 hover:bg-red-50' },
  { label: 'Cấu hình Flash Sale', icon: '⚡', href: '/flash-sale-config', desc: 'Tạo và quản lý phiên flash sale', color: 'hover:border-violet-300 hover:bg-violet-50' },
];

export default function AdminDashboard() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8 flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Bảng điều khiển Admin</h1>
          <p className="text-gray-500 mt-1 text-sm">Tổng quan hệ thống · {new Date().toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</p>
        </div>
        <span className="flex items-center gap-1.5 text-xs font-medium text-green-700 bg-green-100 px-3 py-1.5 rounded-full">
          <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
          Hệ thống hoạt động
        </span>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {STATS.map(({ label, value, icon, gradient, trend }) => (
          <div key={label} className="bg-white rounded-2xl border border-gray-100 p-5 hover:shadow-sm transition-shadow overflow-hidden relative">
            <div className={`absolute top-0 right-0 w-24 h-24 rounded-full bg-gradient-to-br ${gradient} opacity-10 translate-x-8 -translate-y-8`} />
            <div className={`inline-flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-to-br ${gradient} text-white text-lg mb-4 shadow-sm`}>
              {icon}
            </div>
            <p className="text-2xl font-bold text-gray-900 mb-0.5">{value}</p>
            <p className="text-sm text-gray-500 mb-1">{label}</p>
            <p className="text-xs text-gray-400">{trend}</p>
          </div>
        ))}
      </div>

      {/* Quick links */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Truy cập nhanh</h2>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {QUICK_LINKS.map(({ label, icon, href, desc, color }) => (
            <a
              key={label}
              href={href}
              className={`group bg-white rounded-2xl border-2 border-gray-100 p-5 transition-all duration-150 cursor-pointer ${color}`}
            >
              <span className="text-3xl block mb-3">{icon}</span>
              <h3 className="font-semibold text-gray-900 text-sm mb-1">{label}</h3>
              <p className="text-xs text-gray-500">{desc}</p>
            </a>
          ))}
        </div>
      </div>

      {/* Recent activity placeholder */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6">
        <h2 className="font-semibold text-gray-900 mb-4">Hoạt động gần đây</h2>
        <div className="text-center py-10 text-gray-400">
          <span className="text-4xl block mb-3">📊</span>
          <p className="text-sm">Chưa có hoạt động nào được ghi nhận</p>
        </div>
      </div>
    </div>
  );
}
