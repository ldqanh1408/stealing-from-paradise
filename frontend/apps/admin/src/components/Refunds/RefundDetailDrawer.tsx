import type { RefundResponse } from '@shared/api/refund.api';
import { fmtVnd, fmtDate } from '@shared/utils/format';

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:  { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ duyệt' },
  SUCCESS:  { bg: 'bg-green-100',  color: 'text-green-700', label: 'Đã hoàn' },
  FAILED:   { bg: 'bg-red-100',    color: 'text-red-700',   label: 'Thất bại' },
  REJECTED: { bg: 'bg-gray-100',   color: 'text-gray-600',  label: 'Từ chối' },
};

export default function RefundDetailDrawer({ refund, onClose }: { refund: RefundResponse; onClose: () => void }) {
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
              <span className="font-mono font-medium">#{refund.refundId}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-500">Mã đơn</span>
              <span className="font-mono font-medium">#{refund.orderId}</span>
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
              <span className="font-bold text-red-600">{fmtVnd(refund.amount)}</span>
            </div>
            {refund.adjustAmount && (
              <div className="flex justify-between">
                <span className="text-gray-500">Đã điều chỉnh</span>
                <span className="font-medium text-blue-600">{fmtVnd(refund.adjustAmount)}</span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="text-gray-500">Người yêu cầu</span>
              <span className="font-medium">{refund.initiatedBy === 'BUYER' ? 'Khách hàng' : 'Người bán'}</span>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do</h4>
            <p className="text-sm text-gray-700 bg-gray-50 rounded-xl p-3">{refund.reason}</p>
          </div>

          {refund.adminNote && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Ghi chú Admin</h4>
              <p className="text-sm text-gray-700 bg-blue-50 rounded-xl p-3">{refund.adminNote}</p>
            </div>
          )}

          {refund.rejectReason && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Lý do từ chối</h4>
              <p className="text-sm text-red-700 bg-red-50 rounded-xl p-3">{refund.rejectReason}</p>
            </div>
          )}

          {refund.stripeRefundId && (
            <div>
              <h4 className="text-xs font-semibold text-gray-500 uppercase mb-2">Stripe Refund ID</h4>
              <p className="text-xs font-mono text-gray-600 bg-gray-100 rounded-xl p-3 break-all">{refund.stripeRefundId}</p>
            </div>
          )}

          <div className="space-y-2 text-xs text-gray-400">
            <p>Tạo: {fmtDate(refund.createdAt, true)}</p>
            {refund.reviewedAt && <p>Duyệt lúc: {fmtDate(refund.reviewedAt, true)}</p>}
          </div>
        </div>
      </div>
    </div>
  );
}
