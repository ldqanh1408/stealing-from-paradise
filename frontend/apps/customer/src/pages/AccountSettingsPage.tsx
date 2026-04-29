import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { userApi } from '@shared/api/user.api';

export default function AccountSettingsPage() {
  const [activeTab, setActiveTab] = useState<'password' | 'notifications' | 'security'>('password');

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Cài đặt tài khoản</h1>
        <p className="text-gray-500 mt-1 text-sm">Quản lý mật khẩu, thông báo và bảo mật</p>
      </div>

      {/* Tabs */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden mb-6">
        <div className="flex border-b border-gray-100">
          {[
            { id: 'password', label: 'Mật khẩu', icon: '🔑' },
            { id: 'notifications', label: 'Thông báo', icon: '🔔' },
            { id: 'security', label: 'Bảo mật', icon: '🛡️' },
          ].map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id as typeof activeTab)}
              className={`flex-1 flex items-center justify-center gap-2 py-3.5 text-sm font-medium transition-colors ${
                activeTab === tab.id
                  ? 'text-blue-600 border-b-2 border-blue-600 bg-blue-50/50'
                  : 'text-gray-500 hover:text-gray-700 hover:bg-gray-50'
              }`}
            >
              <span>{tab.icon}</span>
              {tab.label}
            </button>
          ))}
        </div>

        <div className="p-6">
          {activeTab === 'password' && <PasswordTab />}
          {activeTab === 'notifications' && <NotificationsTab />}
          {activeTab === 'security' && <SecurityTab />}
        </div>
      </div>
    </div>
  );
}

function PasswordTab() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [showPw, setShowPw] = useState({ current: false, new: false, confirm: false });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const mut = useMutation({
    mutationFn: async () => {
      if (form.newPassword !== form.confirmPassword) throw new Error('Mật khẩu xác nhận không khớp');
      if (form.newPassword.length < 6) throw new Error('Mật khẩu mới phải có ít nhất 6 ký tự');
      await userApi.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      });
    },
    onSuccess: () => {
      setSuccess('Đổi mật khẩu thành công!');
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setError('');
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message ?? err.message ?? 'Đổi mật khẩu thất bại');
      setSuccess('');
    },
  });

  const PwInput = ({ name, label, placeholder }: { name: keyof typeof form; label: string; placeholder: string }) => (
    <div>
      <label className="block text-sm font-medium text-gray-700 mb-1.5">{label}</label>
      <div className="relative">
        <input
          type={showPw[name === 'currentPassword' ? 'current' : name === 'newPassword' ? 'new' : 'confirm'] ? 'text' : 'password'}
          value={form[name]}
          onChange={e => setForm(f => ({ ...f, [name]: e.target.value }))}
          placeholder={placeholder}
          className="w-full px-4 py-2.5 pr-11 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button type="button" onClick={() => setShowPw(p => ({ ...p, [name === 'currentPassword' ? 'current' : name === 'newPassword' ? 'new' : 'confirm']: !p[name === 'currentPassword' ? 'current' : name === 'newPassword' ? 'new' : 'confirm'] }))}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
        </button>
      </div>
    </div>
  );

  return (
    <div>
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{error}</div>}
      {success && <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">{success}</div>}
      <div className="space-y-4 max-w-md">
        <PwInput name="currentPassword" label="Mật khẩu hiện tại" placeholder="Nhập mật khẩu hiện tại" />
        <PwInput name="newPassword" label="Mật khẩu mới" placeholder="Ít nhất 6 ký tự" />
        <PwInput name="confirmPassword" label="Xác nhận mật khẩu mới" placeholder="Nhập lại mật khẩu mới" />
      </div>
      <button
        onClick={() => mut.mutate()}
        disabled={mut.isPending || !form.currentPassword || !form.newPassword || !form.confirmPassword}
        className="mt-6 px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold disabled:opacity-60 transition-all"
      >
        {mut.isPending ? 'Đang đổi...' : 'Đổi mật khẩu'}
      </button>
    </div>
  );
}

