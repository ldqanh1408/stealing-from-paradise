import apiClient from '../lib/axios';
import type { ApiResponse } from '../types/api';

export interface UserAddress {
  addressId: number;
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

export const addressApi = {
  list: () =>
    apiClient.get<ApiResponse<UserAddress[]>>('/users/me/addresses'),

  create: (data: {
    provinceId: number;
    districtId: number;
    fullAddress: string;
    isDefault?: boolean;
  }) =>
    apiClient.post<ApiResponse<UserAddress>>('/users/me/addresses', data),

  update: (addressId: number, data: Partial<{
    provinceId: number;
    districtId: number;
    fullAddress: string;
    isDefault: boolean;
  }>) =>
    apiClient.put<ApiResponse<UserAddress>>(`/users/me/addresses/${addressId}`, data),

  remove: (addressId: number) =>
    apiClient.delete<ApiResponse<void>>(`/users/me/addresses/${addressId}`),
};
