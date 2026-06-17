import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@shared/lib/axios';
import { flashSaleApi, type FlashSaleItem, type FlashSaleSession } from '@shared/api/flashSale.api';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import type { ApiResponse, PageResponse } from '@shared/types/api';
import { Skeleton } from '@shared/components/ui';
import { fmtDateTime, fmtVnd } from '@shared/utils/format';
import { notify } from '@shared/lib/toast';

function statusLabel(status: string) {
  switch (status) {
    case 'APPROVED': return 'Đã duyệt';
    case 'REJECTED': return 'Từ chối';
    case 'PENDING': return 'Chờ duyệt';
    case 'SOLD_OUT': return 'Hết hàng';
    default: return status;
  }
}

function statusClass(status: string) {
  switch (status) {
    case 'APPROVED': return 'bg-green-100 text-green-700';
    case 'REJECTED': return 'bg-red-100 text-red-700';
    case 'PENDING': return 'bg-yellow-100 text-yellow-700';
    default: return 'bg-gray-100 text-gray-600';
  }
}

function mapSellerProduct(p: any): SellerProduct {
  return {
    ...p,
    productId: p.productId ?? p.id,
    stockAvailable: p.stockAvailable ?? p.totalStock ?? 0,
    variantsCount: p.variantsCount ?? p.variantCount ?? 0,
    images: p.images ?? (p.thumbnailUrl ? [p.thumbnailUrl] : []),
  };
}

