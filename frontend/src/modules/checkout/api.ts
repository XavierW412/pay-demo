import { apiClient } from '@/shared/api/client'
import type { CheckoutContext, CheckoutStatus } from '@/shared/api/contracts'

export const getCheckoutContext = async (opaqueToken: string) => {
  const response = await apiClient.get<CheckoutContext>(
    `/api/v1/checkout/${encodeURIComponent(opaqueToken)}/context`,
  )

  return response.data
}

export const getCheckoutStatus = async (opaqueToken: string): Promise<CheckoutStatus> => {
  const response = await apiClient.get<CheckoutStatus>(
    `/api/v1/checkout/${encodeURIComponent(opaqueToken)}/status`,
  )
  return response.data
}

export const refreshCheckoutStatus = async (opaqueToken: string): Promise<CheckoutStatus> => {
  const response = await apiClient.post<CheckoutStatus>(
    `/api/v1/checkout/${encodeURIComponent(opaqueToken)}/refresh`,
  )
  return response.data
}
