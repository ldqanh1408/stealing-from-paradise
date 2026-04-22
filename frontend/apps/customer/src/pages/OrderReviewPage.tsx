import { useEffect, useState } from 'react';
import { useNavigate, Link, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useCartStore } from '@shared/store/cartStore';
import { orderApi, type CheckoutResponse } from '@shared/api/order.api';
import { addressApi, type UserAddress } from '@shared/api/address.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function OrderReviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { cart } = useCartStore();

  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [orderData, setOrderData] = useState<CheckoutResponse | null>(null);
  const [step, setStep] = useState<'address' | 'review' | 'payment'>('address');
  const [paymentMethod, setPaymentMethod] = useState<'stripe' | 'cod'>('stripe');
  const [isProcessing, setIsProcessing] = useState(false);

  const selectedItemIds = (location.state?.selectedItemIds || []) as number[];

  // Fetch addresses from API — show mock addresses when backend unavailable
  const { data: addresses = [], isLoading: addrsLoading } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => addressApi.list().then(r => r.data.data ?? []),
    retry: 1,
    initialData: [],
  });

  useEffect(() => {
    if (addresses.length > 0 && !selectedAddressId) {
      const def = addresses.find(a => a.is_default);
      setSelectedAddressId(def?.address_id ?? addresses[0].address_id);
    }
  }, [addresses, selectedAddressId]);

  const handleCreateOrder = async () => {
    if (!selectedAddressId || selectedItemIds.length === 0) return;
    setIsProcessing(true);
    try {
      const { data } = await orderApi.checkout({
        address_id: selectedAddressId,
        item_ids: selectedItemIds,
      });
      if (data.data) {
        setOrderData(data.data);
        setStep('review');
      }
    } catch (err: any) {
      alert(err?.response?.data?.message || 'Lỗi tạo đơn hàng');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleProceedToPayment = async () => {
    if (!orderData) return;
    setIsProcessing(true);
    try {
      if (paymentMethod === 'cod') {
        navigate('/checkout/result?status=success', {
          state: { parentOrderId: orderData.parent_order_id },
        });
      } else {
        navigate('/checkout/payment', {
          state: { orderData, parentOrderId: orderData.parent_order_id },
        });
      }
    } catch {
      alert('Lỗi xử lý thanh toán');
    } finally {
      setIsProcessing(false);
    }
  };

  const getSelectedItemsData = () => {
    if (!cart || selectedItemIds.length === 0) return [];
    const items: any[] = [];
    cart.sellers.forEach(seller => {
      seller.items.forEach(item => {
        if (selectedItemIds.includes(item.cart_item_id)) {
          items.push({ ...item, seller_name: seller.seller_name });
        }
      });
    });
    return items;
  };

  const selectedItems = getSelectedItemsData();
  const subtotal = selectedItems.reduce((sum, item) => sum + item.unit_price * item.quantity, 0);
  const selectedAddress = addresses.find(a => a.address_id === selectedAddressId);

  return (
    <div className="bg-gray-50 min-h-screen py-8">
      <div className="max-w-5xl mx-auto px-4 sm:px-6">
        {/* Step indicator */}
        <div className="mb-8 flex items-center justify-between">
          {[
            { id: 'address', label: 'Địa chỉ' },
            { id: 'review', label: 'Xem lại' },
            { id: 'payment', label: 'Thanh toán' },
          ].map((s, i, arr) => {
            const currentIdx = ['address', 'review', 'payment'].indexOf(step);
            return (
              <div key={s.id} className="flex items-center flex-1">
                <div
                  className={`w-10 h-10 rounded-full flex items-center justify-center font-bold text-sm shrink-0 ${
                    step === s.id
                      ? 'bg-blue-600 text-white'
                      : currentIdx > i
                        ? 'bg-green-600 text-white'
                        : 'bg-gray-200 text-gray-600'
                  }`}
                >
                  {currentIdx > i ? '✓' : i + 1}
                </div>
                <span className="ml-2 font-medium text-gray-900 hidden sm:inline">{s.label}</span>
                {i < arr.length - 1 && (
                  <div className="flex-1 h-1 mx-2 bg-gray-200 ml-4" />
                )}
              </div>
            );
          })}
        </div>

        {step === 'address' && (
          <div className="max-w-3xl">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">Chọn địa chỉ giao hàng</h2>

            {addrsLoading && (
              <div className="text-center py-12 text-gray-400">
                <div className="text-3xl mb-2">⏳</div>
                Đang tải địa chỉ...
              </div>
            )}

            {!addrsLoading && addresses.length === 0 && (
              <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-6 text-center">
                <p className="text-yellow-700 mb-3">Bạn chưa có địa chỉ giao hàng.</p>
                <Link
                  to="/products"
                  className="inline-block px-4 py-2 bg-blue-600 text-white text-sm rounded-xl hover:bg-blue-700"
                >
                  Tiếp tục mua sắm để thêm địa chỉ
                </Link>
              </div>
            )}

            {!addrsLoading && addresses.length > 0 && (
              <div className="space-y-3 mb-8">
                {addresses.map(addr => (
                  <label
                    key={addr.address_id}
                    className={`flex items-start p-4 border-2 rounded-xl cursor-pointer transition-all has-[:checked]:border-blue-500 has-[:checked]:bg-blue-50 ${
                      addr.address_id === selectedAddressId ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={selectedAddressId === addr.address_id}
                      onChange={() => setSelectedAddressId(addr.address_id)}
                      className="w-5 h-5 mt-1 accent-blue-600 shrink-0"
                    />
                    <div className="ml-4 flex-1">
                      <p className="font-semibold text-gray-900">{addr.full_address}</p>
                      {addr.is_default && (
                        <span className="inline-block mt-2 px-2 py-1 bg-green-100 text-green-800 text-xs rounded-full font-medium">
                          Địa chỉ mặc định
                        </span>
                      )}
                    </div>
                  </label>
                ))}
              </div>
            )}

            {/* Order items summary */}
            {selectedItems.length > 0 && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <h3 className="font-bold text-gray-900 mb-4">Sản phẩm cần giao ({selectedItems.length} sản phẩm)</h3>
                <div className="space-y-3">
                  {selectedItems.map(item => (
                    <div key={item.cart_item_id} className="flex items-center justify-between pb-3 border-b last:border-b-0">
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-gray-900 truncate">{item.product_name}</p>
                        <p className="text-xs text-gray-500">{item.variant_name} × {item.quantity}</p>
                        <p className="text-xs text-gray-500">{item.seller_name}</p>
                      </div>
                      <p className="font-semibold text-gray-900 ml-4">{fmt(item.unit_price * item.quantity)}</p>
                    </div>
                  ))}
                </div>
                <div className="mt-4 pt-4 border-t space-y-2">
                  <div className="flex justify-between text-gray-600">
                    <span>Tạm tính</span>
                    <span>{fmt(subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-gray-600">
                    <span>Phí vận chuyển</span>
                    <span className="text-green-600 font-medium">Miễn phí</span>
                  </div>
                  <div className="flex justify-between font-bold text-base">
                    <span>Tổng cộng</span>
                    <span className="text-red-600">{fmt(subtotal)}</span>
                  </div>
                </div>
              </div>
            )}

            <button
              onClick={handleCreateOrder}
              disabled={isProcessing || !selectedAddressId || selectedItemIds.length === 0}
              className="w-full py-4 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 text-white font-bold rounded-xl transition-colors"
            >
              {isProcessing ? '⏳ Đang xử lý...' : 'Tiếp tục'}
            </button>
          </div>
        )}

        {step === 'review' && orderData && (
          <div className="max-w-3xl">
            <h2 className="text-2xl font-bold text-gray-900 mb-6">Xem lại đơn hàng</h2>

            {selectedAddress && (
              <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
                <h3 className="font-bold text-gray-900 mb-2">📍 Địa chỉ giao hàng</h3>
                <p className="text-gray-700">{selectedAddress.full_address}</p>
              </div>
            )}

            <div className="space-y-6 mb-6">
              {orderData.orders.map(order => (
                <div key={order.order_id} className="bg-white rounded-2xl border border-gray-100 p-6">
                  <div className="flex items-center justify-between mb-4 pb-4 border-b">
                    <div>
                      <p className="font-bold text-gray-900">{order.seller_name}</p>
                      <p className="text-xs text-gray-500 font-mono">{order.order_code}</p>
                    </div>
                    <p className="font-bold text-lg">{fmt(order.final_amt)}</p>
                  </div>
                  <p className="text-sm text-gray-700">
                    <span className="font-semibold text-yellow-600">Chờ xác nhận</span> · {order.item_count} sản phẩm
                  </p>
                </div>
              ))}
            </div>

            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <div className="space-y-3 text-sm mb-4">
                <div className="flex justify-between text-gray-600">
                  <span>Tạm tính</span>
                  <span>{fmt(orderData.total_amount)}</span>
                </div>
                {orderData.loyalty_discount ? (
                  <div className="flex justify-between text-gray-600">
                    <span>Giảm từ điểm thưởng</span>
                    <span className="text-green-600 font-medium">-{fmt(orderData.loyalty_discount)}</span>
                  </div>
                ) : null}
                <div className="flex justify-between text-gray-600">
                  <span>Phí vận chuyển</span>
                  <span className="text-green-600 font-medium">Miễn phí</span>
                </div>
                <div className="h-px bg-gray-100" />
                <div className="flex justify-between font-bold text-base">
                  <span>Tổng thanh toán</span>
                  <span className="text-red-600 text-lg">{fmt(orderData.final_amount)}</span>
                </div>
              </div>
            </div>

            <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
              <h3 className="font-bold text-gray-900 mb-4">Phương thức thanh toán</h3>
              <div className="space-y-3">
                {[
                  { id: 'stripe', label: 'Thẻ tín dụng / Visa / Mastercard', icon: '💳', desc: 'Thanh toán an toàn qua Stripe' },
                  { id: 'cod', label: 'Thanh toán khi nhận hàng (COD)', icon: '💵', desc: 'Trả tiền mặt khi nhận hàng' },
                ].map(({ id, label, icon, desc }) => (
                  <label
                    key={id}
                    className={`flex items-center gap-4 p-4 border-2 rounded-xl cursor-pointer transition-all ${
                      paymentMethod === id ? 'border-blue-500 bg-blue-50' : 'border-gray-200 hover:border-blue-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="payment"
                      value={id}
                      checked={paymentMethod === id}
                      onChange={e => setPaymentMethod(e.target.value as 'stripe' | 'cod')}
                      className="accent-blue-600 shrink-0"
                    />
                    <span className="text-2xl">{icon}</span>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{label}</p>
                      <p className="text-xs text-gray-500">{desc}</p>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            <button
              onClick={handleProceedToPayment}
              disabled={isProcessing}
              className="w-full py-4 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-bold rounded-xl transition-all"
            >
              {isProcessing ? '⏳ Đang xử lý...' : `Thanh toán ${fmt(orderData.final_amount)}`}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