export default function SellerFlashSalePage() {
  const queryClient = useQueryClient();
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);
  const [selectedProductId, setSelectedProductId] = useState('');
  const [selectedSku, setSelectedSku] = useState('');
  const [flashPrice, setFlashPrice] = useState('');
  const [flashStock, setFlashStock] = useState('1');
  const [limitPerUser, setLimitPerUser] = useState('1');
  const [error, setError] = useState<string | null>(null);

  const sessionsQuery = useQuery({
    queryKey: ['seller-flash-sale-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data?.content ?? []),
    staleTime: 30_000,
  });

  const sessions = sessionsQuery.data ?? [];
  const upcomingSessions = sessions.filter(s => s.status === 'UPCOMING');

  useEffect(() => {
    if (!selectedSessionId && upcomingSessions[0]) {
      setSelectedSessionId(upcomingSessions[0].id);
    }
  }, [selectedSessionId, upcomingSessions]);

  const productsQuery = useQuery({
    queryKey: ['seller-flash-sale-products'],
    queryFn: () =>
      apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
        params: { page: 0, size: 100 },
      }).then(r => (r.data.data?.content ?? []).map(mapSellerProduct)),
    staleTime: 30_000,
  });

  const products = productsQuery.data ?? [];

  useEffect(() => {
    if (!selectedProductId && products[0]) {
      setSelectedProductId(products[0].productId);
    }
  }, [products, selectedProductId]);

  const variantsQuery = useQuery({
    queryKey: ['seller-flash-sale-variants', selectedProductId],
    queryFn: () => sellerApi.getVariants(selectedProductId).then(r => r.data.data ?? []),
    enabled: !!selectedProductId,
  });

  const variants = variantsQuery.data ?? [];

  useEffect(() => {
    const first = variants[0];
    if (first && !variants.some(v => v.skuCode === selectedSku)) {
      setSelectedSku(first.skuCode);
      setFlashPrice(first.price ? String(Math.max(1, Math.floor(first.price * 0.8))) : '');
      setFlashStock(first.stock ? String(Math.min(first.stock, 10)) : '1');
    }
  }, [selectedSku, variants]);

  const selectedVariant: SellerVariant | undefined = useMemo(
    () => variants.find(v => v.skuCode === selectedSku),
    [selectedSku, variants],
  );

  const detailQuery = useQuery({
    queryKey: ['seller-flash-sale-detail', selectedSessionId],
    queryFn: () => flashSaleApi.getSession(selectedSessionId!).then(r => r.data.data),
    enabled: !!selectedSessionId,
    staleTime: 15_000,
  });

  const registerMut = useMutation({
    mutationFn: () => {
      if (!selectedSessionId || !selectedSku) throw new Error('MISSING_SELECTION');
      return flashSaleApi.registerItem(selectedSessionId, {
        skuCode: selectedSku,
        flashPrice: Number(flashPrice),
        flashStock: Number(flashStock),
        limitPerUser: Number(limitPerUser) || 1,
      });
    },
    onSuccess: () => {
      setError(null);
      notify.success('Đã gửi sản phẩm vào phiên Flash Sale');
      queryClient.invalidateQueries({ queryKey: ['seller-flash-sale-detail', selectedSessionId] });
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Không thể đăng ký sản phẩm Flash Sale.');
    },
  });

  const submitDisabled =
    !selectedSessionId ||
    !selectedSku ||
    Number(flashPrice) <= 0 ||
    Number(flashStock) <= 0 ||
    registerMut.isPending;

  const sessionItems: FlashSaleItem[] = detailQuery.data?.items ?? [];

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Đăng ký Flash Sale</h1>
        <p className="text-sm text-gray-500 mt-1">Chọn SKU, đặt giá flash và gửi admin duyệt.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 bg-white rounded-2xl border border-gray-100 p-5 h-fit">
          <h2 className="font-bold text-gray-900 mb-4">Thông tin đăng ký</h2>

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Phiên Flash Sale</span>
              <select
                value={selectedSessionId ?? ''}
                onChange={e => setSelectedSessionId(Number(e.target.value))}
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              >
                {upcomingSessions.length === 0 && <option value="">Không có phiên đang nhận đăng ký</option>}
                {upcomingSessions.map(s => (
                  <option key={s.id} value={s.id}>{s.name} - {fmtDateTime(s.startTime)}</option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Sản phẩm</span>
              <select
                value={selectedProductId}
                onChange={e => { setSelectedProductId(e.target.value); setSelectedSku(''); }}
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              >
                {products.length === 0 && <option value="">Chưa có sản phẩm</option>}
                {products.map(p => (
                  <option key={p.productId} value={p.productId}>{p.name}</option>
                ))}
              </select>
            </label>

            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">SKU / biến thể</span>
              <select
                value={selectedSku}
                onChange={e => setSelectedSku(e.target.value)}
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              >
                {variants.length === 0 && <option value="">Sản phẩm chưa có biến thể</option>}
                {variants.map(v => (
                  <option key={v.skuCode} value={v.skuCode}>
                    {v.variantName} - {v.skuCode} - {fmtVnd(v.price)}
                  </option>
                ))}
              </select>
            </label>

            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="block text-sm font-medium text-gray-700 mb-1.5">Giá flash</span>
                <input
                  type="number"
                  min="1"
                  value={flashPrice}
                  onChange={e => setFlashPrice(e.target.value)}
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </label>
              <label className="block">
                <span className="block text-sm font-medium text-gray-700 mb-1.5">Số lượng</span>
                <input
                  type="number"
                  min="1"
                  max={selectedVariant?.stock ?? undefined}
                  value={flashStock}
                  onChange={e => setFlashStock(e.target.value)}
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </label>
            </div>

            <label className="block">
              <span className="block text-sm font-medium text-gray-700 mb-1.5">Giới hạn mỗi khách</span>
              <input
                type="number"
                min="1"
                value={limitPerUser}
                onChange={e => setLimitPerUser(e.target.value)}
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
            </label>

            <button
              onClick={() => registerMut.mutate()}
              disabled={submitDisabled}
              className="w-full py-3 bg-violet-600 hover:bg-violet-700 disabled:bg-gray-300 disabled:text-gray-500 text-white font-semibold rounded-xl transition-colors"
            >
              {registerMut.isPending ? 'Đang gửi...' : 'Gửi admin duyệt'}
            </button>
          </div>
        </div>

        <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="p-5 border-b border-gray-100">
            <h2 className="font-bold text-gray-900">Sản phẩm trong phiên</h2>
            <p className="text-sm text-gray-500 mt-1">
              {detailQuery.data?.name ?? 'Chọn một phiên để xem danh sách đăng ký.'}
            </p>
          </div>

          {sessionsQuery.isLoading || productsQuery.isLoading || detailQuery.isLoading ? (
            <div className="p-5 space-y-3">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
            </div>
          ) : sessionItems.length === 0 ? (
            <div className="p-12 text-center text-gray-500 text-sm">Chưa có sản phẩm nào trong phiên này.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['SKU', 'Giá flash', 'Stock', 'Đã bán', 'Trạng thái'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sessionItems.map(item => (
                    <tr key={item.id} className="border-b border-gray-50">
                      <td className="px-5 py-4">
                        <p className="font-medium text-gray-900">{item.productName || item.skuCode}</p>
                        <p className="text-xs text-gray-400">{item.skuCode}</p>
                      </td>
                      <td className="px-5 py-4">
                        <span className="font-semibold text-red-600">{fmtVnd(item.flashPrice)}</span>
                        {item.originalPrice && <p className="text-xs text-gray-400 line-through">{fmtVnd(item.originalPrice)}</p>}
                      </td>
                      <td className="px-5 py-4 text-gray-700">{item.flashStock}</td>
                      <td className="px-5 py-4 text-gray-700">{item.soldQty}</td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${statusClass(item.status)}`}>
                          {statusLabel(item.status)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
