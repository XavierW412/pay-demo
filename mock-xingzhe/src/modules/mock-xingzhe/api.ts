import { apiClient } from '@/shared/api/client'
import type {
  MockBusinessOrder,
  MockBusinessOrderStatus,
  MockBusinessOrderPage,
  MockPaymentHistory,
  MockRefundRequests,
  MockProduct,
  MockPaymentMode,
} from '@/shared/api/contracts'

export const getMockProducts = async (): Promise<MockProduct[]> => {
  const response = await apiClient.get<MockProduct[]>('/mock-xingzhe/api/v1/products')
  return response.data
}

export const createMockBusinessOrder = async (
  productCode: string,
  paymentMode: MockPaymentMode,
): Promise<MockBusinessOrder> => {
  const response = await apiClient.post<MockBusinessOrder>(
    '/mock-xingzhe/api/v1/orders',
    { productCode, paymentMode },
  )
  return response.data
}

export const simulateMockPaymentResult = async (
  businessOrderId: string,
  result: 'SUCCEEDED' | 'INSUFFICIENT_FUNDS' | 'PROCESSING' | 'TIMEOUT',
): Promise<MockBusinessOrderStatus> => {
  const response = await apiClient.post<MockBusinessOrderStatus>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/simulated-payment`,
    { result },
  )
  return response.data
}

export const getMockBusinessOrders = async (
  page = 0,
  size = 20,
  paymentStatus?: string,
): Promise<MockBusinessOrderPage> => {
  const response = await apiClient.get<MockBusinessOrderPage>('/mock-xingzhe/api/v1/orders', {
    params: { page, size, paymentStatus },
  })
  return response.data
}

export const getMockPaymentHistory = async (businessOrderId: string): Promise<MockPaymentHistory> => {
  const response = await apiClient.get<MockPaymentHistory>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/payment-history`,
  )
  return response.data
}

export const initiateMockBusinessOrderPayment = async (
  businessOrderId: string,
): Promise<MockBusinessOrder> => {
  const response = await apiClient.post<MockBusinessOrder>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/payment`,
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

export interface MockRefundResult {
  refundId: string
  refundRequestId: string
  status: string
  amount: string
  currency: string
}

export interface MockRefundEvent {
  refundRequestId: string
  providerRefundId: string | null
  status: string
  businessOutcome: 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'PENDING' | null
  executionStatus: string | null
  providerStatus: string | null
  rawProviderStatus: string | null
  amount: string
  currency: string
  receivedAt: string
}

export const getMockRefundEvents = async (businessOrderId: string): Promise<MockRefundEvent[]> => {
  const response = await apiClient.get<MockRefundEvent[]>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/refunds`,
  )
  return response.data
}

export const approveMockRefund = async (
  businessOrderId: string,
  refundRequestId: string,
): Promise<MockRefundResult> => {
  const response = await apiClient.post<MockRefundResult>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/refund-requests/${encodeURIComponent(refundRequestId)}/approve`,
  )
  return response.data
}

export const createMockRefundRequest = async (
  businessOrderId: string,
  refundRequestId: string,
  amount: string,
): Promise<void> => {
  await apiClient.post(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/refund-requests`,
    { refundRequestId, amount, reason: '模拟用户申请退款' },
  )
}

export const rejectMockRefund = async (businessOrderId: string, refundRequestId: string): Promise<void> => {
  await apiClient.post(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/refund-requests/${encodeURIComponent(refundRequestId)}/reject`,
  )
}

export const getMockRefundRequests = async (businessOrderId: string): Promise<MockRefundRequests> => {
  const response = await apiClient.get<MockRefundRequests>(
    `/mock-xingzhe/api/v1/orders/${encodeURIComponent(businessOrderId)}/refund-requests`,
  )
  return response.data
}
