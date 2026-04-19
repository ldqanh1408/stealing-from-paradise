import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

/** Matches backend AuthResponse DTO */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  username: string;
  email: string;
  role: string;
  expiresIn: number;
}

export const authApi = {
  login: (body: LoginRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/login', body),

  register: (body: RegisterRequest) =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/register', body),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/auth/logout'),

  refresh: () =>
    apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh'),

  getProfile: () =>
    apiClient.get<ApiResponse<unknown>>('/users/me'),
};
