import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useCartStore } from '@shared/store/cartStore';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function isFlashExpired(iso?: string | null) {
  if (!iso) return false;
  return new Date(iso).getTime() < Date.now();
}

export default function CartPage() {
  const { cart, isLoading, fetchCart, updateQuantity, removeFromCart } = useCartStore();
  const [selectedItems, setSelectedItems] = useState<Set<number>>(new Set());

  useEffect(() => {
    fetchCart();
  }, [fetchCart]);

  const getTotal = () => {
    if (!selectedItems.size || !cart?.sellers) return 0;
    let total = 0;
    cart.sellers.forEach(seller => {
      seller.items.forEach(item => {
        if (selectedItems.has(item.cart_item_id)) {
          total += item.unit_price * item.quantity;
        }
      });
    });
    return total;
  };

  const getSelectedCount = () => selectedItems.size;

  const toggleItemSelection = (itemId: number) => {
    const newSelected = new Set(selectedItems);
    if (newSelected.has(itemId)) {
      newSelected.delete(itemId);
    } else {
      newSelected.add(itemId);
    }
    setSelectedItems(newSelected);
  };

  const selectAllItems = () => {
    if (!cart?.sellers) return;
    if (selectedItems.size === getItemCount()) {
      setSelectedItems(new Set());
    } else {
      const all = new Set<number>();
      cart.sellers.forEach(seller => {
        seller.items.forEach(item => {
          all.add(item.cart_item_id);
        });
      });
      setSelectedItems(all);
    }
  };

  const getItemCount = () => {
    if (!cart?.sellers) return 0;
    return cart.sellers.reduce((sum, seller) => sum + seller.items.length, 0);
  };

  if (isLoading && !cart) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <p className="text-gray-500">Đang tải giỏ hàng...</p>
      </div>
    );
  }

  if (!cart?.sellers || getItemCount() === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <div className="w-24 h-24 rounded-full bg-blue-50 flex items-center justify-center mx-auto mb-6 text-5xl">
          🛒
        </div>
        <h2 className="text-2xl font-bold text-gray-900 mb-2">Giỏ hàng trống</h2>
        <p className="text-gray-500 mb-8">Bạn chưa thêm sản phẩm nào vào giỏ hàng</p>
        <Link
          to="/products"
          className="inline-flex items-center gap-2 px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Tiếp tục mua sắm
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">
        Giỏ hàng ({getItemCount()} sản phẩm)
      </h1>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Items */}
        <div className="lg:col-span-2 space-y-4">
          {/* Select all header */}
          <div className="bg-white rounded-2xl border border-gray-100 p-4 flex items-center gap-3">
            <input
              type="checkbox"
              checked={selectedItems.size === getItemCount() && getItemCount() > 0}
              onChange={selectAllItems}
              className="w-5 h-5 accent-blue-600 cursor-pointer"
            />
            <span className="font-semibold text-gray-900">
              Chọn tất cả ({getItemCount()})
            </span>
          </div>

          {/* Sellers groups */}
          {cart?.sellers.map((seller) => (
            <div key={seller.seller_id} className="bg-white rounded-2xl border border-gray-100 p-4">
              <p className="text-sm font-semibold text-gray-900 mb-4 pb-4 border-b">
                {seller.seller_name}
              </p>
              <div className="space-y-3">
                {seller.items.map((item) => {
                    const isExpired = item.is_flash && isFlashExpired(item.flash_expires_at);
                    const overLimit = item.max_quantity_per_user && item.quantity > item.max_quantity_per_user;
                    const overStock = item.quantity > item.stock_available;

                    return (
                      <div key={item.cart_item_id} className={`flex items-center gap-4 p-3 rounded-xl border transition-colors ${
                        isExpired ? 'border-red-200 bg-red-50/30' : item.is_flash ? 'border-orange-200 bg-orange-50/20' : 'border-gray-100'
                      }`}>
                        <input
                          type="checkbox"
                          checked={selectedItems.has(item.cart_item_id)}
                          onChange={() => toggleItemSelection(item.cart_item_id)}
                          className="w-5 h-5 accent-blue-600 cursor-pointer shrink-0"
                        />
                        <div className="w-20 h-20 rounded-xl bg-gray-100 flex items-center justify-center text-3xl shrink-0 overflow-hidden">
                          🛍️
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <p className="text-xs text-gray-400">{item.variant_name}</p>
                            {item.is_flash && (
                              <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${isExpired ? 'bg-red-100 text-red-600' : 'bg-orange-100 text-orange-600'}`}>
                                🔥 Flash Sale
                              </span>
                            )}
                            {isExpired && (
                              <span className="text-xs px-1.5 py-0.5 rounded-full bg-red-100 text-red-600 font-medium">Đã hết hạn</span>
                            )}
                          </div>
                          <h3 className="font-medium text-gray-900 truncate">{item.product_name}</h3>
                          {item.is_flash && item.flash_price ? (
                            <div className="flex items-center gap-2 mt-0.5">
                              <p className="text-red-600 font-bold">{fmt(item.flash_price)}</p>
                              {item.unit_price !== item.flash_price && (
                                <p className="text-xs text-gray-400 line-through">{fmt(item.unit_price)}</p>
                              )}
                            </div>
                          ) : (
                            <p className="text-red-600 font-bold mt-1">{fmt(item.unit_price)}</p>
                          )}
                          <p className="text-xs text-gray-500 mt-0.5">
                            Kho: <span className={`font-semibold ${overStock ? 'text-red-600' : 'text-green-600'}`}>{item.stock_available}</span>
                            {item.max_quantity_per_user && (
                              <span className="ml-2 text-orange-500">· Mua tối đa: {item.max_quantity_per_user}</span>
                            )}
                          </p>
                          {overLimit && (
                            <p className="text-xs text-red-500 mt-0.5">
                              ⚠️ Số lượng vượt quá giới hạn mua ({item.max_quantity_per_user})
                            </p>
                          )}
                          {overStock && !overLimit && (
                            <p className="text-xs text-red-500 mt-0.5">
                              ⚠️ Số lượng vượt quá tồn kho ({item.stock_available})
                            </p>
                          )}
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <button
                            onClick={() => updateQuantity(item.cart_item_id, Math.max(1, item.quantity - 1))}
                            className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 text-gray-600 font-bold"
                          >
                            −
                          </button>
                          <span className={`w-8 text-center text-sm font-medium ${overLimit || overStock ? 'text-red-600' : 'text-gray-900'}`}>{item.quantity}</span>
                          <button
                            onClick={() => {
                              const max = item.max_quantity_per_user
                                ? Math.min(item.max_quantity_per_user, item.stock_available)
                                : item.stock_available;
                              updateQuantity(item.cart_item_id, item.quantity + 1 > max ? item.quantity : item.quantity + 1);
                            }}
                            className="w-8 h-8 rounded-lg border border-gray-200 flex items-center justify-center hover:bg-gray-50 text-gray-600 font-bold"
                          >
                            +
                          </button>
                        </div>
                        <button
                          onClick={() => removeFromCart(item.cart_item_id)}
                          className="text-gray-300 hover:text-red-400 transition-colors shrink-0"
                        >
                          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                        </button>
                      </div>
                    );
                  })}
              </div>
            </div>
          ))}
        </div>

        {/* Summary */}
        <div className="bg-white rounded-2xl border border-gray-100 p-6 h-fit sticky top-24">
          <h3 className="font-bold text-gray-900 mb-4">Tóm tắt đơn hàng</h3>
          <div className="space-y-3 text-sm mb-6">
            <div className="flex justify-between text-gray-600">
              <span>Tạm tính</span>
              <span className="font-semibold">{fmt(getTotal())}</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Phí vận chuyển</span>
              <span className="text-green-600 font-medium">Miễn phí</span>
            </div>
            <div className="flex justify-between text-gray-600">
              <span>Giảm giá</span>
              <span>—</span>
            </div>
            <div className="h-px bg-gray-100" />
            <div className="flex justify-between font-bold text-gray-900 text-base">
              <span>Tổng cộng</span>
              <span className="text-red-600">{fmt(getTotal())}</span>
            </div>
          </div>

          {selectedItems.size > 0 ? (
            <Link
              to="/checkout"
              state={{ selectedItemIds: Array.from(selectedItems) }}
              className="block w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white font-semibold text-center rounded-xl transition-all"
            >
              Thanh toán ({selectedItems.size} mục)
            </Link>
          ) : (
            <button
              disabled
              className="w-full py-3 bg-gray-300 text-gray-500 font-semibold rounded-xl cursor-not-allowed"
            >
              Chọn sản phẩm để thanh toán
            </button>
          )}

          <Link
            to="/products"
            className="block w-full mt-3 py-2 border border-gray-200 text-gray-700 font-semibold text-center rounded-xl hover:border-gray-300 transition-all"
          >
            Tiếp tục mua sắm
          </Link>
        </div>
      </div>
    </div>
  );
}
