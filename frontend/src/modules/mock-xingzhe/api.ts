import { apiClient } from '@/shared/api/client'
import type {
  MockBusinessOrder,
  MockBusinessOrderStatus,
  MockProduct,
} from '@/shared/api/contracts'

export const getMockProducts = async (): Promise<MockProduct[]> => {
  const response = await apiClient.get<MockProduct[]>('/mock-xingzhe/api/v1/products')
  return response.data
}

export const createMockBusinessOrder = async (
  productCode: string,
): Promise<MockBusinessOrder> => {
  const response = await apiClient.post<MockBusinessOrder>(
    '/mock-xingzhe/api/v1/orders',
    { productCode },
  )
  return response.data
}

export const getMockBusinessOrderStatus = async (
  businessOrderId: string,
): Promise<MockBusinessOrderStatus> => {
  const response = await apiClient.get<MockBusinessOrderStatus>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}`,
  )
  return response.data
}
