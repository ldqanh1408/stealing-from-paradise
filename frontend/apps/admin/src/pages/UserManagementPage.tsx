import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, type AdminUser } from '@shared/api/admin.api';
import BanUserModal from '@/components/UserManagement/BanUserModal';
import UserFilters from '@/components/UserManagement/UserFilters';
import UsersTable from '@/components/UserManagement/UsersTable';
import Pagination from '@shared/components/Pagination';

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
      <UserFilters
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        roleFilter={roleFilter}
        onRoleFilterChange={(r) => { setRoleFilter(r); setPage(0); }}
        statusFilter={statusFilter}
        onStatusFilterChange={(s) => { setStatusFilter(s); setPage(0); }}
      />

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
          <UsersTable
            users={users}
            onBanClick={setBanUser}
          />

          {/* Pagination */}
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      {banUser && (
        <BanUserModal
          user={banUser}
          onClose={() => setBanUser(null)}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['admin-users'] })}
        />
      )}
    </div>
  );
}
