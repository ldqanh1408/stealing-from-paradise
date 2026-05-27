import { useQueries } from '@tanstack/react-query';
import { adminApi } from '@shared/api/admin.api';
import { adminRefundApi } from '@shared/api/refund.api';
import { flashSaleApi } from '@shared/api/flashSale.api';

export default function AdminDashboard() {
  const stats = useQueries({
    queries: [
      {
        queryKey: ['admin-dash-users'],
        queryFn: () => adminApi.getUsers({ page: 0, size: 1 }).then(r => r.data.data?.totalElements ?? 0),
        staleTime: 60_000,
      },
      {
        queryKey: ['admin-dash-pending-products'],
        queryFn: () => adminApi.getPendingProducts({ page: 0, size: 1 }).then(r => r.data.data?.totalElements ?? 0),
        staleTime: 30_000,
      },
      {
        queryKey: ['admin-dash-pending-refunds'],
        queryFn: () => adminRefundApi.list({ status: 'PENDING', page: 0, size: 1 }).then(r => r.data.data?.totalElements ?? 0),
        staleTime: 30_000,
      },
      {
        queryKey: ['admin-dash-active-flash-sales'],
        queryFn: () => flashSaleApi.getSessions({ status: 'ACTIVE' }).then(r => r.data.data?.content?.length ?? 0),
        staleTime: 30_000,
      },
    ],
  });

  const [users, pendingProducts, pendingRefunds, activeFlashSales] = stats;
  const anyLoading = stats.some(s => s.isLoading);
  const anyError = stats.some(s => s.isError);

  const STATS = [
    { label: 'Tổng người dùng', value: anyLoading ? '...' : String(users.data ?? 0), icon: '👥', gradient: 'from-blue-500 to-cyan-500', trend: 'Tất cả vai trò' },
    { label: 'Sản phẩm chờ duyệt', value: anyLoading ? '...' : String(pendingProducts.data ?? 0), icon: '📦', gradient: 'from-amber-500 to-orange-500', trend: 'Cần xem xét' },
    { label: 'Yêu cầu hoàn tiền', value: anyLoading ? '...' : String(pendingRefunds.data ?? 0), icon: '💸', gradient: 'from-red-500 to-pink-500', trend: 'Chờ xử lý' },
    { label: 'Flash Sale đang chạy', value: anyLoading ? '...' : String(activeFlashSales.data ?? 0), icon: '⚡', gradient: 'from-violet-500 to-purple-600', trend: 'Phiên hoạt động' },
  ];

  const QUICK_LINKS = [
    { label: 'Quản lý người dùng', icon: '👤', href: '/users', desc: 'Xem, khoá, mở khoá tài khoản', color: 'hover:border-blue-300 hover:bg-blue-50' },
    { label: 'Duyệt sản phẩm', icon: '✅', href: '/product-moderation', desc: 'Kiểm duyệt sản phẩm mới', color: 'hover:border-green-300 hover:bg-green-50' },
    { label: 'Xử lý hoàn tiền', icon: '💰', href: '/refunds', desc: 'Xem xét và phê duyệt hoàn tiền', color: 'hover:border-red-300 hover:bg-red-50' },
    { label: 'Cấu hình Flash Sale', icon: '⚡', href: '/flash-sale-config', desc: 'Tạo và quản lý phiên flash sale', color: 'hover:border-violet-300 hover:bg-violet-50' },
  ];

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

      {/* Error banner */}
      {anyError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-6">
          Không thể tải một số dữ liệu thống kê. Dữ liệu hiển thị có thể không đầy đủ.
        </div>
      )}

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

      {/* Detail cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Pending refunds detail */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-gray-900">Yêu cầu hoàn tiền chờ duyệt</h3>
            <span className="text-xs font-medium text-red-600 bg-red-50 px-2.5 py-1 rounded-full">
              {pendingRefunds.data ?? 0} yêu cầu
            </span>
          </div>
          {pendingRefunds.isLoading ? (
            <div className="h-16 bg-gray-100 rounded-xl animate-pulse" />
          ) : (pendingRefunds.data ?? 0) > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center gap-3 text-sm">
                <span className="text-2xl">⚠️</span>
                <p className="text-gray-600">
                  Có <strong className="text-red-600">{pendingRefunds.data}</strong> yêu cầu hoàn tiền đang chờ bạn xem xét và phê duyệt.
                </p>
              </div>
              <a href="/refunds" className="inline-flex items-center gap-1.5 px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-xl transition-colors">
                Xử lý ngay →
              </a>
            </div>
          ) : (
            <div className="flex items-center gap-3 text-sm text-gray-400 py-4">
              <span className="text-2xl">✅</span>
              <p>Không có yêu cầu hoàn tiền nào đang chờ.</p>
            </div>
          )}
        </div>

        {/* Pending products detail */}
        <div className="bg-white rounded-2xl border border-gray-100 p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-semibold text-gray-900">Sản phẩm chờ kiểm duyệt</h3>
            <span className="text-xs font-medium text-amber-600 bg-amber-50 px-2.5 py-1 rounded-full">
              {pendingProducts.data ?? 0} sản phẩm
            </span>
          </div>
          {pendingProducts.isLoading ? (
            <div className="h-16 bg-gray-100 rounded-xl animate-pulse" />
          ) : (pendingProducts.data ?? 0) > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center gap-3 text-sm">
                <span className="text-2xl">📋</span>
                <p className="text-gray-600">
                  Có <strong className="text-amber-600">{pendingProducts.data}</strong> sản phẩm mới từ người bán đang chờ bạn kiểm duyệt.
                </p>
              </div>
              <a href="/product-moderation" className="inline-flex items-center gap-1.5 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium rounded-xl transition-colors">
                Kiểm duyệt ngay →
              </a>
            </div>
          ) : (
            <div className="flex items-center gap-3 text-sm text-gray-400 py-4">
              <span className="text-2xl">✅</span>
              <p>Không có sản phẩm nào đang chờ kiểm duyệt.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
