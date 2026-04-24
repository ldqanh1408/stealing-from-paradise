import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi } from '@shared/api/seller.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

type OnboardingStatus = 'NOT_STARTED' | 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';

const STATUS_STEPS: { key: OnboardingStatus; title: string; desc: string }[] = [
  { key: 'NOT_STARTED',  title: 'Tạo tài khoản Stripe', desc: 'Đăng ký tài khoản Stripe miễn phí để nhận thanh toán', done: false },
  { key: 'IN_PROGRESS',  title: 'Xác minh danh tính', desc: 'Cung cấp thông tin cá nhân và giấy tờ tùy thân theo yêu cầu Stripe', done: false },
  { key: 'COMPLETE',     title: 'Liên kết tài khoản ngân hàng', desc: 'Kết nối tài khoản ngân hàng Việt Nam để nhận thanh toán', done: false },
  { key: 'COMPLETE',     title: 'Kích hoạt bán hàng', desc: 'Stripe phê duyệt tài khoản — bắt đầu nhận tiền từ khách hàng', done: false },
];

function getStepState(
  stepKey: OnboardingStatus,
  status: OnboardingStatus
): 'completed' | 'active' | 'pending' {
  const statusOrder: OnboardingStatus[] = ['NOT_STARTED', 'PENDING', 'IN_PROGRESS', 'COMPLETE', 'SUSPENDED'];
  const statusIdx = statusOrder.indexOf(status);
  const stepIdx = statusOrder.indexOf(stepKey === 'COMPLETE' ? 'COMPLETE' : stepKey === 'IN_PROGRESS' ? 'IN_PROGRESS' : stepKey);

  if (status === 'COMPLETE') return stepKey === 'COMPLETE' ? 'active' : 'completed';
  if (statusIdx >= 2 && stepKey !== 'COMPLETE') return 'completed';
  if (stepKey === 'NOT_STARTED') return 'active';
  return 'pending';
}

