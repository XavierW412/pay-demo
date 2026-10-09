import { apiClient } from '@/shared/api/client'
import type { CheckoutContext } from '@/shared/api/contracts'

export const getCheckoutContext = async (opaqueToken: string) => {
  const response = await apiClient.get<CheckoutContext>(
    `/api/v1/checkout/${encodeURIComponent(opaqueToken)}/context`,
  )

  return response.data
}
