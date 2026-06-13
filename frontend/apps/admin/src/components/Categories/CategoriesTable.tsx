import { type Category } from '@shared/api/category.api';

interface CategoriesTableProps {
  categories: Category[];
  getParentName: (parentId?: string | null) => string;
  onEdit: (category: Category) => void;
  onDelete: (category: Category) => void;
}

export default function CategoriesTable({
  categories,
  getParentName,
  onEdit,
  onDelete,
}: CategoriesTableProps) {
  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-100 text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <th className="px-6 py-4">Tên danh mục</th>
              <th className="px-6 py-4">Slug</th>
              <th className="px-6 py-4">Danh mục cha</th>
              <th className="px-6 py-4">Cấp độ (Level)</th>
              <th className="px-6 py-4">Số sản phẩm</th>
              <th className="px-6 py-4 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 text-sm text-gray-700">
            {categories.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-6 py-12 text-center text-gray-400">
                  Chưa có danh mục nào được tạo.
                </td>
              </tr>
            ) : (
              categories.map((cat) => (
                <tr key={cat.categoryId} className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-6 py-4 font-medium text-gray-900">
                    {cat.level > 1 && (
                      <span className="text-gray-300 font-mono mr-1">
                        {'—'.repeat(cat.level - 1)}
                      </span>
                    )}
                    {cat.name}
                  </td>
                  <td className="px-6 py-4 font-mono text-xs text-gray-500">{cat.slug}</td>
                  <td className="px-6 py-4 text-gray-500">
                    {getParentName(cat.parentId)}
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                      Cấp {cat.level}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-gray-500">{cat.productCount ?? 0}</td>
                  <td className="px-6 py-4 text-right space-x-2">
                    <button
                      onClick={() => onEdit(cat)}
                      className="px-2.5 py-1 text-xs border border-gray-200 text-blue-600 bg-white hover:bg-blue-50 hover:border-blue-200 rounded-lg font-medium transition-all"
                    >
                      Sửa
                    </button>
                    <button
                      onClick={() => onDelete(cat)}
                      className="px-2.5 py-1 text-xs border border-gray-200 text-red-600 bg-white hover:bg-red-50 hover:border-red-200 rounded-lg font-medium transition-all"
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
