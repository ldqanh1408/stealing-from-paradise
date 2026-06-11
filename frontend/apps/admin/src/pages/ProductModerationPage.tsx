import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, type PendingProduct } from '@shared/api/admin.api';
import { fmtVnd } from '@shared/utils/format';
import RejectProductModal from '@/components/ProductModeration/RejectProductModal';

const fmt = (n: number) => fmtVnd(n);

const TAB_STATUS = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Chờ duyệt lại' },
];

const STATUS_COLORS: Record<string, string> = {
  PENDING:  'bg-yellow-100 text-yellow-700',
  APPROVED: 'bg-green-100 text-green-700',
  REJECTED: 'bg-red-100 text-red-700',
};

export default function ProductModerationPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('PENDING');
  const [page, setPage] = useState(0);
  const [rejectProduct, setRejectProduct] = useState<PendingProduct | null>(null);
  const [approveProduct, setApproveProduct] = useState<PendingProduct | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-pending-products', tab, page],
    queryFn: () =>
      adminApi.getPendingProducts({ page, size: 20 }).then(r => r.data.data),
    retry: 1,
  });

  const approveMut = useMutation({
    mutationFn: (productId: string) => adminApi.approveProduct(productId),
    onSuccess: () => {
      setApproveProduct(null);
      queryClient.invalidateQueries({ queryKey: ['admin-pending-products'] });
    },
    onError: () => {},
  });

  const pendingProducts: PendingProduct[] = (data?.content ?? []).filter(
    (p: PendingProduct) => tab === 'PENDING' ? p.status === 'PENDING' : p.status === tab
  );
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Duyệt sản phẩm</h1>
          <p className="text-sm text-gray-500 mt-1">Kiểm duyệt sản phẩm mới từ người bán</p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-pending-products'] })}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-5">
        {TAB_STATUS.map(t => (
          <button
            key={t.value}
            onClick={() => { setTab(t.value); setPage(0); }}
            className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
              tab === t.value ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>
          Đang tải...
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải danh sách sản phẩm.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && pendingProducts.length === 0 && (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <span className="text-5xl block mb-4">✅</span>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">
            {tab === 'PENDING' ? 'Không có sản phẩm nào chờ duyệt' : 'Không có sản phẩm nào'}
          </h3>
          <p className="text-sm text-gray-500">
            {tab === 'PENDING' ? 'Tất cả sản phẩm đã được kiểm duyệt.' : 'Không có sản phẩm nào trong danh mục này.'}
          </p>
        </div>
      )}

      {/* List */}
      {!isLoading && !error && pendingProducts.length > 0 && (
        <>
          <div className="space-y-4">
            {pendingProducts.map(p => (
              <div key={p.productId} className="bg-white rounded-2xl border border-gray-100 p-5 flex items-start gap-4 hover:shadow-sm transition-shadow">
                <div className="w-16 h-16 rounded-xl bg-gray-100 flex items-center justify-center text-2xl shrink-0 overflow-hidden">
                  {p.images?.[0] ? (
                    <img src={p.images[0]} alt="" className="w-full h-full object-cover" />
                  ) : '🛍️'}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <h3 className="font-semibold text-gray-900">{p.name}</h3>
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_COLORS[p.status] ?? 'bg-gray-100 text-gray-600'}`}>
                      {p.status === 'PENDING' ? 'Chờ duyệt' : p.status === 'APPROVED' ? 'Đã duyệt' : 'Từ chối'}
                    </span>
                  </div>
                  <p className="text-sm text-gray-500">
                    Người bán: #{p.sellerId} {p.sellerName && `(${p.sellerName})`}
                    {p.category && ` · ${p.category}`}
                  </p>
                  {p.price && <p className="font-bold text-gray-900 mt-1">{fmt(p.price)}</p>}
                  {p.description && (
                    <p className="text-sm text-gray-600 mt-1 line-clamp-2">{p.description}</p>
                  )}
                </div>
                {p.status === 'PENDING' && (
                  <div className="flex flex-col gap-2 shrink-0">
                    <button
                      onClick={() => setApproveProduct(p)}
                      className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white text-sm font-medium rounded-xl transition-colors whitespace-nowrap"
                    >
                      ✓ Duyệt
                    </button>
                    <button
                      onClick={() => setRejectProduct(p)}
                      className="px-4 py-2 bg-red-50 hover:bg-red-100 text-red-600 text-sm font-medium rounded-xl transition-colors border border-red-200 whitespace-nowrap"
                    >
                      ✗ Từ chối
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                ← Trước
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">Trang {page + 1} / {totalPages}</span>
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                Sau →
              </button>
            </div>
          )}
        </>
      )}

      {/* Approve confirmation */}
      {approveProduct && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
            <div className="text-5xl mb-4">✅</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Duyệt sản phẩm?</h3>
            <p className="text-sm text-gray-500 mb-6">
              "<strong>{approveProduct.name}</strong>" sẽ được phép bán trên nền tảng.
            </p>
            <div className="flex gap-3">
              <button onClick={() => setApproveProduct(null)} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
              <button
                onClick={() => approveMut.mutate(approveProduct.productId)}
                disabled={approveMut.isPending}
                className="flex-1 py-2.5 bg-green-600 text-white rounded-xl text-sm font-medium hover:bg-green-700 disabled:opacity-50"
              >
                {approveMut.isPending ? '...' : 'Duyệt'}
              </button>
            </div>
          </div>
        </div>
      )}

      {rejectProduct && (
        <RejectProductModal
          product={rejectProduct}
          onClose={() => setRejectProduct(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-pending-products'] })}
        />
      )}
    </div>
  );
}
