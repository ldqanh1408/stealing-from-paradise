import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi, type Order, type OrderItem } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';
import { refundApi, type FullRefundCreatedResponse } from '@shared/api/refund.api';
import { type ApiResponse } from '@shared/types/api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_STYLE: Record<string, { label: string; bg: string; color: string }> = {
  PENDING:            { label: 'Chờ xác nhận',    bg: 'bg-yellow-100', color: 'text-yellow-700' },
  PAID:               { label: 'Đã thanh toán',    bg: 'bg-blue-100',   color: 'text-blue-700' },
  SHIPPING:           { label: 'Đang giao',         bg: 'bg-purple-100', color: 'text-purple-700' },
  DELIVERED:          { label: 'Đã nhận hàng',     bg: 'bg-green-100',  color: 'text-green-700' },
  CANCELLED:          { label: 'Đã huỷ',            bg: 'bg-red-100',    color: 'text-red-700' },
  RETURNED:           { label: 'Hoàn hàng',         bg: 'bg-orange-100', color: 'text-orange-700' },
  PARTIALLY_REFUNDED: { label: 'Hoàn một phần',    bg: 'bg-indigo-100', color: 'text-indigo-700' },
  REFUNDED:           { label: 'Đã hoàn tiền',     bg: 'bg-gray-100',   color: 'text-gray-600' },
};

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function canCancel(status: string) {
  return status === 'PENDING';
}

function canConfirmReceived(status: string) {
  return status === 'SHIPPING';
}

function canRequestFullRefund(order: Order) {
  return order.status === 'PAID';
}

function canRequestPartialRefund(order: Order) {
  return ['PAID', 'SHIPPING', 'DELIVERED', 'PARTIALLY_REFUNDED'].includes(order.status);
}

