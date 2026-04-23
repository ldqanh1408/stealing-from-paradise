import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@shared/store/cartStore';

const PLACEHOLDER_PRODUCTS = [
  { id: '1', name: 'Tai nghe Sony WH-1000XM5', price: 6_490_000, original: 8_990_000, category: 'Điện tử', badge: 'HOT', skuCode: 'SONY-WH-1000XM5-BK' },
  { id: '2', name: 'Bàn phím cơ Keychron K2', price: 2_190_000, original: 2_790_000, category: 'Phụ kiện', badge: null, skuCode: 'KEY-K2-WHITE' },
  { id: '3', name: 'Áo thun Uniqlo DRY-EX', price: 299_000, original: 499_000, category: 'Thời trang', badge: 'MỚI', skuCode: 'UQ-TSHIRT-M' },
  { id: '4', name: 'Sách Clean Code', price: 189_000, original: 250_000, category: 'Sách', badge: null, skuCode: 'BOOK-CLEAN-CODE' },
  { id: '5', name: 'Giày Nike Air Max', price: 2_890_000, original: 3_500_000, category: 'Giày dép', badge: 'SALE', skuCode: 'NIKE-AIRMAX-10' },
  { id: '6', name: 'Máy pha cà phê Delonghi', price: 4_200_000, original: 5_600_000, category: 'Gia dụng', badge: null, skuCode: 'DELONGHI-COFFEE' },
  { id: '7', name: 'Đồng hồ Casio G-Shock', price: 3_100_000, original: 3_900_000, category: 'Phụ kiện', badge: 'HOT', skuCode: 'CASIO-GSHOCK' },
  { id: '8', name: 'Túi xách da thật', price: 1_490_000, original: 2_200_000, category: 'Túi xách', badge: null, skuCode: 'BAG-LEATHER-BK' },
];

const CATEGORIES = ['Tất cả', 'Điện tử', 'Thời trang', 'Gia dụng', 'Phụ kiện', 'Sách', 'Giày dép', 'Túi xách'];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';
const discount = (orig: number, price: number) => Math.round((1 - price / orig) * 100);

export default function ProductListPage() {
  const navigate = useNavigate();
  const [selectedCategory, setSelectedCategory] = useState('Tất cả');
  const [addingToCart, setAddingToCart] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const { addToCart } = useCartStore();

  const filteredProducts = selectedCategory === 'Tất cả'
    ? PLACEHOLDER_PRODUCTS
    : PLACEHOLDER_PRODUCTS.filter(p => p.category === selectedCategory);

  const handleAddToCart = async (product: typeof PLACEHOLDER_PRODUCTS[0], e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setAddingToCart(product.id);
    setErrorMsg(null);
    try {
      await addToCart(product.skuCode, 1);
      navigate('/cart');
    } catch (err: any) {
      setErrorMsg(err?.response?.data?.message || 'Không thể thêm vào giỏ hàng. Vui lòng đăng nhập.');
    } finally {
      setAddingToCart(null);
    }
  };

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Hero banner */}
      <div className="bg-gradient-to-r from-blue-600 to-violet-700 text-white py-12 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <p className="text-blue-200 text-sm font-medium uppercase tracking-widest mb-2">Khám phá ngay</p>
          <h1 className="text-4xl sm:text-5xl font-bold mb-4">Hàng ngàn sản phẩm</h1>
          <p className="text-blue-100 text-lg mb-8">Giá tốt nhất, giao hàng nhanh nhất toàn quốc</p>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {errorMsg && (
          <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
            {errorMsg}
          </div>
        )}

        {/* Category filter */}
        <div className="flex gap-2 overflow-x-auto pb-2 mb-6 scrollbar-none">
          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`shrink-0 px-4 py-2 rounded-full text-sm font-medium border transition-all ${
                selectedCategory === cat
                  ? 'bg-blue-600 text-white border-blue-600'
                  : 'bg-white text-gray-700 border-gray-200 hover:border-blue-300 hover:text-blue-600'
              }`}
            >
              {cat}
            </button>
          ))}
        </div>

        {/* Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {filteredProducts.map((p) => (
            <div key={p.id} className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 group">
              {/* Image placeholder */}
              <Link to={`/products/${p.id}`}>
                <div className="relative bg-gradient-to-br from-gray-100 to-gray-200 aspect-square flex items-center justify-center">
                  <span className="text-4xl">🛍️</span>
                  {p.badge && (
                    <span className={`absolute top-2 left-2 px-2 py-0.5 rounded-full text-xs font-bold text-white ${
                      p.badge === 'HOT' ? 'bg-red-500' :
                      p.badge === 'MỚI' ? 'bg-green-500' : 'bg-orange-500'
                    }`}>{p.badge}</span>
                  )}
                  <span className="absolute top-2 right-2 bg-white/90 text-green-700 font-bold text-xs px-2 py-0.5 rounded-full">
                    -{discount(p.original, p.price)}%
                  </span>
                </div>
              </Link>
              <div className="p-3">
                <p className="text-xs text-gray-400 mb-1">{p.category}</p>
                <Link to={`/products/${p.id}`}>
                  <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-blue-600 transition-colors">
                    {p.name}
                  </h3>
                </Link>
                <div className="flex items-baseline gap-2 mb-1">
                  <span className="text-base font-bold text-red-600">{fmt(p.price)}</span>
                </div>
                <p className="text-xs text-gray-400 line-through mb-3">{fmt(p.original)}</p>
                <div className="flex gap-2">
                  <Link
                    to={`/products/${p.id}`}
                    className="flex-1 py-2 border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold rounded-xl transition-colors text-center"
                  >
                    Chi tiết
                  </Link>
                  <button
                    onClick={(e) => handleAddToCart(p, e)}
                    disabled={addingToCart === p.id}
                    className="flex-1 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white text-xs font-semibold rounded-xl transition-colors flex items-center justify-center gap-1"
                  >
                    {addingToCart === p.id ? (
                      <svg className="animate-spin h-3 w-3" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                      </svg>
                    ) : '➕'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {filteredProducts.length === 0 && (
          <div className="text-center py-20">
            <p className="text-gray-500">Không có sản phẩm nào trong danh mục này.</p>
          </div>
        )}

        {/* Load more */}
        <div className="text-center mt-10">
          <button className="px-8 py-3 border-2 border-gray-200 text-gray-700 font-medium rounded-xl hover:border-blue-300 hover:text-blue-600 transition-all">
            Xem thêm sản phẩm
          </button>
        </div>
      </div>
    </div>
  );
}
