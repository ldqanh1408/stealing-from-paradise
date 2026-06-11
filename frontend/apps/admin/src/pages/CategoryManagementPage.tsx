import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { categoryApi, adminCategoryApi, type Category } from '@shared/api/category.api';
import CategoryFormModal from '@/components/Categories/CategoryFormModal';

// Helper to slugify Vietnamese text
const slugify = (text: string) => {
  return text
    .toString()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '') // remove accents
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/\s+/g, '-') // replace spaces with -
    .replace(/[^\w\-]+/g, '') // remove all non-word chars
    .replace(/\-\-+/g, '-') // replace multiple - with single -
    .replace(/^-+/, '') // trim - from start of text
    .replace(/-+$/, ''); // trim - from end of text
};

export default function CategoryManagementPage() {
  const queryClient = useQueryClient();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);

  // Form states
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [parentId, setParentId] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  // Delete confirm state
  const [deletingCategory, setDeletingCategory] = useState<Category | null>(null);

  // Fetch categories
  const { data: categories = [], isLoading, error } = useQuery({
    queryKey: ['admin-categories'],
    queryFn: () => categoryApi.getCategories().then((r) => r.data.data ?? []),
  });

  // Auto-slugify when name changes (only if slug was empty or matches name slug)
  const handleNameChange = (val: string) => {
    setName(val);
    if (!editingCategory || slug === slugify(name)) {
      setSlug(slugify(val));
    }
  };

  // Open modal for Create
  const handleOpenCreate = () => {
    setEditingCategory(null);
    setName('');
    setSlug('');
    setParentId('');
    setErrorMsg('');
    setIsModalOpen(true);
  };

  // Open modal for Edit
  const handleOpenEdit = (cat: Category) => {
    setEditingCategory(cat);
    setName(cat.name);
    setSlug(cat.slug);
    setParentId(cat.parentId || '');
    setErrorMsg('');
    setIsModalOpen(true);
  };

  // Create Mutation
  const createMut = useMutation({
    mutationFn: (body: { name: string; slug: string; parentId: string | null; level: number }) =>
      adminCategoryApi.create(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] });
      setIsModalOpen(false);
    },
    onError: (err: any) => {
      setErrorMsg(err?.response?.data?.message || 'Đã xảy ra lỗi khi tạo danh mục.');
    },
  });

  // Update Mutation
  const updateMut = useMutation({
    mutationFn: (data: { categoryId: string; body: { name: string; slug: string; parentId: string | null; level: number } }) =>
      adminCategoryApi.update(data.categoryId, data.body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] });
      setIsModalOpen(false);
    },
    onError: (err: any) => {
      setErrorMsg(err?.response?.data?.message || 'Đã xảy ra lỗi khi cập nhật danh mục.');
    },
  });

  // Delete Mutation
  const deleteMut = useMutation({
    mutationFn: (categoryId: string) => adminCategoryApi.delete(categoryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] });
      setDeletingCategory(null);
    },
    onError: (err: any) => {
      alert(err?.response?.data?.message || 'Không thể xóa danh mục này.');
      setDeletingCategory(null);
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    if (!name.trim()) {
      setErrorMsg('Vui lòng nhập tên danh mục');
      return;
    }
    if (!slug.trim()) {
      setErrorMsg('Vui lòng nhập slug');
      return;
    }

    // Determine level
    let level = 1;
    if (parentId) {
      const parent = categories.find((c) => c.categoryId === parentId);
      if (parent) {
        level = parent.level + 1;
      }
    }

    const body = {
      name: name.trim(),
      slug: slug.trim(),
      parentId: parentId || null,
      level,
    };

    if (editingCategory) {
      updateMut.mutate({ categoryId: editingCategory.categoryId, body });
    } else {
      createMut.mutate(body);
    }
  };

  // Get parent category name
  const getParentName = (pId?: string | null) => {
    if (!pId) return '-';
    const parent = categories.find((c) => c.categoryId === pId);
    return parent ? parent.name : '-';
  };

  // Get options for parent selection (excluding current editing category to prevent self-parent cycles)
  const parentOptions = categories.filter(
    (c) => !editingCategory || c.categoryId !== editingCategory.categoryId
  );

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Quản lý danh mục</h1>
          <p className="text-sm text-gray-500 mt-1">
            {categories.length > 0 ? `${categories.length} danh mục hiện có` : 'Không có danh mục nào'}
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={() => queryClient.invalidateQueries({ queryKey: ['admin-categories'] })}
            className="px-3 py-1.5 text-sm border border-gray-200 bg-white rounded-xl hover:bg-gray-50 text-gray-600 font-medium transition-all"
          >
            🔄 Làm mới
          </button>
          <button
            onClick={handleOpenCreate}
            className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-xl hover:bg-blue-700 font-medium shadow-sm transition-all"
          >
            ➕ Thêm danh mục
          </button>
        </div>
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl animate-bounce mb-3">⏳</div>
          Đang tải danh sách danh mục...
        </div>
      )}

      {/* Error state */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-2xl p-4 text-red-700 text-sm mb-6 flex items-center gap-2">
          <span>⚠️</span>
          <span>Không thể tải danh sách danh mục. Vui lòng thử lại.</span>
        </div>
      )}

      {/* Categories Table */}
      {!isLoading && !error && (
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
                          onClick={() => handleOpenEdit(cat)}
                          className="px-2.5 py-1 text-xs border border-gray-200 text-blue-600 bg-white hover:bg-blue-50 hover:border-blue-200 rounded-lg font-medium transition-all"
                        >
                          Sửa
                        </button>
                        <button
                          onClick={() => setDeletingCategory(cat)}
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
      )}

      {/* Create / Edit Modal */}
      {isModalOpen && (
        <CategoryFormModal
          editingCategory={editingCategory}
          name={name}
          slug={slug}
          parentId={parentId}
          parentOptions={parentOptions}
          errorMsg={errorMsg}
          isPending={createMut.isPending || updateMut.isPending}
          onNameChange={handleNameChange}
          onSlugChange={setSlug}
          onParentIdChange={setParentId}
          onSubmit={handleSubmit}
          onClose={() => setIsModalOpen(false)}
        />
      )}

      {/* Delete Confirmation Modal */}
      {deletingCategory && (
        <div className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 max-w-sm w-full text-center border border-gray-100 shadow-xl">
            <div className="text-5xl mb-4">⚠️</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Xác nhận xóa danh mục?</h3>
            <p className="text-sm text-gray-500 mb-6">
              Bạn có chắc chắn muốn xóa danh mục "{deletingCategory.name}"? Hành động này không thể hoàn tác.
            </p>
            <div className="flex gap-3">
              <button
                onClick={() => setDeletingCategory(null)}
                className="flex-1 py-2.5 border border-gray-200 rounded-xl text-sm font-medium hover:bg-gray-50 text-gray-700"
              >
                Huỷ
              </button>
              <button
                onClick={() => deleteMut.mutate(deletingCategory.categoryId)}
                disabled={deleteMut.isPending}
                className="flex-1 py-2.5 text-white bg-red-600 hover:bg-red-700 rounded-xl text-sm font-medium disabled:opacity-50 transition-all"
              >
                {deleteMut.isPending ? 'Đang xóa...' : 'Xóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
