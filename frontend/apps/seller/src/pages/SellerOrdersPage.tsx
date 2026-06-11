import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary, type OrderStatus } from '@shared/api/order.api';
import { fmtVnd, fmtDate as formatDate } from '@shared/utils/format';
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import OrderDrawer from '@/components/Orders/OrderDrawer';

const fmt = (n: number) => fmtVnd(n);

const STATUS_FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ xác nhận' },
  { value: 'PAID', label: 'Đã thanh toán' },
  { value: 'SHIPPING', label: 'Đang giao' },
  { value: 'DELIVERED', label: 'Đã giao' },
  { value: 'CANCELLED', label: 'Đã huỷ' },
  { value: 'PARTIALLY_REFUNDED', label: 'Hoàn một phần' },
  { value: 'REFUNDED', label: 'Đã hoàn' },
];

const STATUS_STYLE: Record<string, { color: string; bg: string }> = {
  PENDING:            { bg: 'bg-yellow-100', color: 'text-yellow-700' },
  PAID:               { bg: 'bg-blue-100',   color: 'text-blue-700' },
  SHIPPING:           { bg: 'bg-purple-100',  color: 'text-purple-700' },
  DELIVERED:          { bg: 'bg-green-100',   color: 'text-green-700' },
  CANCELLED:          { bg: 'bg-red-100',     color: 'text-red-700' },
  RETURNED:           { bg: 'bg-orange-100',  color: 'text-orange-700' },
  PARTIALLY_REFUNDED: { bg: 'bg-indigo-100',  color: 'text-indigo-700' },
  REFUNDED:           { bg: 'bg-gray-100',    color: 'text-gray-600' },
};

// ─── Main Page ────────────────────────────────────────────────────────────────
export default function SellerOrdersPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);
  const [trackingOrder, setTrackingOrder] = useState<SellerOrderSummary | null>(null);
  const [rtsOrder, setRtsOrder] = useState<SellerOrderSummary | null>(null);
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

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Đơn hàng</h1>
          <p className="text-sm text-gray-500 mt-1">Quản lý và xử lý đơn hàng từ khách</p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['seller-orders'] })}
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

      {/* Empty */}
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
                  {orders.map(order => {
                    const st = STATUS_STYLE[order.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700' };
                    const stLabel = STATUS_FILTERS.find(f => f.value === order.status)?.label ?? order.status;
                    return (
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
                        <td className="px-5 py-4 font-semibold text-gray-900">{fmt(order.finalAmt)}</td>
                        <td className="px-5 py-4">
                          <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${st.bg} ${st.color}`}>{stLabel}</span>
                        </td>
                        <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{formatDate(order.createdAt)}</td>
                        <td className="px-5 py-4">
                          <div className="flex gap-2 flex-wrap">
                            {order.status === 'PENDING' && (
                              <button
                                onClick={() => {
                                  if (confirm(`Hủy đơn ${order.orderCode}? Hành động này không thể hoàn tác.`)) {
                                    orderApi.cancelOrder(order.orderId, { reason: 'Người bán hủy đơn' })
                                      .then(() => queryClient.invalidateQueries({ queryKey: ['seller-orders'] }));
                                  }
                                }}
                                className="text-xs text-red-500 hover:text-red-600 font-medium whitespace-nowrap"
                              >
                                Huỷ đơn
                              </button>
                            )}
                            {order.status === 'PAID' && (
                              <button
                                onClick={() => setTrackingOrder(order)}
                                className="text-xs text-blue-600 hover:text-blue-700 font-medium whitespace-nowrap"
                              >
                                + Vận đơn
                              </button>
                            )}
                            {order.status === 'SHIPPING' && (
                              <span className="text-xs text-gray-400 whitespace-nowrap">Đang giao...</span>
                            )}
                            {order.status === 'RETURNED' && (
                              <button
                                onClick={() => setRtsOrder(order)}
                                className="text-xs text-orange-600 hover:text-orange-700 font-medium whitespace-nowrap"
                              >
                                Xác nhận hoàn
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
      {trackingOrder && (
        <TrackingModal
          order={trackingOrder}
          onClose={() => setTrackingOrder(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['seller-orders'] })}
        />
      )}
      {rtsOrder && (
        <RTSModal
          order={rtsOrder}
          onClose={() => setRtsOrder(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['seller-orders'] })}
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
