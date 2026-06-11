import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { adminApi, type AdminSellerStripeAccountItem } from '@shared/api/admin.api';
import { fmtDateTime } from '@shared/utils/format';

const STATUS_CONFIG: Record<string, { label: string; badgeClass: string; icon: string }> = {
  COMPLETE: {
    label: 'Hoàn thành',
    badgeClass: 'bg-emerald-100 text-emerald-800 border-emerald-200',
    icon: '✅',
  },
  IN_PROGRESS: {
    label: 'Đang KYC',
    badgeClass: 'bg-sky-100 text-sky-800 border-sky-200',
    icon: '⏳',
  },
  PENDING: {
    label: 'Chưa bắt đầu',
    badgeClass: 'bg-amber-100 text-amber-800 border-amber-200',
    icon: '🔒',
  },
  SUSPENDED: {
    label: 'Bị hạn chế',
    badgeClass: 'bg-rose-100 text-rose-800 border-rose-200',
    icon: '⚠️',
  },
};

export default function SellerStripePage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin-seller-stripe-accounts'],
    queryFn: () => adminApi.getSellerStripeAccounts().then(r => r.data.data),
    retry: 1,
  });

  const summary = data?.summary || {
    totalSellers: 0,
    completedSellers: 0,
    pendingSellers: 0,
    inProgressSellers: 0,
    suspendedSellers: 0,
  };

  const accounts = data?.accounts || [];

  const filteredAccounts = accounts.filter((acc) => {
    const statusMatch = !statusFilter || acc.onboardingStatus === statusFilter;
    const query = searchQuery.trim().toLowerCase();
    const searchMatch =
      !query ||
      String(acc.sellerId).includes(query) ||
      acc.stripeAccountId.toLowerCase().includes(query);
    return statusMatch && searchMatch;
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-fade-in">
      <div className="flex flex-col md:flex-row md:items-center md:justify-between mb-8">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900 tracking-tight bg-clip-text bg-gradient-to-r from-gray-900 to-indigo-950">
            Quản lý Đối tác Seller (Stripe)
          </h1>
          <p className="text-gray-500 mt-1.5 text-sm">
            Theo dõi tiến trình onboarding, xác minh thông tin KYC và trạng thái tài khoản thanh toán Stripe Connect của các nhà bán hàng.
          </p>
        </div>
        <button
          onClick={() => refetch()}
          className="mt-4 md:mt-0 px-4.5 py-2 border border-gray-300 text-gray-700 hover:bg-gray-50 font-medium rounded-xl text-sm transition-all shadow-sm hover:shadow active:scale-95 flex items-center gap-2"
        >
          🔄 Làm mới dữ liệu
        </button>
      </div>

      {/* Stats Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-5 mb-8">
        {[
          {
            title: 'Tổng nhà bán hàng',
            val: summary.totalSellers,
            desc: 'Đã tạo tài khoản',
            gradient: 'from-slate-50 to-slate-100 border-slate-200 text-slate-800',
            dot: 'bg-slate-400',
          },
          {
            title: 'Hoàn thành KYC',
            val: summary.completedSellers,
            desc: 'Được phép bán hàng',
            gradient: 'from-emerald-50 to-emerald-100/50 border-emerald-200 text-emerald-800',
            dot: 'bg-emerald-500',
          },
          {
            title: 'Đang xác minh',
            val: summary.inProgressSellers,
            desc: 'Đang điền thông tin',
            gradient: 'from-sky-50 to-sky-100/50 border-sky-200 text-sky-800',
            dot: 'bg-sky-500',
          },
          {
            title: 'Chưa bắt đầu',
            val: summary.pendingSellers,
            desc: 'Cần bắt đầu liên kết',
            gradient: 'from-amber-50 to-amber-100/50 border-amber-200 text-amber-800',
            dot: 'bg-amber-500',
          },
          {
            title: 'Bị hạn chế / Khóa',
            val: summary.suspendedSellers,
            desc: 'Lỗi xác minh Stripe',
            gradient: 'from-rose-50 to-rose-100/50 border-rose-200 text-rose-800',
            dot: 'bg-rose-500',
          },
        ].map((card) => (
          <div
            key={card.title}
            className={`rounded-2xl border p-5 bg-gradient-to-br ${card.gradient} transition-transform hover:-translate-y-0.5 duration-200 shadow-sm`}
          >
            <div className="flex items-center gap-1.5 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
              <span className={`w-2.5 h-2.5 rounded-full ${card.dot} inline-block animate-pulse`} />
              {card.title}
            </div>
            <div className="text-3xl font-black text-gray-900 tracking-tight">{card.val}</div>
            <p className="text-xs text-gray-500 mt-1">{card.desc}</p>
          </div>
        ))}
      </div>

      {/* Filter and Search */}
      <div className="bg-white rounded-2xl border border-gray-200/80 p-5 mb-8 shadow-sm flex flex-col md:flex-row gap-4 items-center justify-between">
        <div className="relative w-full md:w-96">
          <span className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-gray-400 text-sm">
            🔍
          </span>
          <input
            type="text"
            placeholder="Tìm theo Seller ID hoặc Stripe Account ID..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2.5 text-sm bg-gray-50 hover:bg-gray-100/70 focus:bg-white border border-gray-200 rounded-xl transition-all focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          />
        </div>

        <div className="flex gap-3 w-full md:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="w-full md:w-52 px-4 py-2.5 text-sm bg-gray-50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="COMPLETE">Hoàn thành</option>
            <option value="IN_PROGRESS">Đang KYC</option>
            <option value="PENDING">Chưa bắt đầu</option>
            <option value="SUSPENDED">Bị hạn chế</option>
          </select>
        </div>
      </div>

      {/* Main Content Table */}
      {isLoading ? (
        <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center shadow-sm">
          <div className="inline-block animate-spin text-2xl mr-2">⏳</div>
          <span className="text-gray-500 text-sm font-medium">Đang tải dữ liệu onboarding...</span>
        </div>
      ) : error ? (
        <div className="bg-rose-50 border border-rose-200 rounded-2xl p-6 text-center shadow-sm">
          <p className="font-bold text-rose-800 text-lg mb-1">Không thể tải dữ liệu</p>
          <p className="text-rose-600 text-sm mb-4">Lỗi: {(error as any)?.response?.data?.message || error.message}</p>
          <button
            onClick={() => refetch()}
            className="px-5 py-2.5 bg-rose-600 hover:bg-rose-700 text-white font-semibold rounded-xl text-sm transition-all active:scale-95 shadow-sm"
          >
            Thử lại
          </button>
        </div>
      ) : filteredAccounts.length === 0 ? (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center shadow-sm">
          <div className="text-4xl mb-3">📭</div>
          <p className="font-bold text-gray-700 text-lg">Không tìm thấy nhà bán hàng nào</p>
          <p className="text-gray-400 text-sm mt-1">Vui lòng thay đổi từ khóa tìm kiếm hoặc bộ lọc trạng thái.</p>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  <th className="py-4 px-6">Seller ID</th>
                  <th className="py-4 px-6">Stripe Account ID</th>
                  <th className="py-4 px-6">Trạng thái KYC</th>
                  <th className="py-4 px-6 text-center">Xác minh</th>
                  <th className="py-4 px-6 text-center">Nhận tiền (Charges)</th>
                  <th className="py-4 px-6 text-center">Rút tiền (Payouts)</th>
                  <th className="py-4 px-6">Thời gian cập nhật</th>
                  <th className="py-4 px-6 text-right">Liên kết ngoài</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 text-sm text-gray-700">
                {filteredAccounts.map((acc) => {
                  const cfg = STATUS_CONFIG[acc.onboardingStatus] || STATUS_CONFIG.PENDING;
                  return (
                    <tr key={acc.sellerId} className="hover:bg-slate-50/50 transition-colors">
                      <td className="py-4 px-6 font-bold text-gray-900">
                        #{acc.sellerId}
                      </td>
                      <td className="py-4 px-6 font-mono text-xs text-gray-500">
                        {acc.stripeAccountId}
                      </td>
                      <td className="py-4 px-6">
                        <span className={`inline-flex items-center gap-1 px-3 py-1 border rounded-full text-xs font-semibold shadow-sm ${cfg.badgeClass}`}>
                          <span>{cfg.icon}</span>
                          {cfg.label}
                        </span>
                      </td>
                      <td className="py-4 px-6 text-center">
                        {acc.detailsSubmitted ? (
                          <span className="inline-block text-green-600 font-bold bg-green-50 px-2 py-0.5 rounded text-xs border border-green-200">Đã nộp ✓</span>
                        ) : (
                          <span className="inline-block text-gray-400 bg-gray-50 px-2 py-0.5 rounded text-xs border border-gray-200">Chưa —</span>
                        )}
                      </td>
                      <td className="py-4 px-6 text-center">
                        {acc.chargesEnabled ? (
                          <span className="inline-block text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded text-xs border border-emerald-100">Bật ✓</span>
                        ) : (
                          <span className="inline-block text-rose-600 font-semibold bg-rose-50 px-2 py-0.5 rounded text-xs border border-rose-100">Tắt ✕</span>
                        )}
                      </td>
                      <td className="py-4 px-6 text-center">
                        {acc.payoutsEnabled ? (
                          <span className="inline-block text-emerald-700 font-semibold bg-emerald-50 px-2 py-0.5 rounded text-xs border border-emerald-100">Bật ✓</span>
                        ) : (
                          <span className="inline-block text-rose-600 font-semibold bg-rose-50 px-2 py-0.5 rounded text-xs border border-rose-100">Tắt ✕</span>
                        )}
                      </td>
                      <td className="py-4 px-6 text-xs text-gray-400">
                        {fmtDateTime(acc.updatedAt)}
                      </td>
                      <td className="py-4 px-6 text-right">
                        {acc.stripeAccountId.startsWith('acct_manual_') ? (
                          <span className="text-xs text-gray-400 font-medium italic">Kết nối thủ công</span>
                        ) : (
                          <a
                            href={`https://dashboard.stripe.com/test/connect/accounts/${acc.stripeAccountId}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-semibold text-xs rounded-lg transition-colors border border-indigo-100"
                          >
                            Stripe Console ↗
                          </a>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="bg-gray-50 px-6 py-4.5 border-t border-gray-200 text-xs text-gray-500 font-medium flex items-center justify-between">
            <span>Hiển thị <strong>{filteredAccounts.length}</strong> / <strong>{accounts.length}</strong> nhà bán hàng.</span>
            <span className="italic">Lưu ý: Mọi tài khoản Connect thật (Express) đều có thể kiểm tra sâu hơn trong Stripe Console của Nền tảng.</span>
          </div>
        </div>
      )}
    </div>
  );
}
