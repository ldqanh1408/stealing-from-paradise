const ROLE_COLORS: Record<string, string> = {
  ADMIN:  'bg-red-100 text-red-700',
  SELLER: 'bg-purple-100 text-purple-700',
  BUYER:  'bg-blue-100 text-blue-700',
};

const STATUS_COLORS: Record<string, string> = {
  ACTIVE:  'bg-green-100 text-green-700',
  BANNED:  'bg-red-100 text-red-700',
  PENDING: 'bg-yellow-100 text-yellow-700',
};

// Placeholder — sẽ được thay bằng API sau
const USERS: { id: number; username: string; email: string; role: string; status: string; trustScore: number; createdAt: string }[] = [];

export default function UserManagementPage() {
  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý người dùng</h1>
          <p className="text-sm text-gray-500 mt-1">Xem và quản lý tất cả tài khoản trong hệ thống</p>
        </div>
      </div>

      {/* Filter bar */}
      <div className="bg-white rounded-2xl border border-gray-100 p-4 mb-4 flex flex-wrap gap-3 items-center">
        <input
          type="text"
          placeholder="Tìm theo tên, email..."
          className="flex-1 min-w-48 px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        {['Tất cả', 'BUYER', 'SELLER', 'ADMIN'].map((role, i) => (
          <button key={role} className={`px-3 py-1.5 rounded-full text-sm font-medium border transition-all ${
            i === 0 ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}>{role}</button>
        ))}
      </div>

      {/* Table */}
      <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                {['#', 'Người dùng', 'Email', 'Vai trò', 'Trạng thái', 'Trust Score', 'Ngày tạo', 'Thao tác'].map((h) => (
                  <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {USERS.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-16 text-center text-gray-400">
                    <span className="text-4xl block mb-3">👥</span>
                    Chưa có người dùng nào
                  </td>
                </tr>
              ) : (
                USERS.map((u) => (
                  <tr key={u.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-4 text-gray-400 font-mono">{u.id}</td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold uppercase">
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
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <div className="w-20 shrink-0 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                          <div className={`h-full rounded-full ${u.trustScore >= 80 ? 'bg-green-500' : u.trustScore >= 50 ? 'bg-yellow-500' : 'bg-red-500'}`} style={{ width: `${u.trustScore}%` }} />
                        </div>
                        <span className="text-xs font-medium text-gray-700">{u.trustScore}</span>
                      </div>
                    </td>
                    <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{u.createdAt}</td>
                    <td className="px-5 py-4">
                      <div className="flex gap-2">
                        <button className="text-xs text-blue-600 hover:text-blue-700 font-medium">Xem</button>
                        <button className="text-xs text-red-500 hover:text-red-600 font-medium">Khoá</button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
