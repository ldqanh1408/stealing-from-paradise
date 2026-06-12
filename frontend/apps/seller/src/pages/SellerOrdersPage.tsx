/**
 * SellerOrdersPage — UC-ORDER-007 "View Seller Orders".
 *
 * Paginated, status-filterable list of every order placed with this seller's
 * shop. Per-row actions are driven by the business-rule predicates in
 * {@link ../lib/orderActions} so the UI only ever offers a transition the
 * backend will accept:
 *   - PAID  → "Cập nhật vận đơn" (ship, UC-ORDER-004) or "Huỷ đơn" (UC-ORDER-008)
 *   - SHIPPING → "Hoàn hàng" (return-to-sender, UC-ORDER-006 Flow B)
 */
import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary, type OrderStatus } from '@shared/api/order.api';
import { fmtVnd, fmtDate } from '@shared/utils/format';
import { getStatusMeta, OrderStatusBadge } from '@/lib/orderStatus';
import { canShip, canCancel, canReturnToSender } from '@/lib/orderActions';
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import CancelOrderModal from '@/components/Orders/CancelOrderModal';
import OrderDrawer from '@/components/Orders/OrderDrawer';

/** Status filter chips shown above the table (includes the synthetic "ALL"). */
const STATUS_FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: getStatusMeta('PENDING').label },
  { value: 'PAID', label: getStatusMeta('PAID').label },
  { value: 'SHIPPING', label: getStatusMeta('SHIPPING').label },
  { value: 'DELIVERED', label: getStatusMeta('DELIVERED').label },
  { value: 'CANCELLED', label: getStatusMeta('CANCELLED').label },
  { value: 'PARTIALLY_REFUNDED', label: getStatusMeta('PARTIALLY_REFUNDED').label },
  { value: 'REFUNDED', label: getStatusMeta('REFUNDED').label },
];

/** Build a human-friendly buyer label for modal headers. */
const buyerLabel = (o: SellerOrderSummary) => o.buyerName || o.buyerUsername || `User #${o.buyerId}`;

export default function SellerOrdersPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  // Each modal is keyed off the order it acts on (null = closed).
  const [trackingOrder, setTrackingOrder] = useState<SellerOrderSummary | null>(null);
  const [rtsOrder, setRtsOrder] = useState<SellerOrderSummary | null>(null);
  const [cancelOrder, setCancelOrder] = useState<SellerOrderSummary | null>(null);
  const [drawerOrder, setDrawerOrder] = useState<SellerOrderSummary | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-orders', filter, page],
    queryFn: () =>
      orderApi.getSellerOrders({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const orders: SellerOrderSummary[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  /** Refresh the list after any mutation succeeds. */
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['seller-orders'] });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-1">Quản lý và xử lý đơn hàng từ khách</p>
        </div>
        <button
          onClick={refresh}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap gap-2 mb-5">
        {STATUS_FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => { setFilter(f.value); setPage(0); }}
            className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
              filter === f.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
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
          Đang tải đơn hàng...
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải đơn hàng. Vui lòng thử lại.
        </div>
      )}

      {/* Empty (UC-ORDER-007 A1: no orders found) */}
      {!isLoading && !error && orders.length === 0 && (
        <div className="text-center py-20 text-gray-400">
          <span className="text-4xl block mb-3">📋</span>
          Chưa có đơn hàng nào
        </div>
      )}

      {/* Table */}
      {!isLoading && !error && orders.length > 0 && (
        <>
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['Mã đơn', 'Khách hàng', 'Sản phẩm', 'Tổng tiền', 'Trạng thái', 'Ngày đặt', 'Thao tác'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {orders.map(order => (
                    <tr key={order.orderId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4">
                        <button
                          onClick={() => setDrawerOrder(order)}
                          className="font-mono font-medium text-gray-900 hover:text-blue-600"
                        >
                          {order.orderCode}
                        </button>
                      </td>
                      <td className="px-5 py-4 text-gray-700">
                        <p className="font-medium">{order.buyerName || `User #${order.buyerId}`}</p>
                        {order.buyerUsername && <p className="text-xs text-gray-400">@{order.buyerUsername}</p>}
                      </td>
                      <td className="px-5 py-4 text-gray-500">{order.itemCount} sản phẩm</td>
                      <td className="px-5 py-4 font-semibold text-gray-900">{fmtVnd(order.finalAmt)}</td>
                      <td className="px-5 py-4"><OrderStatusBadge status={order.status} /></td>
                      <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{fmtDate(order.createdAt)}</td>
                      <td className="px-5 py-4">
                        <div className="flex gap-2 flex-wrap">
                          {/* PAID, not yet shipped → ship or cancel (UC-ORDER-004 / UC-ORDER-008) */}
                          {canShip(order.status) && (
                            <button
                              onClick={() => setTrackingOrder(order)}
                              className="text-xs text-blue-600 hover:text-blue-700 font-medium whitespace-nowrap"
                            >
                              + Vận đơn
                            </button>
                          )}
                          {canCancel(order.status, order.trackingNumber) && (
                            <button
                              onClick={() => setCancelOrder(order)}
                              className="text-xs text-red-500 hover:text-red-600 font-medium whitespace-nowrap"
                            >
                              Huỷ đơn
                            </button>
                          )}
                          {/* SHIPPING → carrier returned the package (UC-ORDER-006 Flow B) */}
                          {canReturnToSender(order.status) && (
                            <button
                              onClick={() => setRtsOrder(order)}
                              className="text-xs text-orange-600 hover:text-orange-700 font-medium whitespace-nowrap"
                            >
                              Hoàn hàng
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
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

      {/* Action modals — all share the same refresh-on-success behaviour. */}
      {trackingOrder && (
        <TrackingModal
          orderId={trackingOrder.orderId}
          orderCode={trackingOrder.orderCode}
          customerLabel={buyerLabel(trackingOrder)}
          onClose={() => setTrackingOrder(null)}
          onSuccess={refresh}
        />
      )}
      {cancelOrder && (
        <CancelOrderModal
          orderId={cancelOrder.orderId}
          orderCode={cancelOrder.orderCode}
          onClose={() => setCancelOrder(null)}
          onSuccess={refresh}
        />
      )}
      {rtsOrder && (
        <RTSModal
          orderId={rtsOrder.orderId}
          orderCode={rtsOrder.orderCode}
          onClose={() => setRtsOrder(null)}
          onSuccess={refresh}
        />
      )}
      {drawerOrder && (
        <OrderDrawer
          order={drawerOrder}
          onClose={() => setDrawerOrder(null)}
        />
      )}
    </div>
  );
}