export default function StripeOnboardingPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const { data: status, isLoading } = useQuery({
    queryKey: ['stripe-onboarding-status'],
    queryFn: () => sellerApi.getStripeStatus().then(r => r.data.data),
    retry: 1,
  });

  const startMut = useMutation({
    mutationFn: () => sellerApi.startStripeOnboarding(),
    onSuccess: (res) => {
      const url = res.data.data?.onboarding_url;
      if (url) {
        window.open(url, '_blank', 'noopener,noreferrer');
        queryClient.invalidateQueries({ queryKey: ['stripe-onboarding-status'] });
      }
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Không thể khởi tạo Stripe. Vui lòng thử lại.');
    },
  });

  const refreshMut = useMutation({
    mutationFn: () => sellerApi.refreshStripeLink(),
    onSuccess: (res) => {
      const url = res.data.data?.onboarding_url;
      if (url) window.open(url, '_blank', 'noopener,noreferrer');
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || 'Không thể tạo liên kết. Vui lòng thử lại.');
    },
  });

  const currentStatus: OnboardingStatus = status?.status ?? 'NOT_STARTED';
  const isComplete = currentStatus === 'COMPLETE';

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Kết nối thanh toán Stripe</h1>
        <p className="text-gray-500 mt-1">Hoàn thành các bước sau để bắt đầu nhận thanh toán từ khách hàng</p>
      </div>

      {/* Status badge */}
      <div className={`rounded-2xl border p-4 mb-8 flex items-center gap-3 ${
        isComplete
          ? 'bg-green-50 border-green-200'
          : 'bg-indigo-50 border-indigo-100'
      }`}>
        {isComplete ? (
          <>
            <span className="text-2xl">✅</span>
            <div>
              <p className="font-semibold text-green-900">Tài khoản Stripe đã kích hoạt</p>
              <p className="text-sm text-green-700">Bạn có thể nhận thanh toán từ khách hàng ngay bây giờ.</p>
            </div>
          </>
        ) : (
          <>
            <span className="text-2xl">🔒</span>
            <div>
              <p className="font-semibold text-indigo-900">Chưa kết nối Stripe</p>
              <p className="text-sm text-indigo-700">
                Trạng thái hiện tại: <strong>{currentStatus}</strong>
              </p>
            </div>
          </>
        )}
      </div>

      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          {error}
          <button onClick={() => setError(null)} className="ml-2 underline font-medium">Đóng</button>
        </div>
      )}

      {/* Steps */}
      <div className="space-y-4 mb-8">
        {[
          { step: 1, title: 'Tạo tài khoản Stripe', desc: 'Đăng ký tài khoản Stripe miễn phí để nhận thanh toán', key: 'NOT_STARTED' as OnboardingStatus },
          { step: 2, title: 'Xác minh danh tính', desc: 'Cung cấp thông tin cá nhân và giấy tờ tùy thân theo yêu cầu Stripe', key: 'IN_PROGRESS' as OnboardingStatus },
          { step: 3, title: 'Liên kết tài khoản ngân hàng', desc: 'Kết nối tài khoản ngân hàng Việt Nam để nhận thanh toán', key: 'COMPLETE' as OnboardingStatus },
          { step: 4, title: 'Kích hoạt bán hàng', desc: 'Stripe phê duyệt tài khoản — bắt đầu nhận tiền từ khách hàng', key: 'COMPLETE' as OnboardingStatus },
        ].map(({ step, title, desc, key }) => {
          let stepState: 'completed' | 'active' | 'pending' = 'pending';
          if (isComplete) stepState = key === 'COMPLETE' ? 'active' : 'completed';
          else if (currentStatus === 'IN_PROGRESS') stepState = key === 'NOT_STARTED' ? 'completed' : key === 'IN_PROGRESS' ? 'active' : 'pending';
          else if (currentStatus === 'PENDING') stepState = key === 'NOT_STARTED' ? 'completed' : 'active';
          else if (currentStatus === 'SUSPENDED') stepState = 'active';
          else stepState = key === 'NOT_STARTED' ? 'active' : 'pending';

          return (
            <div key={step} className={`bg-white rounded-2xl border p-5 flex items-start gap-4 transition-all ${
              stepState === 'completed' ? 'border-green-200 bg-green-50/30' :
              stepState === 'active' ? 'border-blue-200 bg-blue-50/30' :
              'border-gray-100'
            }`}>
              <div className={`w-10 h-10 rounded-full flex items-center justify-center shrink-0 font-bold text-sm ${
                stepState === 'completed' ? 'bg-green-500 text-white' :
                stepState === 'active' ? 'bg-blue-600 text-white' :
                'bg-gray-100 text-gray-500'
              }`}>
                {stepState === 'completed' ? '✓' : step}
              </div>
              <div className="flex-1">
                <h3 className={`font-semibold mb-1 ${
                  stepState === 'completed' ? 'text-green-800' :
                  stepState === 'active' ? 'text-blue-800' :
                  'text-gray-900'
                }`}>{title}</h3>
                <p className="text-sm text-gray-500">{desc}</p>
              </div>
              {stepState === 'completed' && (
                <span className="text-xs font-medium text-green-700 bg-green-100 px-2.5 py-1 rounded-full shrink-0">Hoàn thành</span>
              )}
            </div>
          );
        })}
      </div>

      {/* CTA */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 text-center">
        {isComplete ? (
          <>
            <div className="text-4xl mb-3">🎉</div>
            <h3 className="font-bold text-gray-900 mb-2">Bạn đã sẵn sàng nhận thanh toán!</h3>
            <p className="text-sm text-gray-500 mb-5">
              Tài khoản Stripe đã được kích hoạt. Tiền từ đơn hàng sẽ được chuyển vào tài khoản ngân hàng của bạn.
            </p>
            <a href="/dashboard" className="inline-block px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-xl text-sm">
              Quay về Dashboard
            </a>
          </>
        ) : (
          <>
            <h3 className="font-bold text-gray-900 mb-2">Sẵn sàng kết nối?</h3>
            <p className="text-sm text-gray-500 mb-5">
              Nhấn nút bên dưới để bắt đầu quá trình onboarding với Stripe. Thường mất 5–10 phút để hoàn thành.
            </p>
            <div className="flex flex-col gap-3 items-center">
              <button
                onClick={() => startMut.mutate()}
                disabled={startMut.isPending || refreshMut.isPending}
                className="px-8 py-3 bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-700 hover:to-purple-700 disabled:from-gray-400 disabled:to-gray-400 text-white font-semibold rounded-xl shadow-sm transition-all text-sm"
              >
                {startMut.isPending ? '⏳ Đang khởi tạo...' : 'Bắt đầu với Stripe →'}
              </button>
              {currentStatus === 'SUSPENDED' && (
                <button
                  onClick={() => refreshMut.mutate()}
                  disabled={startMut.isPending || refreshMut.isPending}
                  className="text-sm text-indigo-600 hover:text-indigo-700 underline disabled:opacity-40"
                >
                  Làm mới liên kết đã hết hạn
                </button>
              )}
            </div>
            <p className="text-xs text-gray-400 mt-3">Miễn phí kết nối · Phí giao dịch 2.9% + 30₫</p>
          </>
        )}
      </div>

      {/* Stripe info banner */}
      <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-100 rounded-2xl p-6 mt-6 flex items-start gap-4">
        <span className="text-4xl shrink-0">🔒</span>
        <div>
          <h3 className="font-semibold text-gray-900 mb-1">Thanh toán bảo mật với Stripe</h3>
          <p className="text-sm text-gray-600">
            Stripe là nền tảng thanh toán hàng đầu thế giới, được sử dụng bởi hàng triệu doanh nghiệp.
            Mọi giao dịch đều được mã hoá và bảo vệ theo tiêu chuẩn PCI DSS.
          </p>
        </div>
      </div>
    </div>
  );
}
