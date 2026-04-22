import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { orderApi, type OrderSummary, type OrderStatus } from '@shared/api/order.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

const STATUS_FILTERS: { value: OrderStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING', label: 'Chờ xác nhận' },
  { value: 'PAID', label: 'Đã thanh toán' },
  { value: 'SHIPPING', label: 'Đang giao' },
  { value: 'DELIVERED', label: 'Đã nhận' },
  { value: 'CANCELLED', label: 'Đã huỷ' },
  { value: 'PARTIALLY_REFUNDED', label: 'Hoàn một phần' },
  { value: 'REFUNDED', label: 'Đã hoàn' },
  { value: 'RETURNED', label: 'Hoàn hàng' },
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

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
}

export default function OrderHistoryPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<OrderStatus | 'ALL'>('ALL');
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['buyer-orders', filter, page],
    queryFn: () =>
      orderApi.getOrders({
        status: filter === 'ALL' ? undefined : filter,
        page,
        size: 10,
      }).then(r => r.data.data),
    retry: 1,
    initialData: undefined,
  });

  const orders: OrderSummary[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Đơn hàng của tôi</h1>

      {/* Filters */}
      <div className="flex gap-2 mb-6 flex-wrap">
        {STATUS_FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => { setFilter(f.value); setPage(0); }}
            className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
              filter === f.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300 hover:bg-blue-50'
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
        <div className="text-center py-20">
          <div className="w-24 h-24 rounded-full bg-purple-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            📦
          </div>
          <h2 className="text-2xl font-bold text-gray-900 mb-2">Chưa có đơn hàng nào</h2>
          <p className="text-gray-500 mb-8">Các đơn hàng của bạn sẽ xuất hiện ở đây sau khi đặt mua</p>
          <Link
            to="/products"
            className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
          >
            Khám phá sản phẩm
          </Link>
        </div>
      )}

      {/* Order list */}
      {!isLoading && !error && orders.length > 0 && (
        <>
          <p className="text-sm text-gray-500 mb-4">{totalElements} đơn hàng</p>
          <div className="space-y-3">
            {orders.map(order => {
              const st = STATUS_STYLE[order.status] ?? { bg: 'bg-gray-100', color: 'text-gray-700' };
              return (
                <div
                  key={order.order_id}
                  onClick={() => navigate(`/orders/${order.parent_order_id}`)}
                  className="bg-white rounded-2xl border border-gray-100 p-5 hover:shadow-sm transition-all cursor-pointer"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1 flex-wrap">
                        <span className="font-bold text-gray-900">{order.order_code}</span>
                        <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${st.bg} ${st.color}`}>
                          {STATUS_FILTERS.find(f => f.value === order.status)?.label ?? order.status}
                        </span>
                        {order.is_flash_sale && (
                          <span className="text-xs font-medium px-2 py-0.5 rounded-full bg-red-100 text-red-600">
                            ⚡ Flash Sale
                          </span>
                        )}
                      </div>
                      <p className="text-sm text-gray-500">
                        {order.seller_name} · {order.item_count} sản phẩm
                      </p>
                      <p className="text-xs text-gray-400 mt-0.5">{formatDate(order.created_at)}</p>
                    </div>
                    <div className="text-right shrink-0">
                      <p className="font-bold text-gray-900">{fmt(order.final_amt)}</p>
                      <button
                        onClick={e => { e.stopPropagation(); navigate(`/orders/${order.parent_order_id}`); }}
                        className="text-sm text-blue-600 hover:text-blue-700 font-medium mt-1"
                      >
                        Xem chi tiết
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
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
    </div>
  );
}
