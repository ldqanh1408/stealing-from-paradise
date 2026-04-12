import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import Cookies from 'js-cookie';
import { authApi } from '@/api/auth.api';

interface AuthUser  { userId: string; role: string; email?: string; }
interface AuthState {
  user:            AuthUser | null;
  isAuthenticated: boolean;
  login:  (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null, isAuthenticated: false,

      login: async (username, password) => {
        const { data } = await authApi.login({ username, password });
        const { accessToken, userId, role } = data.data!;
        Cookies.set('accessToken', accessToken, { secure: true, sameSite: 'strict' });
        set({ user: { userId, role }, isAuthenticated: true });
      },

      logout: async () => {
        try { await authApi.logout(); } catch (_) {}
        Cookies.remove('accessToken');
        set({ user: null, isAuthenticated: false });
      },
    }),
    {
      name:    'auth-store',                                    // key lưu vào sessionStorage
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({ user: s.user, isAuthenticated: s.isAuthenticated }),
    }
  )
);

