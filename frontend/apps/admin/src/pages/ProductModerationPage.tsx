import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, type PendingProduct } from '@shared/api/admin.api';
import RejectProductModal from '@/components/ProductModeration/RejectProductModal';
import ProductModerationTabs from '@/components/ProductModeration/ProductModerationTabs';
import ProductModerationList from '@/components/ProductModeration/ProductModerationList';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import Pagination from '@shared/components/Pagination';

const getErrMsg = (err: unknown) =>
  (err as { response?: { data?: { message?: string } } })?.response?.data?.message
  ?? 'Có lỗi xảy ra, vui lòng thử lại.';

export default function ProductModerationPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('PENDING');
  const [page, setPage] = useState(0);
  const [rejectProduct, setRejectProduct] = useState<PendingProduct | null>(null);
  const [approveProduct, setApproveProduct] = useState<PendingProduct | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-pending-products', tab, page],
    queryFn: () =>
      adminApi.getPendingProducts({ page, size: 20, status: tab }).then(r => {
        const res = r.data.data;
        if (!res) {
          return {
            content: [],
            totalElements: 0,
            totalPages: 0,
            last: true,
          };
        }
        return {
          ...res,
          content: (res.content || []).map((p: any) => ({
            ...p,
            productId: p.id,
            category: p.categoryName,
            status: tab,
          })),
        };
      }),
    retry: 1,
  });

  const approveMut = useMutation({
    mutationFn: (productId: string) => adminApi.approveProduct(productId),
    onSuccess: () => {
      setApproveProduct(null);
      setActionError(null);
      queryClient.invalidateQueries({ queryKey: ['admin-pending-products'] });
    },
    onError: (err: unknown) => setActionError(getErrMsg(err)),
  });

  const pendingProducts: PendingProduct[] = data?.content ?? [];
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
      <ProductModerationTabs
        tab={tab}
        onTabChange={(t) => { setTab(t); setPage(0); }}
      />

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
          <ProductModerationList
            pendingProducts={pendingProducts}
            onApprove={(p) => { setActionError(null); setApproveProduct(p); }}
            onReject={setRejectProduct}
          />

          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      {/* Approve confirmation */}
      {approveProduct && (
        <ConfirmDialog
          title="Duyệt sản phẩm?"
          message={`"${approveProduct.name}" sẽ được phép bán trên nền tảng.`}
          confirmLabel="Duyệt"
          danger={false}
          loading={approveMut.isPending}
          onConfirm={() => approveMut.mutate(approveProduct.productId)}
          onCancel={() => setApproveProduct(null)}
        />
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