function NotificationsTab() {
  const [settings, setSettings] = useState({
    email_order: true,
    email_promo: false,
    email_flashsale: true,
    push_order: true,
    push_promo: false,
  });

  const NOTIFICATION_ITEMS = [
    { k: 'email_order', label: 'Thông báo đơn hàng qua email', desc: 'Cập nhật trạng thái đơn hàng' },
    { k: 'email_promo', label: 'Khuyến mãi qua email', desc: 'Mã giảm giá, ưu đãi đặc biệt' },
    { k: 'email_flashsale', label: 'Thông báo Flash Sale qua email', desc: 'Nhận thông báo sớm về deal hot' },
    { k: 'push_order', label: 'Thông báo đơn hàng (push)', desc: 'Nhắc nhở trạng thái giao hàng' },
    { k: 'push_promo', label: 'Khuyến mãi (push)', desc: 'Thông báo khuyến mãi nhanh' },
  ] as const;
  type NotificationKey = typeof NOTIFICATION_ITEMS[number]['k'];

  const Toggle = ({ k }: { k: NotificationKey }) => (
    <button
      onClick={() => setSettings(s => ({ ...s, [k]: !s[k] }))}
      className={`relative w-11 h-6 rounded-full transition-colors ${settings[k] ? 'bg-blue-600' : 'bg-gray-200'}`}
    >
      <span className={`absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform ${settings[k] ? 'translate-x-5' : ''}`} />
    </button>
  );

  return (
    <div className="space-y-1">
      {NOTIFICATION_ITEMS.map(item => (
        <div key={item.k} className="flex items-center justify-between py-3 border-b border-gray-50 last:border-0">
          <div>
            <p className="text-sm font-medium text-gray-900">{item.label}</p>
            <p className="text-xs text-gray-400">{item.desc}</p>
          </div>
          <Toggle k={item.k} />
        </div>
      ))}
      <button className="mt-4 px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold">
        Lưu thay đổi
      </button>
    </div>
  );
}

function SecurityTab() {
  return (
    <div className="space-y-5">
      {/* Login Sessions */}
      <div className="p-4 bg-gray-50 rounded-xl">
        <div className="flex items-center gap-3 mb-3">
          <span className="text-xl">💻</span>
          <div>
            <p className="text-sm font-medium text-gray-900">Phiên đăng nhập hiện tại</p>
            <p className="text-xs text-gray-400">Đang hoạt động</p>
          </div>
          <span className="ml-auto px-2.5 py-1 bg-green-100 text-green-700 text-xs font-medium rounded-full">Active</span>
        </div>
      </div>

      {/* 2FA */}
      <div className="p-4 bg-gray-50 rounded-xl">
        <div className="flex items-center gap-3">
          <span className="text-xl">📱</span>
          <div className="flex-1">
            <p className="text-sm font-medium text-gray-900">Xác thực hai yếu tố (2FA)</p>
            <p className="text-xs text-gray-400">Bảo vệ tài khoản với SMS OTP</p>
          </div>
          <button className="px-4 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-white transition-colors">
            Bật 2FA
          </button>
        </div>
      </div>

      {/* Login Devices */}
      <div>
        <h4 className="text-sm font-semibold text-gray-900 mb-3">Thiết bị đã đăng nhập</h4>
        <div className="space-y-2">
          {[
            { device: 'Chrome trên Windows', location: 'Hồ Chí Minh, VN', time: 'Bây giờ', active: true },
            { device: 'Safari trên iPhone', location: 'Hồ Chí Minh, VN', time: '2 ngày trước', active: false },
          ].map((d, i) => (
            <div key={i} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
              <div className="flex items-center gap-3">
                <span>{d.active ? '💻' : '📱'}</span>
                <div>
                  <p className="text-sm font-medium text-gray-900">{d.device}</p>
                  <p className="text-xs text-gray-400">{d.location} · {d.time}</p>
                </div>
              </div>
              {d.active && <span className="px-2 py-0.5 bg-green-100 text-green-700 text-xs rounded-full">Hiện tại</span>}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
