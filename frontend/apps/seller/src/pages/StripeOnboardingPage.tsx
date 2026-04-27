import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sellerApi } from '@shared/api/seller.api';

type OnboardingStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETE' | 'SUSPENDED';

function normalizeStatus(raw?: string | null): OnboardingStatus {
  if (!raw) return 'PENDING';
  if (raw === 'COMPLETE' || raw === 'IN_PROGRESS' || raw === 'SUSPENDED') return raw;
  return 'PENDING';
}

interface ErrorContext {
  isPlatformError: boolean;
  message: string;
  hint?: string;
}

function parseStripeError(err: any): ErrorContext {
  const raw = err?.response?.data?.message || err?.message || '';
  const code = err?.response?.data?.code || '';

  if (raw.includes('signed up for Connect') || code === 'CONNECT_NOT_ACTIVATED') {
    return {
      isPlatformError: true,
      message: 'Nền tảng chưa kích hoạt Stripe Connect.',
      hint: 'Đây là lỗi cấu hình phía nền tảng. Vui lòng liên hệ admin để kích hoạt Stripe Connect.',
    };
  }
  if (raw.includes('country_unsupported')) {
    return {
      isPlatformError: true,
      message: 'Quốc gia của bạn chưa được Stripe hỗ trợ.',
      hint: 'Stripe hiện chưa hỗ trợ Vietnam làm quốc gia Connected Account. Vui lòng liên hệ admin.',
    };
  }
  return {
    isPlatformError: false,
    message: err?.response?.data?.message || 'Không thể khởi tạo Stripe. Vui lòng thử lại.',
  };
}

export default function StripeOnboardingPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [error, setError] = useState<ErrorContext | null>(null);

  const justReturnedFromStripe = searchParams.get('from') === 'stripe';
  const requestRefresh         = searchParams.get('refresh') === '1';

  const { data: status } = useQuery({
    queryKey: ['stripe-onboarding-status'],
    queryFn: () => sellerApi.getStripeStatus().then(r => r.data.data),
    retry: 1,
    // Poll while we wait for Stripe webhook / lazy-sync to flip status to COMPLETE.
    refetchInterval: (query) => {
      const data: any = query.state.data;
      const s = normalizeStatus(data?.onboarding_status);
      if (s === 'COMPLETE') return false;
      return justReturnedFromStripe ? 3000 : false;
    },
    refetchOnWindowFocus: true,
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
      setError(parseStripeError(err));
    },
  });

  const refreshMut = useMutation({
    mutationFn: () => sellerApi.refreshStripeLink(),
    onSuccess: (res) => {
      const url = res.data.data?.onboarding_url;
      if (url) window.open(url, '_blank', 'noopener,noreferrer');
    },
    onError: (err: any) => {
      setError(parseStripeError(err));
    },
  });

  // /stripe/refresh redirects here with ?refresh=1 when Stripe's onboarding link expired.
  // Auto-trigger a fresh AccountLink, then strip the param so reloads don't re-fire.
  useEffect(() => {
    if (requestRefresh && !refreshMut.isPending) {
      refreshMut.mutate();
      const next = new URLSearchParams(searchParams);
      next.delete('refresh');
      setSearchParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestRefresh]);

  const currentStatus: OnboardingStatus = normalizeStatus(status?.onboarding_status);
  const isComplete = currentStatus === 'COMPLETE';
  const isSuspended = currentStatus === 'SUSPENDED';
  const isInProgress = currentStatus === 'IN_PROGRESS';

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

      {justReturnedFromStripe && !isComplete && (
        <div className="mb-6 bg-amber-50 border border-amber-200 rounded-xl p-4 text-amber-800 text-sm flex items-start gap-3">
          <span className="text-lg">⏳</span>
          <div>
            <p className="font-semibold">Đang xác minh với Stripe…</p>
            <p className="text-amber-700">Trang sẽ tự động cập nhật khi Stripe phê duyệt tài khoản (thường vài giây).</p>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-6 bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          <div className="flex items-start gap-2">
            <span className="text-base shrink-0">⚠️</span>
            <div className="flex-1">
              <p className="font-semibold text-red-800">{error.message}</p>
              {error.hint && (
                <p className="mt-1 text-red-600">{error.hint}</p>
              )}
            </div>
          </div>
          <button onClick={() => setError(null)} className="mt-2 ml-8 underline font-medium text-red-700 hover:text-red-800">Đóng</button>
        </div>
      )}

      {/* Steps */}
      <div className="space-y-4 mb-8">
        {[
          { step: 1, title: 'Tạo tài khoản Stripe', desc: 'Đăng ký tài khoản Stripe miễn phí để nhận thanh toán', key: 'PENDING' as OnboardingStatus },
          { step: 2, title: 'Xác minh danh tính', desc: 'Cung cấp thông tin cá nhân và giấy tờ tùy thân theo yêu cầu Stripe', key: 'IN_PROGRESS' as OnboardingStatus },
          { step: 3, title: 'Liên kết tài khoản ngân hàng', desc: 'Kết nối thẻ hoặc tài khoản ngân hàng để nhận thanh toán từ khách hàng', key: 'COMPLETE' as OnboardingStatus },
          { step: 4, title: 'Kích hoạt bán hàng', desc: 'Stripe phê duyệt tài khoản — bắt đầu nhận tiền từ khách hàng', key: 'COMPLETE' as OnboardingStatus },
        ].map(({ step, title, desc, key }) => {
          let stepState: 'completed' | 'active' | 'pending' = 'pending';
          if (isComplete) stepState = key === 'COMPLETE' ? 'active' : 'completed';
          else if (currentStatus === 'IN_PROGRESS') stepState = key === 'PENDING' ? 'completed' : key === 'IN_PROGRESS' ? 'active' : 'pending';
          else if (currentStatus === 'PENDING') stepState = key === 'PENDING' ? 'active' : 'pending';
          else if (isSuspended) stepState = 'active';

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
            <div className="flex flex-col sm:flex-row gap-3 justify-center">
              <a href="/payments" className="inline-block px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-medium rounded-xl text-sm transition-colors">
                Xem thu nhập
              </a>
              <a href="/dashboard" className="inline-block px-6 py-2.5 border border-gray-200 hover:bg-gray-50 text-gray-700 font-medium rounded-xl text-sm transition-colors">
                Quay về Dashboard
              </a>
            </div>
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
              {(isSuspended || isInProgress) && (
                <button
                  onClick={() => refreshMut.mutate()}
                  disabled={startMut.isPending || refreshMut.isPending}
                  className="text-sm text-indigo-600 hover:text-indigo-700 underline disabled:opacity-40"
                >
                  {refreshMut.isPending ? 'Đang tạo liên kết…' : 'Làm mới liên kết đã hết hạn'}
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
