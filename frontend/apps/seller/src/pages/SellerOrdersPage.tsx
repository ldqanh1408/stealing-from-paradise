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
import TrackingModal from '@/components/Orders/TrackingModal';
import RTSModal from '@/components/Orders/RTSModal';
import CancelOrderModal from '@/components/Orders/CancelOrderModal';
import OrderDrawer from '@/components/Orders/OrderDrawer';
import OrderFilters from '@/components/Orders/OrderFilters';
import OrdersTable from '@/components/Orders/OrdersTable';
import Pagination from '@shared/components/Pagination';
import { Skeleton, EmptyState } from '@shared/components/ui';

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
      <OrderFilters
        filter={filter}
        onFilterChange={(f) => { setFilter(f); setPage(0); }}
      />

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden divide-y divide-gray-50">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4 px-5 py-4">
              <Skeleton className="h-10 w-10 rounded-xl" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/3" />
                <Skeleton className="h-3 w-1/4" />
              </div>
              <Skeleton className="h-6 w-20 rounded-full" />
              <Skeleton className="h-4 w-16" />
            </div>
          ))}
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
        <EmptyState
          iconKey="receipt"
          title="Chưa có đơn hàng nào"
          description="Đơn hàng từ khách sẽ xuất hiện ở đây khi có người mua sản phẩm của bạn."
        />
      )}

      {/* Table */}
      {!isLoading && !error && orders.length > 0 && (
        <>
          <OrdersTable
            orders={orders}
            onViewDetail={setDrawerOrder}
            onShip={setTrackingOrder}
            onCancel={setCancelOrder}
            onReturnToSender={setRtsOrder}
          />

          {/* Pagination */}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
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
