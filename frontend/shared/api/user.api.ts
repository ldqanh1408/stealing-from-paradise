import apiClient from '../lib/axios';
import type { ApiResponse, PageResponse } from '../types/api';

// ─── User Profile ─────────────────────────────────────────────────────────────

export interface UserProfileResponse {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  fullName?: string;
  avatarUrl?: string;
  roles: string[];
  status: string;
  trustScore: number;
  trustTier?: string;
  appealCount: number;
  productPostingSuspended: boolean;
  lockReason?: string;
  lockedUntil?: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserProfileUpdateRequest {
  fullName?: string;
  avatarUrl?: string;
  phone?: string;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  objectKey: string;
  cdnUrl: string;
  expiresIn: number;
}

// ─── Address ───────────────────────────────────────────────────────────────────

export interface AddressResponse {
  addressId: number;
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault: boolean;
}

export interface AddressCreateRequest {
  provinceId: number;
  districtId: number;
  fullAddress: string;
  isDefault?: boolean;
}

export interface AddressUpdateRequest {
  provinceId?: number;
  districtId?: number;
  fullAddress?: string;
  isDefault?: boolean;
}

// ─── Loyalty ───────────────────────────────────────────────────────────────────

export interface LoyaltyBalanceResponse {
  userId: number;
  loyaltyAccountId: number;
  availablePoints: number;
  pendingPoints: number;
  expiredPoints: number;
  totalEarned: number;
  totalUsed: number;
  conversionRate: number;
  note?: string;
  maxUsablePerOrder: number;
  maxUsablePercentage: number;
  expiryPolicy: ExpiryPolicy;
  tierBenefits: TierBenefits;
  recentTransactions: RecentTransaction[];
}

export interface ExpiryPolicy {
  expiryDays: number;
  nextExpiryDate: string;
  pointsExpiringSoon: number;
}

export interface TierBenefits {
  tier: string;
  trustScore: number;
  earningRate: string;
  maxDiscountRate: string;
}

export interface RecentTransaction {
  transactionId: number;
  type: string;
  delta: number;
  status: string;
  orderId?: number;
  orderCode?: string;
  createdAt: string;
  expiresAt?: string;
}

export interface PointTransactionSummary {
  transactionId: number;
  type: string;
  delta: number;
  status: string;
  orderId?: number;
  orderCode?: string;
  balanceAfter: number;
  note?: string;
  expiresAt?: string;
  createdAt: string;
}

export interface LoyaltyEstimateResponse {
  estimatedPoints: number;
  currentBalance: number;
  afterBalance: number;
}

// ─── Trust Score ───────────────────────────────────────────────────────────────

export interface TrustScoreLogResponse {
  logId: number;
  eventCode: string;
  delta: number;
  scoreAfter: number;
  changedBy: string;
  reason?: string;
  createdAt: string;
}

// ─── Appeals ──────────────────────────────────────────────────────────────────

export interface AppealResponse {
  appealId: number;
  logId: number;
  userId: number;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reason: string;
  evidenceUrls: string[];
  adminNote?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface AppealCreateRequest {
  logId: number;
  reason: string;
  evidenceUrls: string[];
}

// ─── Internal User ────────────────────────────────────────────────────────────

export interface InternalUserInfoResponse {
  userId: number;
  username: string;
  email: string;
  phone?: string;
  role: string;
  status: string;
  trustScore: number;
}

// ─── Admin ─────────────────────────────────────────────────────────────────────

export interface AdminUserDetail extends AdminUser {
  phone?: string;
  fullName?: string;
  avatarUrl?: string;
  trustTier?: string;
  appealCount: number;
  productPostingSuspended: boolean;
  lockReason?: string;
  lockedUntil?: string;
  addresses?: AddressResponse[];
  banHistory?: BanHistoryResponse[];
}

export interface BanHistoryResponse {
  id: number;
  bannedBy: string;
  reason: string;
  bannedAt: string;
  unbannedAt?: string;
  unbannedBy?: string;
}

export interface TrustScoreEventConfig {
  eventCode: string;
  description: string;
  delta: number;
  enabled: boolean;
}

export interface AppealAdminResponse {
  appealId: number;
  userId: number;
  username: string;
  logId: number;
  eventCode: string;
  delta: number;
  reason: string;
  evidenceUrls: string[];
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  adminNote?: string;
  resolvedAt?: string;
  createdAt: string;
}

export interface AdminUser {
  user_id: number;
  username: string;
  email: string;
  role: string;
  status: 'ACTIVE' | 'BANNED' | 'PENDING';
  trust_score: number;
  created_at: string;
}

// ─── API ─────────────────────────────────────────────────────────────────────

export const userApi = {
  // Profile
  getProfile: () =>
    apiClient.get<ApiResponse<UserProfileResponse>>('/users/me'),

  updateProfile: (data: UserProfileUpdateRequest) =>
    apiClient.put<ApiResponse<UserProfileResponse>>('/users/me', data),

  getAvatarPresignedUrl: (contentType: string) =>
    apiClient.get<ApiResponse<PresignedUrlResponse>>('/users/me/avatar/presigned-url', {
      params: { contentType },
    }),

  registerAsSeller: () =>
    apiClient.post<ApiResponse<void>>('/users/me/roles/seller'),

  // Trust Score
  getTrustScoreLogs: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<TrustScoreLogResponse>>>('/users/me/trust-score/logs', { params }),

  // Loyalty
  getLoyaltyBalance: () =>
    apiClient.get<ApiResponse<LoyaltyBalanceResponse>>('/loyalty/balance'),

  getLoyaltyTransactions: (params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<PointTransactionSummary>>>('/loyalty/transactions', { params }),

  estimateLoyaltyPoints: (orderAmount: number) =>
    apiClient.get<ApiResponse<LoyaltyEstimateResponse>>('/loyalty/estimate', {
      params: { orderAmount },
    }),

  // Appeals
  getAppeals: () =>
    apiClient.get<ApiResponse<AppealResponse[]>>('/support/trust-score-appeal'),

  getAppealPresignedUrl: (contentType: string) =>
    apiClient.get<ApiResponse<PresignedUrlResponse>>('/support/trust-score-appeal/presigned-url', {
      params: { contentType },
    }),

  submitAppeal: (data: AppealCreateRequest) =>
    apiClient.post<ApiResponse<AppealResponse>>('/support/trust-score-appeal', data),
};

export const adminUserApi = {
  getUserDetail: (userId: number) =>
    apiClient.get<ApiResponse<AdminUserDetail>>(`/users/${userId}`),

  getUsers: (params?: { role?: string; status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AdminUser>>>('/users', { params }),

  lockUser: (userId: number, body: { reason: string; lockedUntil?: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/lock`, body),

  unlockUser: (userId: number, body: { reason: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/unlock`, body),

  adjustTrustScore: (userId: number, body: { delta: number; reason: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/trust-score`, body),

  getTrustScoreLogs: (userId: number, params?: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<TrustScoreLogResponse>>>(`/admin/users/${userId}/trust-score/logs`, { params }),

  getBanHistory: (userId: number) =>
    apiClient.get<ApiResponse<BanHistoryResponse[]>>(`/admin/users/${userId}/ban-history`),

  unlockProductPosting: (userId: number, body: { note?: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/users/${userId}/unlock-product-posting`, body),

  getAppeals: (params?: { status?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AppealAdminResponse>>>('/admin/appeals', { params }),

  resolveAppeal: (appealId: number, body: { approved: boolean; adminNote?: string }) =>
    apiClient.post<ApiResponse<void>>(`/admin/appeals/${appealId}/resolve`, body),

  getTrustScoreConfig: () =>
    apiClient.get<ApiResponse<TrustScoreEventConfig[]>>('/admin/trust-score-events-config'),

  updateTrustScoreConfig: (eventCode: string, body: { enabled?: boolean; delta?: number; reason?: string }) =>
    apiClient.put<ApiResponse<void>>(`/admin/trust-score-events-config/${eventCode}`, body),
};
