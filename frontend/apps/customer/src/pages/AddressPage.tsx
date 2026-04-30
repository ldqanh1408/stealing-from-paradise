import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { addressApi } from '@shared/api/address.api';
import { userApi, type AddressResponse } from '@shared/api/user.api';

interface AddressFormData {
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

function AddressModal({ address, defaultData, onClose, onSuccess }: {
  address?: AddressResponse;
  defaultData?: AddressFormData;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<AddressFormData>({
    provinceId: address?.provinceId ?? defaultData?.provinceId ?? 0,
    districtId: address?.districtId ?? defaultData?.districtId ?? 0,
    fullAddress: address?.fullAddress ?? defaultData?.fullAddress ?? '',
    isDefault: address?.isDefault ?? defaultData?.isDefault ?? false,
  });
  const [error, setError] = useState('');

  const createMut = useMutation({
    mutationFn: () => userApi ? addressApi.create(form) : Promise.resolve(null as any),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Lưu thất bại'),
  });

  const updateMut = useMutation({
    mutationFn: () => addressApi.update(address!.addressId, form),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); onSuccess(); onClose(); },
    onError: (err: any) => setError(err?.response?.data?.message ?? 'Cập nhật thất bại'),
  });

  const mut = address ? updateMut : createMut;

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl p-6 w-full max-w-md">
        <h3 className="text-lg font-bold text-gray-900 mb-5">
          {address ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
        </h3>
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-4">
            {error}
          </div>
        )}
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Tỉnh / Thành phố</label>
            <select
              value={form.provinceId}
              onChange={e => setForm(f => ({ ...f, provinceId: Number(e.target.value), districtId: 0 }))}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value={0}>Chọn tỉnh/thành phố</option>
              {PROVINCES.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Quận / Huyện</label>
            <select
              value={form.districtId}
              onChange={e => setForm(f => ({ ...f, districtId: Number(e.target.value) }))}
              disabled={!form.provinceId}
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-50 disabled:cursor-not-allowed"
            >
              <option value={0}>Chọn quận/huyện</option>
              {DISTRICTS.filter(d => d.provinceId === form.provinceId).map(d => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">Địa chỉ chi tiết</label>
            <textarea
              value={form.fullAddress}
              onChange={e => setForm(f => ({ ...f, fullAddress: e.target.value }))}
              rows={3}
              placeholder="Số nhà, đường, phường/xã..."
              className="w-full px-4 py-2.5 border border-gray-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
            />
          </div>
          <label className="flex items-center gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={form.isDefault}
              onChange={e => setForm(f => ({ ...f, isDefault: e.target.checked }))}
              className="w-4 h-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
            />
            <span className="text-sm text-gray-700">Đặt làm địa chỉ mặc định</span>
          </label>
        </div>
        <div className="flex gap-3 mt-6">
          <button onClick={onClose} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
            Huỷ
          </button>
          <button
            onClick={() => mut.mutate()}
            disabled={mut.isPending || !form.provinceId || !form.districtId || !form.fullAddress.trim()}
            className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
          >
            {mut.isPending ? 'Đang lưu...' : 'Lưu'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Minimal Vietnam province/district data (expandable)
const PROVINCES = [
  { id: 1, name: 'Hồ Chí Minh' },
  { id: 2, name: 'Hà Nội' },
  { id: 3, name: 'Đà Nẵng' },
  { id: 4, name: 'Hải Phòng' },
  { id: 5, name: 'Cần Thơ' },
  { id: 6, name: 'An Giang' },
  { id: 7, name: 'Bà Rịa - Vũng Tàu' },
  { id: 8, name: 'Bắc Giang' },
  { id: 9, name: 'Bắc Kạn' },
  { id: 10, name: 'Bạc Liêu' },
  { id: 11, name: 'Bắc Ninh' },
  { id: 12, name: 'Bến Tre' },
  { id: 13, name: 'Bình Định' },
  { id: 14, name: 'Bình Dương' },
  { id: 15, name: 'Bình Phước' },
  { id: 16, name: 'Bình Thuận' },
  { id: 17, name: 'Cà Mau' },
  { id: 18, name: 'Cao Bằng' },
  { id: 19, name: 'Đắk Lắk' },
  { id: 20, name: 'Đắk Nông' },
  { id: 21, name: 'Điện Biên' },
  { id: 22, name: 'Đồng Nai' },
  { id: 23, name: 'Đồng Tháp' },
  { id: 24, name: 'Gia Lai' },
  { id: 25, name: 'Hà Giang' },
  { id: 26, name: 'Hà Nam' },
  { id: 27, name: 'Hà Tĩnh' },
  { id: 28, name: 'Hải Dương' },
  { id: 29, name: 'Hậu Giang' },
  { id: 30, name: 'Hòa Bình' },
  { id: 31, name: 'Hưng Yên' },
  { id: 32, name: 'Khánh Hòa' },
  { id: 33, name: 'Kiên Giang' },
  { id: 34, name: 'Kon Tum' },
  { id: 35, name: 'Lai Châu' },
  { id: 36, name: 'Lâm Đồng' },
  { id: 37, name: 'Lạng Sơn' },
  { id: 38, name: 'Lào Cai' },
  { id: 39, name: 'Long An' },
  { id: 40, name: 'Nam Định' },
  { id: 41, name: 'Nghệ An' },
  { id: 42, name: 'Ninh Bình' },
  { id: 43, name: 'Ninh Thuận' },
  { id: 44, name: 'Phú Thọ' },
  { id: 45, name: 'Phú Yên' },
  { id: 46, name: 'Quảng Bình' },
  { id: 47, name: 'Quảng Nam' },
  { id: 48, name: 'Quảng Ngãi' },
  { id: 49, name: 'Quảng Ninh' },
  { id: 50, name: 'Quảng Trị' },
  { id: 51, name: 'Sóc Trăng' },
  { id: 52, name: 'Sơn La' },
  { id: 53, name: 'Tây Ninh' },
  { id: 54, name: 'Thái Bình' },
  { id: 55, name: 'Thái Nguyên' },
  { id: 56, name: 'Thanh Hóa' },
  { id: 57, name: 'Thừa Thiên Huế' },
  { id: 58, name: 'Tiền Giang' },
  { id: 59, name: 'Trà Vinh' },
  { id: 60, name: 'Tuyên Quang' },
  { id: 61, name: 'Vĩnh Long' },
  { id: 62, name: 'Vĩnh Phúc' },
  { id: 63, name: 'Yên Bái' },
];

const DISTRICTS = [
  { id: 1, provinceId: 1, name: 'Quận 1' },
  { id: 2, provinceId: 1, name: 'Quận 2' },
  { id: 3, provinceId: 1, name: 'Quận 3' },
  { id: 4, provinceId: 1, name: 'Quận 4' },
  { id: 5, provinceId: 1, name: 'Quận 5' },
  { id: 6, provinceId: 1, name: 'Quận 6' },
  { id: 7, provinceId: 1, name: 'Quận 7' },
  { id: 8, provinceId: 1, name: 'Quận 8' },
  { id: 9, provinceId: 1, name: 'Quận 9' },
  { id: 10, provinceId: 1, name: 'Quận 10' },
  { id: 11, provinceId: 1, name: 'Quận 11' },
  { id: 12, provinceId: 1, name: 'Quận 12' },
  { id: 13, provinceId: 1, name: 'Bình Thạnh' },
  { id: 14, provinceId: 1, name: 'Gò Vấp' },
  { id: 15, provinceId: 1, name: 'Phú Nhuận' },
  { id: 16, provinceId: 1, name: 'Tân Bình' },
  { id: 17, provinceId: 1, name: 'Tân Phú' },
  { id: 18, provinceId: 1, name: 'Thủ Đức' },
  { id: 19, provinceId: 1, name: 'Bình Tân' },
  { id: 20, provinceId: 1, name: 'Hóc Môn' },
  { id: 21, provinceId: 1, name: 'Củ Chi' },
  { id: 22, provinceId: 1, name: 'Nhà Bè' },
  { id: 23, provinceId: 1, name: 'Cần Giờ' },
  { id: 24, provinceId: 2, name: 'Hoàn Kiếm' },
  { id: 25, provinceId: 2, name: 'Đống Đa' },
  { id: 26, provinceId: 2, name: 'Ba Đình' },
  { id: 27, provinceId: 2, name: 'Hai Bà Trưng' },
  { id: 28, provinceId: 2, name: 'Hoàng Mai' },
  { id: 29, provinceId: 2, name: 'Thanh Xuân' },
  { id: 30, provinceId: 2, name: 'Long Biên' },
  { id: 31, provinceId: 2, name: 'Nam Từ Liêm' },
  { id: 32, provinceId: 2, name: 'Bắc Từ Liêm' },
  { id: 33, provinceId: 2, name: 'Tây Hồ' },
  { id: 34, provinceId: 2, name: 'Cầu Giấy' },
  { id: 35, provinceId: 3, name: 'Hải Châu' },
  { id: 36, provinceId: 3, name: 'Thanh Khê' },
  { id: 37, provinceId: 3, name: 'Sơn Trà' },
  { id: 38, provinceId: 3, name: 'Ngũ Hành Sơn' },
  { id: 39, provinceId: 3, name: 'Liên Chiểu' },
  { id: 40, provinceId: 3, name: 'Hòa Vang' },
];

function getProvinceName(id: number) {
  return PROVINCES.find(p => p.id === id)?.name ?? `Tỉnh #${id}`;
}
function getDistrictName(id: number) {
  return DISTRICTS.find(d => d.id === id)?.name ?? `Huyện #${id}`;
}

export default function AddressPage() {
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [editAddr, setEditAddr] = useState<AddressResponse | undefined>();
  const [defaultData, setDefaultData] = useState<AddressFormData | undefined>();
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const { data: addresses, isLoading, error } = useQuery({
    queryKey: ['user-addresses'],
    queryFn: () => addressApi.list().then(r => r.data.data ?? []),
    retry: 1,
  });

  const deleteMut = useMutation({
    mutationFn: (id: number) => addressApi.remove(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['user-addresses'] }); setDeletingId(null); },
  });

  return (
    <div className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Địa chỉ giao hàng</h1>
          <p className="text-gray-500 mt-1 text-sm">Quản lý danh sách địa chỉ nhận hàng của bạn</p>
        </div>
        <button
          onClick={() => { setEditAddr(undefined); setDefaultData(undefined); setAddOpen(true); }}
          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold flex items-center gap-2 transition-all"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          Thêm địa chỉ
        </button>
      </div>

      {isLoading && (
        <div className="text-center py-20 text-gray-400">
          <div className="text-4xl mb-3">⏳</div>Đang tải...
        </div>
      )}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-red-700 text-sm">
          Không thể tải danh sách địa chỉ.
        </div>
      )}

      {!isLoading && !error && (!addresses || addresses.length === 0) && (
        <div className="bg-white rounded-2xl border border-gray-100 py-20 text-center text-gray-400">
          <span className="text-4xl block mb-3">📍</span>
          <p className="font-medium text-gray-600">Chưa có địa chỉ nào</p>
          <p className="text-sm mt-1">Thêm địa chỉ để mua hàng nhanh hơn</p>
          <button onClick={() => setAddOpen(true)} className="mt-4 px-5 py-2 bg-blue-600 text-white rounded-xl text-sm font-semibold">
            Thêm địa chỉ đầu tiên
          </button>
        </div>
      )}

      <div className="space-y-3">
        {addresses?.map(addr => (
          <div key={addr.addressId} className="bg-white rounded-2xl border border-gray-100 p-5">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                {addr.isDefault && (
                  <span className="inline-block px-2.5 py-0.5 bg-blue-100 text-blue-700 text-xs font-semibold rounded-full mb-2">
                    Mặc định
                  </span>
                )}
                <p className="text-sm font-medium text-gray-900 leading-relaxed">
                  {addr.fullAddress}
                </p>
                <p className="text-xs text-gray-400 mt-1">
                  {getDistrictName(addr.districtId)}, {getProvinceName(addr.provinceId)}
                </p>
              </div>
              <div className="flex items-center gap-2 ml-4">
                <button
                  onClick={() => { setEditAddr(addr); setAddOpen(true); }}
                  className="text-xs text-blue-600 hover:text-blue-700 font-medium px-3 py-1.5 border border-blue-200 rounded-lg hover:bg-blue-50 transition-colors"
                >
                  Sửa
                </button>
                <button
                  onClick={() => setDeletingId(addr.addressId)}
                  className="text-xs text-red-500 hover:text-red-600 px-3 py-1.5 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                >
                  Xoá
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {addOpen && (
        <AddressModal
          address={editAddr}
          defaultData={defaultData}
          onClose={() => { setAddOpen(false); setEditAddr(undefined); setDefaultData(undefined); }}
          onSuccess={() => queryClient.invalidateQueries({ queryKey: ['user-addresses'] })}
        />
      )}

      {deletingId !== null && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-sm text-center">
            <div className="text-5xl mb-4">🗑️</div>
            <h3 className="text-lg font-bold text-gray-900 mb-2">Xoá địa chỉ?</h3>
            <p className="text-sm text-gray-500 mb-6">Hành động này không thể hoàn tác.</p>
            <div className="flex gap-3">
              <button onClick={() => setDeletingId(null)} className="flex-1 py-2.5 border border-gray-300 rounded-xl text-sm font-medium hover:bg-gray-50">
                Huỷ
              </button>
              <button
                onClick={() => deleteMut.mutate(deletingId)}
                disabled={deleteMut.isPending}
                className="flex-1 py-2.5 bg-red-600 hover:bg-red-700 text-white rounded-xl text-sm font-medium disabled:opacity-60"
              >
                {deleteMut.isPending ? 'Đang xoá...' : 'Xoá'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
