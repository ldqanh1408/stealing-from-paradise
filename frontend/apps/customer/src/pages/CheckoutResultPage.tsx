import { Link, useSearchParams } from 'react-router-dom';

export default function CheckoutResultPage() {
  const [params] = useSearchParams();
  const success = params.get('status') !== 'failed';

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
          <p className="text-sm text-gray-400 mb-8">
            Bạn sẽ nhận được email xác nhận trong vài phút tới.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/orders" className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors">
              Xem đơn hàng
            </Link>
            <Link to="/products" className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors">
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
          <p className="text-gray-500 mb-8">
            Đã xảy ra lỗi trong quá trình thanh toán. Vui lòng thử lại hoặc chọn phương thức khác.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 justify-center">
            <Link to="/checkout" className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl transition-colors">
              Thử lại
            </Link>
            <Link to="/cart" className="px-6 py-3 border border-gray-200 hover:border-gray-300 text-gray-700 font-semibold rounded-xl transition-colors">
              Quay lại giỏ hàng
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
