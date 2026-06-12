import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from '@shared/components/Layout';
import PrivateRoute from '@shared/components/PrivateRoute';
import ChatWidget from '@/components/ChatWidget';
import { useAuthStore } from '@shared/store/authStore';

const LoginPage          = lazy(() => import('@shared/pages/LoginPage'));
const RegisterPage       = lazy(() => import('@shared/pages/RegisterPage'));
const ProductListPage    = lazy(() => import('@/pages/ProductListPage'));
const ProductDetailPage  = lazy(() => import('@/pages/ProductDetailPage'));
const CartPage           = lazy(() => import('@/pages/CartPage'));
const OrderReviewPage    = lazy(() => import('@/pages/OrderReviewPage'));
const CheckoutPage       = lazy(() => import('@/pages/CheckoutPage'));
const CheckoutResultPage = lazy(() => import('@/pages/CheckoutResultPage'));
const FlashSalePage      = lazy(() => import('@/pages/FlashSalePage'));
const OrderHistoryPage   = lazy(() => import('@/pages/OrderHistoryPage'));
const OrderDetailPage    = lazy(() => import('@/pages/OrderDetailPage'));
const ProfilePage        = lazy(() => import('@/pages/ProfilePage'));
const AddressPage        = lazy(() => import('@/pages/AddressPage'));
const AccountSettingsPage = lazy(() => import('@/pages/AccountSettingsPage'));
const RefundHistoryPage = lazy(() => import('@/pages/RefundHistoryPage'));
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage'));

const NAV_LINKS = [
  { label: 'Sản phẩm', to: '/products' },
  { label: 'Flash Sale', to: '/flash-sales' },
];

const AUTH_LINKS = [
  { label: 'Thông báo', to: '/notifications' },
  { label: 'Giỏ hàng', to: '/cart' },
  { label: 'Đơn hàng', to: '/orders' },
  { label: 'Hoàn tiền', to: '/refunds' },
  { label: 'Hồ sơ', to: '/profile' },
  { label: 'Địa chỉ', to: '/addresses' },
  { label: 'Cài đặt', to: '/account-settings' },
];

export default function App() {
  const { isAuthenticated } = useAuthStore();

  return (
    <Suspense fallback={<div className="p-8 text-center">Loading...</div>}>
      <Routes>
        {/* Auth pages — no layout */}
        <Route path="/login"    element={<LoginPage redirectTo="/products" />} />
        <Route path="/register" element={<RegisterPage redirectTo="/products" />} />

        {/* All other pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <Layout appName="FlashSale" links={NAV_LINKS} authLinks={AUTH_LINKS}>
              <Routes>
                <Route path="/products"        element={<ProductListPage />} />
                <Route path="/products/:productId" element={<ProductDetailPage />} />
                <Route path="/flash-sales"     element={<FlashSalePage />} />
                <Route path="/checkout/result" element={<CheckoutResultPage />} />

                <Route path="/cart"     element={<PrivateRoute><CartPage /></PrivateRoute>} />
                <Route path="/checkout" element={<PrivateRoute><OrderReviewPage /></PrivateRoute>} />
                <Route path="/checkout/payment" element={<PrivateRoute><CheckoutPage /></PrivateRoute>} />
                <Route path="/orders"   element={<PrivateRoute><OrderHistoryPage /></PrivateRoute>} />
                <Route path="/orders/:parentOrderId" element={<PrivateRoute><OrderDetailPage /></PrivateRoute>} />
                <Route path="/refunds" element={<PrivateRoute><RefundHistoryPage /></PrivateRoute>} />

                <Route path="/profile"          element={<PrivateRoute><ProfilePage /></PrivateRoute>} />
                <Route path="/addresses"        element={<PrivateRoute><AddressPage /></PrivateRoute>} />
                <Route path="/account-settings" element={<PrivateRoute><AccountSettingsPage /></PrivateRoute>} />
                <Route path="/notifications"    element={<PrivateRoute><NotificationsPage /></PrivateRoute>} />

                <Route path="/"  element={<Navigate to="/products" replace />} />
                <Route path="*"  element={<Navigate to="/" replace />} />
              </Routes>
            </Layout>
          }
        />
      </Routes>
      {isAuthenticated && <ChatWidget />}
    </Suspense>
  );
}
