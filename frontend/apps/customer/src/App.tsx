import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@shared/store/authStore';

const LoginPage          = lazy(() => import('@shared/pages/LoginPage'));
const RegisterPage       = lazy(() => import('@shared/pages/RegisterPage'));
const ProductListPage    = lazy(() => import('@/pages/ProductListPage'));
const CartPage           = lazy(() => import('@/pages/CartPage'));
const CheckoutPage       = lazy(() => import('@/pages/CheckoutPage'));
const CheckoutResultPage = lazy(() => import('@/pages/CheckoutResultPage'));
const FlashSalePage      = lazy(() => import('@/pages/FlashSalePage'));

// Guard — redirect nếu chưa đăng nhập hoặc sai role
function PrivateRoute({ children, role }: { children: JSX.Element; role?: string }) {
  const { isAuthenticated, user } = useAuthStore();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role && user?.role !== role) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Suspense fallback={<div className="p-8 text-center">Loading...</div>}>
      <Routes>
        {/* Public */}
        <Route path="/login"           element={<LoginPage />} />
        <Route path="/register"        element={<RegisterPage />} />
        <Route path="/products"        element={<ProductListPage />} />
        <Route path="/flash-sales"     element={<FlashSalePage />} />
        <Route path="/checkout/result" element={<CheckoutResultPage />} />

        {/* Protected — BUYER */}
        <Route path="/cart"     element={<PrivateRoute><CartPage /></PrivateRoute>} />
        <Route path="/checkout" element={<PrivateRoute><CheckoutPage /></PrivateRoute>} />

        <Route path="/"  element={<Navigate to="/products" replace />} />
        <Route path="*"  element={<Navigate to="/"        replace />} />
      </Routes>
    </Suspense>
  );
}
