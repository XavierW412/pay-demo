export type Currency = 'CNY' | 'USD'
export type PaymentStatus =
  | 'CREATING'
  | 'AWAITING_PAYMENT'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'CANCELLED'
  | 'FAILED'
  | 'REQUIRES_REVIEW'
export type MockPaymentMode = 'AIRWALLEX_SANDBOX' | 'LOCAL_SIMULATOR'

export interface MockProduct {
  productCode: string
  displayName: string
  amount: string
  currency: Currency
  shopperCountryCode: string
  description: string
}

export interface MockBusinessOrder {
  businessOrderId: string
  paymentOrderId: string | null
  paymentMode: MockPaymentMode
  amount: string
  currency: Currency
  paymentStatus: PaymentStatus
  checkoutUrl: string | null
}

export interface MockBusinessOrderStatus {
  businessOrderId: string
  displayName: string
  paymentOrderId: string | null
  paymentMode: MockPaymentMode
  amount: string
  currency: Currency
  businessStatus: 'PENDING_PAYMENT' | 'PAID'
  paymentStatus: PaymentStatus
  serviceStatus: 'NOT_ACTIVATED' | 'ACTIVE'
  checkoutUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface MockBusinessOrderRow {
  businessOrderId: string
  displayName: string
  paymentMode: MockPaymentMode
  amount: string
  currency: Currency
  businessStatus: string
  paymentStatus: PaymentStatus
  serviceStatus: string
  createdAt: string
  updatedAt: string
}

export interface MockBusinessOrderPage {
  items: MockBusinessOrderRow[]
  page: number
  size: number
  total: number
}

export interface MockPaymentHistory {
  events: {
    eventId: string
    eventType: string
    paymentStatus: PaymentStatus | null
    amount: string | null
    currency: Currency | null
    occurredAt: string
  }[]
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

export interface MockRefundRequests {
  requests: {
    refundRequestId: string
    businessOrderId: string
    amount: string
    currency: Currency
    reason: string | null
    businessDecision: 'REQUESTED' | 'APPROVED' | 'REJECTED'
    paymentServiceStatus: string
    businessOutcome: 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'PENDING' | null
    providerStatus: string | null
    rawProviderStatus: string | null
    paymentRefundId: string | null
    createdAt: string
    decidedAt: string | null
    lastSentAt: string | null
  }[]
}
