const STATUS_TABS = [
  { value: '', label: 'Tất cả' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'ACTIVE', label: 'Đang bán' },
  { value: 'INACTIVE', label: 'Đã ẩn' },
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'DRAFT', label: 'Nháp' },
  { value: 'OUT_OF_STOCK', label: 'Hết hàng' },
];

interface ProductTabsProps {
  statusFilter: string;
  onStatusFilterChange: (status: string) => void;
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
}

export default function ProductTabs({
  statusFilter,
  onStatusFilterChange,
  searchQuery,
  onSearchQueryChange,
}: ProductTabsProps) {
  return (
    <div className="flex gap-4 mb-5 flex-wrap">
      <div className="flex gap-2 flex-wrap">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.value}
            onClick={() => onStatusFilterChange(tab.value)}
            className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
              statusFilter === tab.value
                ? 'bg-blue-600 text-white border-blue-600'
                : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <input
        type="text"
        value={searchQuery}
        onChange={(e) => onSearchQueryChange(e.target.value)}
        placeholder="Tìm sản phẩm..."
        className="px-4 py-1.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </div>
  );
}
