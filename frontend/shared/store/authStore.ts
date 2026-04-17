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
  login: (username: string, password: string) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      isAuthenticated: false,

      login: async (username, password) => {
        const { data } = await authApi.login({ username, password });
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

      logout: async () => {
        try { await authApi.logout(); } catch (_) {}
        Cookies.remove('accessToken');
        Cookies.remove('refreshToken');
        set({ user: null, isAuthenticated: false });
      },
    }),
    {
      name: 'auth-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({ user: s.user, isAuthenticated: s.isAuthenticated }),
    }
  )
);
