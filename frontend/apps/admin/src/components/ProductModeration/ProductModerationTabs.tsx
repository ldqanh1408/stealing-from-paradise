const TAB_STATUS = [
  { value: 'PENDING', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Chờ duyệt lại' },
];

interface ProductModerationTabsProps {
  tab: string;
  onTabChange: (tab: string) => void;
}

export default function ProductModerationTabs({
  tab,
  onTabChange,
}: ProductModerationTabsProps) {
  return (
    <div className="flex gap-2 mb-5">
      {TAB_STATUS.map((t) => (
        <button
          key={t.value}
          onClick={() => onTabChange(t.value)}
          className={`px-4 py-1.5 rounded-full text-sm font-medium border transition-all ${
            tab === t.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-600 border-gray-200 hover:border-blue-300'
          }`}
        >
          {t.label}
        </button>
      ))}
    </div>
  );
}
