import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { cartApi } from '@shared/api/cart.api';
import { orderApi } from '@shared/api/order.api';

const fmt = (n: number) => n.toLocaleString('vi-VN') + '₫';

interface PreviewItem {
  variantId: string;
  skuCode: string;
  productName: string;
  variantName: string;
  priceSnapshot: number;
  quantity: number;
  imageUrl?: string;
  subtotal: number;
  sellerId: number;
}

interface PreviewSellerGroup {
  sellerId: number;
  sellerName?: string;
  items: PreviewItem[];
  subtotal: number;
}

interface PreviewData {
  previewToken: string;
  expiresAt: string;
  customerId: number;
  sellers: PreviewSellerGroup[];
  totalItems: number;
  totalAmount: number;
  allValid: boolean;
}

export default function CheckoutPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasAddress, setHasAddress] = useState(false);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);

  const selectedItemIds: string[] = location.state?.selectedItemIds ?? [];

  // Fetch addresses
  const { data: addresses = [] } = useQuery({
    queryKey: ['addresses'],
    queryFn: async () => {
      const { addressApi } = await import('@shared/api/address.api');
      const res = await addressApi.list();
      return res.data.data ?? [];
    },
    retry: 1,
  });

  // Auto-select default address
  useEffect(() => {
    const def = addresses.find((a: any) => a.isDefault);
    if (def) {
      setSelectedAddressId(def.addressId);
      setHasAddress(true);
    } else if (addresses.length > 0) {
      setSelectedAddressId(addresses[0].addressId);
      setHasAddress(true);
    }
  }, [addresses]);

  // Call checkout preview
  const { data: previewData, isLoading: isPreviewLoading, error: previewError } = useQuery({
    queryKey: ['checkout-preview', selectedItemIds.join(',')],
    queryFn: () => cartApi.checkoutPreview(selectedItemIds).then(r => r.data.data),
    enabled: selectedItemIds.length > 0,
    retry: 1,
  });

  const handlePlaceOrder = async () => {
    if (!previewData || !selectedAddressId) return;
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const { data: submitRes } = await cartApi.checkoutSubmit(
        previewData.previewToken,
        selectedAddressId
      );
      if (submitRes?.data) {
        sessionStorage.setItem('pending_checkout', JSON.stringify(submitRes.data));
        navigate('/checkout/payment', {
          state: { orderData: submitRes.data },
        });
      }
    } catch (err: any) {
      setSubmitError(
        err?.response?.data?.message ||
        err?.response?.data?.error ||
        'Đặt hàng thất bại. Vui lòng thử lại.'
      );
      setIsSubmitting(false);
    }
  };

  if (selectedItemIds.length === 0) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không có sản phẩm nào được chọn.</p>
        <button onClick={() => navigate('/cart')} className="text-blue-600 hover:underline">
          ← Quay lại giỏ hàng
        </button>
      </div>
    );
  }

  if (isPreviewLoading) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <p className="text-gray-500">Đang kiểm tra thông tin sản phẩm...</p>
      </div>
    );
  }

  if (previewError || !previewData) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-20 text-center">
        <p className="text-red-500 mb-4">Không thể xem thông tin đơn hàng. Vui lòng quay lại giỏ hàng.</p>
        <button onClick={() => navigate('/cart')} className="text-blue-600 hover:underline">
          ← Quay lại giỏ hàng
        </button>
      </div>
    );
  }

  const totalAmount = previewData.totalAmount;

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Xác nhận đơn hàng</h1>

      {/* Error */}
      {submitError && (
        <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-xl text-red-700 text-sm">
          {submitError}
        </div>
      )}

      {/* Items preview */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
        <h2 className="font-bold text-gray-900 mb-4">📦 Sản phẩm ({previewData.totalItems})</h2>
        <div className="space-y-3">
          {previewData.sellers.map((seller: PreviewSellerGroup) => (
            <div key={seller.sellerId}>
              <p className="text-sm font-semibold text-gray-700 mb-2">
                {seller.sellerName ?? `Seller ${seller.sellerId}`}
              </p>
              {seller.items.map((item: PreviewItem) => (
                <div key={item.variantId} className="flex items-center gap-3 py-2 border-b border-gray-50 last:border-0">
                  <div className="w-14 h-14 bg-gray-100 rounded-lg flex items-center justify-center text-2xl shrink-0">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt={item.productName} className="w-full h-full object-cover rounded" />
                    ) : '🛍️'}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-gray-500">{item.variantName}</p>
                    <p className="text-sm font-medium text-gray-900 truncate">{item.productName}</p>
                  </div>
                  <div className="text-right shrink-0">
                    <p className="font-bold text-red-600">{fmt(item.subtotal)}</p>
                    <p className="text-xs text-gray-400">x{item.quantity}</p>
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>

      {/* Address selection */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
        <h2 className="font-bold text-gray-900 mb-4">📍 Địa chỉ giao hàng</h2>
        {addresses.length === 0 ? (
          <div className="text-center py-4">
              <p className="text-sm text-gray-500">Bạn chưa có địa chỉ giao hàng.</p>
            <button
              onClick={() => navigate('/profile/addresses')}
              className="text-blue-600 hover:underline text-sm"
            >
              + Thêm địa chỉ mới
            </button>
          </div>
        ) : (
          <div className="space-y-2">
            {addresses.map((addr: any) => (
              <label
                key={addr.addressId}
                className={`flex items-center gap-3 p-3 rounded-xl border cursor-pointer transition-all ${
                  selectedAddressId === addr.addressId
                    ? 'border-blue-500 bg-blue-50'
                    : 'border-gray-200 hover:border-blue-300'
                }`}
              >
                <input
                  type="radio"
                  name="address"
                  value={addr.addressId}
                  checked={selectedAddressId === addr.addressId}
                  onChange={() => setSelectedAddressId(addr.addressId)}
                  className="accent-blue-600"
                />
                <div>
                  <p className="text-sm font-medium text-gray-900">
                    {addr.recipientName} · {addr.phone}
                  </p>
                  <p className="text-xs text-gray-500">{addr.fullAddress}</p>
                </div>
                {addr.isDefault && (
                <p className="ml-auto text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">Mặc định</p>
                )}
              </label>
            ))}
          </div>
        )}
      </div>

      {/* Summary */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 mb-6">
        <div className="space-y-2 text-sm mb-4">
          <div className="flex justify-between text-gray-600">
              <p>Tạm tính</p>
              <p>{fmt(totalAmount)}</p>
          </div>
          <div className="flex justify-between text-gray-600">
              <p>Phí ship</p>
              <p className="text-green-600">Miễn phí</p>
          </div>
          <div className="h-px bg-gray-100" />
          <div className="flex justify-between font-bold text-gray-900 text-base">
              <p>Tổng cộng</p>
              <p className="text-red-600">{fmt(totalAmount)}</p>
          </div>
        </div>

        <button
          onClick={handlePlaceOrder}
          disabled={isSubmitting || !hasAddress || !selectedAddressId}
          className={`w-full py-4 font-bold text-lg rounded-xl transition-all ${
            isSubmitting || !hasAddress || !selectedAddressId
              ? 'bg-gray-400 text-white cursor-not-allowed'
              : 'bg-gradient-to-r from-blue-600 to-violet-600 hover:from-blue-700 hover:to-violet-700 text-white'
          }`}
        >
          {isSubmitting ? '⏳ Đang xử lý...' : `Đặt hàng · ${fmt(totalAmount)}`}
        </button>
      </div>

      <button onClick={() => navigate('/cart')} className="text-gray-500 hover:text-gray-700 text-sm">
        ← Quay lại giỏ hàng
      </button>
    </div>
  );
}
