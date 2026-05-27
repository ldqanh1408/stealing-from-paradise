import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi } from '@shared/api/user.api';

function SellerProfileCard({ profile }: {
  profile: {
    fullName?: string; phone?: string; email: string; avatarUrl?: string;
  }
}) {
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

      <div className="space-y-3">
        {[
          { label: 'Tên hiển thị', value: profile.fullName || 'Chưa cập nhật' },
          { label: 'Email', value: profile.email },
          { label: 'Số điện thoại', value: profile.phone || 'Chưa cập nhật' },
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

      {profile && (
        <>
          <SellerProfileCard profile={profile} />

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
