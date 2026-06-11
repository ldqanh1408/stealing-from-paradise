import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary } from '@shared/api/order.api';

export default function TrackingModal({ order, onClose, onSuccess }: { order: SellerOrderSummary; onClose: () => void; onSuccess: () => void }) {
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
