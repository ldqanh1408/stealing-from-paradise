import { useEffect } from 'react';
import { Link, useSearchParams, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { paymentApi } from '@shared/api/payment.api';
import { useCartStore } from '@shared/store/cartStore';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

export default function CheckoutResultPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const { clearCart } = useCartStore();
  const status = params.get('status');
  const success = status !== 'failed';
  const locationState = location.state as { parentOrderId?: number; paymentIntentId?: string; method?: string; error?: string } | null;
  const parentOrderId = locationState?.parentOrderId;

  useEffect(() => {
    if (success && parentOrderId) {
      clearCart();
    }
  }, [success, parentOrderId, clearCart]);

  const { data: paymentData } = useQuery({
    queryKey: ['payment', parentOrderId],
    queryFn: () => paymentApi.getPayment(parentOrderId!).then(r => r.data.data),
    enabled: !!parentOrderId,
    refetchInterval: (query) => {
      const payment = query.state.data;
      if (!payment) return 2000;
      if (payment.status === 'SUCCESS' || payment.status === 'FAILED') return false;
      return 2000;
    },
  });

  return (
    <div className="max-w-lg mx-auto px-4 py-20 text-center">
      {success ? (
        <>
          <div className="w-24 h-24 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            ✅
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Đặt hàng thành công!</h1>
          <p className="text-gray-500 mb-2">
            Cảm ơn bạn đã mua hàng. Đơn hàng của bạn đang được xử lý.
          </p>
          {locationState?.method === 'COD' && (
            <div className="bg-yellow-50 border border-yellow-200 rounded-xl p-4 mb-6 text-left text-sm text-yellow-800">
              <strong>Thanh toán khi nhận hàng (COD)</strong>
              <p className="mt-1 text-yellow-700">
                Bạn sẽ thanh toán khi nhận được hàng. Vui lòng giữ liên lạc để nhận hàng đúng hạn.
              </p>
            </div>
          )}

          {paymentData && (
            <div className="bg-gray-50 rounded-xl p-4 mb-6 text-left">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <p className="text-gray-500 text-xs">Mã giao dịch</p>
                  <p className="font-medium text-gray-700 font-mono text-xs">{paymentData.trans_ref}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Số tiền</p>
                  <p className="font-bold text-gray-900">{fmt(paymentData.amount)}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Phương thức</p>
                  <p className="font-medium text-gray-700">{paymentData.method}</p>
                </div>
                <div>
                  <p className="text-gray-500 text-xs">Trạng thái</p>
                  <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                    paymentData.status === 'SUCCESS'
                      ? 'bg-green-100 text-green-700'
                      : paymentData.status === 'PENDING'
                        ? 'bg-yellow-100 text-yellow-700'
                        : 'bg-red-100 text-red-700'
                  }`}>
                    {paymentData.status === 'SUCCESS' ? 'Thành công' :
                     paymentData.status === 'PENDING' ? 'Đang xử lý' : 'Thất bại'}
                  </span>
                </div>
                {paymentData.paid_at && (
                  <div>
                    <p className="text-gray-500 text-xs">Thanh toán lúc</p>
                    <p className="font-medium text-gray-700 text-xs">
                      {new Date(paymentData.paid_at).toLocaleString('vi-VN')}
                    </p>
                  </div>
                )}
              </div>
            </div>
          )}

          {!paymentData && parentOrderId && (
            <div className="bg-gray-50 rounded-xl p-4 mb-6 text-center">
              <p className="text-sm text-gray-500">Đang tải thông tin thanh toán...</p>
            </div>
          )}

          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            {parentOrderId && (
              <Link
                to={`/orders/${parentOrderId}`}
                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
              >
                Xem đơn hàng
              </Link>
            )}
            <Link
              to="/products"
              className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors"
            >
              Tiếp tục mua sắm
            </Link>
          </div>
        </>
      ) : (
        <>
          <div className="w-24 h-24 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-6 text-5xl">
            ❌
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">Thanh toán thất bại</h1>
          <p className="text-gray-500 mb-2">
            {locationState?.error || 'Đã xảy ra lỗi trong quá trình thanh toán.'}
          </p>
          <p className="text-gray-400 text-sm mb-8">
            Đơn hàng của bạn vẫn được lưu với trạng thái "Chờ thanh toán". Bạn có thể thử lại.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link
              to="/checkout"
              className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors"
            >
              Thử lại thanh toán
            </Link>
            <Link
              to="/cart"
              className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors"
            >
              Quay lại giỏ hàng
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
