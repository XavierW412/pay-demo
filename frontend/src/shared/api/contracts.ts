export type Currency = 'CNY' | 'USD'

export type PaymentEnvironment = 'sandbox' | 'prod'

export type PaymentStatus =
  | 'CREATING'
  | 'AWAITING_PAYMENT'
  | 'PROCESSING'
  | 'SUCCEEDED'
  | 'CANCELLED'
  | 'FAILED'
  | 'REQUIRES_REVIEW'

export interface CheckoutContext {
  intentId: string
  clientSecret: string
  businessOrderId: string
  paymentOrderId: string
  amount: string
  currency: Currency
  countryCode?: string | null
  environment: PaymentEnvironment
  businessReturnUrl: string
  returnPageUrl: string
}

export interface CheckoutStatus {
  businessOrderId: string
  paymentOrderId: string
  paymentStatus: PaymentStatus
  notificationStatus: 'NOT_REQUIRED' | 'PENDING' | 'RETRY' | 'DELIVERED'
  environment: PaymentEnvironment
  businessReturnUrl: string
}
