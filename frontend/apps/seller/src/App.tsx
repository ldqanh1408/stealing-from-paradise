import { lazy, Suspense } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from '@shared/components/Layout';
import PrivateRoute from '@shared/components/PrivateRoute';

const LoginPage              = lazy(() => import('@shared/pages/LoginPage'));
const SellerRegisterPage     = lazy(() => import('@/pages/SellerRegisterPage'));
const SellerDashboard        = lazy(() => import('@/pages/SellerDashboard'));
const ProductManagementPage   = lazy(() => import('@/pages/ProductManagementPage'));
const SellerOrdersPage       = lazy(() => import('@/pages/SellerOrdersPage'));
const SellerOrderDetailPage  = lazy(() => import('@/pages/SellerOrderDetailPage'));
const StripeOnboardingPage   = lazy(() => import('@/pages/StripeOnboardingPage'));
const SellerPaymentsPage     = lazy(() => import('@/pages/SellerPaymentsPage'));
const TrustScorePage         = lazy(() => import('@/pages/TrustScorePage'));

const AUTH_LINKS = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Sản phẩm', to: '/products' },
  { label: 'Đơn hàng', to: '/orders' },
  { label: 'Thu nhập', to: '/payments' },
  { label: 'Stripe', to: '/stripe-onboarding' },
  { label: 'Trust Score', to: '/trust-score' },
];

export default function App() {
  return (
    <Suspense fallback={<div className="p-8 text-center">Loading...</div>}>
      <Routes>
        {/* Auth pages — no layout */}
        <Route path="/login"    element={<LoginPage title="Cửa hàng" redirectTo="/dashboard" showRegisterLink={false} />} />
        <Route path="/register" element={<SellerRegisterPage />} />

        {/* Protected pages — wrapped in Layout */}
        <Route
          path="/*"
          element={
            <Layout appName="FlashSale Seller" authLinks={AUTH_LINKS}>
              <Routes>
                <Route path="/dashboard"         element={<PrivateRoute role="SELLER"><SellerDashboard /></PrivateRoute>} />
                <Route path="/products"          element={<PrivateRoute role="SELLER"><ProductManagementPage /></PrivateRoute>} />
                <Route path="/orders"            element={<PrivateRoute role="SELLER"><SellerOrdersPage /></PrivateRoute>} />
                <Route path="/orders/:orderId"   element={<PrivateRoute role="SELLER"><SellerOrderDetailPage /></PrivateRoute>} />
                <Route path="/stripe-onboarding" element={<PrivateRoute role="SELLER"><StripeOnboardingPage /></PrivateRoute>} />
                {/* Stripe redirects sellers here after KYC; bounce back to onboarding page with hint flags */}
                <Route path="/stripe/return"  element={<Navigate to="/stripe-onboarding?from=stripe" replace />} />
                <Route path="/stripe/refresh" element={<Navigate to="/stripe-onboarding?refresh=1"  replace />} />
                <Route path="/payments"          element={<PrivateRoute role="SELLER"><SellerPaymentsPage /></PrivateRoute>} />
                <Route path="/trust-score"        element={<PrivateRoute role="SELLER"><TrustScorePage /></PrivateRoute>} />

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
