import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from '@shared/components/Layout';
import PrivateRoute from '@shared/components/PrivateRoute';

const LoginPage          = lazy(() => import('@shared/pages/LoginPage'));
const RegisterPage       = lazy(() => import('@shared/pages/RegisterPage'));
const ProductListPage    = lazy(() => import('@/pages/ProductListPage'));
const CartPage           = lazy(() => import('@/pages/CartPage'));
const CheckoutPage       = lazy(() => import('@/pages/CheckoutPage'));
const CheckoutResultPage = lazy(() => import('@/pages/CheckoutResultPage'));
const FlashSalePage      = lazy(() => import('@/pages/FlashSalePage'));
const OrderHistoryPage   = lazy(() => import('@/pages/OrderHistoryPage'));

const NAV_LINKS = [
  { label: 'Sản phẩm', to: '/products' },
  { label: 'Flash Sale', to: '/flash-sales' },
];

const AUTH_LINKS = [
  { label: 'Giỏ hàng', to: '/cart' },
  { label: 'Đơn hàng', to: '/orders' },
];

export default function App() {
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
                <Route path="/flash-sales"     element={<FlashSalePage />} />
                <Route path="/checkout/result" element={<CheckoutResultPage />} />

                <Route path="/cart"     element={<PrivateRoute><CartPage /></PrivateRoute>} />
                <Route path="/checkout" element={<PrivateRoute><CheckoutPage /></PrivateRoute>} />
                <Route path="/orders"   element={<PrivateRoute><OrderHistoryPage /></PrivateRoute>} />

                <Route path="/"  element={<Navigate to="/products" replace />} />
                <Route path="*"  element={<Navigate to="/" replace />} />
              </Routes>
            </Layout>
          }
        />
      </Routes>
    </Suspense>
  );
}
