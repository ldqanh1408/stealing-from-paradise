import { useState, useEffect } from 'react';

const FLASH_PRODUCTS = [
  { id: 1, name: 'AirPods Pro 2nd Gen', price: 4_290_000, original: 6_990_000, sold: 87, total: 100 },
  { id: 2, name: 'iPhone 15 Case MagSafe', price: 349_000, original: 890_000, sold: 45, total: 50 },
  { id: 3, name: 'Samsung Galaxy Watch 6', price: 5_490_000, original: 7_290_000, sold: 23, total: 30 },
  { id: 4, name: 'Balo laptop Samsonite', price: 1_290_000, original: 2_500_000, sold: 12, total: 20 },
];

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

function Countdown({ seconds }: { seconds: number }) {
  const h = String(Math.floor(seconds / 3600)).padStart(2, '0');
  const m = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0');
  const s = String(seconds % 60).padStart(2, '0');
  return (
    <div className="flex items-center gap-1">
      {[h, m, s].map((unit, i) => (
        <span key={i} className="flex items-center gap-1">
          <span className="bg-white text-gray-900 font-bold text-xl w-10 h-10 flex items-center justify-center rounded-lg shadow-sm tabular-nums">
            {unit}
          </span>
          {i < 2 && <span className="text-white font-bold text-lg">:</span>}
        </span>
      ))}
    </div>
  );
}

export default function FlashSalePage() {
  const [timeLeft, setTimeLeft] = useState(3600 * 2 + 47 * 60 + 13);

  useEffect(() => {
    const t = setInterval(() => setTimeLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(t);
  }, []);

  return (
    <div className="bg-gray-50 min-h-screen">
      {/* Hero */}
      <div className="bg-gradient-to-r from-red-500 via-orange-500 to-yellow-400 text-white py-10 px-4">
        <div className="max-w-7xl mx-auto text-center">
          <div className="flex items-center justify-center gap-2 mb-2">
            <span className="text-3xl animate-bounce">⚡</span>
            <h1 className="text-4xl font-black tracking-tight">FLASH SALE</h1>
            <span className="text-3xl animate-bounce">⚡</span>
          </div>
          <p className="text-orange-100 mb-6 text-lg">Giảm giá cực sốc — chỉ trong hôm nay!</p>
          <div className="flex flex-col items-center gap-2">
            <p className="text-sm text-orange-100 uppercase tracking-widest font-medium">Kết thúc sau</p>
            <Countdown seconds={timeLeft} />
          </div>
        </div>
      </div>

      {/* Session tabs */}
      <div className="bg-white border-b border-gray-200 sticky top-16 z-30">
        <div className="max-w-7xl mx-auto px-4 flex gap-1 overflow-x-auto py-2 scrollbar-none">
          {['14:00', '16:00', '18:00', '20:00', '22:00'].map((time, i) => (
            <button
              key={time}
              className={`shrink-0 flex flex-col items-center px-5 py-2 rounded-xl text-sm font-medium transition-all ${
                i === 1
                  ? 'bg-red-500 text-white shadow-sm'
                  : 'text-gray-600 hover:bg-gray-100'
              }`}
            >
              <span>{time}</span>
              {i === 1 && <span className="text-xs text-red-100 font-normal">Đang diễn ra</span>}
              {i === 2 && <span className="text-xs text-gray-400 font-normal">Sắp diễn ra</span>}
            </button>
          ))}
        </div>
      </div>

      {/* Products */}
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          {FLASH_PRODUCTS.map((p) => {
            const pct = Math.round((p.sold / p.total) * 100);
            const disc = Math.round((1 - p.price / p.original) * 100);
            return (
              <div key={p.id} className="bg-white rounded-2xl border border-gray-100 overflow-hidden hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 cursor-pointer group">
                <div className="relative bg-gradient-to-br from-orange-50 to-red-50 aspect-square flex items-center justify-center">
                  <span className="text-5xl">🔥</span>
                  <span className="absolute top-2 left-2 bg-red-500 text-white font-black text-sm px-2 py-1 rounded-lg">
                    -{disc}%
                  </span>
                </div>
                <div className="p-3">
                  <h3 className="text-sm font-medium text-gray-900 line-clamp-2 mb-2 group-hover:text-red-600 transition-colors">
                    {p.name}
                  </h3>
                  <div className="flex items-baseline gap-2 mb-2">
                    <span className="text-base font-bold text-red-600">{fmt(p.price)}</span>
                    <span className="text-xs text-gray-400 line-through">{fmt(p.original)}</span>
                  </div>
                  {/* Progress */}
                  <div className="mb-2">
                    <div className="flex justify-between text-xs text-gray-500 mb-1">
                      <span>Đã bán {pct}%</span>
                      <span>Còn {p.total - p.sold}</span>
                    </div>
                    <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div
                        className={`h-full rounded-full transition-all ${pct > 80 ? 'bg-red-500' : 'bg-orange-400'}`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                  <button className="w-full py-2 bg-gradient-to-r from-red-500 to-orange-500 hover:from-red-600 hover:to-orange-600 text-white text-xs font-semibold rounded-xl transition-all">
                    Mua ngay
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
