import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { userApi, type PointTransactionSummary } from '@shared/api/user.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';
const fmtPts = (n: number) => n.toLocaleString('vi-VN');

function PointCard({ balance, pending, expired }: {
  balance: number; pending: number; expired: number;
}) {
  return (
    <div className="bg-gradient-to-br from-violet-600 to-blue-700 rounded-2xl p-6 text-white mb-6">
      <p className="text-blue-200 text-sm mb-1">Số dư điểm tích luỹ</p>
      <p className="text-4xl font-black mb-1">{fmtPts(balance)}</p>
      <p className="text-blue-200 text-sm">điểm có thể sử dụng</p>

      <div className="grid grid-cols-2 gap-3 mt-5">
        <div className="bg-white/15 rounded-xl p-3">
          <p className="text-blue-200 text-xs">Đang xử lý</p>
          <p className="text-lg font-bold">+{fmtPts(pending)}</p>
        </div>
        <div className="bg-white/15 rounded-xl p-3">
          <p className="text-blue-200 text-xs">Sắp hết hạn</p>
          <p className="text-lg font-bold">{fmtPts(expired)}</p>
        </div>
      </div>

      <div className="bg-white/10 rounded-xl p-3 mt-3">
        <p className="text-xs text-blue-200">Quy đổi</p>
        <p className="text-sm font-medium">
          100 điểm = {fmt(100 * (balance > 0 ? 1000 : 1000))} giảm giá
        </p>
      </div>
    </div>
  );
}

