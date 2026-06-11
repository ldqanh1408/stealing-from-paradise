import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminRefundApi, type RefundResponse } from '@shared/api/refund.api';
import { fmtVnd, fmtDate } from '@shared/utils/format';
import ApproveRefundModal from '@/components/Refunds/ApproveRefundModal';
import RejectRefundModal from '@/components/Refunds/RejectRefundModal';
import RefundDetailDrawer from '@/components/Refunds/RefundDetailDrawer';

const fmt = (n: number) => fmtVnd(n);

type RefundStatus = 'ALL' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'REJECTED';
type RefundType = 'ALL' | 'FULL' | 'PARTIAL';

const STATUS_FILTERS: { value: RefundStatus; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'SUCCESS', label: 'Đã hoàn' },
  { value: 'FAILED', label: 'Thất bại' },
  { value: 'REJECTED', label: 'Từ chối' },
];

const TYPE_FILTERS: { value: RefundType; label: string }[] = [
  { value: 'ALL', label: 'Tất cả loại' },
  { value: 'FULL', label: 'Hoàn toàn bộ' },
  { value: 'PARTIAL', label: 'Một phần' },
];

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:  { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ duyệt' },
  SUCCESS:  { bg: 'bg-green-100',  color: 'text-green-700', label: 'Đã hoàn' },
  FAILED:   { bg: 'bg-red-100',    color: 'text-red-700',   label: 'Thất bại' },
  REJECTED: { bg: 'bg-gray-100',   color: 'text-gray-600',  label: 'Từ chối' },
};

function formatDate(iso?: string) { return fmtDate(iso, true); }

// ─── Main Page ────────────────────────────────────────────────────────────────
export default function RefundsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<RefundStatus>('ALL');
  const [typeFilter, setTypeFilter] = useState<RefundType>('ALL');
  const [page, setPage] = useState(0);
  const [approveRefund, setApproveRefund] = useState<RefundResponse | null>(null);
  const [rejectRefund, setRejectRefund] = useState<RefundResponse | null>(null);
  const [detailRefund, setDetailRefund] = useState<RefundResponse | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-refunds', statusFilter, typeFilter, page],
    queryFn: () =>
      adminRefundApi.list({
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        type: typeFilter === 'ALL' ? undefined : typeFilter,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const refunds: RefundResponse[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý hoàn tiền</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && <span>{totalElements} yêu cầu</span>}
          </p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-5 flex-wrap">
        <div className="flex gap-2 flex-wrap">
          {STATUS_FILTERS.map(f => (
            <button
              key={f.value}
              onClick={() => { setStatusFilter(f.value); setPage(0); }}
              className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                statusFilter === f.value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>
        <select
          value={typeFilter}
          onChange={e => { setTypeFilter(e.target.value as RefundType); setPage(0); }}
          className="px-3 py-1.5 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          {TYPE_FILTERS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
        </select>
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>
          Đang tải...
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải danh sách hoàn tiền. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && refunds.length === 0 && (
        <div className="text-center py-20 text-gray-400">
          <span className="text-4xl block mb-3">💸</span>
          Không có yêu cầu hoàn tiền nào
        </div>
      )}

      {/* Table */}
      {!isLoading && !error && refunds.length > 0 && (
        <>
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['Mã YC', 'Đơn hàng', 'Loại', 'Số tiền', 'Người YC', 'Lý do', 'Trạng thái', 'Ngày', 'Thao tác'].map(h => (
                      <th key={h} className="px-4 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {refunds.map(r => {
                    const st = STATUS_STYLE[r.status] ?? { bg: 'bg-gray-100', color: 'text-gray-600', label: r.status };
                    return (
                      <tr key={r.refundId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-4 font-mono text-gray-900">#{r.refundId}</td>
                        <td className="px-4 py-4 font-mono text-gray-500">#{r.orderId}</td>
                        <td className="px-4 py-4">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                            r.type === 'FULL' ? 'bg-blue-50 text-blue-700' : 'bg-purple-50 text-purple-700'
                          }`}>
                            {r.type === 'FULL' ? 'Toàn bộ' : 'Một phần'}
                          </span>
                        </td>
                        <td className="px-4 py-4 font-semibold text-gray-900">{fmt(r.amount)}</td>
                        <td className="px-4 py-4 text-gray-700">
                          <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                            r.initiatedBy === 'BUYER' ? 'bg-green-50 text-green-700' : 'bg-orange-50 text-orange-700'
                          }`}>
                            {r.initiatedBy === 'BUYER' ? 'Khách' : 'Người bán'}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-gray-500 max-w-[200px] truncate" title={r.reason}>{r.reason}</td>
                        <td className="px-4 py-4">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.bg} ${st.color}`}>{st.label}</span>
                        </td>
                        <td className="px-4 py-4 text-gray-400 whitespace-nowrap text-xs">{formatDate(r.createdAt)}</td>
                        <td className="px-4 py-4">
                          <div className="flex gap-2 flex-wrap">
                            <button
                              onClick={() => setDetailRefund(r)}
                              className="text-xs text-gray-500 hover:text-gray-700 font-medium"
                            >
                              Chi tiết
                            </button>
                            {r.status === 'PENDING' && (
                              <>
                                <button
                                  onClick={() => setApproveRefund(r)}
                                  className="text-xs text-green-600 hover:text-green-700 font-medium"
                                >
                                  Duyệt
                                </button>
                                <button
                                  onClick={() => setRejectRefund(r)}
                                  className="text-xs text-red-500 hover:text-red-600 font-medium"
                                >
                                  Từ chối
                                </button>
                              </>
                            )}
                            {r.status === 'FAILED' && (
                              <button
                                onClick={() => setApproveRefund(r)}
                                className="text-xs text-green-600 hover:text-green-700 font-medium"
                              >
                                Retry
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
              >
                ← Trước
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">
                Trang {page + 1} / {totalPages}
              </span>
              <button
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
              >
                Sau →
              </button>
            </div>
          )}
        </>
      )}

      {/* Modals */}
      {approveRefund && (
        <ApproveRefundModal
          refund={approveRefund}
          onClose={() => setApproveRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {rejectRefund && (
        <RejectRefundModal
          refund={rejectRefund}
          onClose={() => setRejectRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {detailRefund && (
        <RefundDetailDrawer
          refund={detailRefund}
          onClose={() => setDetailRefund(null)}
        />
      )}
    </div>
  );
}
