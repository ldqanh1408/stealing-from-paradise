import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

const LoginPage = lazy(() => import('@/pages/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/RegisterPage'));
const AdminDashboard = lazy(() => import('@/pages/AdminDashboard'));
const UserManagementPage = lazy(() => import('@/pages/UserManagementPage'));
const ProductModerationPage = lazy(() => import('@/pages/ProductModerationPage'));
const RefundsPage = lazy(() => import('@/pages/RefundsPage'));
const FlashSaleConfigPage = lazy(() => import('@/pages/FlashSaleConfigPage'));

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

        {/* Protected — ADMIN */}
        <Route path="/dashboard"              element={<PrivateRoute role="ADMIN"><AdminDashboard /></PrivateRoute>} />
        <Route path="/users"                  element={<PrivateRoute role="ADMIN"><UserManagementPage /></PrivateRoute>} />
        <Route path="/product-moderation"     element={<PrivateRoute role="ADMIN"><ProductModerationPage /></PrivateRoute>} />
        <Route path="/refunds"                element={<PrivateRoute role="ADMIN"><RefundsPage /></PrivateRoute>} />
        <Route path="/flash-sale-config"      element={<PrivateRoute role="ADMIN"><FlashSaleConfigPage /></PrivateRoute>} />

        <Route path="/"  element={<Navigate to="/dashboard" replace />} />
        <Route path="*"  element={<Navigate to="/"         replace />} />
      </Routes>
    </Suspense>
  );
}

