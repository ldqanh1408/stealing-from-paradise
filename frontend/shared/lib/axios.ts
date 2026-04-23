import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios';
import Cookies from 'js-cookie';
import { installMockInterceptor, isMockMode, isNetworkError } from '../api/mock';

const BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1';

// ─── Singleton Axios instance ─────────────────────────────────────
export const apiClient: AxiosInstance = axios.create({
  baseURL: `${BASE_URL}`,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,          // gửi cookie (refresh token HttpOnly)
});

// ─── Install mock interceptor ─────────────────────────────────────
installMockInterceptor(apiClient);

// ─── Request interceptor: gắn Access Token ───────────────────────
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Skip auth header in mock mode
    if (isMockMode()) return config;
    const token = Cookies.get('accessToken');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Response interceptor: auto-refresh token khi 401 ───────────
let isRefreshing = false;
let failedQueue: Array<{ resolve: (v: unknown) => void; reject: (r: unknown) => void }> = [];

const processQueue = (error: AxiosError | null, token: string | null = null) => {
  failedQueue.forEach((p) => error ? p.reject(error) : p.resolve(token));
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    // Network error or mock response — pass through without token refresh
    if (isNetworkError(error) || (error as any)?.isMockResponse) {
      return Promise.reject(error);
    }

    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Gọi refresh-token — Gateway route /auth/refresh, gửi refreshToken qua HttpOnly cookie
        const { data } = await axios.post<{ data: { accessToken: string } }>(
          `${BASE_URL}/api/v1/auth/refresh`,
          {},
          { withCredentials: true }
        );
        const newToken = data.data.accessToken;
        Cookies.set('accessToken', newToken, { secure: true, sameSite: 'strict' });
        processQueue(null, newToken);
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError as AxiosError, null);
        Cookies.remove('accessToken');
        window.location.href = '/login';   // Vite SPA — luôn chạy trên browser
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;
