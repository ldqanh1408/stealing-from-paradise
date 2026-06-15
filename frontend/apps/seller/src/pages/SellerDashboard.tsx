import { useQuery } from '@tanstack/react-query';
import { sellerApi } from '@shared/api/seller.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const QUICK_ACTIONS = [
  { label: 'Thêm sản phẩm', icon: '➕', desc: 'Đăng sản phẩm mới lên cửa hàng', href: '/products', color: 'border-blue-200 hover:border-blue-400 hover:bg-blue-50' },
  { label: 'Xem đơn hàng', icon: '📋', desc: 'Quản lý và xử lý đơn hàng', href: '/orders', color: 'border-green-200 hover:border-green-400 hover:bg-green-50' },
  { label: 'Xem thu nhập', icon: '💰', desc: 'Theo dõi thu nhập từ Stripe', href: '/payments', color: 'border-violet-200 hover:border-violet-400 hover:bg-violet-50' },
];

export default function SellerDashboard() {
  const { data: stats, isLoading, error } = useQuery({
    queryKey: ['seller-dashboard-stats'],
    queryFn: () => sellerApi.getDashboardStats().then(r => r.data.data),
    retry: 1,
  });

  const displayStats = [
    {
      label: 'Tổng sản phẩm',
      value: stats?.totalProducts?.toLocaleString('vi-VN') ?? '—',
      icon: '📦',
      color: 'from-blue-500 to-blue-600',
      light: 'bg-blue-50 text-blue-700',
    },
    {
      label: 'Đơn hàng hôm nay',
      value: stats?.ordersToday?.toLocaleString('vi-VN') ?? '—',
      icon: '🛒',
      color: 'from-green-500 to-green-600',
      light: 'bg-green-50 text-green-700',
    },
    {
      label: 'Doanh thu tháng',
      value: stats ? fmt(stats.revenueMonth) : '—',
      icon: '💰',
      color: 'from-purple-500 to-purple-600',
      light: 'bg-purple-50 text-purple-700',
    },
  ];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard Người Bán</h1>
        <p className="text-gray-500 mt-1">Tổng quan hoạt động cửa hàng của bạn</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-8">
        {displayStats.map(({ label, value, icon, color, light }) => (
          <div key={label} className="bg-white rounded-2xl border border-gray-100 p-5 hover:shadow-sm transition-shadow">
            <div className="flex items-start justify-between mb-4">
              <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${color} flex items-center justify-center text-lg shadow-sm`}>
                {icon}
              </div>
            </div>
            {isLoading ? (
              <div className="h-8 bg-gray-100 rounded-lg animate-pulse mb-1" />
            ) : (
              <p className="text-2xl font-bold text-gray-900 mb-1">{value}</p>
            )}
            <p className="text-sm text-gray-500">{label}</p>
          </div>
        ))}
      </div>

      {/* Pending orders badge */}
      {stats && stats.pendingOrders > 0 && (
        <div className="mb-8 bg-amber-50 border border-amber-200 rounded-2xl p-4 flex items-center gap-3">
          <span className="text-2xl">⚠️</span>
          <div className="flex-1">
            <p className="font-semibold text-amber-900">
              Bạn có {stats.pendingOrders} đơn hàng chờ xác nhận
            </p>
            <p className="text-sm text-amber-700">Hãy cập nhật mã vận đơn để đơn hàng được chuyển sang trạng thái "Đang giao".</p>
          </div>
          <a
            href="/orders"
            className="shrink-0 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium rounded-xl transition-colors"
          >
            Xem ngay
          </a>
        </div>
      )}

      {/* Getting started notice */}
      {(!stats || stats.totalProducts === 0) && !isLoading && (
        <div className="bg-gradient-to-r from-blue-50 to-violet-50 border border-blue-100 rounded-2xl p-6 flex items-start gap-4 mb-8">
          <span className="text-3xl shrink-0">🚀</span>
          <div>
            <h3 className="font-semibold text-gray-900 mb-1">Bắt đầu bán hàng ngay!</h3>
            <p className="text-sm text-gray-600 mb-3">
              Đăng sản phẩm đầu tiên và kết nối Stripe để bắt đầu nhận thanh toán từ khách hàng.
            </p>
            <div className="flex gap-2">
              <a href="/products" className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-xl transition-colors">
                Thêm sản phẩm
              </a>
              <a href="/stripe-onboarding" className="px-4 py-2 border border-blue-200 text-blue-700 hover:bg-blue-100 text-sm font-medium rounded-xl transition-colors">
                Kết nối Stripe
              </a>
            </div>
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div className="mb-8">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Thao tác nhanh</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {QUICK_ACTIONS.map(({ label, icon, desc, href, color }) => (
            <a
              key={label}
              href={href}
              className={`group bg-white rounded-2xl border-2 p-6 transition-all duration-150 cursor-pointer ${color}`}
            >
              <span className="text-3xl block mb-3">{icon}</span>
              <h3 className="font-semibold text-gray-900 group-hover:text-gray-800 mb-1">{label}</h3>
              <p className="text-sm text-gray-500">{desc}</p>
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}
