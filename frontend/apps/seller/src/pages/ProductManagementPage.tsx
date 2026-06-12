import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@shared/lib/axios';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import type { ApiResponse, PageResponse } from '@shared/types/api';
import { fmtVnd } from '@shared/utils/format';
import VariantModal from '@/components/ProductManagement/VariantModal';
import ProductFormModal from '@/components/ProductManagement/ProductFormModal';
import { ProductStatusBadge } from '@/lib/productStatus';
import { canSubmit, canPublish, canUnpublish, canDelete } from '@/lib/productActions';

const fmt = (n: number) => fmtVnd(n);

// ─── Main Page ─────────────────────────────────────────────────────────────
const STATUS_TABS = [
  { value: '', label: 'Tất cả' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'PUBLISHED', label: 'Đang bán' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'DRAFT', label: 'Nháp' },
];

export default function ProductManagementPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [editProduct, setEditProduct] = useState<SellerProduct | undefined>(undefined);
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchQuery), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-products', statusFilter, page, debouncedSearch],
    queryFn: () =>
      apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
        params: { status: statusFilter || undefined, page, size: 20, search: debouncedSearch || undefined },
      }).then(r => r.data.data),
    retry: 1,
  });

  const products: SellerProduct[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  // Action mutations
  const submitMut = useMutation({ mutationFn: sellerApi.submitForReview, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const publishMut = useMutation({ mutationFn: sellerApi.publishProduct, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const unpublishMut = useMutation({ mutationFn: sellerApi.unpublishProduct, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });
  const deleteMut = useMutation({ mutationFn: sellerApi.deleteProduct, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['seller-products'] }) });

  const handleEdit = (product: SellerProduct) => {
    setEditProduct(product);
    setShowForm(true);
  };

  const handleAdd = () => {
    setEditProduct(undefined);
    setShowForm(true);
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Sản phẩm của tôi</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && <span>{totalElements} sản phẩm</span>}
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => queryClient.invalidateQueries({ queryKey: ['seller-products'] })}
            className="px-3 py-2 border rounded-xl text-sm text-gray-600 hover:bg-gray-50">
            🔄 Làm mới
          </button>
          <button onClick={handleAdd}
            className="flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-colors shadow-sm">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            Thêm sản phẩm
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="flex gap-4 mb-5 flex-wrap">
        <div className="flex gap-2 flex-wrap">
          {STATUS_TABS.map(tab => (
            <button key={tab.value} onClick={() => { setStatusFilter(tab.value); setPage(0); }}
              className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
                statusFilter === tab.value
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
              }`}>
              {tab.label}
            </button>
          ))}
        </div>
        <input type="text" value={searchQuery} onChange={e => { setSearchQuery(e.target.value); setPage(0); }}
          placeholder="Tìm sản phẩm..."
          className="px-4 py-1.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="bg-white rounded-2xl border border-gray-100 p-8 text-center text-gray-400">
          ⏳ Đang tải sản phẩm...
        </div>
      )}

      {/* Error */}
      {error && !isLoading && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải sản phẩm. Vui lòng thử lại.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && products.length === 0 && (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <div className="w-20 h-20 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-5 text-4xl">📦</div>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Chưa có sản phẩm nào</h3>
          <p className="text-gray-500 text-sm mb-6 max-w-sm mx-auto">
            Hãy thêm sản phẩm đầu tiên để bắt đầu bán hàng trên nền tảng
          </p>
          <button onClick={handleAdd}
            className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm rounded-xl transition-colors">
            Thêm sản phẩm đầu tiên
          </button>
        </div>
      )}

      {/* Table */}
      {!isLoading && !error && products.length > 0 && (
        <>
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['Sản phẩm', 'Danh mục', 'Giá', 'Tồn kho', 'Trạng thái', 'Ngày tạo', 'Thao tác'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {products.map(p => (
                    <tr key={p.productId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-12 h-12 rounded-xl bg-gray-100 flex items-center justify-center text-xl shrink-0 overflow-hidden">
                            {p.images?.[0] ? (
                              <img src={p.images[0]} alt="" className="w-full h-full object-cover" />
                            ) : '📦'}
                          </div>
                          <div>
                            <p className="font-medium text-gray-900 line-clamp-1 max-w-[200px]">{p.name}</p>
                            {p.variantsCount > 0 && (
                              <p className="text-xs text-gray-400">{p.variantsCount} biến thể</p>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-gray-500 capitalize">{p.categoryName || p.category || '—'}</td>
                      <td className="px-5 py-4 font-semibold text-gray-900">
                        {p.price ? fmt(p.price) : '—'}
                      </td>
                      <td className="px-5 py-4">
                        <span className={`font-medium ${p.stockAvailable > 0 ? 'text-green-700' : 'text-red-600'}`}>
                          {p.stockAvailable}
                        </span>
                      </td>
                      <td className="px-5 py-4"><ProductStatusBadge status={p.status} /></td>
                      <td className="px-5 py-4 text-gray-400 whitespace-nowrap text-xs">
                        {p.createdAt ? new Date(p.createdAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—'}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex gap-2 flex-wrap">
                          <button onClick={() => handleEdit(p)}
                            className="text-xs text-blue-600 hover:text-blue-700 font-medium">
                            Sửa
                          </button>

                          {/* DRAFT → Submit for review (UC-PRODUCT-003) */}
                          {canSubmit(p.status) && (
                            <button onClick={() => submitMut.mutate(p.productId)}
                              disabled={submitMut.isPending}
                              className="text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                              {submitMut.isPending ? '...' : 'Gửi duyệt'}
                            </button>
                          )}

                          {/* APPROVED / UNPUBLISHED → Publish (UC-PRODUCT-014) */}
                          {canPublish(p.status) && (
                            <button onClick={() => publishMut.mutate(p.productId)}
                              disabled={publishMut.isPending}
                              className="text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                              {publishMut.isPending ? '...' : 'Hiển thị'}
                            </button>
                          )}

                          {/* PUBLISHED → Unpublish */}
                          {canUnpublish(p.status) && (
                            <button onClick={() => unpublishMut.mutate(p.productId)}
                              disabled={unpublishMut.isPending}
                              className="text-xs text-orange-600 hover:text-orange-700 font-medium disabled:opacity-50">
                              {unpublishMut.isPending ? '...' : 'Ẩn'}
                            </button>
                          )}

                          {/* Delete (DRAFT or REJECTED only) */}
                          {canDelete(p.status) && (
                            <button onClick={() => {
                              if (confirm(`Xóa sản phẩm "${p.name}"? Hành động này không thể hoàn tác.`)) {
                                deleteMut.mutate(p.productId);
                              }
                            }}
                              disabled={deleteMut.isPending}
                              className="text-xs text-red-500 hover:text-red-600 font-medium disabled:opacity-50">
                              {deleteMut.isPending ? '...' : 'Xóa'}
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                ← Trước
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">
                Trang {page + 1} / {totalPages}
              </span>
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1}
                className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                Sau →
              </button>
            </div>
          )}
        </>
      )}

      {/* Form modal */}
      {showForm && (
        <ProductFormModal
          product={editProduct}
          onClose={() => setShowForm(false)}
          onSuccess={() => {}}
        />
      )}
    </div>
  );
}
