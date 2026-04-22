import { create } from 'zustand';
import { cartApi, type Cart, type CartItem } from '../api/cart.api';

interface CartState {
  cart: Cart | null;
  isLoading: boolean;
  error: string | null;

  fetchCart: () => Promise<void>;
  addToCart: (skuCode: string, quantity: number, fsItemId?: number) => Promise<void>;
  updateQuantity: (itemId: number, quantity: number) => Promise<void>;
  removeFromCart: (itemId: number) => Promise<void>;
  clearCart: () => Promise<void>;

  getTotalItems: () => number;
  getTotalAmount: () => number;
  getItemCount: () => number;
}

export const useCartStore = create<CartState>((set, get) => ({
  cart: null,
  isLoading: false,
  error: null,

  fetchCart: async () => {
    set({ isLoading: true, error: null });
    try {
      const { data } = await cartApi.getCart();
      set({ cart: data.data || null, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to fetch cart',
        isLoading: false,
      });
    }
  },

  addToCart: async (skuCode, quantity, fsItemId) => {
    set({ isLoading: true, error: null });
    try {
      await cartApi.addItem(skuCode, quantity, fsItemId);
      await get().fetchCart();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to add item to cart',
        isLoading: false,
      });
      throw err;
    }
  },

  updateQuantity: async (itemId, quantity) => {
    set({ isLoading: true, error: null });
    try {
      await cartApi.updateItemQuantity(itemId, quantity);
      await get().fetchCart();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to update quantity',
        isLoading: false,
      });
      throw err;
    }
  },

  removeFromCart: async (itemId) => {
    set({ isLoading: true, error: null });
    try {
      await cartApi.removeItem(itemId);
      await get().fetchCart();
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to remove item',
        isLoading: false,
      });
      throw err;
    }
  },

  clearCart: async () => {
    set({ isLoading: true, error: null });
    try {
      await cartApi.clearCart();
      set({ cart: { sellers: [], total_items: 0, subtotal: 0 }, isLoading: false });
    } catch (err: any) {
      set({
        error: err?.response?.data?.message || 'Failed to clear cart',
        isLoading: false,
      });
    }
  },

  getTotalItems: () => {
    const state = get();
    return state.cart?.total_items || 0;
  },

  getTotalAmount: () => {
    const state = get();
    return state.cart?.subtotal || 0;
  },

  getItemCount: () => {
    const state = get();
    if (!state.cart?.sellers) return 0;
    return state.cart.sellers.reduce((sum, seller) => sum + seller.items.length, 0);
  },
}));
