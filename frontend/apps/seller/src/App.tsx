import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

const LoginPage = lazy(() => import('@/pages/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/RegisterPage'));
const SellerDashboard = lazy(() => import('@/pages/SellerDashboard'));
const ProductManagementPage = lazy(() => import('@/pages/ProductManagementPage'));
const SellerOrdersPage = lazy(() => import('@/pages/SellerOrdersPage'));
const StripeOnboardingPage = lazy(() => import('@/pages/StripeOnboardingPage'));

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
        <Route path="/login"    element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />

        {/* Protected — SELLER */}
        <Route path="/dashboard"            element={<PrivateRoute role="SELLER"><SellerDashboard /></PrivateRoute>} />
        <Route path="/products"             element={<PrivateRoute role="SELLER"><ProductManagementPage /></PrivateRoute>} />
        <Route path="/orders"               element={<PrivateRoute role="SELLER"><SellerOrdersPage /></PrivateRoute>} />
        <Route path="/stripe-onboarding"    element={<PrivateRoute role="SELLER"><StripeOnboardingPage /></PrivateRoute>} />

        <Route path="/"  element={<Navigate to="/dashboard" replace />} />
        <Route path="*"  element={<Navigate to="/"         replace />} />
      </Routes>
    </Suspense>
  );
}

