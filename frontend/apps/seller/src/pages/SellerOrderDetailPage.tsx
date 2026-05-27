import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_STYLE: Record<string, { bg: string; color: string; label: string }> = {
  PENDING:            { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ xác nhận' },
  PAID:               { bg: 'bg-blue-100',   color: 'text-blue-700', label: 'Đã thanh toán' },
  SHIPPING:           { bg: 'bg-purple-100', color: 'text-purple-700', label: 'Đang giao' },
  DELIVERED:          { bg: 'bg-green-100',  color: 'text-green-700', label: 'Đã giao' },
  CANCELLED:          { bg: 'bg-red-100',    color: 'text-red-700', label: 'Đã hủy' },
  RETURNED:           { bg: 'bg-orange-100', color: 'text-orange-700', label: 'Hoàn hàng' },
  PARTIALLY_REFUNDED: { bg: 'bg-indigo-100', color: 'text-indigo-700', label: 'Hoàn một phần' },
  REFUNDED:           { bg: 'bg-gray-100',  color: 'text-gray-600', label: 'Đã hoàn' },
};

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function TrackingModal({ orderId, orderCode, onClose, onSuccess }: { orderId: number; orderCode: string; onClose: () => void; onSuccess: () => void }) {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [carrier, setCarrier] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => orderApi.updateTracking(orderId, {
      trackingNumber: trackingNumber.trim(),
      carrier: carrier.trim() || undefined,
    }),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Cập nhật thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Cập nhật vận đơn</h3>
        <p className="text-sm text-gray-500 mb-5">Đơn hàng <strong>{orderCode}</strong></p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn *</label>
            <input
              type="text"
              value={trackingNumber}
              onChange={e => setTrackingNumber(e.target.value)}
              placeholder="vd: VN123456789"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Đơn vị vận chuyển</label>
            <select
              value={carrier}
              onChange={e => setCarrier(e.target.value)}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">Chọn đơn vị</option>
              <option value="GHN">Giao Hàng Nhanh (GHN)</option>
              <option value="GHTK">Giao Hàng Tiết Kiệm (GHTK)</option>
              <option value="VNPOST">Vietnam Post (VNPOST)</option>
              <option value="J&T">J&T Express</option>
              <option value="NINJAVAN">Ninja Van</option>
              <option value="BEST">Best Express</option>
              <option value="OTHER">Khác</option>
            </select>
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!trackingNumber.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang lưu...' : 'Xác nhận giao hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

function ReturnToSenderModal({ orderId, orderCode, onClose, onSuccess }: { orderId: number; orderCode: string; onClose: () => void; onSuccess: () => void }) {
  const [returnTracking, setReturnTracking] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      fd.append('return_tracking_number', returnTracking.trim());
      if (note.trim()) fd.append('note', note.trim());
      return orderApi.returnToSender(orderId, fd);
    },
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Thao tác thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Hoàn hàng về người gửi</h3>
        <p className="text-sm text-gray-500 mb-5">Đơn hàng <strong>{orderCode}</strong></p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn hoàn *</label>
            <input
              type="text"
              value={returnTracking}
              onChange={e => setReturnTracking(e.target.value)}
              placeholder="Mã vận đơn hàng hoàn về"
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú</label>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              placeholder="Lý do hoàn hàng..."
              rows={2}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!returnTracking.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-orange-600 text-white rounded-xl text-sm font-medium hover:bg-orange-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Xác nhận hoàn hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function SellerOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const id = Number(orderId);
  const queryClient = useQueryClient();
  const [showTracking, setShowTracking] = useState(false);
  const [showReturn, setShowReturn] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['seller-order', id],
    queryFn: () => orderApi.getOrderById(id).then(r => r.data.data),
    enabled: id > 0,
    retry: 1,
  });

  const { data: paymentData } = useQuery({
    queryKey: ['payment-for-seller', order?.parentOrderId],
    queryFn: () => paymentApi.getPayment(order!.parentOrderId!).then(r => r.data.data),
    enabled: !!order?.parentOrderId,
    retry: 1,
  });

  const cancelMut = useMutation({
    mutationFn: (reason: string) => orderApi.cancelOrder(id, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-order', id] });
      queryClient.invalidateQueries({ queryKey: ['seller-orders'] });
    },
  });

  const onMutationSuccess = () => {
    queryClient.invalidateQueries({ queryKey: ['seller-order', id] });
    queryClient.invalidateQueries({ queryKey: ['seller-orders'] });
  };

  if (id <= 0) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-gray-500">Vui lòng truy cập từ danh sách đơn hàng.</p>
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

  if (error || !order) {
    return (
      <div className="max-w-4xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy đơn hàng.</p>
        <a href="/orders" className="text-blue-600 hover:underline">← Quay lại</a>
      </div>
    );
  }

  const st = STATUS_STYLE[order.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700', label: order.status };

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <a href="/orders" className="text-gray-400 hover:text-gray-600 text-2xl">←</a>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500 font-mono">{order.orderCode}</p>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6 flex items-center gap-4 flex-wrap">
        <span className={`px-3 py-1.5 rounded-full text-sm font-semibold ${st.bg} ${st.color}`}>
          {st.label}
        </span>
        <div className="flex-1 text-sm text-gray-500">
          Đặt lúc {formatDate(order.createdAt)}
        </div>
        {order.trackingNumber && (
          <div className="text-sm">
            <span className="text-gray-500">Mã vận đơn: </span>
            <span className="font-mono font-medium text-gray-900">{order.trackingNumber}</span>
            {order.carrier && <span className="text-gray-400"> ({order.carrier})</span>}
          </div>
        )}

        <div className="flex gap-2">
          {order.status === 'PAID' && (
            <button
              onClick={() => setShowTracking(true)}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium transition-colors"
            >
              📦 Cập nhật vận đơn
            </button>
          )}
          {order.status === 'SHIPPING' && (
            <button
              onClick={() => setShowReturn(true)}
              className="px-4 py-2 bg-orange-50 hover:bg-orange-100 text-orange-600 border border-orange-200 rounded-xl text-sm font-medium transition-colors"
            >
              ↩ Hoàn hàng
            </button>
          )}
          {order.status === 'PENDING' && (
            <button
              onClick={() => {
                if (confirm(`Hủy đơn ${order.orderCode}? Hành động này không thể hoàn tác.`)) {
                  cancelMut.mutate('Người bán hủy đơn');
                }
              }}
              disabled={cancelMut.isPending}
              className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-xl text-sm font-medium disabled:opacity-50"
            >
              {cancelMut.isPending ? 'Đang huỷ...' : 'Huỷ đơn'}
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">👤 Thông tin khách hàng</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500 text-xs">Tên</p>
            <p className="font-medium text-gray-900">{order.buyerName || `User #${order.buyerId}`}</p>
          </div>
          <div>
            <p className="text-gray-500 text-xs">Username</p>
            <p className="font-medium text-gray-900">{order.buyerName ? `@${order.buyerId}` : '—'}</p>
          </div>
          {order.shippingAddress && (
            <div className="col-span-2">
              <p className="text-gray-500 text-xs">Địa chỉ giao hàng</p>
              <p className="font-medium text-gray-900">{order.shippingAddress.fullAddress}</p>
            </div>
          )}
        </div>
      </div>

      {paymentData && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-3">💳 Thông tin thanh toán</h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500 text-xs">Số tiền</p>
              <p className="font-bold text-gray-900">{fmt(paymentData.amount)}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Phương thức</p>
              <p className="font-medium text-gray-700">{paymentData.method}</p>
            </div>
            <div>
              <p className="text-gray-500 text-xs">Mã giao dịch</p>
              <p className="font-mono text-xs text-gray-600">{paymentData.transRef}</p>
            </div>
            {paymentData.paidAt && (
              <div>
                <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                <p className="font-medium text-gray-700 text-xs">{formatDate(paymentData.paidAt)}</p>
              </div>
            )}
          </div>
        </div>
      )}

      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden mb-6">
        <div className="px-5 py-4 border-b border-gray-50">
          <h2 className="font-bold text-gray-900">📦 Sản phẩm ({order.items?.length ?? 0})</h2>
        </div>
        {order.items?.map(item => (
          <div key={item.orderItemId} className="flex items-center gap-4 px-5 py-4 border-b border-gray-50 last:border-b-0">
            <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
              {item.imageSnapshot ? (
                <img src={item.imageSnapshot} alt="" className="w-full h-full object-cover" />
              ) : '📦'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-medium text-gray-900 truncate">{item.productName}</p>
              <p className="text-sm text-gray-500">{item.variantName}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="text-xs text-gray-400">SKU: {item.skuCode}</span>
                <span className="text-gray-300">·</span>
                <span className="text-xs text-gray-400">x{item.quantity}</span>
                {item.refundedQuantity > 0 && (
                  <>
                    <span className="text-gray-300">·</span>
                    <span className="text-xs text-blue-600">Đã hoàn: {item.refundedQuantity}</span>
                  </>
                )}
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="font-bold text-gray-900">{fmt(item.priceSnapshot * item.quantity)}</p>
              <p className="text-xs text-gray-400">{fmt(item.priceSnapshot)}/sp</p>
            </div>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-5">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền</span>
            <span>{fmt(order.totalAmt)}</span>
          </div>
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmt(order.finalAmt)}</span>
          </div>
        </div>
      </div>

      {showTracking && (
        <TrackingModal
          orderId={id}
          orderCode={order.orderCode}
          onClose={() => setShowTracking(false)}
          onSuccess={onMutationSuccess}
        />
      )}
      {showReturn && (
        <ReturnToSenderModal
          orderId={id}
          orderCode={order.orderCode}
          onClose={() => setShowReturn(false)}
          onSuccess={onMutationSuccess}
        />
      )}
    </div>
  );
}
