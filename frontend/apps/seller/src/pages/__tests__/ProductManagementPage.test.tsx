import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { renderWithProviders } from '@/test/utils';
import ProductManagementPage from '../ProductManagementPage';
import apiClient from '@shared/lib/axios';

// The page lists products via a raw apiClient.get('/sellers/me/products'); mutations
// go through sellerApi (also backed by this client). Mocking the axios default
// covers both without hitting the network.
vi.mock('@shared/lib/axios', () => {
  const m = { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() };
  return { default: m, apiClient: m };
});

const product = (over: any) => ({
  productId: 'p1', name: 'SP', category: 'Cat', price: 1000, status: 'DRAFT',
  stockAvailable: 5, variantsCount: 0, createdAt: '2026-01-01T00:00:00Z', ...over,
});

const list = (content: any[]) =>
  (apiClient.get as any).mockResolvedValue({ data: { data: { content, totalPages: 1, totalElements: content.length } } });

beforeEach(() => {
  vi.clearAllMocks();
  (apiClient.get as any).mockResolvedValue({ data: { data: { content: [], totalPages: 0, totalElements: 0 } } });
});

describe('ProductManagementPage — action matrix per status', () => {
  it('shows the right lifecycle action per product status', async () => {
    list([
      product({ productId: 'd', name: 'Draft SP', status: 'DRAFT' }),
      product({ productId: 'a', name: 'Approved SP', status: 'APPROVED' }),
      product({ productId: 'pub', name: 'Published SP', status: 'PUBLISHED' }),
      product({ productId: 'r', name: 'Rejected SP', status: 'REJECTED' }),
      product({ productId: 'pen', name: 'Pending SP', status: 'PENDING' }),
    ]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });
    expect(await screen.findByText('Draft SP')).toBeInTheDocument();

    expect(screen.getAllByText('Gửi duyệt')).toHaveLength(1);   // DRAFT
    expect(screen.getAllByText('Hiển thị')).toHaveLength(1);    // APPROVED
    expect(screen.getAllByText('Ẩn')).toHaveLength(1);          // PUBLISHED
    expect(screen.getAllByText('Xóa')).toHaveLength(2);         // DRAFT + REJECTED
  });

  it('shows the empty state when the seller has no products', async () => {
    list([]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });
    expect(await screen.findByText(/Chưa có sản phẩm nào/i)).toBeInTheDocument();
  });

  it('debounces the search input query by 300ms', async () => {
    vi.useFakeTimers();
    try {
      list([]);
      renderWithProviders(<ProductManagementPage />, { route: '/products' });

      // Run pending timers to complete the initial load query
      await act(async () => {
        await vi.runOnlyPendingTimersAsync();
      });

      // Initially called once for loading all products
      expect(apiClient.get).toHaveBeenCalledTimes(1);

      const searchInput = screen.getByPlaceholderText('Tìm sản phẩm...');
      
      // Type a search query
      fireEvent.change(searchInput, { target: { value: 'laptop' } });

      // Should not call apiClient immediately
      expect(apiClient.get).toHaveBeenCalledTimes(1);

      // Advance by 200ms - still shouldn't call
      await act(async () => {
        await vi.advanceTimersByTimeAsync(200);
      });
      expect(apiClient.get).toHaveBeenCalledTimes(1);

      // Advance by another 100ms (total 300ms) - should trigger call
      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });

      expect(apiClient.get).toHaveBeenCalledTimes(2);
      expect(apiClient.get).toHaveBeenLastCalledWith('/sellers/me/products', expect.objectContaining({
        params: expect.objectContaining({ search: 'laptop' })
      }));
    } finally {
      vi.useRealTimers();
    }
  });

  it('filters products by clicking status tabs and resets page', async () => {
    list([]);
    renderWithProviders(<ProductManagementPage />, { route: '/products' });

    // Initially called with default params
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled());
    expect(apiClient.get).toHaveBeenLastCalledWith('/sellers/me/products', expect.objectContaining({
      params: expect.objectContaining({ status: undefined, page: 0 })
    }));

    // Click "Đang bán" tab
    const publishedTab = screen.getByText('Đang bán');
    fireEvent.click(publishedTab);

    // Verify it triggers a new request with status = PUBLISHED
    await waitFor(() => {
      expect(apiClient.get).toHaveBeenLastCalledWith('/sellers/me/products', expect.objectContaining({
        params: expect.objectContaining({ status: 'PUBLISHED', page: 0 })
      }));
    });
  });
});
