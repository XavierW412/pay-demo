import { apiClient } from '@/shared/api/client'
import type { PaymentStatus } from '@/shared/api/contracts'

export type OperatorRole = 'OWNER_READONLY' | 'STAFF_ADMIN' | 'STAFF_OPERATOR'

export interface OperatorSession {
  username: string
  role: OperatorRole
}

export interface TransactionRow {
  id: string
  businessOrderId: string
  amount: string
  currency: string
  paymentStatus: PaymentStatus
  provider: 'AIRWALLEX' | 'LOCAL_SIMULATOR'
  notificationStatus: string
  refundStatus: string
  providerPaymentIntentId: string | null
  createdAt: string
  updatedAt: string
}

export interface TransactionPage {
  items: TransactionRow[]
  page: number
  size: number
  total: number
}

export interface OperatorNote {
  id: number
  actor: string
  body: string
  createdAt: string
}

export interface OperatorAudit {
  id: number
  actor: string
  paymentOrderId: string | null
  action: string
  outcome: string
  detail: string | null
  createdAt: string
}

export interface TransactionDetail {
  transaction: TransactionRow
  notes: OperatorNote[]
  audit: OperatorAudit[]
}

export type ReconciliationOutcome = 'MATCH' | 'DIFFERENCE' | 'QUERY_FAILED' | 'NOT_CHECKABLE'

export interface ReconciliationSnapshot {
  id: number
  paymentOrderId: string
  checkedAt: string
  outcome: ReconciliationOutcome
  issueCodes: string[]
  localStatus: string
  localProviderStatus: string | null
  providerStatus: string | null
  localProviderPaymentIntentId: string | null
  providerPaymentIntentId: string | null
  localBusinessOrderId: string
  providerMerchantOrderId: string | null
  localAmount: string
  providerAmount: string | null
  localCurrency: string
  providerCurrency: string | null
  notificationStatus: string
  failureCategory: string | null
}

export interface ReconciliationCheck {
  checkType: 'PAYMENT_STATUS_CHECK'
  settlementReconciled: false
  snapshot: ReconciliationSnapshot
}

export interface RefundRow {
  id: string
  refundRequestId: string
  amount: string
  currency: string
  status: string
  notificationStatus: string
  providerRefundId: string | null
  reviewReason: string | null
  createdAt: string
  updatedAt: string
}

export interface FinanceCurrencySummary {
  currency: string
  paymentSucceededAmount: string
  paymentSucceededCount: number
  refundAwaitingConfirmationAmount: string
  refundProcessingAmount: string
  refundSucceededAmount: string
  blockedRefundCount: number
  paymentExceptionCount: number
  refundExceptionCount: number
  settlementStatus: 'NOT_CONNECTED'
  settlementAmount: string | null
  settlementFee: string | null
}

export interface FinanceOverview {
  from: string
  to: string
  settlementSourceStatus: 'AIRWALLEX_SETTLEMENT_REPORT_NOT_CONNECTED'
  currencies: FinanceCurrencySummary[]
}

interface CsrfToken {
  token: string
  headerName: string
}

let csrfToken: CsrfToken | null = null

const loadCsrf = async (): Promise<CsrfToken> => {
  if (csrfToken) return csrfToken
  const response = await apiClient.get<CsrfToken>('/api/v1/operations/csrf')
  csrfToken = response.data
  return response.data
}

const postWithCsrf = async <T = void>(path: string, data?: unknown): Promise<T> => {
  const csrf = await loadCsrf()
  const response = await apiClient.post<T>(path, data, {
    headers: { [csrf.headerName]: csrf.token },
  })
  return response.data
}

export const getCurrentOperator = async (): Promise<OperatorSession> => {
  const response = await apiClient.get<OperatorSession>('/api/v1/operations/me')
  return response.data
}

export const loginOperator = async (username: string, password: string): Promise<OperatorSession> => {
  const csrf = await loadCsrf()
  await apiClient.post('/api/v1/operations/login', new URLSearchParams({ username, password }), {
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      [csrf.headerName]: csrf.token,
    },
  })
  csrfToken = null
  await loadCsrf()
  return getCurrentOperator()
}

export const logoutOperator = async (): Promise<void> => {
  await postWithCsrf('/api/v1/operations/logout')
  csrfToken = null
}

export const getTransactions = async (params: {
  query?: string
  status?: string
  page?: number
}): Promise<TransactionPage> => {
  const response = await apiClient.get<TransactionPage>('/api/v1/operations/transactions', { params })
  return response.data
}

export const getTransactionDetail = async (paymentOrderId: string): Promise<TransactionDetail> => {
  const response = await apiClient.get<TransactionDetail>(
    `/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}`,
  )
  return response.data
}

export const refreshProviderStatus = async (paymentOrderId: string): Promise<void> => {
  await postWithCsrf(`/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/refresh`)
}

export const retryNotification = async (paymentOrderId: string): Promise<void> => {
  await postWithCsrf(`/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/retry-notification`)
}

export const addOperatorNote = async (paymentOrderId: string, body: string): Promise<void> => {
  await postWithCsrf(`/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/notes`, { body })
}

export const getOperatorAudit = async (): Promise<OperatorAudit[]> => {
  const response = await apiClient.get<OperatorAudit[]>('/api/v1/operations/audit')
  return response.data
}

export const issueInvitation = async (): Promise<string> => {
  const response = await postWithCsrf<{ token: string }>('/api/v1/operations/invitations')
  return response.token
}

export const registerOperator = async (token: string, username: string, password: string): Promise<void> => {
  await postWithCsrf('/api/v1/operations/register', { token, username, password })
}

export const getReconciliationDifferences = async (): Promise<ReconciliationCheck[]> => {
  const response = await apiClient.get<ReconciliationCheck[]>('/api/v1/operations/reconciliation/differences')
  return response.data
}

export const getReconciliationHistory = async (paymentOrderId: string): Promise<ReconciliationCheck[]> => {
  const response = await apiClient.get<ReconciliationCheck[]>(
    `/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/reconciliation-checks`,
  )
  return response.data
}

export const runReconciliationCheck = async (paymentOrderId: string): Promise<ReconciliationCheck> => {
  return postWithCsrf<ReconciliationCheck>(
    `/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/reconciliation-checks`,
  )
}

export const getRefundsForPayment = async (paymentOrderId: string): Promise<RefundRow[]> => {
  const response = await apiClient.get<RefundRow[]>(
    `/api/v1/operations/transactions/${encodeURIComponent(paymentOrderId)}/refunds`,
  )
  return response.data
}

export const confirmRefundExecution = async (refundId: string): Promise<{ refundId: string; status: string }> => {
  return postWithCsrf<{ refundId: string; status: string }>(
    `/api/v1/operations/refunds/${encodeURIComponent(refundId)}/confirm`,
  )
}

export const getFinanceOverview = async (from: string, to: string): Promise<FinanceOverview> => {
  const response = await apiClient.get<FinanceOverview>('/api/v1/operations/finance/overview', {
    params: { from, to },
  })
  return response.data
}
