import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary, type OrderStatus } from '@shared/api/order.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

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

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// ─── Update Tracking Modal ─────────────────────────────────────────────────────
function TrackingModal({ order, onClose, onSuccess }: { order: SellerOrderSummary; onClose: () => void; onSuccess: () => void }) {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [carrier, setCarrier] = useState('ViettelPost');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => orderApi.updateTracking(order.orderId, { trackingNumber, carrier, note }),
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Cập nhật vận đơn thất bại');
    },
  });

  const carriers = ['ViettelPost', 'GHN', 'GHTK', 'Ninja Van', 'J&T Express', 'Bưu điện', 'GrabExpress', 'Ahamove', 'Khác'];

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Cập nhật vận đơn</h3>
        <p className="text-sm text-gray-500 mb-4">
          Đơn: <strong>{order.orderCode}</strong> · Khách: {order.buyerName || order.buyerUsername || `User #${order.buyerId}`}
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn *</label>
            <input
              type="text"
              value={trackingNumber}
              onChange={e => setTrackingNumber(e.target.value)}
              placeholder="Ví dụ: VT123456789"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Đơn vị vận chuyển</label>
            <select
              value={carrier}
              onChange={e => setCarrier(e.target.value)}
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {carriers.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              rows={2}
              placeholder="VD: Giao trong giờ hành chính..."
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={!trackingNumber.trim() || mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang cập nhật...' : 'Cập nhật vận đơn'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── RTS Modal ─────────────────────────────────────────────────────────────────
function RTSModal({ order, onClose, onSuccess }: { order: SellerOrderSummary; onClose: () => void; onSuccess: () => void }) {
  const [returnTracking, setReturnTracking] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [files, setFiles] = useState<FileList | null>(null);

  const mut = useMutation({
    mutationFn: () => {
      const fd = new FormData();
      if (files) Array.from(files).forEach(f => fd.append('evidence_images', f));
      if (returnTracking) fd.append('return_tracking_number', returnTracking);
      if (note) fd.append('note', note);
      return orderApi.returnToSender(order.orderId, fd);
    },
    onSuccess: () => { onSuccess(); onClose(); },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Xác nhận hoàn hàng thất bại');
    },
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full my-4">
        <h3 className="text-lg font-bold text-gray-900 mb-2">Xác nhận hoàn hàng (RTS)</h3>
        <p className="text-sm text-gray-500 mb-4">
          Đơn <strong>{order.orderCode}</strong> đã được hoàn về. Xác nhận để kích hoạt hoàn tiền tự động cho khách.
        </p>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4 mb-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ảnh bằng chứng (1-5 ảnh) *</label>
            <input
              type="file"
              multiple
              accept="image/*"
              onChange={e => setFiles(e.target.files)}
              className="w-full text-sm text-gray-600 file:mr-3 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
            />
            {files && (
              <p className="text-xs text-gray-500 mt-1">{files.length} ảnh được chọn</p>
            )}
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Mã vận đơn hoàn (tùy chọn)</label>
            <input
              type="text"
              value={returnTracking}
              onChange={e => setReturnTracking(e.target.value)}
              placeholder="Mã vận đơn từ đơn vị vận chuyển"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Ghi chú (tùy chọn)</label>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              rows={2}
              placeholder="Mô tả tình trạng hàng hoàn..."
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className="flex-1 py-2.5 bg-orange-600 text-white rounded-xl text-sm font-medium hover:bg-orange-700 disabled:opacity-50"
          >
            {mut.isPending ? 'Đang xử lý...' : 'Xác nhận hoàn hàng'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Order Detail Drawer ──────────────────────────────────────────────────────
function OrderDrawer({ order, onClose }: { order: SellerOrderSummary; onClose: () => void }) {
  const navigate = useNavigate();

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex justify-end" onClick={onClose}>
      <div
        className="bg-white w-full max-w-md h-full overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="sticky top-0 bg-white border-b p-5 flex items-center justify-between">
          <h3 className="font-bold text-gray-900">Chi tiết đơn #{order.orderCode}</h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl">×</button>
        </div>
        <div className="p-5 space-y-5">
          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">Thông tin khách hàng</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <p><span className="text-gray-500">Tên:</span> <span className="font-medium">{order.buyerName || `User #${order.buyerId}`}</span></p>
              {order.buyerUsername && <p><span className="text-gray-500">Username:</span> <span className="font-medium">@{order.buyerUsername}</span></p>}
              <p><span className="text-gray-500">Địa chỉ:</span> <span className="font-medium">{order.shippingAddress?.fullAddress || '—'}</span></p>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">Thông tin vận chuyển</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <p><span className="text-gray-500">Mã vận đơn:</span> <span className="font-medium font-mono">{order.trackingNumber || '—'}</span></p>
              <p><span className="text-gray-500">Đơn vị:</span> <span className="font-medium">{order.carrier || '—'}</span></p>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-500 uppercase mb-3">Thông tin thanh toán</h4>
            <div className="bg-gray-50 rounded-xl p-3 text-sm space-y-1">
              <p><span className="text-gray-500">Tổng tiền:</span> <span className="font-bold text-red-600">{fmt(order.totalAmt)}</span></p>
              <p><span className="text-gray-500">Thanh toán:</span> <span className="font-medium">{fmt(order.finalAmt)}</span></p>
            </div>
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => { onClose(); navigate(`/orders/${order.orderId}`); }}
              className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50"
            >
              Xem chi tiết đầy đủ
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

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
