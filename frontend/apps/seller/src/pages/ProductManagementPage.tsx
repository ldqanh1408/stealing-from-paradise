import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@shared/lib/axios';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import type { ApiResponse, PageResponse } from '@shared/types/api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function StatusBadge({ status }: { status: string }) {
  const cfg: Record<string, { bg: string; color: string; label: string }> = {
    DRAFT:      { bg: 'bg-gray-100', color: 'text-gray-600', label: 'Nháp' },
    PENDING:    { bg: 'bg-yellow-100', color: 'text-yellow-700', label: 'Chờ duyệt' },
    APPROVED:   { bg: 'bg-green-100', color: 'text-green-700', label: 'Đã duyệt' },
    REJECTED:   { bg: 'bg-red-100', color: 'text-red-700', label: 'Từ chối' },
    UNPUBLISHED:{ bg: 'bg-blue-100', color: 'text-blue-700', label: 'Đã ẩn' },
    PUBLISHED:  { bg: 'bg-green-100', color: 'text-green-700', label: 'Đang bán' },
  };
  const c = cfg[status] ?? { bg: 'bg-gray-100', color: 'text-gray-600', label: status };
  return <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${c.bg} ${c.color}`}>{c.label}</span>;
}

// ─── Variant Modal ──────────────────────────────────────────────────────────
function VariantModal({
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
  const [sku, setSku] = useState(initial?.sku_code ?? '');
  const [name, setName] = useState(initial?.variant_name ?? '');
  const [price, setPrice] = useState(initial?.price?.toString() ?? '');
  const [stock, setStock] = useState(initial?.stock?.toString() ?? '1');
  const [error, setError] = useState('');
  const [done, setDone] = useState(false);

  const mut = useMutation({
    mutationFn: () => {
      const data = { sku_code: sku.trim(), variant_name: name.trim(), price: Number(price), stock: Number(stock) };
      return initial
        ? sellerApi.updateVariant(initial.sku_code, data)
        : sellerApi.createVariant(productId, data);
    },
    onSuccess: () => { setDone(true); setTimeout(() => { onSuccess(); onClose(); }, 1200); },
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

// ─── Image Upload ───────────────────────────────────────────────────────────
function ImageUploader({ productId, images, onChange }: { productId: string; images: string[]; onChange: (imgs: string[]) => void }) {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');

  const handleUpload = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    setUploading(true);
    setError('');
    try {
      const newImages: string[] = [];
      for (const file of Array.from(files)) {
        if (!file.type.startsWith('image/')) continue;
        const ext = file.name.split('.').pop() ?? 'jpg';
        const { data } = await sellerApi.getPresignedUrl(productId, file.name, file.type);
        await fetch(data.url, { method: 'PUT', body: file, headers: { 'Content-Type': file.type } });
        newImages.push(data.url.split('?')[0]);
      }
      onChange([...images, ...newImages]);
    } catch (e: any) {
      setError('Tải ảnh thất bại. Vui lòng thử lại.');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">Hình ảnh</label>
      <div className="flex flex-wrap gap-2 mb-2">
        {images.map((url, i) => (
          <div key={i} className="relative w-16 h-16 rounded-lg overflow-hidden border border-gray-200 group">
            <img src={url} alt="" className="w-full h-full object-cover" />
            <button
              onClick={() => onChange(images.filter((_, j) => j !== i))}
              className="absolute top-0 right-0 w-5 h-5 bg-red-500 text-white rounded-bl-md text-xs opacity-0 group-hover:opacity-100 transition-opacity"
            >×</button>
          </div>
        ))}
        {uploading && (
          <div className="w-16 h-16 rounded-lg border border-dashed border-gray-300 flex items-center justify-center">
            <div className="w-4 h-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
          </div>
        )}
      </div>
      <label className="cursor-pointer inline-block">
        <span className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs text-gray-600 hover:bg-gray-50 transition-colors">
          + Thêm ảnh
        </span>
        <input type="file" multiple accept="image/*" className="hidden" onChange={e => handleUpload(e.target.files)} />
      </label>
      {error && <p className="text-xs text-red-500 mt-1">{error}</p>}
    </div>
  );
}

// ─── Product Form Modal ─────────────────────────────────────────────────────
function ProductFormModal({
  product,
  onClose,
  onSuccess,
}: {
  product?: SellerProduct;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(product?.name ?? '');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState(product?.category ?? 'electronics');
  const [images, setImages] = useState<string[]>(product?.images ?? []);
  const [price, setPrice] = useState(product?.price?.toString() ?? '');
  const [stock, setStock] = useState(product?.stock_available?.toString() ?? '1');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [activeTab, setActiveTab] = useState<'info' | 'images' | 'variants'>('info');

  // Variants
  const { data: variants = [] } = useQuery({
    queryKey: ['seller-variants', product?.product_id],
    queryFn: () => sellerApi.getVariants(product!.product_id).then(r => r.data.data ?? []),
    enabled: !!product?.product_id,
  });
  const [showVariant, setShowVariant] = useState<SellerVariant | undefined>(undefined);
  const [showVariantForm, setShowVariantForm] = useState(false);

  const mut = useMutation({
    mutationFn: (data: { name: string; description: string; category_id: string; images?: string[] }) =>
      product
        ? apiClient.put<ApiResponse<SellerProduct>>(`/products/${product.product_id}`, data)
        : apiClient.post<ApiResponse<SellerProduct>>('/products', { ...data, price: Number(price), stock: Number(stock) }),
    onSuccess: (res) => {
      if (!product && res.data.data) {
        // New product — navigate to edit for variants
        queryClient.invalidateQueries({ queryKey: ['seller-products'] });
        setDone(true);
        setTimeout(() => { onSuccess(); onClose(); }, 1200);
      } else {
        queryClient.invalidateQueries({ queryKey: ['seller-products'] });
        setDone(true);
        setTimeout(() => { onSuccess(); onClose(); }, 1200);
      }
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Lưu sản phẩm thất bại'),
  });

  const handleSaveInfo = () => {
    if (!name.trim() || !price) { setError('Vui lòng điền tên và giá sản phẩm.'); return; }
    setError(null);
    mut.mutate({ name: name.trim(), description, category_id: category, images });
  };

  if (done) {
    return (
      <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
        <div className="bg-white rounded-2xl p-8 max-w-sm w-full text-center">
          <div className="text-5xl mb-4">✅</div>
          <h3 className="text-lg font-bold text-gray-900 mb-2">
            {product ? 'Cập nhật thành công!' : 'Tạo sản phẩm thành công!'}
          </h3>
          <p className="text-sm text-gray-500">Sản phẩm đã được lưu.</p>
        </div>
      </div>
    );
  }

  const isNew = !product;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-2xl p-6 max-w-lg w-full my-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-bold text-gray-900">
            {product ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}
          </h3>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-2xl leading-none">×</button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-gray-100 mb-4">
          {(['info', 'images', 'variants'] as const).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>
              {tab === 'info' ? 'Thông tin' : tab === 'images' ? 'Hình ảnh' : 'Biến thể'}
            </button>
          ))}
        </div>

        {error && <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{error}</div>}

        {activeTab === 'info' && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên sản phẩm *</label>
              <input type="text" value={name} onChange={e => setName(e.target.value)}
                placeholder="VD: Tai nghe Bluetooth Sony WH-1000XM5"
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Mô tả</label>
              <textarea value={description} onChange={e => setDescription(e.target.value)} rows={3}
                placeholder="Mô tả chi tiết sản phẩm..."
                className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Danh mục</label>
                <select value={category} onChange={e => setCategory(e.target.value)}
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="electronics">Điện tử</option>
                  <option value="fashion">Thời trang</option>
                  <option value="home">Gia dụng</option>
                  <option value="accessories">Phụ kiện</option>
                  <option value="books">Sách</option>
                  <option value="footwear">Giày dép</option>
                  <option value="bags">Túi xách</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Giá (VND) *</label>
                <input type="number" value={price} onChange={e => setPrice(e.target.value)} min="0"
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            </div>
            {isNew && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1.5">Số lượng ban đầu</label>
                <input type="number" value={stock} onChange={e => setStock(e.target.value)} min="0"
                  className="w-full px-3 py-2.5 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
            )}
          </div>
        )}

        {activeTab === 'images' && (
          <ImageUploader productId={product?.product_id ?? 'new'} images={images} onChange={setImages} />
        )}

        {activeTab === 'variants' && (
          <div>
            {!product ? (
              <div className="text-center py-8 text-gray-400 text-sm">
                Lưu sản phẩm trước để thêm biến thể.
              </div>
            ) : (
              <>
                <div className="space-y-2 mb-4 max-h-48 overflow-y-auto">
                  {variants.length === 0 && (
                    <p className="text-sm text-gray-400 text-center py-4">Chưa có biến thể nào.</p>
                  )}
                  {variants.map(v => (
                    <div key={v.sku_code} className="flex items-center gap-3 p-2 border border-gray-100 rounded-lg">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{v.variant_name}</p>
                        <p className="text-xs text-gray-400">SKU: {v.sku_code} · {fmt(v.price)} · Kho: {v.stock}</p>
                      </div>
                      <button onClick={() => { setShowVariant(v); setShowVariantForm(true); }}
                        className="text-xs text-blue-600 hover:text-blue-700 font-medium shrink-0">Sửa</button>
                      <button onClick={() => {
                        if (confirm(`Xóa biến thể "${v.variant_name}"?`)) {
                          sellerApi.deleteVariant(v.sku_code).then(() => {
                            queryClient.invalidateQueries({ queryKey: ['seller-variants', product.product_id] });
                          });
                        }
                      }} className="text-xs text-red-500 hover:text-red-600 font-medium shrink-0">Xoá</button>
                    </div>
                  ))}
                </div>
                <button onClick={() => { setShowVariant(undefined); setShowVariantForm(true); }}
                  className="w-full py-2 border border-dashed border-gray-300 rounded-xl text-sm text-gray-500 hover:border-gray-400 hover:text-gray-600 transition-colors">
                  + Thêm biến thể
                </button>
              </>
            )}
          </div>
        )}

        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          {activeTab === 'info' && (
            <button onClick={handleSaveInfo} disabled={mut.isPending}
              className="flex-1 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              {mut.isPending ? 'Đang lưu...' : product ? 'Cập nhật' : 'Tạo sản phẩm'}
            </button>
          )}
        </div>
      </div>

      {showVariantForm && product && (
        <VariantModal
          productId={product.product_id}
          initial={showVariant}
          onClose={() => { setShowVariantForm(false); setShowVariant(undefined); }}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['seller-variants', product.product_id] });
            queryClient.invalidateQueries({ queryKey: ['seller-products'] });
          }}
        />
      )}
    </div>
  );
}

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

  const { data, isLoading, error } = useQuery({
    queryKey: ['seller-products', statusFilter, page, searchQuery],
    queryFn: () =>
      apiClient.get<ApiResponse<PageResponse<SellerProduct>>>('/sellers/me/products', {
        params: { status: statusFilter || undefined, page, size: 20, search: searchQuery || undefined },
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
        <input type="text" value={searchQuery} onChange={e => handleSearch(e.target.value)}
          placeholder="Tìm sản phẩm..."
          onChange={e => { setSearchQuery(e.target.value); setPage(0); }}
          className="px-4 py-1.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />

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
                    <tr key={p.product_id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-12 h-12 rounded-xl bg-gray-100 flex items-center justify-center text-xl shrink-0 overflow-hidden">
                            {p.images?.[0] ? (
                              <img src={p.images[0]} alt="" className="w-full h-full object-cover" />
                            ) : '📦'}
                          </div>
                          <div>
                            <p className="font-medium text-gray-900 line-clamp-1 max-w-[200px]">{p.name}</p>
                            {p.variants_count > 0 && (
                              <p className="text-xs text-gray-400">{p.variants_count} biến thể</p>
                            )}
                          </div>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-gray-500 capitalize">{p.category || '—'}</td>
                      <td className="px-5 py-4 font-semibold text-gray-900">
                        {p.price ? fmt(p.price) : '—'}
                      </td>
                      <td className="px-5 py-4">
                        <span className={`font-medium ${p.stock_available > 0 ? 'text-green-700' : 'text-red-600'}`}>
                          {p.stock_available}
                        </span>
                      </td>
                      <td className="px-5 py-4"><StatusBadge status={p.status} /></td>
                      <td className="px-5 py-4 text-gray-400 whitespace-nowrap text-xs">
                        {p.created_at ? new Date(p.created_at).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—'}
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex gap-2 flex-wrap">
                          <button onClick={() => handleEdit(p)}
                            className="text-xs text-blue-600 hover:text-blue-700 font-medium">
                            Sửa
                          </button>

                          {/* DRAFT → Submit for review */}
                          {p.status === 'DRAFT' && (
                            <button onClick={() => submitMut.mutate(p.product_id)}
                              disabled={submitMut.isPending}
                              className="text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                              {submitMut.isPending ? '...' : 'Gửi duyệt'}
                            </button>
                          )}

                          {/* APPROVED → Publish */}
                          {(p.status === 'APPROVED' || p.status === 'UNPUBLISHED') && (
                            <button onClick={() => publishMut.mutate(p.product_id)}
                              disabled={publishMut.isPending}
                              className="text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-50">
                              {publishMut.isPending ? '...' : 'Hiển thị'}
                            </button>
                          )}

                          {/* PUBLISHED → Unpublish */}
                          {p.status === 'PUBLISHED' && (
                            <button onClick={() => unpublishMut.mutate(p.product_id)}
                              disabled={unpublishMut.isPending}
                              className="text-xs text-orange-600 hover:text-orange-700 font-medium disabled:opacity-50">
                              {unpublishMut.isPending ? '...' : 'Ẩn'}
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
