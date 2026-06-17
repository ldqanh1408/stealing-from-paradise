import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { flashSaleApi, type FlashSaleItem, type FlashSaleSession } from '@shared/api/flashSale.api';
import { adminApi } from '@shared/api/admin.api';
import FlashSaleSessionForm from '@/components/FlashSale/FlashSaleSessionForm';
import FlashSaleSessionsTable from '@/components/FlashSale/FlashSaleSessionsTable';
import ConfirmDialog from '@shared/components/ConfirmDialog';
import { notify } from '@shared/lib/toast';
import { Skeleton } from '@shared/components/ui';
import { fmtVnd } from '@shared/utils/format';

function toLocalDatetime(iso?: string) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function itemStatusLabel(status: string) {
  switch (status) {
    case 'APPROVED': return 'Đã duyệt';
    case 'REJECTED': return 'Từ chối';
    case 'PENDING': return 'Chờ duyệt';
    case 'SOLD_OUT': return 'Hết hàng';
    default: return status;
  }
}

function itemStatusClass(status: string) {
  switch (status) {
    case 'APPROVED': return 'bg-green-100 text-green-700';
    case 'REJECTED': return 'bg-red-100 text-red-700';
    case 'PENDING': return 'bg-yellow-100 text-yellow-700';
    default: return 'bg-gray-100 text-gray-600';
  }
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
  const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);

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
  const activeSessionId = selectedSessionId ?? sessions[0]?.id ?? null;

  const sessionDetailQuery = useQuery({
    queryKey: ['admin-flash-sale-session-detail', activeSessionId],
    queryFn: () => flashSaleApi.getSession(activeSessionId!).then(r => r.data.data),
    enabled: !!activeSessionId,
    staleTime: 15_000,
  });

  const invalidateSessionDetail = () => {
    queryClient.invalidateQueries({ queryKey: ['admin-flash-sale-sessions'] });
    queryClient.invalidateQueries({ queryKey: ['admin-flash-sale-session-detail', activeSessionId] });
  };

  const approveItemMut = useMutation({
    mutationFn: (item: FlashSaleItem) => flashSaleApi.approveItem(item.sessionId, item.id, 'Approved from admin UI'),
    onSuccess: () => {
      notify.success('Đã duyệt sản phẩm Flash Sale');
      invalidateSessionDetail();
    },
    onError: (err: any) => notify.error(err?.response?.data?.message || 'Duyệt sản phẩm thất bại'),
  });

  const rejectItemMut = useMutation({
    mutationFn: (item: FlashSaleItem) => flashSaleApi.rejectItem(item.sessionId, item.id, 'Không đạt điều kiện Flash Sale'),
    onSuccess: () => {
      notify.success('Đã từ chối sản phẩm Flash Sale');
      invalidateSessionDetail();
    },
    onError: (err: any) => notify.error(err?.response?.data?.message || 'Từ chối sản phẩm thất bại'),
  });

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
        <div className="bg-white rounded-2xl border border-gray-100 p-5 space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="grid grid-cols-[1.5fr_1fr_1fr_120px] gap-4">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-6 w-24 rounded-full" />
            </div>
          ))}
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

      {!isLoading && !error && sessions.length > 0 && (
        <div className="mt-6 bg-white rounded-2xl border border-gray-100 overflow-hidden shadow-sm">
          <div className="p-5 border-b border-gray-100 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <h2 className="font-bold text-gray-900">Sản phẩm đăng ký Flash Sale</h2>
              <p className="text-sm text-gray-500 mt-1">Duyệt sản phẩm seller gửi vào từng phiên.</p>
            </div>
            <select
              value={activeSessionId ?? ''}
              onChange={e => setSelectedSessionId(Number(e.target.value))}
              className="px-3 py-2 border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-violet-500"
            >
              {sessions.map(s => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          {sessionDetailQuery.isLoading ? (
            <div className="p-5 space-y-3">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-12 w-full" />)}
            </div>
          ) : !sessionDetailQuery.data?.items?.length ? (
            <div className="p-12 text-center text-gray-500 text-sm">Chưa có sản phẩm nào đăng ký trong phiên này.</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="bg-gray-50 border-b border-gray-100">
                  <tr>
                    {['Sản phẩm', 'Seller', 'Giá flash', 'Stock', 'Trạng thái', 'Thao tác'].map(h => (
                      <th key={h} className="px-5 py-3.5 text-xs font-semibold text-gray-500 uppercase tracking-wider whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {sessionDetailQuery.data.items.map(item => (
                    <tr key={item.id} className="border-b border-gray-50 hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-4">
                        <p className="font-medium text-gray-900">{item.productName || item.skuCode}</p>
                        <p className="text-xs text-gray-400">{item.skuCode}</p>
                      </td>
                      <td className="px-5 py-4 text-gray-600">#{item.sellerId ?? '—'}</td>
                      <td className="px-5 py-4">
                        <span className="font-semibold text-red-600">{fmtVnd(item.flashPrice)}</span>
                        {item.originalPrice && <p className="text-xs text-gray-400 line-through">{fmtVnd(item.originalPrice)}</p>}
                      </td>
                      <td className="px-5 py-4 text-gray-700">{item.flashStock}</td>
                      <td className="px-5 py-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${itemStatusClass(item.status)}`}>
                          {itemStatusLabel(item.status)}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex gap-2">
                          <button
                            onClick={() => approveItemMut.mutate(item)}
                            disabled={item.status === 'APPROVED' || approveItemMut.isPending || rejectItemMut.isPending}
                            className="text-xs text-green-600 hover:text-green-700 font-medium disabled:opacity-40"
                          >
                            Duyệt
                          </button>
                          <button
                            onClick={() => rejectItemMut.mutate(item)}
                            disabled={item.status === 'REJECTED' || approveItemMut.isPending || rejectItemMut.isPending}
                            className="text-xs text-red-500 hover:text-red-600 font-medium disabled:opacity-40"
                          >
                            Từ chối
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
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
