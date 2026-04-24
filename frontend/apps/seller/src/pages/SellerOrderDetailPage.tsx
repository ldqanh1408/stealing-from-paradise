import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
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

export default function SellerOrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const id = Number(orderId);

  const { data: order, isLoading, error } = useQuery({
    queryKey: ['seller-order', id],
    queryFn: () => orderApi.getOrderById(id).then(r => r.data.data),
    enabled: id > 0,
    retry: 1,
  });

  const { data: paymentData } = useQuery({
    queryKey: ['payment-for-seller', order?.parent_order_id],
    queryFn: () => paymentApi.getPayment(order!.parent_order_id!).then(r => r.data.data),
    enabled: !!order?.parent_order_id,
    retry: 1,
  });

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
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <a href="/orders" className="text-gray-400 hover:text-gray-600 text-2xl">←</a>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
          <p className="text-sm text-gray-500 font-mono">{order.order_code}</p>
        </div>
      </div>

      {/* Status */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6 flex items-center gap-4">
        <span className={`px-3 py-1.5 rounded-full text-sm font-semibold ${st.bg} ${st.color}`}>
          {st.label}
        </span>
        <div className="flex-1 text-sm text-gray-500">
          Đặt lúc {formatDate(order.created_at)}
        </div>
        {order.tracking_number && (
          <div className="text-sm">
            <span className="text-gray-500">Mã vận đơn: </span>
            <span className="font-mono font-medium text-gray-900">{order.tracking_number}</span>
            {order.carrier && <span className="text-gray-400"> ({order.carrier})</span>}
          </div>
        )}
      </div>

      {/* Buyer info */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-6">
        <h2 className="font-bold text-gray-900 mb-3">👤 Thông tin khách hàng</h2>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <p className="text-gray-500 text-xs">Tên</p>
            <p className="font-medium text-gray-900">{order.buyer_name || `User #${order.buyer_id}`}</p>
          </div>
          <div>
            <p className="text-gray-500 text-xs">Username</p>
            <p className="font-medium text-gray-900">{order.buyer_name ? `@${order.buyer_id}` : '—'}</p>
          </div>
          {order.shipping_address && (
            <>
              <div className="col-span-2">
                <p className="text-gray-500 text-xs">Địa chỉ giao hàng</p>
                <p className="font-medium text-gray-900">{order.shipping_address.full_address}</p>
              </div>
            </>
          )}
        </div>
      </div>

      {/* Payment info */}
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
              <p className="font-mono text-xs text-gray-600">{paymentData.trans_ref}</p>
            </div>
            {paymentData.paid_at && (
              <div>
                <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                <p className="font-medium text-gray-700 text-xs">{formatDate(paymentData.paid_at)}</p>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Order items */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden mb-6">
        <div className="px-5 py-4 border-b border-gray-50">
          <h2 className="font-bold text-gray-900">📦 Sản phẩm ({order.items?.length ?? 0})</h2>
        </div>
        {order.items?.map(item => (
          <div key={item.order_item_id} className="flex items-center gap-4 px-5 py-4 border-b border-gray-50 last:border-b-0">
            <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
              {item.image_snapshot ? (
                <img src={item.image_snapshot} alt="" className="w-full h-full object-cover" />
              ) : '📦'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="font-medium text-gray-900 truncate">{item.product_name}</p>
              <p className="text-sm text-gray-500">{item.variant_name}</p>
              <div className="flex items-center gap-2 mt-0.5">
                <span className="text-xs text-gray-400">SKU: {item.sku_code}</span>
                <span className="text-gray-300">·</span>
                <span className="text-xs text-gray-400">x{item.quantity}</span>
                {item.refunded_quantity > 0 && (
                  <>
                    <span className="text-gray-300">·</span>
                    <span className="text-xs text-blue-600">Đã hoàn: {item.refunded_quantity}</span>
                  </>
                )}
              </div>
            </div>
            <div className="text-right shrink-0">
              <p className="font-bold text-gray-900">{fmt(item.price_snapshot * item.quantity)}</p>
              <p className="text-xs text-gray-400">{fmt(item.price_snapshot)}/sp</p>
            </div>
          </div>
        ))}
      </div>

      {/* Price summary */}
      <div className="bg-white rounded-2xl border border-gray-100 p-5">
        <h2 className="font-bold text-gray-900 mb-3">💰 Tổng kết</h2>
        <div className="space-y-2 text-sm">
          <div className="flex justify-between text-gray-600">
            <span>Tổng tiền</span>
            <span>{fmt(order.total_amt)}</span>
          </div>
          <div className="flex justify-between font-bold text-base">
            <span>Thanh toán</span>
            <span className="text-red-600 text-lg">{fmt(order.final_amt)}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
