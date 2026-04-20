import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useCartStore } from '@shared/store/cartStore';

// Placeholder product detail - would be fetched from API
const getProductById = (id: string) => {
  const products: Record<string, any> = {
    '1': {
      id: '1',
      sellerId: 1,
      sellerName: 'Shop Sony',
      name: 'Tai nghe Sony WH-1000XM5',
      price: 6_490_000,
      original: 8_990_000,
      category: 'Điện tử',
      description: 'Tai nghe chủ động khử tiếng ồn hàng đầu thế giới. Công nghệ NC (Noise Cancelling) giúp bạn tập trung vào công việc hoặc thư giãn với âm nhạc yêu thích mà không bị phiền nhiễu từ bên ngoài.',
      rating: 4.8,
      reviews: 256,
      stock: 15,
      skuCode: 'SONY-WH-1000XM5-BK',
      images: ['🎧', '🎧', '🎧'],
      features: [
        'Khử tiếng ồn chủ động cấp độ cao',
        'Pin 30 giờ',
        'Kết nối Bluetooth 5.0',
        'Chế độ đeo thoải mái',
        'Hỗ trợ codec LDAC'
      ]
    },
    '2': {
      id: '2',
      sellerId: 2,
      sellerName: 'Shop Keychron',
      name: 'Bàn phím cơ Keychron K2',
      price: 2_190_000,
      original: 2_790_000,
      category: 'Phụ kiện',
      description: 'Bàn phím cơ không dây Keychron K2 với thiết kế nhỏ gọn 75%, hot-swap switch.',
      rating: 4.6,
      reviews: 128,
      stock: 8,
      skuCode: 'KEY-K2-WHITE',
      images: ['⌨️', '⌨️', '⌨️'],
      features: [
        'Layout 75% nhỏ gọn',
        'Hot-swap switch',
        'Pin 160 giờ',
        'Kết nối Bluetooth 5.0',
        'Aluminum frame'
      ]
    },
    // ... thêm các sản phẩm khác tương tự
  };
  return products[id] || products['1'];
};

export default function ProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const product = getProductById(productId || '1');
  const [quantity, setQuantity] = useState(1);
  const [isAdding, setIsAdding] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const { addToCart } = useCartStore();

  const discount = Math.round((1 - product.price / product.original) * 100);
  const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

  const handleAddToCart = async () => {
    if (quantity <= 0) return;
    setIsAdding(true);
    try {
      await addToCart(product.skuCode, quantity);
      setSuccessMessage('Đã thêm vào giỏ hàng thành công!');
      setTimeout(() => {
        navigate('/cart');
      }, 1000);
    } catch (err: any) {
      console.error('Failed:', err);
    } finally {
      setIsAdding(false);
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        {/* Breadcrumb */}
        <div className="mb-8 flex items-center gap-2 text-sm">
          <Link to="/products" className="text-blue-600 hover:underline">Sản phẩm</Link>
          <span className="text-gray-400">/</span>
          <Link to={`/products?category=${product.category}`} className="text-blue-600 hover:underline">
            {product.category}
          </Link>
          <span className="text-gray-400">/</span>
          <span className="text-gray-600">{product.name}</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {/* Product images */}
          <div>
            <div className="bg-white rounded-2xl border border-gray-100 p-8 mb-6">
              <div className="aspect-square bg-gradient-to-br from-gray-100 to-gray-200 rounded-xl flex items-center justify-center text-9xl mb-4">
                {product.images?.[0] || '🛍️'}
              </div>
              <div className="grid grid-cols-3 gap-3">
                {product.images?.map((img: string, i: number) => (
                  <div key={i} className="aspect-square bg-gray-100 rounded-lg flex items-center justify-center text-4xl cursor-pointer hover:bg-gray-200 transition">
                    {img}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Product info */}
          <div>
            {/* Header */}
            <div className="mb-6">
              <div className="flex items-start justify-between mb-3">
                <div>
                  <p className="text-xs text-gray-500 mb-1">{product.category}</p>
                  <h1 className="text-3xl font-bold text-gray-900">{product.name}</h1>
                </div>
                <span className="inline-flex items-center gap-1 px-3 py-1 bg-green-100 text-green-800 text-sm font-semibold rounded-full">
                  -{discount}%
                </span>
              </div>

              {/* Rating */}
              <div className="flex items-center gap-3 mb-4">
                <div className="flex items-center gap-1">
                  {[...Array(5)].map((_, i) => (
                    <span key={i} className="text-lg">
                      {i < Math.floor(product.rating) ? '⭐' : '☆'}
                    </span>
                  ))}
                </div>
                <span className="text-sm text-gray-600">
                  {product.rating} ({product.reviews} đánh giá)
                </span>
              </div>

              {/* Seller */}
              <div className="p-3 bg-blue-50 rounded-lg text-sm mb-4">
                <p className="text-gray-600">Bán bởi <span className="font-semibold text-gray-900">{product.sellerName}</span></p>
              </div>
            </div>

            {/* Price section */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="mb-4">
                <p className="text-xs text-gray-500 mb-2">Giá</p>
                <div className="flex items-baseline gap-3">
                  <span className="text-4xl font-bold text-red-600">{fmt(product.price)}</span>
                  <span className="text-lg text-gray-400 line-through">{fmt(product.original)}</span>
                </div>
              </div>

              <div className="h-px bg-gray-200 my-4" />

              <p className="text-xs text-gray-600 mb-3">
                ✓ Giao hàng miễn phí · ✓ Hỗ trợ 24/7 · ✓ Đổi trả 7 ngày
              </p>
            </div>

            {/* Quantity & Add to cart */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <p className="text-xs text-gray-600 mb-3">
                <span className="text-green-600 font-semibold">{product.stock}</span> sản phẩm có sẵn
              </p>

              <div className="flex items-center gap-4 mb-6">
                <span className="text-sm font-medium text-gray-700">Số lượng:</span>
                <div className="flex items-center border border-gray-200 rounded-lg">
                  <button
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold"
                  >
                    −
                  </button>
                  <span className="w-12 text-center font-semibold">{quantity}</span>
                  <button
                    onClick={() => setQuantity(Math.min(product.stock, quantity + 1))}
                    className="w-10 h-10 flex items-center justify-center hover:bg-gray-100 font-bold"
                  >
                    +
                  </button>
                </div>
              </div>

              <button
                onClick={handleAddToCart}
                disabled={isAdding || product.stock === 0}
                className="w-full py-4 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-bold text-lg rounded-xl transition-all"
              >
                {isAdding ? '⏳ Đang thêm...' : '🛒 Thêm vào giỏ hàng'}
              </button>

              {successMessage && (
                <div className="mt-3 p-3 bg-green-100 text-green-800 text-sm rounded-lg text-center font-semibold">
                  ✓ {successMessage}
                </div>
              )}
            </div>

            {/* Features */}
            <div className="bg-white rounded-2xl border border-gray-100 p-6">
              <h3 className="font-bold text-gray-900 mb-4">Đặc điểm nổi bật</h3>
              <ul className="space-y-3">
                {product.features?.map((feature: string, i: number) => (
                  <li key={i} className="flex items-start gap-3 text-sm">
                    <span className="text-green-600 font-bold">✓</span>
                    <span className="text-gray-700">{feature}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        {/* Description */}
        <div className="mt-12 bg-white rounded-2xl border border-gray-100 p-8">
          <h2 className="text-2xl font-bold text-gray-900 mb-4">Mô tả sản phẩm</h2>
          <p className="text-gray-700 leading-relaxed">{product.description}</p>
        </div>
      </div>
    </div>
  );
}

