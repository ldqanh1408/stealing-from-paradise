import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface UserAddress {
  address_id: number;
  province_id: number;
  district_id: number;
  full_address: string;
  is_default: boolean;
}

export const addressApi = {
  list: () =>
    apiClient.get<ApiResponse<UserAddress[]>>('/users/me/addresses'),

  create: (data: {
    province_id: number;
    district_id: number;
    full_address: string;
    is_default?: boolean;
  }) =>
    apiClient.post<ApiResponse<UserAddress>>('/users/me/addresses', data),

  update: (addressId: number, data: Partial<{
    province_id: number;
    district_id: number;
    full_address: string;
    is_default: boolean;
  }>) =>
    apiClient.put<ApiResponse<UserAddress>>(`/users/me/addresses/${addressId}`, data),

  remove: (addressId: number) =>
    apiClient.delete<ApiResponse<void>>(`/users/me/addresses/${addressId}`),
};
