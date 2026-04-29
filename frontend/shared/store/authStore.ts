import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import Cookies from 'js-cookie';
import { authApi, type RegisterRequest } from '../api/auth.api';
import { userApi, type UserProfileResponse } from '../api/user.api';
import { logoutApi } from '../lib/axios';

export interface AuthUser {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  fullName?: string;
  role: string;
  roles: string[];
  trustScore: number;
  trustTier?: string;
  status: string;
  avatarUrl?: string;
}

interface AuthState {
  user: AuthUser | null;
  profile: UserProfileResponse | null;
  isAuthenticated: boolean;
  _hasHydrated: boolean;
  login: (credential: string, password: string) => Promise<void>;
  register: (req: RegisterRequest) => Promise<void>;
  registerSeller: (req: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  setHydrated: () => void;
  fetchProfile: () => Promise<void>;
  syncFromAuthResponse: (auth: {
    userId: number;
    username: string;
    email: string;
    phone?: string;
    fullName?: string;
    role: string;
    roles: string[];
    trustScore: number;
    trustTier?: string;
    status: string;
    avatarUrl?: string;
  }) => void;
}

export function isAuthFromCookie(): boolean {
  return !!Cookies.get('accessToken');
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      profile: null,
      isAuthenticated: false,
      _hasHydrated: false,

      syncFromAuthResponse: (auth) => {
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: auth.role,
            roles: auth.roles,
            trustScore: auth.trustScore,
            trustTier: auth.trustTier,
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      login: async (credential, password) => {
        const { data } = await authApi.login({ credential, password });
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: auth.role,
            roles: auth.roles,
            trustScore: auth.trustScore,
            trustTier: auth.trustTier,
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      register: async (req) => {
        const { data } = await authApi.register(req);
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: auth.role,
            roles: auth.roles,
            trustScore: auth.trustScore,
            trustTier: auth.trustTier,
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      registerSeller: async (req) => {
        const { data } = await authApi.registerSeller(req);
        const auth = data.data!;
        Cookies.set('accessToken', auth.accessToken, { secure: true, sameSite: 'lax' });
        if (auth.refreshToken) {
          Cookies.set('refreshToken', auth.refreshToken, { secure: true, sameSite: 'lax' });
        }
        set({
          user: {
            userId: auth.userId,
            username: auth.username,
            email: auth.email,
            phone: auth.phone,
            fullName: auth.fullName,
            role: auth.role,
            roles: auth.roles,
            trustScore: auth.trustScore,
            trustTier: auth.trustTier,
            status: auth.status,
            avatarUrl: auth.avatarUrl,
          },
          isAuthenticated: true,
        });
      },

      logout: async () => {
        try { await logoutApi(); } catch (_) {}
        Cookies.remove('accessToken');
        Cookies.remove('refreshToken');
        set({ user: null, profile: null, isAuthenticated: false });
      },

      fetchProfile: async () => {
        const { data } = await userApi.getProfile();
        const profile = data.data!;
        set({ profile });
        if (get().user) {
          set((state) => ({
            user: state.user ? {
              ...state.user,
              username: profile.username,
              email: profile.email,
              phone: profile.phone,
              fullName: profile.fullName,
              roles: profile.roles,
              trustScore: profile.trustScore,
              trustTier: profile.trustTier,
              status: profile.status,
              avatarUrl: profile.avatarUrl,
            } : null,
          }));
        }
      },

      setHydrated: () => set({ _hasHydrated: true }),
    }),
    {
      name: 'auth-store',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (s) => ({ user: s.user }),
      onRehydrateStorage: () => (state) => {
        if (state) {
          state.isAuthenticated = isAuthFromCookie();
          state.setHydrated();
        }
      },
    }
  )
);
