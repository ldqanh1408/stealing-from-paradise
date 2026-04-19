const STEPS = [
  { step: 1, title: 'Tạo tài khoản Stripe', desc: 'Đăng ký tài khoản Stripe miễn phí để nhận thanh toán', done: false },
  { step: 2, title: 'Xác minh danh tính', desc: 'Cung cấp thông tin cá nhân và giấy tờ tùy thân theo yêu cầu Stripe', done: false },
  { step: 3, title: 'Liên kết tài khoản ngân hàng', desc: 'Kết nối tài khoản ngân hàng Việt Nam để nhận thanh toán', done: false },
  { step: 4, title: 'Kích hoạt bán hàng', desc: 'Stripe phê duyệt tài khoản — bắt đầu nhận tiền từ khách hàng', done: false },
];

export default function StripeOnboardingPage() {
  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Kết nối thanh toán Stripe</h1>
        <p className="text-gray-500 mt-1">Hoàn thành các bước sau để bắt đầu nhận thanh toán từ khách hàng</p>
      </div>

      {/* Stripe info banner */}
      <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-100 rounded-2xl p-6 mb-8 flex items-start gap-4">
        <span className="text-4xl shrink-0">🔒</span>
        <div>
          <h3 className="font-semibold text-gray-900 mb-1">Thanh toán bảo mật với Stripe</h3>
          <p className="text-sm text-gray-600">
            Stripe là nền tảng thanh toán hàng đầu thế giới, được sử dụng bởi hàng triệu doanh nghiệp.
            Mọi giao dịch đều được mã hoá và bảo vệ theo tiêu chuẩn PCI DSS.
          </p>
        </div>
      </div>

      {/* Steps */}
      <div className="space-y-4 mb-8">
        {STEPS.map(({ step, title, desc, done }) => (
          <div key={step} className={`bg-white rounded-2xl border p-5 flex items-start gap-4 transition-all ${done ? 'border-green-200 bg-green-50/30' : 'border-gray-100'}`}>
            <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 font-bold text-sm ${
              done ? 'bg-green-500 text-white' : 'bg-gray-100 text-gray-500'
            }`}>
              {done ? '✓' : step}
            </div>
            <div className="flex-1">
              <h3 className={`font-semibold mb-1 ${done ? 'text-green-800' : 'text-gray-900'}`}>{title}</h3>
              <p className="text-sm text-gray-500">{desc}</p>
            </div>
            {done && (
              <span className="text-xs font-medium text-green-700 bg-green-100 px-2.5 py-1 rounded-full shrink-0">Hoàn thành</span>
            )}
          </div>
        ))}
      </div>

      {/* CTA */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 text-center">
        <h3 className="font-bold text-gray-900 mb-2">Sẵn sàng kết nối?</h3>
        <p className="text-sm text-gray-500 mb-5">
          Nhấn nút bên dưới để bắt đầu quá trình onboarding với Stripe. Thường mất 5–10 phút để hoàn thành.
        </p>
        <button className="px-8 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 text-white font-semibold rounded-xl shadow-sm transition-all">
          Bắt đầu với Stripe →
        </button>
        <p className="text-xs text-gray-400 mt-3">Miễn phí kết nối · Phí giao dịch 2.9% + 30₵</p>
      </div>
    </div>
  );
}
