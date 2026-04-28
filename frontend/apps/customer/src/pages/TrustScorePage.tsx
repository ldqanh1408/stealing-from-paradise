import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userApi, type TrustScoreLogResponse, type AppealResponse } from '@shared/api/user.api';

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });

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

function TrustScoreCard({ logs }: { logs: TrustScoreLogResponse[] }) {
  const tier =
    (score: number) =>
      score >= 90 ? { label: 'Diamond', emoji: '💎', color: 'text-blue-600 bg-blue-50 border-blue-200' } :
      score >= 80 ? { label: 'Gold', emoji: '🥇', color: 'text-amber-600 bg-amber-50 border-amber-200' } :
      score >= 70 ? { label: 'Silver', emoji: '🥈', color: 'text-gray-600 bg-gray-50 border-gray-200' } :
                   { label: 'Bronze', emoji: '🥉', color: 'text-orange-600 bg-orange-50 border-orange-200' };

  const currentScore = logs[0]?.scoreAfter ?? 80;
  const t = tier(currentScore);

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
      <div className="flex items-center gap-4 mb-5">
        <span className="text-5xl">{t.emoji}</span>
        <div className="flex-1">
          <div className="flex items-center gap-3 mb-2">
            <h3 className="text-xl font-bold text-gray-900">Điểm tin cậy</h3>
            <span className={`px-2.5 py-1 rounded-full text-xs font-semibold border ${t.color}`}>
              {t.label}
            </span>
          </div>
          <ScoreBar score={currentScore} />
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

function AppealForm({ logs, onClose }: { logs: TrustScoreLogResponse[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const mut = useMutation({
    mutationFn: () => userApi.submitAppeal({ logId: selectedLogId!, reason, evidenceUrls: [] }),
    onSuccess: () => {
      setSuccess('Khiếu nại đã được gửi thành công!');
      setError('');
      queryClient.invalidateQueries({ queryKey: ['user-appeals'] });
    },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Gửi khiếu nại thất bại'),
  });

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-1">Khiếu nại điểm tin cậy</h3>
        <p className="text-sm text-gray-500 mb-5">
          Chọn sự kiện bạn cho rằng không chính xác và giải thích lý do.
        </p>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">{error}</div>}
        {success && <div className="bg-green-50 border border-green-200 text-green-700 text-sm px-4 py-3 rounded-xl mb-4">{success}</div>}

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Chọn sự kiện</label>
            <select
              value={selectedLogId ?? ''}
              onChange={e => setSelectedLogId(Number(e.target.value))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">-- Chọn sự kiện --</option>
              {logs.map(log => (
                <option key={log.logId} value={log.logId}>
                  {log.eventCode} ({log.delta >= 0 ? '+' : ''}{log.delta}đ) - {fmtDate(log.createdAt)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Lý do khiếu nại</label>
            <textarea
              value={reason}
              onChange={e => setReason(e.target.value)}
              rows={4}
              placeholder="Giải thích chi tiết lý do bạn cho rằng điểm bị trừ không chính xác..."
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
        </div>

        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
            Đóng
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending || !selectedLogId || !reason.trim()}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
          >
            {mut.isPending ? 'Đang gửi...' : 'Gửi khiếu nại'}
          </button>
        </div>
      </div>
    </div>
  );
}

function AppealsSection({ appeals }: { appeals: AppealResponse[] }) {
  const statusColors: Record<string, string> = {
    PENDING: 'bg-yellow-100 text-yellow-700',
    APPROVED: 'bg-green-100 text-green-700',
    REJECTED: 'bg-red-100 text-red-700',
  };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-5">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-base font-semibold text-gray-900">Khiếu nại của tôi</h3>
        <span className="text-xs text-gray-400">{appeals.length} khiếu nại</span>
      </div>

      {appeals.length === 0 && (
        <div className="text-center py-8 text-gray-400 text-sm">
          Chưa có khiếu nại nào
        </div>
      )}

      <div className="space-y-3">
        {appeals.map(appeal => (
          <div key={appeal.appealId} className="p-3 bg-gray-50 rounded-xl">
            <div className="flex items-start justify-between gap-2 mb-2">
              <div>
                <p className="text-sm font-medium text-gray-900">#{appeal.appealId} - {appeal.logId}</p>
                <p className="text-xs text-gray-400">{fmtDate(appeal.createdAt)}</p>
              </div>
              <span className={`px-2.5 py-0.5 rounded-full text-xs font-medium shrink-0 ${statusColors[appeal.status] ?? 'bg-gray-100 text-gray-600'}`}>
                {appeal.status}
              </span>
            </div>
            <p className="text-sm text-gray-600 mb-1">{appeal.reason}</p>
            {appeal.adminNote && (
              <p className="text-xs text-gray-400 italic">Phản hồi: {appeal.adminNote}</p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function TrustScorePage() {
  const queryClient = useQueryClient();
  const [appealOpen, setAppealOpen] = useState(false);

  const { data: logs, isLoading: loadingLogs } = useQuery({
    queryKey: ['trust-score-logs'],
    queryFn: () => userApi.getTrustScoreLogs({ page: 0, size: 20 }).then(r => r.data.data!.content),
    retry: 1,
  });

  const { data: appeals } = useQuery({
    queryKey: ['user-appeals'],
    queryFn: () => userApi.getAppeals().then(r => r.data.data ?? []),
    retry: 1,
  });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Điểm tin cậy & Khiếu nại</h1>
          <p className="text-gray-500 mt-1 text-sm">Theo dõi và khiếu nại điểm tin cậy</p>
        </div>
        {logs && logs.length > 0 && (
          <button
            onClick={() => setAppealOpen(true)}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold flex items-center gap-2"
          >
            📝 Khiếu nại
          </button>
        )}
      </div>

      {loadingLogs && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>Đang tải...
        </div>
      )}

      {!loadingLogs && logs && (
        <>
          <TrustScoreCard logs={logs} />
          {appeals && appeals.length > 0 && <AppealsSection appeals={appeals} />}

          {/* Tips */}
          <div className="bg-blue-50 border border-blue-100 rounded-2xl p-5">
            <h3 className="font-semibold text-gray-900 mb-3">Cách cải thiện điểm tin cậy</h3>
            <ul className="space-y-2 text-sm text-gray-700">
              <li className="flex items-start gap-2">
                <span className="text-green-600 shrink-0">✓</span>
                Thanh toán đơn hàng đúng hạn
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 shrink-0">✓</span>
                Không huỷ đơn sau khi đã thanh toán
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 shrink-0">✓</span>
                Giữ tỷ lệ hoàn hàng dưới 5%
              </li>
              <li className="flex items-start gap-2">
                <span className="text-green-600 shrink-0">✓</span>
                Không vi phạm điều khoản sử dụng
              </li>
            </ul>
          </div>
        </>
      )}

      {appealOpen && logs && (
        <AppealForm
          logs={logs}
          onClose={() => setAppealOpen(false)}
        />
      )}
    </div>
  );
}
