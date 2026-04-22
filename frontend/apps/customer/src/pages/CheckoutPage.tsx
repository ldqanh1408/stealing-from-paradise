import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { paymentApi } from '@shared/api/payment.api';
import type { CheckoutResponse } from '@shared/api/order.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function CheckoutPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number | null>(null);

  const orderData = (location.state?.orderData as CheckoutResponse) || null;
  const parentOrderId = (location.state?.parentOrderId as number) || orderData?.parent_order_id;

  // Get PaymentIntent client secret from backend
  const { data: clientSecretData, isLoading: secretLoading } = useQuery({
    queryKey: ['client-secret', parentOrderId],
    queryFn: () => paymentApi.getClientSecret(parentOrderId!).then(r => r.data.data),
    enabled: !!parentOrderId && !!orderData,
  });

  useEffect(() => {
    if (!orderData) {
      navigate('/cart');
      return;
    }
  }, [orderData, navigate]);

  // Payment timeout countdown
  useEffect(() => {
    if (!orderData?.timeout_at) return;
    const target = new Date(orderData.timeout_at).getTime();
    const tick = () => {
      const remaining = Math.max(0, Math.floor((target - Date.now()) / 1000));
      setCountdown(remaining);
      if (remaining === 0) {
        navigate('/checkout/result?status=failed', { state: { error: 'Hết thời gian thanh toán' } });
      }
    };
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [orderData?.timeout_at, navigate]);

  const handlePayment = async () => {
    if (!clientSecretData) return;
    setIsProcessing(true);
    setError(null);

    try {
      // In a real implementation, you would use Stripe.js here:
      // const stripe = await loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY);
      // const result = await stripe.confirmCardPayment(clientSecretData.client_secret, {
      //   payment_method: { card: cardElement }
      // });
      // if (result.error) throw new Error(result.error.message);

      // For testing purposes, we simulate a successful payment
      // when the backend returns a valid client secret
      await new Promise(resolve => setTimeout(resolve, 2000));
      navigate('/checkout/result?status=success', {
        state: { parentOrderId },
      });
    } catch (err: any) {
      setError(err?.message || 'Lỗi xử lý thanh toán');
      navigate('/checkout/result?status=failed', {
        state: { error: err?.message },
      });
    } finally {
      setIsProcessing(false);
    }
  };

  if (!orderData) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <p className="text-gray-500">Đang chuyển hướng...</p>
      </div>
    );
  }

  const minutes = countdown !== null ? Math.floor(countdown / 60) : null;
  const seconds = countdown !== null ? countdown % 60 : null;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-2">Xác nhận thanh toán</h1>

      {/* Countdown */}
      {countdown !== null && countdown > 0 && (
        <div className="mb-6 flex items-center gap-2">
          <span className="text-sm text-gray-500">Thanh toán trong:</span>
          <span className={`text-sm font-bold ${countdown < 60 ? 'text-red-600' : 'text-gray-900'}`}>
            {minutes}:{seconds!.toString().padStart(2, '0')}
          </span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Payment form */}
        <div className="lg:col-span-3 space-y-5">
          {error && (
            <div className="bg-red-50 border border-red-200 rounded-2xl p-4">
              <p className="text-red-700 text-sm">{error}</p>
            </div>
          )}

          {/* Stripe Card Element placeholder */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center font-bold">
                💳
              </span>
              Thông tin thẻ tín dụng
            </h2>
            {secretLoading ? (
              <div className="space-y-4 animate-pulse">
                <div className="h-10 bg-gray-100 rounded-xl" />
                <div className="grid grid-cols-2 gap-4">
                  <div className="h-10 bg-gray-100 rounded-xl" />
                  <div className="h-10 bg-gray-100 rounded-xl" />
                </div>
              </div>
            ) : (
              <>
                <div className="p-4 border border-gray-200 rounded-xl bg-gray-50 text-sm text-gray-500 mb-4">
                  {clientSecretData
                    ? `PaymentIntent: ${clientSecretData.client_secret.slice(0, 20)}... (demo mode)`
                    : 'Đang kết nối với Stripe...'}
                </div>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1.5">Số thẻ</label>
                    <input
                      type="text"
                      placeholder="1234 5678 9012 3456"
                      className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-gray-400"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">MM/YY</label>
                      <input
                        type="text"
                        placeholder="12/25"
                        className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-gray-400"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1.5">CVC</label>
                      <input
                        type="text"
                        placeholder="123"
                        className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 placeholder:text-gray-400"
                      />
                    </div>
                  </div>
                </div>
                <p className="text-xs text-gray-400 mt-3">
                  Thử nghiệm: Dùng thẻ test Stripe (4242 4242 4242 4242)
                </p>
              </>
            )}
          </div>
        </div>

        {/* Order summary */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-2xl border border-gray-100 p-6 sticky top-24">
            <h2 className="font-bold text-gray-900 mb-4">📋 Đơn hàng</h2>

            <div className="space-y-3 mb-4 pb-4 border-b">
              {orderData.orders.map(order => (
                <div key={order.order_id} className="text-sm">
                  <p className="font-medium text-gray-900">{order.seller_name}</p>
                  <p className="text-xs text-gray-500 font-mono">{order.order_code}</p>
                  <p className="font-bold text-red-600 mt-1">{fmt(order.final_amt)}</p>
                </div>
              ))}
            </div>

            <div className="space-y-2 text-sm mb-5">
              <div className="flex justify-between text-gray-600">
                <span>Tạm tính</span>
                <span>{fmt(orderData.total_amount)}</span>
              </div>
              {orderData.loyalty_discount ? (
                <div className="flex justify-between text-gray-600">
                  <span>Giảm điểm</span>
                  <span className="text-green-600">-{fmt(orderData.loyalty_discount)}</span>
                </div>
              ) : null}
              <div className="flex justify-between text-gray-600">
                <span>Phí ship</span>
                <span className="text-green-600">Miễn phí</span>
              </div>
              <div className="h-px bg-gray-100" />
              <div className="flex justify-between font-bold text-gray-900 text-base">
                <span>Tổng</span>
                <span className="text-red-600">{fmt(orderData.final_amount)}</span>
              </div>
            </div>

            <button
              onClick={handlePayment}
              disabled={isProcessing || secretLoading || !clientSecretData}
              className="w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-semibold rounded-xl transition-all"
            >
              {isProcessing
                ? '⏳ Đang xử lý...'
                : secretLoading
                  ? '⏳ Đang kết nối...'
                  : `Thanh toán ${fmt(orderData.final_amount)}`}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
