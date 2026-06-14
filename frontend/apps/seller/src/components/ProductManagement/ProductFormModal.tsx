import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi, type SellerProduct, type SellerVariant } from '@shared/api/seller.api';
import { categoryApi } from '@shared/api/category.api';
import { fmtVnd as fmt } from '@shared/utils/format';
import VariantModal from './VariantModal';
import ImageUploader from './ImageUploader';
import InventoryPanel from './InventoryPanel';
import { notify } from '@shared/lib/toast';

type ProductFormTab = 'info' | 'images' | 'variants' | 'inventory';

export default function ProductFormModal({
  product,
  initialTab = 'info',
  onClose,
  onSuccess,
}: {
  product?: SellerProduct;
  initialTab?: ProductFormTab;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(product?.name ?? '');
  const [description, setDescription] = useState(product?.description ?? '');
  const [category, setCategory] = useState(product?.categoryId ?? '');
  const [images, setImages] = useState<string[]>(product?.images ?? []);
  const [price, setPrice] = useState(product?.price?.toString() ?? '');
  const [stock, setStock] = useState(product?.stockAvailable?.toString() ?? '1');
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current); }, []);
  const [activeTab, setActiveTab] = useState<ProductFormTab>(initialTab);

  useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab, product?.productId]);

  // Categories query
  const { data: categories = [] } = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoryApi.getCategories().then(r => r.data.data ?? []),
  });

  useEffect(() => {
    if (!category && categories.length > 0) {
      setCategory(categories[0].categoryId);
    }
  }, [categories, category]);

  // Variants
  const { data: variants = [] } = useQuery({
    queryKey: ['seller-variants', product?.productId],
    queryFn: () => sellerApi.getVariants(product!.productId).then(r => r.data.data ?? []),
    enabled: !!product?.productId,
  });
  const [showVariant, setShowVariant] = useState<SellerVariant | undefined>(undefined);
  const [showVariantForm, setShowVariantForm] = useState(false);

  const mut = useMutation({
    mutationFn: (data: { name: string; description: string; categoryId: string; images?: string[] }) =>
      product
        ? sellerApi.updateProduct(product.productId, data)
        : sellerApi.createProduct({ ...data, price: Number(price), stock: Number(stock) }),
    onSuccess: (res) => {
      if (!product && res.data.data) {
        // New product — navigate to edit for variants
        queryClient.invalidateQueries({ queryKey: ['seller-products'] });
        setDone(true);
        timerRef.current = setTimeout(() => { onSuccess(); onClose(); }, 1200);
      } else {
        queryClient.invalidateQueries({ queryKey: ['seller-products'] });
        setDone(true);
        timerRef.current = setTimeout(() => { onSuccess(); onClose(); }, 1200);
      }
    },
    onError: (err: any) => setError(err?.response?.data?.message || 'Lưu sản phẩm thất bại'),
  });

  const handleSaveInfo = () => {
    if (!name.trim() || !price) { setError('Vui lòng điền tên và giá sản phẩm.'); return; }
    setError(null);
    mut.mutate({ name: name.trim(), description, categoryId: category, images });
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
          {(['info', 'images', 'variants', 'inventory'] as const).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}>
              {tab === 'info' ? 'Thông tin' : tab === 'images' ? 'Hình ảnh' : tab === 'variants' ? 'Biến thể' : 'Tồn kho'}
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
                  {categories.length === 0 && <option value="">Đang tải danh mục...</option>}
                  {categories.map(c => (
                    <option key={c.categoryId} value={c.categoryId}>
                      {c.name}
                    </option>
                  ))}
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
          <ImageUploader productId={product?.productId ?? 'new'} images={images} onChange={setImages} />
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
                    <div key={v.skuCode} className="flex items-center gap-3 p-2 border border-gray-100 rounded-lg">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{v.variantName}</p>
                        <p className="text-xs text-gray-400">SKU: {v.skuCode} · {fmt(v.price)} · Kho: {v.stock}</p>
                      </div>
                      <button onClick={() => { setShowVariant(v); setShowVariantForm(true); }}
                        className="text-xs text-blue-600 hover:text-blue-700 font-medium shrink-0">Sửa</button>
                      <button onClick={async () => {
                        if (!confirm(`Xóa biến thể "${v.variantName}"?`)) return;
                        try {
                          await sellerApi.deleteVariant(v.variantId);
                          queryClient.invalidateQueries({ queryKey: ['seller-variants', product.productId] });
                        } catch (err: any) {
                          notify.error(err?.response?.data?.message || 'Xóa biến thể thất bại');
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

        {activeTab === 'inventory' && (
          <div>
            {!product ? (
              <div className="text-center py-8 text-gray-400 text-sm">
                Lưu sản phẩm trước để quản lý tồn kho.
              </div>
            ) : (
              <InventoryPanel productId={product.productId} variants={variants} />
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
          productId={product.productId}
          initial={showVariant}
          onClose={() => { setShowVariantForm(false); setShowVariant(undefined); }}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['seller-variants', product.productId] });
            queryClient.invalidateQueries({ queryKey: ['seller-products'] });
          }}
        />
      )}
    </div>
  );
}
