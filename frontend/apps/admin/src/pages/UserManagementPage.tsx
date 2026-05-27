import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { adminApi, type AdminUser } from '@shared/api/admin.api';

const ROLE_COLORS: Record<string, string> = {
  ADMIN:  'bg-red-100 text-red-700',
  SELLER: 'bg-purple-100 text-purple-700',
  BUYER:  'bg-blue-100 text-blue-700',
};

const STATUS_COLORS: Record<string, string> = {
  ACTIVE:  'bg-green-100 text-green-700',
  BANNED:  'bg-red-100 text-red-700',
  LOCKED:  'bg-red-100 text-red-700',
  PENDING: 'bg-yellow-100 text-yellow-700',
};

const isLocked = (status: string) => status === 'BANNED' || status === 'LOCKED';

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });

function BanModal({ user, onClose, onSuccess }: { user: AdminUser; onClose: () => void; onSuccess: () => void }) {
  const queryClient = useQueryClient();
  const mut = useMutation({
    mutationFn: () => {
      if (isLocked(user.status)) {
        return adminApi.unlockUser(user.userId, 'Mở khoá tài khoản bởi Admin');
      } else {
        return adminApi.lockUser(user.userId, 'Khoá tài khoản bởi Admin');
      }
    },
    onSuccess: () => { onSuccess(); onClose(); },
    onError: () => {},
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
        <div className="text-5xl mb-4">{isLocked(user.status) ? '🔓' : '🔒'}</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">
          {isLocked(user.status) ? 'Mở khoá tài khoản?' : 'Khoá tài khoản?'}
        </h3>
        <p className="text-sm text-gray-500 mb-6">
          {isLocked(user.status)
            ? `Mở khoá tài khoản "${user.username}" để họ có thể tiếp tục sử dụng.`
            : `Khoá tài khoản "${user.username}" sẽ không cho phép họ đăng nhập.`}
        </p>
        <div className="flex gap-3">
          <button onClick={onClose} className="flex-1 py-2.5 border rounded-xl text-sm font-medium hover:bg-gray-50">Huỷ</button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending}
            className={`flex-1 py-2.5 text-white rounded-xl text-sm font-medium ${
              isLocked(user.status) ? 'bg-green-600 hover:bg-green-700' : 'bg-red-600 hover:bg-red-700'
            } disabled:opacity-50`}
          >
            {mut.isPending ? '...' : isLocked(user.status) ? 'Mở khoá' : 'Khoá'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default function UserManagementPage() {
  const queryClient = useQueryClient();
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [banUser, setBanUser] = useState<AdminUser | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-users', roleFilter, statusFilter, page],
    queryFn: () =>
      adminApi.getUsers({
        role: roleFilter || undefined,
        status: statusFilter || undefined,
        page,
        size: 20,
      }).then(r => r.data.data),
    retry: 1,
  });

  const users: AdminUser[] = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý người dùng</h1>
          <p className="text-sm text-gray-500 mt-1">
            {totalElements > 0 && `${totalElements} tài khoản`}
          </p>
        </div>
        <button
          onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-users'] })}
          className="px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-600"
        >
          🔄 Làm mới
        </button>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-2xl border border-gray-100 p-4 mb-4 flex flex-wrap gap-3 items-center">
        <input
          type="text"
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          placeholder="Tìm theo tên, email..."
          className="flex-1 min-w-48 px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        {[{ value: '', label: 'Tất cả' }, { value: 'BUYER', label: 'BUYER' }, { value: 'SELLER', label: 'SELLER' }, { value: 'ADMIN', label: 'ADMIN' }].map(r => (
          <button
            key={r.value}
            onClick={() => { setRoleFilter(r.value); setPage(0); }}
            className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
              roleFilter === r.value ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {r.label}
          </button>
        ))}
        {[{ value: '', label: 'Tất cả trạng thái' }, { value: 'ACTIVE', label: 'ACTIVE' }, { value: 'LOCKED', label: 'LOCKED' }, { value: 'BANNED', label: 'BANNED' }].map(s => (
          <button
            key={s.value}
            onClick={() => { setStatusFilter(s.value); setPage(0); }}
            className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
              statusFilter === s.value ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      {/* Loading */}
      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>
          Đang tải...
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm mb-4">
          Không thể tải danh sách người dùng.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && users.length === 0 && (
        <div className="bg-white rounded-2xl border border-gray-100 py-20 text-center text-gray-400">
          <span className="text-4xl block mb-3">👥</span>
          Không có người dùng nào
        </div>
      )}

      {/* Table */}
      {!isLoading && !error && users.length > 0 && (
        <>
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['#', 'Người dùng', 'Email', 'Vai trò', 'Trạng thái', 'Ngày tạo', 'Thao tác'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.map((u) => (
                    <tr key={u.userId} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4 text-gray-400 font-mono text-xs">{u.userId}</td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold uppercase shrink-0">
                            {u.username.charAt(0)}
                          </div>
                          <span className="font-medium text-gray-900">{u.username}</span>
                        </div>
                      </td>
                      <td className="px-5 py-4 text-gray-500">{u.email}</td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${ROLE_COLORS[u.role] ?? 'bg-gray-100 text-gray-600'}`}>{u.role}</span>
                      </td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[u.status] ?? 'bg-gray-100 text-gray-600'}`}>{u.status}</span>
                      </td>
                      <td className="px-5 py-4 text-gray-400 whitespace-nowrap text-xs">{fmtDate(u.createdAt)}</td>
                      <td className="px-5 py-4">
                        {u.role !== 'ADMIN' && (
                          <div className="flex gap-2">
                            <button className="text-xs text-blue-600 hover:text-blue-700 font-medium">Xem</button>
                            <button
                              onClick={() => setBanUser(u)}
                              className={`text-xs font-medium ${isLocked(u.status) ? 'text-green-600 hover:text-green-700' : 'text-red-500 hover:text-red-600'}`}
                            >
                              {isLocked(u.status) ? 'Mở khoá' : 'Khoá'}
                            </button>
                          </div>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                ← Trước
              </button>
              <span className="px-4 py-2 text-sm text-gray-600">Trang {page + 1} / {totalPages}</span>
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page >= totalPages - 1} className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50">
                Sau →
              </button>
            </div>
          )}
        </>
      )}

      {banUser && (
        <BanModal
          user={banUser}
          onClose={() => setBanUser(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-users'] })}
        />
      )}
    </div>
  );
}
