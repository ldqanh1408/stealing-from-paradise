import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi } from '@shared/api/user.api';
import { useAuthStore } from '@shared/store/authStore';

function ScoreBar({ score }: { score: number }) {
  const pct = Math.round(score);
  const color = pct >= 80 ? 'bg-green-500' : pct >= 60 ? 'bg-yellow-500' : pct >= 40 ? 'bg-orange-500' : 'bg-red-500';
  return (
    <div className="flex items-center gap-3">
      <div className="flex-1 h-2.5 bg-gray-100 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full transition-all`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-sm font-semibold text-gray-700 w-10 text-right">{score}</span>
    </div>
  );
}

function TrustScoreCard({ logs }: { logs: { logId: number; eventCode: string; delta: number; scoreAfter: number; changedBy: string; reason?: string; createdAt: string }[] }) {
  const currentScore = logs[0]?.scoreAfter ?? 80;
  const tier =
    currentScore >= 90 ? { label: 'Diamond', emoji: '💎', color: 'text-blue-600 bg-blue-50 border-blue-200' } :
    currentScore >= 80 ? { label: 'Gold', emoji: '🥇', color: 'text-amber-600 bg-amber-50 border-amber-200' } :
    currentScore >= 70 ? { label: 'Silver', emoji: '🥈', color: 'text-gray-600 bg-gray-50 border-gray-200' } :
                        { label: 'Bronze', emoji: '🥉', color: 'text-orange-600 bg-orange-50 border-orange-200' };

  const fmtDate = (iso: string) =>
    new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
      <div className="flex items-center gap-4 mb-5">
        <span className="text-5xl">{tier.emoji}</span>
        <div className="flex-1">
          <div className="flex items-center gap-3 mb-2">
            <h3 className="text-xl font-bold text-gray-900">Điểm tin cậy</h3>
            <span className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${tier.color}`}>
              {tier.label}
            </span>
          </div>
          <ScoreBar score={currentScore} />
          <p className="text-xs text-gray-400 mt-2">
            {currentScore >= 80 ? 'Bạn là người bán đáng tin cậy!' :
             currentScore >= 60 ? 'Cố gắng giữ điểm cao để có thứ hạng tốt hơn.' :
             'Điểm thấp có thể ảnh hưởng đến thứ hạng sản phẩm và niềm tin khách hàng.'}
          </p>
        </div>
      </div>

      {/* History */}
      <h4 className="text-sm font-semibold text-gray-700 mb-3">Lịch sử thay đổi</h4>
      <div className="space-y-2">
        {logs.slice(0, 10).map(log => (
          <div key={log.logId} className="flex items-start gap-3 py-2 border-b border-gray-50 last:border-0">
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0 mt-0.5 ${
              log.delta >= 0 ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
            }`}>
              {log.delta >= 0 ? '+' : ''}{log.delta}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2">
                <p className="text-sm font-medium text-gray-900">{log.eventCode}</p>
                <span className="text-xs text-gray-400 shrink-0">{fmtDate(log.createdAt)}</span>
              </div>
              <p className="text-xs text-gray-500 truncate">{log.reason ?? 'Không có ghi chú'}</p>
              <p className="text-xs text-gray-400">
                Sau thay đổi: <span className="font-medium">{log.scoreAfter}</span> · {log.changedBy}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function SellerProfileCard({ profile }: {
  profile: {
    fullName?: string; phone?: string; email: string; avatarUrl?: string;
    productPostingSuspended: boolean; lockReason?: string; lockedUntil?: string;
  }
}) {
  const fmtDate = (iso?: string) =>
    iso ? new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-';

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
      <div className="flex items-start gap-5 mb-4">
        {profile.avatarUrl ? (
          <img src={profile.avatarUrl} alt="" className="w-16 h-16 rounded-full object-cover ring-4 ring-gray-100" />
        ) : (
          <div className="w-16 h-16 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xl font-bold ring-4 ring-gray-100">
            {profile.fullName?.charAt(0) ?? profile.email.charAt(0).toUpperCase()}
          </div>
        )}
        <div className="flex-1">
          <h3 className="text-lg font-bold text-gray-900">{profile.fullName || 'Người bán'}</h3>
          <p className="text-sm text-gray-500">{profile.email}</p>
          {profile.phone && <p className="text-sm text-gray-400">{profile.phone}</p>}
        </div>
      </div>

      {profile.productPostingSuspended && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 mb-4">
          <div className="flex items-start gap-3">
            <span className="text-xl">⚠️</span>
            <div>
              <p className="text-sm font-semibold text-red-800">Tạm ngừng đăng sản phẩm</p>
              <p className="text-xs text-red-600 mt-0.5">{profile.lockReason ?? 'Lý do không xác định'}</p>
              {profile.lockedUntil && <p className="text-xs text-red-500 mt-0.5">Đến: {fmtDate(profile.lockedUntil)}</p>}
            </div>
          </div>
        </div>
      )}

      <div className="space-y-3">
        {[
          { label: 'Tên hiển thị', value: profile.fullName || 'Chưa cập nhật' },
          { label: 'Email', value: profile.email },
          { label: 'Số điện thoại', value: profile.phone || 'Chưa cập nhật' },
          { label: 'Trạng thái đăng sản phẩm', value: profile.productPostingSuspended ? 'Tạm ngừng' : 'Đang hoạt động' },
        ].map(({ label, value }) => (
          <div key={label} className="flex items-center justify-between py-2 border-b border-gray-50 last:border-0">
            <span className="text-sm text-gray-500">{label}</span>
            <span className="text-sm font-medium text-gray-900">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function EditProfileModal({ profile, onClose }: {
  profile: { fullName?: string; phone?: string; email: string };
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ fullName: profile.fullName ?? '', phone: profile.phone ?? '' });
  const [error, setError] = useState('');

  const mut = useMutation({
    mutationFn: () => userApi.updateProfile(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['seller-profile'] });
      onClose();
    },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Cập nhật thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-5">Chỉnh sửa hồ sơ</h3>
        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{error}</div>}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên cửa hàng / Họ tên</label>
            <input
              type="text"
              value={form.fullName}
              onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Nhập tên cửa hàng"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Số điện thoại</label>
            <input
              type="tel"
              value={form.phone}
              onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="0xxx xxx xxx"
            />
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
          >
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function SellerSettingsPage() {
  const queryClient = useQueryClient();
  const [editOpen, setEditOpen] = useState(false);

  const { data: profile, isLoading } = useQuery({
    queryKey: ['seller-profile'],
    queryFn: () => userApi.getProfile().then(r => r.data.data!),
    retry: 1,
  });

  const { data: logs } = useQuery({
    queryKey: ['seller-trust-score-logs'],
    queryFn: () => userApi.getTrustScoreLogs({ page: 0, size: 20 }).then(r => r.data.data!.content),
    retry: 1,
  });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Hồ sơ người bán</h1>
          <p className="text-gray-500 mt-1 text-sm">Quản lý thông tin cửa hàng của bạn</p>
        </div>
        {profile && (
          <button
            onClick={() => setEditOpen(true)}
            className="px-4 py-2 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50"
          >
            Chỉnh sửa
          </button>
        )}
      </div>

      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>Đang tải...
        </div>
      )}

      {profile && logs && (
        <>
          <SellerProfileCard profile={profile} />
          <TrustScoreCard logs={logs} />

          {/* Stripe Info */}
          <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
            <h3 className="text-base font-semibold text-gray-900 mb-4">Thông tin thanh toán</h3>
            <p className="text-sm text-gray-500 mb-4">
              Quản lý tài khoản Stripe để nhận thanh toán từ khách hàng.
            </p>
            <a
              href="/stripe-onboarding"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-gradient-to-r from-violet-600 to-blue-600 hover:from-violet-700 hover:to-blue-700 text-white rounded-xl text-sm font-semibold transition-all"
            >
              ⚙️ Quản lý Stripe
            </a>
          </div>
        </>
      )}

      {editOpen && profile && (
        <EditProfileModal
          profile={profile}
          onClose={() => setEditOpen(false)}
        />
      )}
    </div>
  );
}
