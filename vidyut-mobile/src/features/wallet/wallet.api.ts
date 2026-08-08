import { apiClient } from '../../services/apiClient';
import { ApiResponse, getApiErrorMessage, unwrapApiResponse } from '../../services/apiResponse';
import { AutoRechargeRule, SaveAutoRechargeRuleRequest, WalletData } from './wallet.types';

export async function getWallet(): Promise<WalletData> {
  try {
    const response = await apiClient.get<ApiResponse<WalletData>>('/ev/wallet');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load your wallet.'));
  }
}

export async function topUpWallet(amount: number): Promise<WalletData> {
  try {
    const response = await apiClient.post<ApiResponse<WalletData>>('/ev/wallet/topup', { amount });
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to top up your wallet.'));
  }
}

export async function getAutoRechargeRules(): Promise<AutoRechargeRule[]> {
  try {
    const response = await apiClient.get<ApiResponse<AutoRechargeRule[]>>('/ev/wallet/auto-recharge');
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to load auto-recharge settings.'));
  }
}

export async function saveAutoRechargeRule(request: SaveAutoRechargeRuleRequest): Promise<AutoRechargeRule> {
  try {
    const response = await apiClient.put<ApiResponse<AutoRechargeRule>>(
      `/ev/wallet/auto-recharge/${request.vehicleId}`,
      request,
    );
    return unwrapApiResponse(response.data);
  } catch (error) {
    throw new Error(getApiErrorMessage(error, 'Unable to update auto-recharge.'));
  }
}