// ─── Cancel Modal ─────────────────────────────────────────────────────────────
function CancelModal({ order, onClose, onSuccess }: { order: Order; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => orderApi.cancelOrder(order.order_id, { reason, note }),
    onSuccess: () => {
      onSuccess();
      onClose();
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Hủy đơn thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Hủy đơn hàng</h3>
        <p className="text-sm text-gray-500 mb-4">
          Bạn đang hủy đơn <strong>{order.order_code}</strong>. Hành động này không thể hoàn tác.
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hủy *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Thay đổi ý định">Thay đổi ý định</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Giá sản phẩm rẻ hơn chỗ khác">Giá sản phẩm rẻ hơn chỗ khác</option>
            <option value="Thời gian giao hàng quá lâu">Thời gian giao hàng quá lâu</option>
            <option value="Đặt nhầm sản phẩm">Đặt nhầm sản phẩm</option>
            <option value="Khác">Khác</option>
          </select>
        </div>
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
          <textarea
            value={note}
            onChange={e => setNote(e.target.value)}
            placeholder="Bổ sung thông tin..."
            rows={3}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
          />
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang hủy...' : 'Xác nhận hủy'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Partial Refund Modal ──────────────────────────────────────────────────────
function PartialRefundModal({ order, onClose, onSuccess }: { order: Order; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [selectedItems, setSelectedItems] = useState<Map<number, { qty: number; itemReason: string }>>(new Map());
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  const mut = useMutation({
    mutationFn: () => {
      const items = Array.from(selectedItems.entries()).map(([itemId, v]) => ({
        order_item_id: itemId,
        quantity: v.qty,
        item_reason: v.itemReason,
      }));
      return refundApi.requestPartialRefund(order.order_id, { reason, items });
    },
    onSuccess: () => {
      setSuccess(true);
      setTimeout(() => { onSuccess(); onClose(); }, 1500);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Yêu cầu hoàn tiền thất bại');
    },
  });

  const toggleItem = (item: OrderItem) => {
    const next = new Map(selectedItems);
    if (next.has(item.order_item_id)) {
      next.delete(item.order_item_id);
    } else {
      next.set(item.order_item_id, {
        qty: item.quantity - item.refunded_quantity,
        itemReason: '',
      });
    }
    setSelectedItems(next);
  };

  const updateQty = (itemId: number, qty: number) => {
    const next = new Map(selectedItems);
    const existing = next.get(itemId);
    if (existing) next.set(itemId, { ...existing, qty: Math.max(1, qty) });
    setSelectedItems(next);
  };

  const refundTotal = () => {
    if (!order.items) return 0;
    return Array.from(selectedItems.entries()).reduce((sum, [itemId, v]) => {
      const item = order.items?.find(i => i.order_item_id === itemId);
      return sum + (item ? item.price_snapshot * v.qty : 0);
    }, 0);
  };

  const remaining = (item: OrderItem) => item.quantity - item.refunded_quantity;

  if (success) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Yêu cầu hoàn tiền đã gửi!</h3>
          <p className="text-sm text-gray-500">Admin sẽ xem xét và xử lý trong 1-3 ngày làm việc.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-lg w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Yêu cầu hoàn tiền một phần</h3>
        <p className="text-sm text-gray-500 mb-4">
          Hoàn tiền cho đơn <strong>{order.order_code}</strong> ({order.seller_name})
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}

        {/* Item selection */}
        <div className="space-y-3 mb-4 max-h-64 overflow-y-auto">
          {order.items?.map(item => {
            const avail = remaining(item);
            const sel = selectedItems.get(item.order_item_id);
            if (avail <= 0) return null;
            return (
              <div key={item.order_item_id} className={`border rounded-xl p-3 cursor-pointer transition-all ${sel ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-gray-300'}`}
                onClick={() => toggleItem(item)}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{item.product_name}</p>
                    <p className="text-xs text-gray-500">{item.variant_name} · Còn hoàn: {avail}/{item.quantity}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="text-sm font-bold text-gray-900">{fmt(item.price_snapshot * avail)}</p>
                    <p className="text-xs text-gray-400">{fmt(item.price_snapshot)}/sp</p>
                  </div>
                </div>
                {sel && avail > 1 && (
                  <div className="mt-2 flex items-center gap-2" onClick={e => e.stopPropagation()}>
                    <span className="text-xs text-gray-500">SL:</span>
                    <button onClick={() => updateQty(item.order_item_id, sel.qty - 1)} className="w-6 h-6 rounded border text-xs font-bold hover:bg-gray-100">−</button>
                    <span className="text-xs font-medium w-4 text-center">{sel.qty}</span>
                    <button onClick={() => updateQty(item.order_item_id, sel.qty + 1)} disabled={sel.qty >= avail} className="w-6 h-6 rounded border text-xs font-bold hover:bg-gray-100 disabled:opacity-30">+</button>
                  </div>
                )}
                {sel && (
                  <div className="mt-2" onClick={e => e.stopPropagation()}>
                    <input
                      placeholder="Lý do cho sản phẩm này..."
                      value={sel.itemReason}
                      onChange={e => {
                        const next = new Map(selectedItems);
                        next.set(item.order_item_id, { ...next.get(item.order_item_id)!, itemReason: e.target.value });
                        setSelectedItems(next);
                      }}
                      className="w-full px-2 py-1 border rounded text-xs focus:outline-none focus:ring-1 focus:ring-blue-500"
                    />
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Reason */}
        <div className="mb-4">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hoàn tiền *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Sản phẩm lỗi">Sản phẩm lỗi</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Giao thiếu sản phẩm">Giao thiếu sản phẩm</option>
            <option value="Sản phẩm hư hỏng">Sản phẩm hư hỏng trong vận chuyển</option>
            <option value="Đặt nhầm sản phẩm">Đặt nhầm sản phẩm</option>
            <option value="Khác">Khác</option>
          </select>
        </div>

        {/* Summary */}
        {selectedItems.size > 0 && (
          <div className="bg-gray-50 rounded-xl p-3 mb-4">
            <div className="flex justify-between text-sm">
              <span className="text-gray-600">Tổng hoàn:</span>
              <span className="font-bold text-red-600">{fmt(refundTotal())}</span>
            </div>
          </div>
        )}

        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={selectedItems.size === 0 || !reason || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Full Refund Modal ─────────────────────────────────────────────────────────
function FullRefundModal({ parentOrderId, onClose, onSuccess }: { parentOrderId: number; onClose: () => void; onSuccess: () => void }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [result, setResult] = useState<any>(null);

  const mut = useMutation({
    mutationFn: () =>
      refundApi.requestFullRefund(parentOrderId, { reason }) as Promise<{ data: ApiResponse<FullRefundCreatedResponse> }>,
    onSuccess: (res) => {
      setResult(res.data.data);
      setSuccess(true);
      setTimeout(() => { onSuccess(); onClose(); }, 3000);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Yêu cầu hoàn tiền thất bại');
    },
  });

  if (success && result) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">Yêu cầu hoàn tiền đã gửi!</h3>
          <p className="text-sm text-gray-500 mb-2">
            Tổng cộng: <strong className="text-red-600">{fmt(result.total_amount)}</strong>
          </p>
          <p className="text-xs text-gray-400">
            {result.refunds.length} yêu cầu hoàn tiền cho {result.refunds.length} người bán
          </p>
          <p className="text-xs text-gray-400 mt-1">Ước tính hoàn: {result.estimated_days} ngày</p>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">Yêu cầu hoàn tiền toàn bộ</h3>
        <p className="text-sm text-gray-500 mb-4">
          Bạn sẽ nhận lại toàn bộ số tiền đã thanh toán cho tất cả đơn hàng trong đơn cha này.
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do hoàn tiền *</label>
          <select
            value={reason}
            onChange={e => setReason(e.target.value)}
            className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">Chọn lý do</option>
            <option value="Thay đổi ý định">Thay đổi ý định</option>
            <option value="Đơn hàng không được xử lý kịp thời">Đơn hàng không được xử lý kịp thời</option>
            <option value="Sản phẩm không đúng mô tả">Sản phẩm không đúng mô tả</option>
            <option value="Khác">Khác</option>
          </select>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Đóng</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!reason || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Confirm Received Modal ───────────────────────────────────────────────────
function ConfirmReceivedModal({ order, onClose, onSuccess }: { order: Order; onClose: () => void; onSuccess: () => void }) {
  const mut = useMutation({
    mutationFn: () => orderApi.confirmReceived(order.order_id),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: () => {},
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
        <div className="text-5xl mb-4">📦</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">Xác nhận đã nhận hàng?</h3>
        <p className="text-sm text-gray-500 mb-6">
          Xác nhận bạn đã nhận được đơn hàng <strong>{order.order_code}</strong> từ {order.seller_name}.
          <br />Bạn sẽ nhận được điểm thưởng từ đơn hàng này.
        </p>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Chưa</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-medium hover:bg-green-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Đã nhận hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Order Detail Page ────────────────────────────────────────────────────────
export default function OrderDetailPage() {
  const { parentOrderId } = useParams<{ parentOrderId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const id = Number(parentOrderId);

  const { data: orderData, isLoading, error } = useQuery({
    queryKey: ['parent-order', id],
    queryFn: () => orderApi.getParentOrder(id).then(r => r.data.data),
    enabled: !isNaN(id),
  });

  const { data: paymentData } = useQuery({
    queryKey: ['payment', id],
    queryFn: () => paymentApi.getPayment(id).then(r => r.data.data),
    enabled: !isNaN(id),
  });

  const [showCancel, setShowCancel] = useState<Order | null>(null);
  const [showPartialRefund, setShowPartialRefund] = useState<Order | null>(null);
  const [showFullRefund, setShowFullRefund] = useState(false);
  const [showConfirm, setShowConfirm] = useState<Order | null>(null);

  if (isNaN(id)) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500">ID đơn hàng không hợp lệ.</p>
        <Link to="/orders" className="text-blue-600 hover:underline mt-2 inline-block">← Quay lại</Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center text-gray-400">
        <div className="text-4xl mb-3">⏳</div>
        Đang tải chi tiết đơn hàng...
      </div>
    );
  }

  if (error || !orderData) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy đơn hàng.</p>
        <Link to="/orders" className="text-blue-600 hover:underline">← Quay lại danh sách</Link>
      </div>
    );
  }

  const parent = orderData;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <button onClick={() => navigate('/orders')} className="text-gray-400 hover:text-gray-600">
          ←
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500">{parent.order_code}</p>
        </div>
      </div>

      {/* Payment info */}
      {paymentData && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-3 flex items-center gap-2">
            💳 Thông tin thanh toán
          </h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500 text-xs">Tổng tiền</p>
              <p className="font-bold text-gray-900">{fmt(paymentData.amount)}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Phương thức</p>
              <p className="font-medium text-gray-700">{paymentData.method}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Trạng thái</p>
              <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                paymentData.status === 'SUCCESS' ? 'bg-green-100 text-green-700' :
                paymentData.status === 'PENDING' ? 'bg-yellow-100 text-yellow-700' :
                'bg-red-100 text-red-700'
              }`}>
                {paymentData.status === 'SUCCESS' ? 'Thành công' :
                 paymentData.status === 'PENDING' ? 'Đang chờ' : 'Thất bại'}
              </span>
            </div>
            {paymentData.paid_at && (
              <div>
                <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                <p className="font-medium text-gray-700">{formatDate(paymentData.paid_at)}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Shipping address */}
      {parent.shipping_address && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-2">📍 Địa chỉ giao hàng</h2>
          <p className="text-sm text-gray-700">{parent.shipping_address.full_address}</p>
        </div>
      )}

      {/* Sub-orders */}
      <div className="space-y-4 mb-6">
        {parent.orders.map(subOrder => {
          const st = STATUS_STYLE[subOrder.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700', label: subOrder.status };
          return (
            <div key={subOrder.order_id} className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
              {/* Sub-order header */}
              <div className="px-5 py-4 border-b border-gray-50 flex items-start justify-between gap-3 flex-wrap">
                <div>
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-bold text-gray-900">{subOrder.order_code}</span>
                    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${st.bg} ${st.color}`}>{st.label}</span>
                  </div>
                  <p className="text-sm text-gray-500 mt-0.5">{subOrder.seller_name}</p>
                  {subOrder.tracking_number && (
                    <p className="text-xs text-gray-400 mt-0.5">
                      Mã vận đơn: {subOrder.tracking_number}
                      {subOrder.carrier ? ` (${subOrder.carrier})` : ''}
                    </p>
                  )}
                  {subOrder.cancelled_by && (
                    <p className="text-xs text-red-500 mt-0.5">
                      Đã hủy bởi {subOrder.cancelled_by}: {subOrder.cancel_reason}
                    </p>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <p className="font-bold text-gray-900">{fmt(subOrder.final_amt)}</p>
                  <p className="text-xs text-gray-400">{formatDate(subOrder.created_at)}</p>
                </div>
              </div>

              {/* Order items */}
              <div className="px-5 py-4">
                {subOrder.items?.map(item => (
                  <div key={item.order_item_id} className="flex items-center gap-3 mb-3 last:mb-0">
                    <div className="w-14 h-14 rounded-lg bg-gray-100 flex items-center justify-center text-2xl shrink-0">
                      {item.image_snapshot ? (
                        <img src={item.image_snapshot} alt="" className="w-full h-full object-cover rounded-lg" />
                      ) : '📦'}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">{item.product_name}</p>
                      <p className="text-xs text-gray-500">{item.variant_name}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-gray-400">x{item.quantity}</span>
                        {item.refunded_quantity > 0 && (
                          <span className="text-xs text-blue-600">Đã hoàn: {item.refunded_quantity}</span>
                        )}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="text-sm font-semibold text-gray-900">{fmt(item.price_snapshot * item.quantity)}</p>
                      <p className="text-xs text-gray-400">{fmt(item.price_snapshot)}/sp</p>
                    </div>
                  </div>
                ))}
              </div>

              {/* Action buttons */}
              <div className="px-5 py-4 border-t border-gray-50 flex flex-wrap gap-2">
                {canCancel(subOrder.status) && (
                  <button
                    onClick={() => setShowCancel(subOrder)}
                    className="px-4 py-2 text-sm font-medium border border-red-200 text-red-600 rounded-xl hover:bg-red-50 transition-colors"
                  >
                    Hủy đơn
                  </button>
                )}
                {canConfirmReceived(subOrder.status) && (
                  <button
                    onClick={() => setShowConfirm(subOrder)}
                    className="px-4 py-2 text-sm font-medium bg-green-600 text-white rounded-xl hover:bg-green-700 transition-colors"
                  >
                    Xác nhận đã nhận
                  </button>
                )}
                {canRequestPartialRefund(subOrder) && (
                  <button
                    onClick={() => setShowPartialRefund(subOrder)}
                    className="px-4 py-2 text-sm font-medium border border-blue-200 text-blue-600 rounded-xl hover:bg-blue-50 transition-colors"
                  >
                    Hoàn tiền một phần
                  </button>
                )}
                {canRequestFullRefund(subOrder) && (
                  <button
                    onClick={() => setShowFullRefund(true)}
                    className="px-4 py-2 text-sm font-medium border border-blue-200 text-blue-600 rounded-xl hover:bg-blue-50 transition-colors"
                  >
                    Hoàn tiền toàn bộ
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Price summary */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền hàng</span>
            <span>{fmt(parent.total_amt)}</span>
          </div>
          {orderData.orders[0]?.items && (
            <div className="flex justify-between text-gray-600">
              <span>Phí vận chuyển</span>
              <span className="text-green-600">Miễn phí</span>
            </div>
          )}
          <div className="h-px bg-gray-100" />
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmt(parent.final_amt)}</span>
          </div>
        </div>
      </div>

      {/* Back */}
      <div className="text-center">
        <Link to="/orders" className="text-blue-600 hover:underline text-sm">
          ← Quay lại danh sách đơn hàng
        </Link>
      </div>

      {/* Modals */}
      {showCancel && (
        <CancelModal
          order={showCancel}
          onClose={() => setShowCancel(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showPartialRefund && (
        <PartialRefundModal
          order={showPartialRefund}
          onClose={() => setShowPartialRefund(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showFullRefund && (
        <FullRefundModal
          parentOrderId={id}
          onClose={() => setShowFullRefund(false)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
      {showConfirm && (
        <ConfirmReceivedModal
          order={showConfirm}
          onClose={() => setShowConfirm(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['parent-order', id] })}
        />
      )}
    </div>
  );
}
