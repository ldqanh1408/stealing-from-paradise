/**
 * SellerOrderDetailPage — full detail view for a single seller sub-order.
 *
 * Shows buyer, payment, line items and totals, and exposes the same
 * UC-driven actions as the orders list, reusing the shared modals:
 *   - PAID (not shipped) → ship (UC-ORDER-004) or cancel (UC-ORDER-008)
 *   - SHIPPING           → return-to-sender (UC-ORDER-006 Flow B)
 */
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '@shared/api/order.api';
import { paymentApi } from '@shared/api/payment.api';
import { fmtVnd, fmtDateTime } from '@shared/utils/format';
import { getStatusMeta } from '@/lib/orderStatus';
import { canShip, canCancel, canReturnToSender } from '@/lib/orderActions';
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import CancelOrderModal from '@/components/Orders/CancelOrderModal';

export default function SellerOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const id = Number(orderId);
  const queryClient = useQueryClient();

  const [showTracking, setShowTracking] = useState(false);
  const [showReturn, setShowReturn] = useState(false);
  const [showCancel, setShowCancel] = useState(false);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['seller-order', id],
    queryFn: () => orderApi.getOrderById(id).then(r => r.data.data),
    enabled: id > 0,
    retry: 1,
  });

  // Payment info is fetched from the parent order (a parent may span sellers).
  const { data: paymentData } = useQuery({
    queryKey: ['payment-for-seller', order?.parentOrderId],
    queryFn: () => paymentApi.getPayment(order!.parentOrderId!).then(r => r.data.data),
    enabled: !!order?.parentOrderId,
    retry: 1,
  });

  /** Invalidate both the detail and list queries after a successful mutation. */
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

  const st = getStatusMeta(order.status);

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center gap-3 mb-6">
        <a href="/orders" className="text-gray-400 hover:text-gray-600 text-2xl">←</a>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500 font-mono">{order.orderCode}</p>
        </div>
      </div>

      {/* Status + actions strip */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6 flex items-center gap-4 flex-wrap">
        <span className={`px-3 py-1.5 rounded-full text-sm font-semibold ${st.bg} ${st.color}`}>
          {st.label}
        </span>
        <div className="flex-1 text-sm text-gray-500">
          Đặt lúc {fmtDateTime(order.createdAt)}
        </div>
        {order.trackingNumber && (
          <div className="text-sm">
            <span className="text-gray-500">Mã vận đơn: </span>
            <span className="font-mono font-medium text-gray-900">{order.trackingNumber}</span>
            {order.carrier && <span className="text-gray-400"> ({order.carrier})</span>}
          </div>
        )}

        <div className="flex gap-2">
          {canShip(order.status) && (
            <button
              onClick={() => setShowTracking(true)}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium transition-colors"
            >
              📦 Cập nhật vận đơn
            </button>
          )}
          {canCancel(order.status, order.trackingNumber) && (
            <button
              onClick={() => setShowCancel(true)}
              className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-xl text-sm font-medium transition-colors"
            >
              Huỷ đơn
            </button>
          )}
          {canReturnToSender(order.status) && (
            <button
              onClick={() => setShowReturn(true)}
              className="px-4 py-2 bg-orange-50 hover:bg-orange-100 text-orange-600 border border-orange-200 rounded-xl text-sm font-medium transition-colors"
            >
              ↩ Hoàn hàng
            </button>
          )}
        </div>
      </div>

      {/* Customer */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">👤 Thông tin khách hàng</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500 text-xs">Tên</p>
            <p className="font-medium text-gray-900">{order.buyerName || `User #${order.buyerId}`}</p>
          </div>
          {order.shippingAddress && (
            <div className="col-span-2">
              <p className="text-gray-500 text-xs">Địa chỉ giao hàng</p>
              <p className="font-medium text-gray-900">{order.shippingAddress.fullAddress}</p>
            </div>
          )}
        </div>
      </div>

      {/* Payment */}
      {paymentData && (
        <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
          <h2 className="font-bold text-gray-900 mb-3">💳 Thông tin thanh toán</h2>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-gray-500 text-xs">Số tiền</p>
              <p className="font-bold text-gray-900">{fmtVnd(paymentData.amount)}</p>
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
                <p className="font-medium text-gray-700 text-xs">{fmtDateTime(paymentData.paidAt)}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Line items */}
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
              <p className="font-bold text-gray-900">{fmtVnd(item.priceSnapshot * item.quantity)}</p>
              <p className="text-xs text-gray-400">{fmtVnd(item.priceSnapshot)}/sp</p>
            </div>
          </div>
        ))}
      </div>

      {/* Totals */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền</span>
            <span>{fmtVnd(order.totalAmt)}</span>
          </div>
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmtVnd(order.finalAmt)}</span>
          </div>
        </div>
      </div>

      {/* Action modals (shared with the orders list) */}
      {showTracking && (
        <TrackingModal
          orderId={id}
          orderCode={order.orderCode}
          customerLabel={order.buyerName || `User #${order.buyerId}`}
          onClose={() => setShowTracking(false)}
          onSuccess={onMutationSuccess}
        />
      )}
      {showCancel && (
        <CancelOrderModal
          orderId={id}
          orderCode={order.orderCode}
          onClose={() => setShowCancel(false)}
          onSuccess={onMutationSuccess}
        />
      )}
      {showReturn && (
        <RTSModal
          orderId={id}
          orderCode={order.orderCode}
          onClose={() => setShowReturn(false)}
          onSuccess={onMutationSuccess}
        />
      )}
    </div>
  );
}
