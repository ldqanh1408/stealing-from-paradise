import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { flashSaleApi, type FlashSaleSession } from '@shared/api/flashSale.api';
import { adminApi } from '@shared/api/admin.api';
import FlashSaleSessionForm from '@/components/FlashSale/FlashSaleSessionForm';
import FlashSaleSessionsTable from '@/components/FlashSale/FlashSaleSessionsTable';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { notify } from '@shared/lib/toast';

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
      notify.error(err?.response?.data?.message || 'Không thể xoá phiên.');
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
          onClick={() => {
            if (showForm) {
              setShowForm(false);
              setEditingSession(null);
              resetForm();
            } else {
              handleOpenCreate();
            }
          }}
          className="flex items-center gap-2 px-4 py-2.5 bg-violet-600 hover:bg-violet-700 text-white font-semibold text-sm rounded-xl transition-colors shadow-sm"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          {showForm && editingSession ? 'Thoát chỉnh sửa' : 'Tạo phiên mới'}
        </button>
      </div>

      {/* Create / Edit form */}
      {showForm && (
        <FlashSaleSessionForm
          editingSession={editingSession}
          name={name}
          startTime={startTime}
          endTime={endTime}
          formError={formError}
          isMutating={isMutating}
          onNameChange={setName}
          onStartTimeChange={setStartTime}
          onEndTimeChange={setEndTime}
          onSubmit={handleSubmit}
          onCancel={() => { setShowForm(false); setEditingSession(null); resetForm(); }}
        />
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
        <FlashSaleSessionsTable
          sessions={sessions}
          onEdit={handleOpenEdit}
          onDelete={setDeletingSession}
        />
      )}

      {/* Delete confirmation modal */}
      {deletingSession && (
        <ConfirmDialog
          title="Xoá phiên Flash Sale?"
          message={`Bạn có chắc muốn xoá phiên "${deletingSession.name}"? Hành động này không thể hoàn tác.`}
          confirmLabel="Xoá"
          danger
          loading={deleteMut.isPending}
          onConfirm={() => deleteMut.mutate(deletingSession.id)}
          onCancel={() => setDeletingSession(null)}
        />
      )}
    </div>
  );
}
