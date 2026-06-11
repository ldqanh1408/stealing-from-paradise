import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { orderApi, type SellerOrderSummary } from '@shared/api/order.api';

export default function RTSModal({ order, onClose, onSuccess }: { order: SellerOrderSummary; onClose: () => void; onSuccess: () => void }) {
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
