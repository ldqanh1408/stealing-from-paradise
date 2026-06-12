import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import SellerDashboard from '../SellerDashboard';
import { sellerApi } from '@shared/api/seller.api';

vi.mock('@shared/api/seller.api', () => ({ sellerApi: { getDashboardStats: vi.fn() } }));

const setStats = (s: any) => (sellerApi.getDashboardStats as any).mockResolvedValue({ data: { data: s } });

beforeEach(() => vi.clearAllMocks());

describe('SellerDashboard (UC-ORDER-007 dashboard)', () => {
  it('renders the stat cards', async () => {
    setStats({ totalProducts: 12, ordersToday: 3, revenueMonth: 5_000_000, pendingOrders: 0, activeProducts: 10 });
    renderWithProviders(<SellerDashboard />, { route: '/dashboard' });
    expect(await screen.findByText('Tổng sản phẩm')).toBeInTheDocument();
    expect(screen.getByText('Đơn hàng hôm nay')).toBeInTheDocument();
    expect(screen.getByText('Doanh thu tháng')).toBeInTheDocument();
  });

  it('shows the pending-orders banner when there are pending orders', async () => {
    setStats({ totalProducts: 5, ordersToday: 1, revenueMonth: 1000, pendingOrders: 4, activeProducts: 5 });
    renderWithProviders(<SellerDashboard />, { route: '/dashboard' });
    expect(await screen.findByText(/4 đơn hàng chờ xác nhận/i)).toBeInTheDocument();
  });

  it('shows the getting-started notice when there are no products', async () => {
    setStats({ totalProducts: 0, ordersToday: 0, revenueMonth: 0, pendingOrders: 0, activeProducts: 0 });
    renderWithProviders(<SellerDashboard />, { route: '/dashboard' });
    expect(await screen.findByText(/Bắt đầu bán hàng ngay/i)).toBeInTheDocument();
  });
});