function TransactionRow({ tx }: { tx: PointTransactionSummary }) {
  const isPositive = tx.delta > 0;
  const typeLabels: Record<string, string> = {
    EARN: 'Tích điểm',
    REDEEM: 'Đổi điểm',
    EXPIRE: 'Hết hạn',
    ADJUST: 'Điều chỉnh',
    REFUND: 'Hoàn điểm',
  };
  const typeColors: Record<string, string> = {
    EARN: 'text-green-600 bg-green-50',
    REDEEM: 'text-red-600 bg-red-50',
    EXPIRE: 'text-gray-600 bg-gray-100',
    ADJUST: 'text-blue-600 bg-blue-50',
    REFUND: 'text-green-600 bg-green-50',
  };
  const fmtDate = (iso: string) =>
    new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

  return (
    <div className="flex items-center justify-between py-3.5 border-b border-gray-50 last:border-0">
      <div className="flex items-center gap-3">
        <div className={`w-9 h-9 rounded-full flex items-center justify-center text-sm ${isPositive ? 'bg-green-50 text-green-600' : 'bg-red-50 text-red-600'}`}>
          {isPositive ? '+' : '-'}
        </div>
        <div>
          <p className="text-sm font-medium text-gray-900">
            {typeLabels[tx.type] ?? tx.type}
          </p>
          {tx.orderCode && (
            <p className="text-xs text-gray-400">Đơn #{tx.orderCode}</p>
          )}
          <p className="text-xs text-gray-400">{fmtDate(tx.createdAt)}</p>
        </div>
      </div>
      <div className="text-right">
        <p className={`text-sm font-bold ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
          {isPositive ? '+' : ''}{fmtPts(tx.delta)} đ
        </p>
        <p className="text-xs text-gray-400">Còn {fmtPts(tx.balanceAfter)} đ</p>
      </div>
    </div>
  );
}

function TierCard({ tier, earningRate, maxDiscount }: { tier?: string; earningRate?: string; maxDiscount?: string }) {
  const cfg =
    !tier ? { label: 'Bronze', emoji: '🥉', color: 'text-orange-600 bg-orange-50 border-orange-200' } :
    tier === 'GOLD' ? { label: 'Gold', emoji: '🥇', color: 'text-amber-600 bg-amber-50 border-amber-200' } :
    tier === 'DIAMOND' ? { label: 'Diamond', emoji: '💎', color: 'text-blue-600 bg-blue-50 border-blue-200' } :
                        { label: 'Silver', emoji: '🥈', color: 'text-gray-600 bg-gray-50 border-gray-200' };

  return (
    <div className="bg-white rounded-2xl border border-gray-100 p-5 mb-5">
      <div className="flex items-center gap-3 mb-3">
        <span className="text-2xl">{cfg.emoji}</span>
        <div>
          <p className="font-semibold text-gray-900">{cfg.label} Member</p>
          <p className="text-xs text-gray-400">Hạng thành viên</p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-gray-50 rounded-xl p-3">
          <p className="text-xs text-gray-400">Tỷ lệ tích</p>
          <p className="text-sm font-bold text-gray-900">{earningRate ?? '1%/đơn'}</p>
        </div>
        <div className="bg-gray-50 rounded-xl p-3">
          <p className="text-xs text-gray-400">Giảm tối đa</p>
          <p className="text-sm font-bold text-gray-900">{maxDiscount ?? '5%/đơn'}</p>
        </div>
      </div>
    </div>
  );
}

export default function LoyaltyPage() {
  const [page, setPage] = useState(0);

  const { data: balance, isLoading: loadingBalance } = useQuery({
    queryKey: ['loyalty-balance'],
    queryFn: () => userApi.getLoyaltyBalance().then(r => r.data.data!),
    retry: 1,
  });

  const { data: txPage, isLoading: loadingTx } = useQuery({
    queryKey: ['loyalty-transactions', page],
    queryFn: () => userApi.getLoyaltyTransactions({ page, size: 20 }).then(r => r.data.data!),
    retry: 1,
  });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Điểm tích luỹ</h1>
        <p className="text-gray-500 mt-1 text-sm">Theo dõi và sử dụng điểm thưởng từ FlashSale</p>
      </div>

      {loadingBalance && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>Đang tải...
        </div>
      )}

      {balance && (
        <>
          <PointCard
            balance={balance.availablePoints}
            pending={balance.pendingPoints}
            expired={balance.expiredPoints}
          />

          <TierCard
            tier={balance.tierBenefits?.tier}
            earningRate={balance.tierBenefits?.earningRate}
            maxDiscount={balance.tierBenefits?.maxDiscountRate}
          />

          {/* Expiry Policy */}
          {balance.expiryPolicy && balance.expiryPolicy.pointsExpiringSoon > 0 && (
            <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-5 flex items-start gap-3">
              <span className="text-xl">⏰</span>
              <div>
                <p className="text-sm font-semibold text-amber-800">
                  {balance.expiryPolicy.pointsExpiringSoon.toLocaleString('vi-VN')} điểm sắp hết hạn
                </p>
                <p className="text-xs text-amber-600 mt-0.5">
                  Hạn sử dụng: {balance.expiryPolicy.nextExpiryDate} · Chính sách: {balance.expiryPolicy.expiryDays} ngày
                </p>
              </div>
            </div>
          )}

          {/* Transaction History */}
          <div className="bg-white rounded-2xl border border-gray-100 overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100 flex items-center justify-between">
              <h3 className="text-base font-semibold text-gray-900">Lịch sử giao dịch</h3>
              <span className="text-xs text-gray-400">
                {txPage?.totalElements ?? 0} giao dịch
              </span>
            </div>

            {loadingTx && (
              <div className="text-center py-10 text-gray-400 text-sm">Đang tải...</div>
            )}

            {!loadingTx && (!txPage?.content || txPage.content.length === 0) && (
              <div className="text-center py-10 text-gray-400 text-sm">
                Chưa có giao dịch nào
              </div>
            )}

            {!loadingTx && txPage?.content && txPage.content.length > 0 && (
              <div className="px-5">
                {txPage.content.map((tx) => (
                  <TransactionRow key={tx.transactionId} tx={tx} />
                ))}
              </div>
            )}

            {txPage && txPage.totalPages > 1 && (
              <div className="flex justify-center gap-2 py-4 border-t border-gray-100">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  ← Trước
                </button>
                <span className="px-4 py-2 text-sm text-gray-600">
                  Trang {page + 1} / {txPage.totalPages}
                </span>
                <button
                  onClick={() => setPage(p => Math.min(txPage.totalPages - 1, p + 1))}
                  disabled={page >= txPage.totalPages - 1}
                  className="px-4 py-2 rounded-xl border text-sm font-medium disabled:opacity-40 hover:bg-gray-50"
                >
                  Sau →
                </button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
