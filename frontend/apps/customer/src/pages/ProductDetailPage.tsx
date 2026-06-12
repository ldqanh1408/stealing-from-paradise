import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { productApi, type ProductDetail } from '@shared/api/product.api';
import { addressApi } from '@shared/api/address.api';
import { cartApi } from '@shared/api/cart.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { addToCart } = useCartStore();
  const [selectedVariant, setSelectedVariant] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);
  const [isBuyNow, setIsBuyNow] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [addError, setAddError] = useState<string | null>(null);
  const navTimerRef = useRef<ReturnType<typeof setTimeout>>();
  useEffect(() => () => { if (navTimerRef.current) clearTimeout(navTimerRef.current); }, []);

  const { data: product, isLoading, error } = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productApi.getProductById(productId!).then(r => r.data.data),
    enabled: !!productId,
    retry: 1,
  });

  const { data: addresses = [] } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => addressApi.list().then(r => r.data.data ?? []),
    retry: 1,
  });

  const disc = product && product.price && product.originalPrice
    ? Math.round((1 - product.price / product.originalPrice) * 100)
    : null;

  const handleAddToCart = async () => {
    if (!product || !product.variants?.length) return;
    setIsAdding(true);
    setAddError(null);
    setSuccessMsg(null);
    try {
      const variant = product.variants[selectedVariant];
      await addToCart(variant.variantId, quantity, (product as any).fsItemId);
      setSuccessMsg('Đã thêm vào giỏ hàng!');
      navTimerRef.current = setTimeout(() => navigate('/cart'), 1200);
    } catch (err: any) {
      setAddError(err?.response?.data?.message || err?.response?.data?.error || 'Không thể thêm vào giỏ hàng.');
    } finally {
      setIsAdding(false);
    }
  };

  const handleBuyNow = async () => {
    if (!product || !product.variants?.length) return;
    setIsBuyNow(true);
    setAddError(null);
    setSuccessMsg(null);
    try {
      const addr = addresses.find((a: any) => a.isDefault) ?? addresses[0];
      if (!addr) {
        setAddError('Vui lòng thêm địa chỉ giao hàng trước khi mua hàng.');
        setIsBuyNow(false);
        return;
      }

      const variant = product.variants[selectedVariant];
      if (variant.stock <= 0) {
        setAddError('Sản phẩm đã hết hàng.');
        setIsBuyNow(false);
        return;
      }

      const fsItemId = (product as any).fsItemId;

      // Add item to cart, then find it in the updated cart by variantId
      await cartApi.addItem(variant.variantId, quantity, fsItemId);
      const { data: cartRes } = await cartApi.getCart();
      const cartData = cartRes?.data;

      const newestItem = (cartData?.sellers ?? [])
        .flatMap(s => s?.items ?? [])
        .find(item => item?.variantId === variant.variantId);

      if (!newestItem?.cartItemId) {
        throw new Error('Không tìm thấy sản phẩm trong giỏ hàng');
      }

      // Go to the checkout review flow (preview → submit)
      navigate('/checkout', {
        state: { selectedItemIds: [newestItem.cartItemId] },
      });
    } catch (err: any) {
      setAddError(err?.response?.data?.message || err?.response?.data?.error || err.message || 'Mua ngay thất bại.');
    } finally {
      setIsBuyNow(false);
    }
  };

  if (!productId) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500">ID sản phẩm không hợp lệ.</p>
        <Link to="/products" className="text-blue-600 hover:underline mt-2 inline-block">← Quay lại</Link>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="bg-gray-50 min-h-screen py-8">
        <div className="max-w-5xl mx-auto px-4 sm:px-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 animate-pulse">
            <div className="bg-white rounded-2xl border p-8">
              <div className="aspect-square bg-gray-200 rounded-xl" />
            </div>
            <div className="space-y-4">
              <div className="h-8 bg-gray-200 rounded w-3/4" />
              <div className="h-4 bg-gray-200 rounded w-1/2" />
              <div className="h-12 bg-gray-200 rounded w-1/3" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không tìm thấy sản phẩm.</p>
        <Link to="/products" className="text-blue-600 hover:underline">← Quay lại danh sách</Link>
      </div>
    );
  }

  const price = product.price ?? 0;
  const original = product.originalPrice ?? price;
  const selectedVariantData = product.variants?.[selectedVariant];
  const maxQty = selectedVariantData?.stock ?? 0;

  return (
    <div className="bg-gray-50 min-h-screen py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        {/* Breadcrumb */}
        <div className="mb-8 flex items-center gap-2 text-sm flex-wrap">
          <Link to="/products" className="text-blue-600 hover:underline">Sản phẩm</Link>
          <span className="text-gray-400">/</span>
          {product.categoryName && (
            <>
              <Link to={`/products?category=${product.categoryName}`} className="text-blue-600 hover:underline">
                {product.categoryName}
              </Link>
              <span className="text-gray-400">/</span>
            </>
          )}
          <span className="text-gray-600 line-clamp-1">{product.name}</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Images */}
          <div>
            <div className="bg-white rounded-2xl border border-gray-100 p-8 mb-6">
              <div className="aspect-square bg-gradient-to-br from-gray-100 to-gray-200 rounded-xl flex items-center justify-center text-8xl overflow-hidden">
                {product.images?.[0] ? (
                  <img src={product.images[0]} alt={product.name} className="w-full h-full object-contain" />
                ) : '🛍️'}
              </div>
              {product.images && product.images.length > 1 && (
                <div className="grid grid-cols-4 gap-3 mt-4">
                  {product.images.map((img, i) => (
                    <button key={i} className="aspect-square bg-gray-100 rounded-lg overflow-hidden hover:ring-2 hover:ring-blue-500 transition-all">
                      <img src={img} alt="" className="w-full h-full object-cover" />
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Product info */}
          <div>
            {/* Header */}
            <div className="mb-6">
              {product.isFlash && (
                <span className="inline-block px-3 py-1 bg-red-100 text-red-700 text-xs font-bold rounded-full mb-3">
                  ⚡ FLASH SALE
                </span>
              )}
              <h1 className="text-3xl font-bold text-gray-900 mb-2">{product.name}</h1>
              {product.sellerName && (
                <p className="text-sm text-gray-500">
                  Bán bởi <span className="font-semibold text-gray-700">{product.sellerName}</span>
                </p>
              )}
            </div>

            {/* Price */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="flex items-baseline gap-3 mb-3">
                <span className="text-4xl font-bold text-red-600">{fmt(price)}</span>
                {disc && (
                  <>
                    <span className="text-xl text-gray-400 line-through">{fmt(original)}</span>
                    <span className="text-sm font-semibold text-green-600 bg-green-50 px-2 py-1 rounded-lg">
                      -{disc}%
                    </span>
                  </>
                )}
              </div>
              <div className="flex items-center gap-2 text-sm text-gray-600">
                <span className="text-green-600 font-semibold">✓ Còn hàng</span>
                <span className="text-gray-300">·</span>
                <span>{product.stockAvailable} sản phẩm</span>
              </div>
            </div>

            {/* Variants */}
            {product.variants && product.variants.length > 0 && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <h3 className="text-sm font-semibold text-gray-700 mb-3">Phân loại</h3>
                <div className="flex flex-wrap gap-2">
                  {product.variants.map((v, i) => (
                    <button
                      key={v.skuCode}
                      onClick={() => setSelectedVariant(i)}
                      className={`px-4 py-2 rounded-xl text-sm font-medium border transition-all ${
                        selectedVariant === i
                          ? 'border-blue-500 bg-blue-50 text-blue-700'
                          : 'border-gray-200 text-gray-700 hover:border-blue-300'
                      } ${v.stock <= 0 ? 'opacity-40 cursor-not-allowed' : ''}`}
                      disabled={v.stock <= 0}
                    >
                      {v.variantName}
                      {v.stock <= 5 && v.stock > 0 && (
                        <span className="ml-1 text-xs text-orange-500">(còn {v.stock})</span>
                      )}
                      {v.stock <= 0 && <span className="ml-1 text-xs text-gray-400">(hết)</span>}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Quantity & Add */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="flex items-center gap-4 mb-4">
                <span className="text-sm font-medium text-gray-700">Số lượng:</span>
                <div className="flex items-center border border-gray-200 rounded-lg">
                  <button
                    onClick={() => setQuantity(q => Math.max(1, q - 1))}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold text-lg"
                  >
                    −
                  </button>
                  <span className="w-14 text-center font-semibold text-lg">{quantity}</span>
                  <button
                    onClick={() => setQuantity(q => Math.min(maxQty, q + 1))}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold text-lg"
                  >
                    +
                  </button>
                </div>
                <span className="text-sm text-gray-500">
                  (tối đa {maxQty})
                </span>
              </div>

              <button
                onClick={handleAddToCart}
                disabled={isAdding || isBuyNow || maxQty <= 0}
                className="flex-1 py-4 bg-white border-2 border-blue-600 hover:bg-blue-50 disabled:from-gray-400 disabled:to-gray-400 disabled:border-gray-300 text-blue-600 font-bold text-lg rounded-xl transition-all"
              >
                {isAdding ? '⏳ Đang thêm...' : '🛒 Thêm vào giỏ hàng'}
              </button>

              <button
                onClick={handleBuyNow}
                disabled={isAdding || isBuyNow || maxQty <= 0}
                className="flex-1 py-4 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-bold text-lg rounded-xl transition-all"
              >
                {isBuyNow ? '⏳ Đang xử lý...' : '⚡ Mua ngay'}
              </button>

              {successMsg && (
                <div className="mt-3 p-3 bg-green-100 text-green-800 text-sm rounded-lg text-center font-semibold">
                  ✓ {successMsg}
                </div>
              )}
              {addError && (
                <div className="mt-3 p-3 bg-red-50 text-red-700 text-sm rounded-lg text-center">
                  {addError}
                </div>
              )}
            </div>

            {/* Badges */}
            <div className="flex flex-col gap-2 text-sm text-gray-600">
              <div className="flex items-center gap-2">
                <span>✓</span> Miễn phí giao hàng cho đơn từ 500K
              </div>
              <div className="flex items-center gap-2">
                <span>✓</span> Đổi trả trong 7 ngày
              </div>
              <div className="flex items-center gap-2">
                <span>✓</span> Hỗ trợ 24/7
              </div>
              {product.isFlash && (
                <div className="flex items-center gap-2">
                  <span>⚡</span> Sản phẩm flash sale — giá đặc biệt
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Description */}
        {product.description && (
          <div className="mt-12 bg-white rounded-2xl border border-gray-100 p-8">
            <h2 className="text-2xl font-bold text-gray-900 mb-4">Mô tả sản phẩm</h2>
            <p className="text-gray-700 leading-relaxed whitespace-pre-line">{product.description}</p>
          </div>
        )}
      </div>
    </div>
  );
}
