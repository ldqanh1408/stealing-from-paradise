import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { flashSaleApi, type FlashSaleSession } from '@shared/api/flashSale.api';
import { adminApi } from '@shared/api/admin.api';

const STATUS_COLORS: Record<string, string> = {
  UPCOMING: 'bg-blue-100 text-blue-700',
  ACTIVE:   'bg-green-100 text-green-700',
  ENDED:    'bg-gray-100 text-gray-600',
};

function formatDateTime(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

function toLocalDatetime(iso?: string) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function FlashSaleConfigPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [editingSession, setEditingSession] = useState<FlashSaleSession | null>(null);
  const [deletingSession, setDeletingSession] = useState<FlashSaleSession | null>(null);
  const [name, setName] = useState('');
  const [startTime, setStartTime] = useState('');
  const [endTime, setEndTime] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-flash-sale-sessions'],
    queryFn: () => flashSaleApi.getSessions().then(r => r.data.data),
    staleTime: 1000 * 60,
  });

  const createMut = useMutation({
    mutationFn: () => flashSaleApi.createSession({ name, startTime, endTime }),
    onSuccess: () => {
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['admin-flash-sale-sessions'] });
    },
    onError: (err: any) => {
      setFormError(err?.response?.data?.message || 'Tạo phiên thất bại');
    },
  });

  const updateMut = useMutation({
    mutationFn: () => adminApi.updateFlashSaleSession(editingSession!.id, {
      name,
      startTime,
      endTime,
    }),
    onSuccess: () => {
      setEditingSession(null);
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['admin-flash-sale-sessions'] });
    },
    onError: (err: any) => {
      setFormError(err?.response?.data?.message || 'Cập nhật thất bại');
    },
  });

  const deleteMut = useMutation({
    mutationFn: (sessionId: number) => adminApi.deleteFlashSaleSession(sessionId),
    onSuccess: () => {
      setDeletingSession(null);
      queryClient.invalidateQueries({ queryKey: ['admin-flash-sale-sessions'] });
    },
    onError: (err: any) => {
      alert(err?.response?.data?.message || 'Không thể xoá phiên.');
      setDeletingSession(null);
    },
  });

  const sessions: FlashSaleSession[] = data?.content ?? [];

  const resetForm = () => {
    setName('');
    setStartTime('');
    setEndTime('');
    setFormError(null);
  };

  const handleOpenCreate = () => {
    resetForm();
    setEditingSession(null);
    setShowForm(true);
  };

  const handleOpenEdit = (s: FlashSaleSession) => {
    setFormError(null);
    setName(s.name);
    setStartTime(toLocalDatetime(s.startTime));
    setEndTime(toLocalDatetime(s.endTime));
    setEditingSession(s);
    setShowForm(true);
  };

  const handleSubmit = () => {
    if (!name.trim() || !startTime || !endTime) {
      setFormError('Vui lòng điền đầy đủ thông tin.');
      return;
    }
    if (new Date(startTime) >= new Date(endTime)) {
      setFormError('Thời gian kết thúc phải sau thời gian bắt đầu.');
      return;
    }
    setFormError(null);
    if (editingSession) {
      updateMut.mutate();
    } else {
      createMut.mutate();
    }
  };

  const isMutating = createMut.isPending || updateMut.isPending;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Cấu hình Flash Sale</h1>
          <p className="text-sm text-gray-500 mt-1">Tạo và quản lý các phiên flash sale</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className="flex items-center gap-2 px-4 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors shadow-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Tạo phiên mới
        </button>
      </div>

      {/* Create / Edit form */}
      {showForm && (
        <div className="bg-white rounded-2xl border border-violet-100 p-6 mb-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4">
            {editingSession ? `Chỉnh sửa: ${editingSession.name}` : 'Tạo phiên Flash Sale mới'}
          </h2>
          {formError && (
            <div className="bg-red-50 border border-red-200 rounded-xl p-3 text-red-700 text-sm mb-4">{formError}</div>
          )}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="sm:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Tên phiên</label>
              <input
                type="text"
                value={name}
                onChange={e => setName(e.target.value)}
                placeholder="vd: Flash Sale 18:00 Thứ Sáu"
                className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian bắt đầu</label>
              <input
                type="datetime-local"
                value={startTime}
                onChange={e => setStartTime(e.target.value)}
                className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1.5">Thời gian kết thúc</label>
              <input
                type="datetime-local"
                value={endTime}
                onChange={e => setEndTime(e.target.value)}
                className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
              />
            </div>
          </div>
          <div className="flex gap-3 mt-5">
            <button
              onClick={handleSubmit}
              disabled={isMutating}
              className="px-5 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors disabled:opacity-50"
            >
              {isMutating ? 'Đang xử lý...' : editingSession ? 'Cập nhật' : 'Tạo phiên'}
            </button>
            <button
              onClick={() => { setShowForm(false); setEditingSession(null); resetForm(); }}
              className="px-5 py-2.5 border border-gray-200 text-gray-700 font-semibold text-sm rounded-xl hover:bg-gray-50 transition-colors"
            >
              Huỷ
            </button>
          </div>
        </div>
      )}

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
          Không thể tải danh sách phiên flash sale.
        </div>
      )}

      {/* Empty */}
      {!isLoading && !error && sessions.length === 0 && (
        <div className="bg-white rounded-2xl border-2 border-dashed border-gray-300 py-20 text-center">
          <span className="text-5xl block mb-4">⚡</span>
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Chưa có phiên Flash Sale nào</h3>
          <p className="text-sm text-gray-500 mb-6 max-w-sm mx-auto">
            Tạo phiên flash sale đầu tiên để thu hút khách hàng với giá ưu đãi đặc biệt
          </p>
          <button
            onClick={handleOpenCreate}
            className="px-6 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors"
          >
            Tạo phiên đầu tiên
          </button>
        </div>
      )}

      {/* Sessions list */}
      {!isLoading && !error && sessions.length > 0 && (
        <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Tên phiên', 'Bắt đầu', 'Kết thúc', 'Sản phẩm', 'Trạng thái', 'Thao tác'].map(h => (
                    <th key={h} className="px-5 py-3.5 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sessions.map(s => (
                  <tr key={s.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-4 font-medium text-gray-900">{s.name}</td>
                    <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{formatDateTime(s.startTime)}</td>
                    <td className="px-5 py-4 text-gray-500 whitespace-nowrap">{formatDateTime(s.endTime)}</td>
                    <td className="px-5 py-4 text-gray-700">{s.items?.length ?? 0} sản phẩm</td>
                    <td className="px-5 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${STATUS_COLORS[s.status] ?? 'bg-gray-100 text-gray-600'}`}>
                        {s.status === 'UPCOMING' ? 'Sắp diễn ra' : s.status === 'ACTIVE' ? 'Đang chạy' : 'Đã kết thúc'}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex gap-2">
                        {s.status === 'UPCOMING' && (
                          <button
                            onClick={() => handleOpenEdit(s)}
                            className="text-xs text-blue-600 hover:text-blue-700 font-medium"
                          >
                            Chỉnh sửa
                          </button>
                        )}
                        <button
                          onClick={() => setDeletingSession(s)}
                          className="text-xs text-red-500 hover:text-red-600 font-medium"
                        >
                          Xoá
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Delete confirmation modal */}
      {deletingSession && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center">
            <div className="text-5xl mb-4">⚠️</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Xoá phiên Flash Sale?</h3>
            <p className="text-sm text-gray-500 mb-6">
              Bạn có chắc muốn xoá phiên <strong>"{deletingSession.name}"</strong>? Hành động này không thể hoàn tác.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeletingSession(null)}
                className="flex-1 py-2.5 border border-gray-200 rounded-xl text-sm font-medium hover:bg-gray-50"
              >
                Huỷ
              </button>
              <button
                onClick={() => deleteMut.mutate(deletingSession.id)}
                disabled={deleteMut.isPending}
                className="flex-1 py-2.5 bg-red-600 text-white rounded-xl text-sm font-medium hover:bg-red-700 disabled:opacity-50"
              >
                {deleteMut.isPending ? 'Đang xoá...' : 'Xoá'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
