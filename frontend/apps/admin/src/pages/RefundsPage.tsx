import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminRefundApi, type RefundResponse } from '@shared/api/refund.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

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

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// ─── Approve Modal ─────────────────────────────────────────────────────────────
function ApproveModal({ refund, onClose, onSuccess }: { refund: RefundResponse; onClose: () => void; onSuccess: () => void }) {
  const [adminNote, setAdminNote] = useState('');
  const [adjustAmount, setAdjustAmount] = useState('');
  const [causedBy, setCausedBy] = useState<'SELLER' | 'BUYER'>('BUYER');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const mut = useMutation({
    mutationFn: () => adminRefundApi.approve(refund.refund_id, {
      admin_note: adminNote,
      adjust_amount: adjustAmount ? parseFloat(adjustAmount) : undefined,
      caused_by: causedBy,
      tracking_number: trackingNumber || undefined,
    }),
    onSuccess: () => { setDone(true); setTimeout(() => { onSuccess(); onClose(); }, 1500); },
    onError: (err: any) => { setError(err?.response?.data?.message || 'Duyệt hoàn tiền thất bại'); },
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Duyệt hoàn tiền thành công!</h3>
          <p className="text-sm text-gray-500">Stripe refund đã được tạo. Khách hàng sẽ nhận được tiền trong 3-5 ngày.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Duyệt hoàn tiền</h3>
        <div className="bg-gray-50 rounded-xl p-3 mb-4 space-y-1 text-sm">
          <p><span className="text-gray-500">Mã hoàn:</span> <span className="font-mono font-medium">#{refund.refund_id}</span></p>
          <p><span className="text-gray-500">Loại:</span> <span className="font-medium">{refund.type === 'FULL' ? 'Hoàn toàn bộ' : 'Hoàn một phần'}</span></p>
          <p><span className="text-gray-500">Số tiền:</span> <span className="font-bold text-red-600">{fmt(refund.amount)}</span></p>
          <p><span className="text-gray-500">Lý do:</span> <span className="font-medium">{refund.reason}</span></p>
          <p><span className="text-gray-500">Người yêu cầu:</span> <span className="font-medium">{refund.initiated_by === 'BUYER' ? 'Khách hàng' : 'Người bán'}</span></p>
        </div>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú Admin *</label>
            <textarea
              value={adminNote}
              onChange={e => setAdminNote(e.target.value)}
              placeholder="Lý do duyệt hoàn tiền..."
              rows={2}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Điều chỉnh số tiền (VND)</label>
              <input
                type="number"
                value={adjustAmount}
                onChange={e => setAdjustAmount(e.target.value)}
                placeholder={`Mặc định: ${refund.amount}`}
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Nguyên nhân</label>
              <select
                value={causedBy}
                onChange={e => setCausedBy(e.target.value as 'SELLER' | 'BUYER')}
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="BUYER">Lỗi khách hàng</option>
                <option value="SELLER">Lỗi người bán</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn hoàn (v5.3)</label>
            <input
              type="text"
              value={trackingNumber}
              onChange={e => setTrackingNumber(e.target.value)}
              placeholder="Mã vận đơn hàng hoàn về (nếu có)"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <p className="text-xs text-gray-400 mt-1">Ghi nhận mã vận đơn để truy vết hàng hoàn về.</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!adminNote.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Duyệt hoàn tiền'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Reject Modal ──────────────────────────────────────────────────────────────
function RejectModal({ refund, onClose, onSuccess }: { refund: RefundResponse; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [fraudEvidence, setFraudEvidence] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const mut = useMutation({
    mutationFn: () => adminRefundApi.reject(refund.refund_id, {
      reject_reason: reason,
      fraud_evidence: fraudEvidence,
    }),
    onSuccess: () => { setDone(true); setTimeout(() => { onSuccess(); onClose(); }, 1500); },
    onError: (err: any) => { setError(err?.response?.data?.message || 'Từ chối thất bại'); },
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Đã từ chối yêu cầu hoàn tiền</h3>
          <p className="text-sm text-gray-500">Khách hàng sẽ nhận được thông báo từ chối.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Từ chối hoàn tiền</h3>
        <div className="bg-gray-50 rounded-xl p-3 mb-4 space-y-1 text-sm">
          <p><span className="text-gray-500">Mã hoàn:</span> <span className="font-mono font-medium">#{refund.refund_id}</span></p>
          <p><span className="text-gray-500">Số tiền:</span> <span className="font-bold text-red-600">{fmt(refund.amount)}</span></p>
          <p><span className="text-gray-500">Lý do khách:</span> <span className="font-medium">{refund.reason}</span></p>
        </div>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do từ chối *</label>
            <select
              value={reason}
              onChange={e => setReason(e.target.value)}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Chọn lý do</option>
              <option value="Yêu cầu không hợp lệ">Yêu cầu không hợp lệ</option>
              <option value="Không có bằng chứng đầy đủ">Không có bằng chứng đầy đủ</option>
              <option value="Đã quá thời hạn hoàn tiền">Đã quá thời hạn hoàn tiền</option>
              <option value="Hàng đã sử dụng / hư hỏng">Hàng đã sử dụng / hư hỏng</option>
              <option value="Trùng lặp yêu cầu hoàn">Trùng lặp yêu cầu hoàn</option>
              <option value="Khác">Khác</option>
            </select>
          </div>
          <label className="flex items-center gap-3 p-3 border rounded-xl cursor-pointer hover:bg-gray-50">
            <input
              type="checkbox"
              checked={fraudEvidence}
              onChange={e => setFraudEvidence(e.target.checked)}
              className="w-4 h-4 accent-red-600"
            />
            <div>
              <p className="text-sm font-medium text-gray-900">Cảnh báo gian lận</p>
              <p className="text-xs text-gray-500">Đánh dấu nếu phát hiện hành vi lạm dụng hoàn tiền. Điểm tin cậy của khách sẽ bị trừ.</p>
            </div>
          </label>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Từ chối'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Detail Drawer ─────────────────────────────────────────────────────────────
function DetailDrawer({ refund, onClose }: { refund: RefundResponse; onClose: () => void }) {
  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex justify-end" onClick={onClose}>
      <div className="bg-white w-full max-w-md h-full overflow-y-auto" onClick={e => e.stopPropagation()}>
        <div className="sticky top-0 bg-white border-b p-5 flex items-center justify-between">
          <h3 className="font-bold text-gray-900">Chi tiết hoàn tiền</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl">×</button>
        </div>
        <div className="p-5 space-y-5">
          <div className="bg-gray-50 rounded-xl p-4 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-500">Mã hoàn</span>
              <span className="font-mono font-medium">#{refund.refund_id}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Mã đơn</span>
              <span className="font-mono font-medium">#{refund.order_id}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Loại</span>
              <span className="font-medium">{refund.type === 'FULL' ? 'Hoàn toàn bộ' : 'Hoàn một phần'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Trạng thái</span>
              <span className={`font-medium ${STATUS_STYLE[refund.status]?.color}`}>
                {STATUS_STYLE[refund.status]?.label ?? refund.status}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Số tiền</span>
              <span className="font-bold text-red-600">{fmt(refund.amount)}</span>
            </div>
            {refund.adjust_amount && (
              <div className="flex justify-between">
                <span className="text-gray-500">Đã điều chỉnh</span>
                <span className="font-medium text-blue-600">{fmt(refund.adjust_amount)}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-gray-500">Người yêu cầu</span>
              <span className="font-medium">{refund.initiated_by === 'BUYER' ? 'Khách hàng' : 'Người bán'}</span>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do</h4>
            <p className="text-sm text-gray-700 bg-gray-50 rounded-xl p-3">{refund.reason}</p>
          </div>

          {refund.admin_note && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Ghi chú Admin</h4>
              <p className="text-sm text-gray-700 bg-blue-50 rounded-xl p-3">{refund.admin_note}</p>
            </div>
          )}

          {refund.reject_reason && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do từ chối</h4>
              <p className="text-sm text-red-700 bg-red-50 rounded-xl p-3">{refund.reject_reason}</p>
            </div>
          )}

          {refund.stripe_refund_id && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Stripe Refund ID</h4>
              <p className="text-xs font-mono text-gray-600 bg-gray-100 rounded-xl p-3 break-all">{refund.stripe_refund_id}</p>
            </div>
          )}

          <div className="space-y-2 text-xs text-gray-400">
            <p>Tạo: {formatDate(refund.created_at)}</p>
            {refund.reviewed_at && <p>Duyệt lúc: {formatDate(refund.reviewed_at)}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}

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
                      <tr key={r.refund_id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                        <td className="px-4 py-4 font-mono text-gray-900">#{r.refund_id}</td>
                        <td className="px-4 py-4 font-mono text-gray-500">#{r.order_id}</td>
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
                            r.initiated_by === 'BUYER' ? 'bg-green-50 text-green-700' : 'bg-orange-50 text-orange-700'
                          }`}>
                            {r.initiated_by === 'BUYER' ? 'Khách' : 'Người bán'}
                          </span>
                        </td>
                        <td className="px-4 py-4 text-gray-500 max-w-[200px] truncate" title={r.reason}>{r.reason}</td>
                        <td className="px-4 py-4">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.bg} ${st.color}`}>{st.label}</span>
                        </td>
                        <td className="px-4 py-4 text-gray-400 whitespace-nowrap text-xs">{formatDate(r.created_at)}</td>
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
        <ApproveModal
          refund={approveRefund}
          onClose={() => setApproveRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {rejectRefund && (
        <RejectModal
          refund={rejectRefund}
          onClose={() => setRejectRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-refunds'] })}
        />
      )}
      {detailRefund && (
        <DetailDrawer
          refund={detailRefund}
          onClose={() => setDetailRefund(null)}
        />
      )}
    </div>
  );
}
