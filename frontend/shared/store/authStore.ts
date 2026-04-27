import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import Cookies from 'js-cookie';
import { authApi, type RegisterRequest } from '../api/auth.api';

export interface AuthUser {
  userId: number;
  username: string;
  email: string;
  role: string;
}

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  _hasHydrated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  registerSeller: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  setHydrated: () => void;
}

/** Derives auth state from the accessToken cookie synchronously.
 *  This avoids the race condition where persist-hydration is slow and
 *  PrivateRoute sees isAuthenticated=false before state is restored. */
export function isAuthFromCookie(): boolean {
  return !!Cookies.get('accessToken');
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      _hasHydrated: false,

      login: async (credential, password) => {
        const { data } = await authApi.login({ credential, password });
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'strict' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'strict' });
        }
        set({
          user: { userId: auth.userId, username: auth.username, email: auth.email, role: auth.role },
          isAuthenticated: true,
        });
      },

      register: async (req) => {
        const { data } = await authApi.register(req);
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'strict' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'strict' });
        }
        set({
          user: { userId: auth.userId, username: auth.username, email: auth.email, role: auth.role },
          isAuthenticated: true,
        });
      },

      registerSeller: async (req) => {
        const { data } = await authApi.registerSeller(req);
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'strict' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'strict' });
        }
        set({
          user: { userId: auth.userId, username: auth.username, email: auth.email, role: auth.role },
          isAuthenticated: true,
        });
      },

      logout: async () => {
        try { await authApi.logout(); } catch (_) {}
        Cookies.remove('accessToken');
        Cookies.remove('refreshToken');
        set({ user: null, isAuthenticated: false });
      },

      setHydrated: () => set({ _hasHydrated: true }),
    }),
    {
      name: 'auth-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({ user: s.user }),
      onRehydrateStorage: () => (state) => {
        // After persist rehydrates, sync isAuthenticated from the cookie
        // so PrivateRoute always sees the correct value.
        if (state) {
          state.isAuthenticated = isAuthFromCookie();
          state.setHydrated();
        }
      },
    }
  )
);
