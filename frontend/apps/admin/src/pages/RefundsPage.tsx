import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminRefundApi, type RefundResponse } from '@shared/api/refund.api';
import ApproveRefundModal from '@/components/Refunds/ApproveRefundModal';
import RejectRefundModal from '@/components/Refunds/RejectRefundModal';
import RefundDetailDrawer from '@/components/Refunds/RefundDetailDrawer';
import RefundFilters, { type RefundStatus, type RefundType } from '@/components/Refunds/RefundFilters';
import RefundsTable from '@/components/Refunds/RefundsTable';
import Pagination from '@shared/components/Pagination';

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
      <RefundFilters
        statusFilter={statusFilter}
        onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
        typeFilter={typeFilter}
        onTypeFilterChange={(t) => { setTypeFilter(t); setPage(0); }}
      />

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
          <RefundsTable
            refunds={refunds}
            onDetail={setDetailRefund}
            onApprove={setApproveRefund}
            onReject={setRejectRefund}
          />

          {/* Pagination */}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
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
