import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { refundApi, type RefundResponse } from '@shared/api/refund.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_STYLE: Record<string, { label: string; bg: string; color: string }> = {
  PENDING:   { label: 'Chờ duyệt',    bg: 'bg-yellow-100', color: 'text-yellow-700' },
  APPROVED:  { label: 'Đã duyệt',     bg: 'bg-blue-100',  color: 'text-blue-700' },
  REJECTED:  { label: 'Từ chối',       bg: 'bg-red-100',   color: 'text-red-700' },
  PROCESSING:{ label: 'Đang xử lý',  bg: 'bg-purple-100',color: 'text-purple-700' },
  COMPLETED: { label: 'Hoàn thành',   bg: 'bg-green-100',  color: 'text-green-700' },
};

const TYPE_LABEL: Record<string, string> = {
  FULL: 'Hoàn toàn bộ',
  PARTIAL: 'Hoàn một phần',
};

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export default function RefundHistoryPage() {
  const [filter, setFilter] = useState<string>('ALL');
  const [page, setPage] = useState(0);

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['my-refunds', filter, page],
    queryFn: () =>
      refundApi.getMyRefunds({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: 10,
      }).then(r => r.data.data),
    retry: 1,
  });

  const refunds: RefundResponse[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const STATUS_FILTERS = [
    { value: 'ALL', label: 'Tất cả' },
    { value: 'PENDING', label: 'Chờ duyệt' },
    { value: 'APPROVED', label: 'Đã duyệt' },
    { value: 'REJECTED', label: 'Từ chối' },
    { value: 'PROCESSING', label: 'Đang xử lý' },
    { value: 'COMPLETED', label: 'Hoàn thành' },
  ];

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Lịch sử hoàn tiền</h1>
          <p className="text-sm text-gray-500 mt-1">Theo dõi các yêu cầu hoàn tiền của bạn</p>
        </div>
        <button
          onClick={() => refetch()}
          className="text-sm text-gray-500 hover:text-blue-600 flex items-center gap-1 transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Làm mới
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {STATUS_FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => { setFilter(f.value); setPage(0); }}
            className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
              filter === f.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300 hover:bg-blue-50'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>
          Đang tải lịch sử hoàn tiền...
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải lịch sử hoàn tiền. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && refunds.length === 0 && (
        <div className="text-center py-20">
          <div className="w-24 h-24 rounded-full bg-gray-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            💰
          </div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Chưa có yêu cầu hoàn tiền nào</h2>
          <p className="text-gray-500 mb-8">Các yêu cầu hoàn tiền của bạn sẽ xuất hiện ở đây</p>
          <Link
            to="/orders"
            className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
          >
            Xem đơn hàng của tôi
          </Link>
        </div>
      )}

      {/* Refund list */}
      {!isLoading && !error && refunds.length > 0 && (
        <>
          <p className="text-sm text-gray-500 mb-4">{totalElements} yêu cầu hoàn tiền</p>
          <div className="space-y-4">
            {refunds.map(refund => {
              const st = STATUS_STYLE[refund.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700', label: refund.status };
              return (
                <div key={refund.refundId} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
                  {/* Header */}
                  <div className="px-5 py-4 border-b border-gray-50 flex items-start justify-between gap-4 flex-wrap">
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-gray-900 font-mono">#{refund.refundId}</span>
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${st.bg} ${st.color}`}>
                          {st.label}
                        </span>
                        <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">
                          {TYPE_LABEL[refund.type] ?? refund.type}
                        </span>
                      </div>
                      <p className="text-sm text-gray-500 mt-0.5">
                        {refund.groupRef && <span className="font-mono text-xs">Nhóm: {refund.groupRef}</span>}
                        {refund.items && refund.items.length > 0 && (
                          <span> · {refund.items.length} sản phẩm</span>
                        )}
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">{formatDate(refund.createdAt)}</p>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="font-bold text-red-600 text-lg">{fmt(refund.amount)}</p>
                      <p className="text-xs text-gray-400 mt-0.5">{refund.reason}</p>
                    </div>
                  </div>

                  {/* Items */}
                  {refund.items && refund.items.length > 0 && (
                    <div className="px-5 py-3 border-b border-gray-50">
                      <p className="text-xs font-semibold text-gray-500 mb-2 uppercase">Sản phẩm hoàn</p>
                      <div className="space-y-2">
                        {refund.items.map((item, i) => (
                          <div key={i} className="flex items-center justify-between text-sm">
                            <div>
                              <p className="text-gray-700">x{item.quantity} sản phẩm</p>
                              {item.itemReason && (
                                <p className="text-xs text-gray-400">{item.itemReason}</p>
                              )}
                            </div>
                            <div className="text-right">
                              <p className="font-medium text-gray-900">{fmt(item.refundAmount)}</p>
                              <p className="text-xs text-gray-400">{item.status}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Footer info */}
                  <div className="px-5 py-3 bg-gray-50/50 flex items-center justify-between gap-4 flex-wrap text-xs text-gray-500">
                    {refund.adminNote && (
                      <p className="italic">Ghi chú: {refund.adminNote}</p>
                    )}
                    {refund.rejectReason && (
                      <p className="text-red-500">Lý do từ chối: {refund.rejectReason}</p>
                    )}
                    {refund.stripeRefundId && (
                      <p className="font-mono">Stripe: {refund.stripeRefundId}</p>
                    )}
                    {refund.reviewedAt && (
                      <p>Xử lý lúc: {formatDate(refund.reviewedAt)}</p>
                    )}
                  </div>

                  {/* Order link */}
                  {refund.orderId && (
                    <div className="px-5 py-3 border-t border-gray-50">
                      <Link
                        to={`/orders?highlight=${refund.orderId}`}
                        className="text-sm text-blue-600 hover:underline flex items-center gap-1"
                      >
                        <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                        </svg>
                        Xem đơn hàng liên quan
                      </Link>
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50 flex items-center gap-1"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
                Trước
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">
                Trang {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50 flex items-center gap-1"
              >
                Sau
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
