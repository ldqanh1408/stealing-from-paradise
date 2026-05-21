import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from '@shared/components/Layout';
import PrivateRoute from '@shared/components/PrivateRoute';

const LoginPage             = lazy(() => import('@shared/pages/LoginPage'));
const AdminDashboard        = lazy(() => import('@/pages/AdminDashboard'));
const UserManagementPage    = lazy(() => import('@/pages/UserManagementPage'));
const CategoryManagementPage = lazy(() => import('@/pages/CategoryManagementPage'));
const ProductModerationPage = lazy(() => import('@/pages/ProductModerationPage'));
const RefundsPage           = lazy(() => import('@/pages/RefundsPage'));
const FlashSaleConfigPage   = lazy(() => import('@/pages/FlashSaleConfigPage'));

const ADMIN_LINKS = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Danh mục', to: '/categories' },
  { label: 'Người dùng', to: '/users' },
  { label: 'Duyệt sản phẩm', to: '/product-moderation' },
  { label: 'Hoàn tiền', to: '/refunds' },
  { label: 'Flash Sale', to: '/flash-sale-config' },
];

export default function App() {
  return (
    <Suspense fallback={<div className="p-8 text-center">Loading...</div>}>
      <Routes>
        {/* Auth page — no layout, no register for admin */}
        <Route path="/login" element={<LoginPage title="Admin Login" redirectTo="/dashboard" showRegisterLink={false} />} />

        {/* Protected pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <Layout appName="FlashSale Admin" authLinks={ADMIN_LINKS}>
              <Routes>
                <Route path="/dashboard"          element={<PrivateRoute role="ADMIN"><AdminDashboard /></PrivateRoute>} />
                <Route path="/categories"         element={<PrivateRoute role="ADMIN"><CategoryManagementPage /></PrivateRoute>} />
                <Route path="/users"              element={<PrivateRoute role="ADMIN"><UserManagementPage /></PrivateRoute>} />
                <Route path="/product-moderation" element={<PrivateRoute role="ADMIN"><ProductModerationPage /></PrivateRoute>} />
                <Route path="/refunds"            element={<PrivateRoute role="ADMIN"><RefundsPage /></PrivateRoute>} />
                <Route path="/flash-sale-config"  element={<PrivateRoute role="ADMIN"><FlashSaleConfigPage /></PrivateRoute>} />

                <Route path="/"  element={<Navigate to="/dashboard" replace />} />
                <Route path="*"  element={<Navigate to="/" replace />} />
              </Routes>
            </Layout>
          }
        />
      </Routes>
    </Suspense>
  );
}
