import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi, type SellerVariant, type InventoryLogEntry } from '@shared/api/seller.api';
import { fmtVnd as fmt } from '@shared/utils/format';

export default function InventoryPanel({ productId, variants }: { productId: string; variants: SellerVariant[] }) {
  const queryClient = useQueryClient();
  const [adjustSku, setAdjustSku] = useState<string | null>(null);
  const [adjustDelta, setAdjustDelta] = useState(0);
  const [adjustReason, setAdjustReason] = useState('');
  const [adjustError, setAdjustError] = useState('');
  const [restockQty, setRestockQty] = useState<number>(0);
  const [logSku, setLogSku] = useState<string | null>(null);

  const adjustMut = useMutation({
    mutationFn: (data: { skuCode: string; delta: number; reason: string }) =>
      sellerApi.adjustInventory(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      queryClient.invalidateQueries({ queryKey: ['seller-variants', productId] });
      setAdjustSku(null);
      setAdjustDelta(0);
      setAdjustReason('');
    },
    onError: (err: any) => setAdjustError(err?.response?.data?.message || 'Điều chỉnh thất bại'),
  });

  const restockMut = useMutation({
    mutationFn: (data: { skuCode: string; quantity: number; reason: string }) =>
      sellerApi.restockInventory(data.skuCode, { quantity: data.quantity, reason: data.reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-products'] });
      queryClient.invalidateQueries({ queryKey: ['seller-variants', productId] });
    },
    onError: (err: any) => setAdjustError(err?.response?.data?.message || 'Nhập hàng thất bại'),
  });

  const { data: logs = [], isLoading: logsLoading, error: logsQueryError } = useQuery({
    queryKey: ['inventory-logs', logSku],
    queryFn: () => sellerApi.getInventoryLogs(logSku!).then(r => r.data.data ?? []),
    enabled: !!logSku,
    retry: 1,
  });
  const logsError = !!logsQueryError;

  if (variants.length === 0) {
    return (
      <div className="text-center py-8 text-gray-400 text-sm">
        Chưa có biến thể nào. Tạo biến thể trước để quản lý tồn kho.
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {adjustError && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-2">
          {adjustError}
          <button onClick={() => setAdjustError('')} className="ml-2 underline">Đóng</button>
        </div>
      )}

      <div className="space-y-2 max-h-64 overflow-y-auto">
        {variants.map(v => (
          <div key={v.skuCode} className="flex items-center gap-3 p-3 border border-gray-100 rounded-xl bg-white">
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-gray-900 truncate">{v.variantName}</p>
              <p className="text-xs text-gray-400">SKU: {v.skuCode} · Kho: <span className={`font-semibold ${v.stock > 0 ? 'text-green-700' : 'text-red-600'}`}>{v.stock}</span> · {fmt(v.price)}</p>
            </div>

            {/* Quick adjust buttons */}
            <div className="flex gap-1 shrink-0">
              <button
                onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(-1); setAdjustReason(''); }}
                className="px-2 py-1 text-xs bg-red-50 text-red-600 rounded-lg hover:bg-red-100"
              >-1</button>
              <button
                onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(1); setAdjustReason(''); }}
                className="px-2 py-1 text-xs bg-green-50 text-green-600 rounded-lg hover:bg-green-100"
              >+1</button>
              <button
                onClick={() => { setAdjustSku(v.skuCode); setAdjustDelta(0); setAdjustReason(''); }}
                className="px-2 py-1 text-xs bg-blue-50 text-blue-600 rounded-lg hover:bg-blue-100"
              >±</button>
              <button
                onClick={() => setLogSku(logSku === v.skuCode ? null : v.skuCode)}
                className={`px-2 py-1 text-xs rounded-lg ${logSku === v.skuCode ? 'bg-gray-200 text-gray-700' : 'bg-gray-50 text-gray-500 hover:bg-gray-100'}`}
              >
                Log
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Adjust modal */}
      {adjustSku && (
        <div className="border border-blue-200 bg-blue-50/30 rounded-xl p-4 mt-3">
          <h4 className="font-semibold text-gray-900 text-sm mb-3">
            Điều chỉnh tồn kho: <span className="font-mono text-blue-700">{adjustSku}</span>
          </h4>
          <div className="space-y-3">
            <div className="flex gap-2">
              <input
                type="number"
                value={adjustDelta}
                onChange={e => setAdjustDelta(Number(e.target.value))}
                placeholder="Số lượng (+/-)"
                className="flex-1 px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <input
                type="number"
                value={restockQty || ''}
                onChange={e => setRestockQty(Number(e.target.value))}
                placeholder="SL nhập"
                min="1"
                className="w-20 px-2 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
              />
              <button
                onClick={() => {
                  if (restockQty > 0) {
                    restockMut.mutate({ skuCode: adjustSku, quantity: restockQty, reason: adjustReason || 'Nhập hàng' });
                  }
                }}
                disabled={restockMut.isPending || restockQty <= 0}
                className="px-3 py-2 bg-green-600 text-white text-xs font-medium rounded-xl hover:bg-green-700 disabled:opacity-50"
              >
                {restockMut.isPending ? '...' : 'Nhập hàng'}
              </button>
            </div>
            <input
              type="text"
              value={adjustReason}
              onChange={e => setAdjustReason(e.target.value)}
              placeholder="Lý do (vd: Hàng hỏng, kiểm kê...)"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <div className="flex gap-2 justify-end">
              <button onClick={() => { setAdjustSku(null); setAdjustError(''); }}
                className="px-4 py-2 border rounded-xl text-sm hover:bg-gray-50">Huỷ</button>
              <button
                onClick={() => {
                  if (adjustDelta === 0) { setAdjustError('Vui lòng nhập số lượng điều chỉnh.'); return; }
                  adjustMut.mutate({ skuCode: adjustSku, delta: adjustDelta, reason: adjustReason || 'Điều chỉnh tồn kho' });
                }}
                disabled={adjustMut.isPending}
                className="px-4 py-2 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
              >
                {adjustMut.isPending ? 'Đang xử lý...' : 'Xác nhận'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Logs panel */}
      {logSku && (
        <div className="border border-gray-200 rounded-xl p-4 mt-3">
          <div className="flex items-center justify-between mb-3">
            <h4 className="font-semibold text-gray-900 text-sm">
              Lịch sử tồn kho: <span className="font-mono text-blue-700">{logSku}</span>
            </h4>
            <button onClick={() => setLogSku(null)} className="text-gray-400 hover:text-gray-600">×</button>
          </div>
          {logsLoading ? (
            <p className="text-sm text-gray-400">Đang tải...</p>
          ) : logsError ? (
            <div className="text-center py-4 text-gray-400 text-sm">
              <p>Tính năng nhật ký tồn kho đang được phát triển.</p>
              <p className="text-xs mt-1">Vui lòng quay lại sau.</p>
            </div>
          ) : logs.length === 0 ? (
            <p className="text-sm text-gray-400">Chưa có lịch sử điều chỉnh.</p>
          ) : (
            <div className="max-h-48 overflow-y-auto space-y-2">
              {logs.map((log: InventoryLogEntry) => (
                <div key={log.logId} className="flex items-start gap-2 text-sm py-1.5 border-b border-gray-50 last:border-0">
                  <span className={`shrink-0 w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold ${log.delta >= 0 ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'}`}>
                    {log.delta >= 0 ? '+' : ''}{log.delta}
                  </span>
                  <div className="flex-1 min-w-0">
                    <p className="text-gray-700 text-xs truncate">{log.reason || '—'}</p>
                    <p className="text-gray-400 text-xs">
                      {log.stockBefore} → {log.stockAfter} · {log.changedBy} · {new Date(log.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
