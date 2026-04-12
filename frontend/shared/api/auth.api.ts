import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface LoginRequest  { username: string; password: string; }
export interface AuthResponse  { accessToken: string; userId: string; role: string; }

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', body),

  register: (body: unknown) =>
    apiClient.post<ApiResponse<void>>('/auth/register', body),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  getProfile: () =>
    apiClient.get<ApiResponse<unknown>>('/users/me'),
};

