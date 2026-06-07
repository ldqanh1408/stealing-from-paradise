import { describe, it, expect, beforeEach } from 'vitest';
import { useCartStore } from '../store/cartStore';

describe('cartStore - getters', () => {
  beforeEach(() => {
    useCartStore.setState({ cart: null, isLoading: false, error: null });
  });

  it('getTotalItems returns 0 for empty cart', () => {
    expect(useCartStore.getState().getTotalItems()).toBe(0);
  });

  it('getTotalItems returns totalItems from cart', () => {
    useCartStore.setState({
      cart: { sellers: [], totalItems: 5, subtotal: 100 },
    });
    expect(useCartStore.getState().getTotalItems()).toBe(5);
  });

  it('getTotalAmount returns 0 for empty cart', () => {
    expect(useCartStore.getState().getTotalAmount()).toBe(0);
  });

  it('getTotalAmount returns subtotal from cart', () => {
    useCartStore.setState({
      cart: { sellers: [], totalItems: 2, subtotal: 49.99 },
    });
    expect(useCartStore.getState().getTotalAmount()).toBe(49.99);
  });

  it('getItemCount returns 0 for empty cart', () => {
    expect(useCartStore.getState().getItemCount()).toBe(0);
  });

  it('getItemCount sums items across all sellers', () => {
    useCartStore.setState({
      cart: {
        sellers: [
          {
            sellerId: 1,
            sellerName: 'Shop A',
            items: [
              { id: 1, skuCode: 'SKU1', productName: 'Item 1', quantity: 2 },
              { id: 2, skuCode: 'SKU2', productName: 'Item 2', quantity: 1 },
            ] as any,
          },
          {
            sellerId: 2,
            sellerName: 'Shop B',
            items: [
              { id: 3, skuCode: 'SKU3', productName: 'Item 3', quantity: 3 },
            ] as any,
          },
        ],
        totalItems: 6,
        subtotal: 150,
      },
    });
    expect(useCartStore.getState().getItemCount()).toBe(3);
  });
});
