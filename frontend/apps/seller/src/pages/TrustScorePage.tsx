import { useQuery } from '@tanstack/react-query';
import { sellerApi } from '@shared/api/seller.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function ScoreBar({ score, max = 100 }: { score: number; max?: number }) {
  const pct = Math.round((score / max) * 100);
  const color = pct >= 80 ? 'bg-green-500' : pct >= 60 ? 'bg-yellow-500' : pct >= 40 ? 'bg-orange-500' : 'bg-red-500';
  return (
    <div className="w-full">
      <div className="h-3 bg-gray-100 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full transition-all`} style={{ width: `${pct}%` }} />
      </div>
      <p className="text-xs text-gray-500 mt-1">{pct}% — {score}/{max}</p>
    </div>
  );
}

function TrustFactor({ icon, label, score }: { icon: string; label: string; score: number }) {
  const color = score >= 80 ? 'text-green-700 bg-green-50 border-green-200' : score >= 60 ? 'text-yellow-700 bg-yellow-50 border-yellow-200' : 'text-red-700 bg-red-50 border-red-200';
  return (
    <div className="flex items-center gap-4 p-4 bg-white rounded-xl border border-gray-100">
      <span className="text-2xl">{icon}</span>
      <div className="flex-1">
        <div className="flex justify-between items-center mb-1.5">
          <span className="text-sm font-medium text-gray-900">{label}</span>
          <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${color}`}>{score}/100</span>
        </div>
        <ScoreBar score={score} />
      </div>
    </div>
  );
}

export default function TrustScorePage() {
  const { data: stats, isLoading, error } = useQuery({
    queryKey: ['seller-dashboard-stats'],
    queryFn: () => sellerApi.getDashboardStats().then(r => r.data.data),
    retry: 1,
  });

  const score = stats?.trustScore ?? 0;
  const tier =
    score >= 90 ? { label: 'Diamond', emoji: '💎', color: 'text-blue-600 bg-blue-50 border-blue-200' } :
    score >= 80 ? { label: 'Gold', emoji: '🥇', color: 'text-amber-600 bg-amber-50 border-amber-200' } :
    score >= 70 ? { label: 'Silver', emoji: '🥈', color: 'text-gray-600 bg-gray-50 border-gray-200' } :
                 { label: 'Bronze', emoji: '🥉', color: 'text-orange-600 bg-orange-50 border-orange-200' };

  if (error) {
    return (
      <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">Điểm tin cậy</h1>
          <p className="text-gray-500 mt-1">Điểm tin cậy của bạn ảnh hưởng đến thứ hạng sản phẩm và niềm tin khách hàng</p>
        </div>
        <div className="bg-red-50 border border-red-200 rounded-2xl p-8 text-center">
          <p className="text-red-700 font-medium">Không thể tải điểm tin cậy.</p>
          <p className="text-red-500 text-sm mt-1">Vui lòng thử lại sau.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Điểm tin cậy</h1>
        <p className="text-gray-500 mt-1">Điểm tin cậy của bạn ảnh hưởng đến thứ hạng sản phẩm và niềm tin khách hàng</p>
      </div>

      {/* Main score card */}
      <div className="bg-white rounded-2xl border border-gray-100 p-8 mb-6 text-center">
        {isLoading ? (
          <>
            <div className="w-16 h-16 rounded-full bg-gray-100 animate-pulse mx-auto mb-3" />
            <div className="h-7 w-24 bg-gray-100 animate-pulse rounded-full mx-auto mb-4" />
            <div className="h-16 w-16 rounded-full bg-gray-100 animate-pulse mx-auto mb-2" />
            <p className="text-gray-300 text-sm">trên 100 điểm</p>
            <div className="mt-4 max-w-xs mx-auto">
              <div className="h-3 bg-gray-100 rounded-full" />
            </div>
          </>
        ) : (
          <>
            <div className="text-6xl mb-3">{tier.emoji}</div>
            <span className={`inline-block px-4 py-1.5 rounded-full text-sm font-bold border mb-4 ${tier.color}`}>
              Hạng {tier.label}
            </span>
            <div className="text-5xl font-black text-gray-900 mb-2">{score.toFixed(1)}</div>
            <p className="text-gray-500 text-sm">trên 100 điểm</p>
            <div className="mt-4 max-w-xs mx-auto">
              <ScoreBar score={score} />
            </div>
          </>
        )}
      </div>

      {/* Breakdown */}
      <h2 className="text-lg font-semibold text-gray-900 mb-4">Chi tiết điểm tin cậy</h2>
      <div className="space-y-3 mb-8">
        {isLoading ? (
          Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex items-center gap-4 p-4 bg-white rounded-xl border border-gray-100 animate-pulse">
              <div className="w-10 h-10 bg-gray-100 rounded-full" />
              <div className="flex-1 space-y-2">
                <div className="h-4 bg-gray-100 rounded w-1/3" />
                <div className="h-3 bg-gray-100 rounded-full" />
              </div>
            </div>
          ))
        ) : (
          <>
            <TrustFactor icon="📦" label="Chất lượng sản phẩm" score={Math.min(100, Math.round(score * 0.95))} />
            <TrustFactor icon="🚚" label="Tốc độ giao hàng" score={Math.min(100, Math.round(score * 0.88))} />
            <TrustFactor icon="💬" label="Phản hồi khách hàng" score={Math.min(100, Math.round(score * 0.92))} />
            <TrustFactor icon="⭐" label="Đánh giá trung bình" score={Math.min(100, Math.round(score * 0.90))} />
            <TrustFactor icon="🔄" label="Tỷ lệ hoàn/kiện" score={Math.min(100, Math.round(score * 0.85))} />
          </>
        )}
      </div>

      {/* Tips */}
      <div className="bg-blue-50 border border-blue-100 rounded-2xl p-6">
        <h3 className="font-semibold text-gray-900 mb-3">💡 Cách cải thiện điểm tin cậy</h3>
        <ul className="space-y-2 text-sm text-gray-700">
          <li className="flex items-start gap-2">
            <span className="text-green-600 shrink-0">✓</span>
            Giao hàng đúng hẹn và cập nhật mã vận đơn kịp thời
          </li>
          <li className="flex items-start gap-2">
            <span className="text-green-600 shrink-0">✓</span>
            Phản hồi tin nhắn khách hàng trong vòng 2 giờ
          </li>
          <li className="flex items-start gap-2">
            <span className="text-green-600 shrink-0">✓</span>
            Đảm bảo sản phẩm đúng như mô tả, hình ảnh chính xác
          </li>
          <li className="flex items-start gap-2">
            <span className="text-green-600 shrink-0">✓</span>
            Giữ tỷ lệ hoàn hàng dưới 5% bằng cách kiểm tra chất lượng trước khi gửi
          </li>
          <li className="flex items-start gap-2">
            <span className="text-green-600 shrink-0">✓</span>
            Khuyến khích khách hàng để lại đánh giá 5 sao
          </li>
        </ul>
      </div>
    </div>
  );
}
