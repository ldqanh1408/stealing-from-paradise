export default function CheckoutPage() {
  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-8">Thanh toán</h1>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Form */}
        <div className="lg:col-span-3 space-y-5">
          {/* Shipping */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center font-bold">1</span>
              Địa chỉ giao hàng
            </h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {[
                { label: 'Họ và tên', placeholder: 'Nguyễn Văn A', span: 2 },
                { label: 'Số điện thoại', placeholder: '0912 345 678', span: 1 },
                { label: 'Email', placeholder: 'email@example.com', span: 1 },
                { label: 'Địa chỉ', placeholder: '123 Đường ABC', span: 2 },
              ].map(({ label, placeholder, span }) => (
                <div key={label} className={span === 2 ? 'sm:col-span-2' : ''}>
                  <label className="block text-sm font-medium text-gray-700 mb-1.5">{label}</label>
                  <input
                    type="text"
                    placeholder={placeholder}
                    className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent placeholder:text-gray-400"
                  />
                </div>
              ))}
            </div>
          </div>

          {/* Payment */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6">
            <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
              <span className="w-6 h-6 rounded-full bg-blue-600 text-white text-xs flex items-center justify-center font-bold">2</span>
              Phương thức thanh toán
            </h2>
            <div className="space-y-3">
              {[
                { id: 'stripe', label: 'Thẻ tín dụng / Visa / Mastercard', icon: '💳', desc: 'Thanh toán an toàn qua Stripe' },
                { id: 'cod', label: 'Thanh toán khi nhận hàng (COD)', icon: '💵', desc: 'Trả tiền mặt khi nhận hàng' },
              ].map(({ id, label, icon, desc }) => (
                <label key={id} className="flex items-center gap-4 p-4 border-2 border-gray-200 rounded-xl cursor-pointer hover:border-blue-300 hover:bg-blue-50/50 transition-all has-[:checked]:border-blue-500 has-[:checked]:bg-blue-50/60">
                  <input type="radio" name="payment" defaultChecked={id === 'stripe'} className="accent-blue-600" />
                  <span className="text-2xl">{icon}</span>
                  <div>
                    <p className="text-sm font-medium text-gray-900">{label}</p>
                    <p className="text-xs text-gray-500">{desc}</p>
                  </div>
                </label>
              ))}
            </div>
          </div>
        </div>

        {/* Order summary */}
        <div className="lg:col-span-2">
          <div className="bg-white rounded-2xl border border-gray-100 p-6 sticky top-24">
            <h2 className="font-bold text-gray-900 mb-4">Đơn hàng</h2>
            <div className="text-center py-8 text-gray-400 text-sm">
              <span className="text-3xl block mb-2">🛒</span>
              Giỏ hàng trống
            </div>
            <div className="h-px bg-gray-100 my-4" />
            <div className="space-y-2 text-sm mb-5">
              <div className="flex justify-between text-gray-600"><span>Tạm tính</span><span>—</span></div>
              <div className="flex justify-between text-gray-600"><span>Phí ship</span><span className="text-green-600">Miễn phí</span></div>
              <div className="flex justify-between font-bold text-gray-900"><span>Tổng</span><span>—</span></div>
            </div>
            <button className="w-full py-3 bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white font-semibold rounded-xl transition-all">
              Đặt hàng
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
