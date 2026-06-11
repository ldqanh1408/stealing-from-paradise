import { useState, useEffect, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { sellerApi, type SellerVariant } from '@shared/api/seller.api';

export default function VariantModal({
  productId,
  initial,
  onClose,
  onSuccess,
}: {
  productId: string;
  initial?: SellerVariant;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [sku, setSku] = useState(initial?.skuCode ?? '');
  const [name, setName] = useState(initial?.variantName ?? '');
  const [price, setPrice] = useState(initial?.price?.toString() ?? '');
  const [stock, setStock] = useState(initial?.stock?.toString() ?? '1');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);

  const mut = useMutation({
    mutationFn: () => {
      const data = { skuCode: sku.trim(), variantName: name.trim(), price: Number(price), stock: Number(stock) };
      return initial
        ? sellerApi.updateVariant(initial.variantId, data)
        : sellerApi.createVariant(productId, data);
    },
    onSuccess: () => { setDone(true); timerRef.current = setTimeout(() => { onSuccess(); onClose(); }, 1200); },
    onError: (err: any) => setError(err?.response?.data?.message || 'Lưu biến thể thất bại'),
  });

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60] p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">
            {initial ? 'Cập nhật thành công!' : 'Tạo biến thể thành công!'}
          </h3>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-[60] p-4">
      <div className="bg-white rounded-2xl p-6 max-w-md w-full">
        <h3 className="text-lg font-bold text-gray-900 mb-4">
          {initial ? 'Chỉnh sửa biến thể' : 'Thêm biến thể'}
        </h3>
        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">SKU *</label>
            <input type="text" value={sku} onChange={e => setSku(e.target.value)} placeholder="VD: IPHONE15-BLK-128"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên biến thể *</label>
            <input type="text" value={name} onChange={e => setName(e.target.value)} placeholder="VD: iPhone 15 Black 128GB"
              className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá (VND) *</label>
              <input type="number" value={price} onChange={e => setPrice(e.target.value)} min="0"
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tồn kho *</label>
              <input type="number" value={stock} onChange={e => setStock(e.target.value)} min="0"
                className="w-full px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button onClick={() => {
            if (!sku.trim() || !name.trim() || !price) { setError('Vui lòng điền đầy đủ thông tin.'); return; }
            setError('');
            mut.mutate();
          }} disabled={mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}
