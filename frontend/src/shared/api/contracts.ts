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
  countryCode: string
  environment: PaymentEnvironment
}

export interface MockProduct {
  productCode: string
  displayName: string
  amount: string
  currency: Currency
  description: string
}

export interface MockBusinessOrder {
  businessOrderId: string
  paymentOrderId: string
  amount: string
  currency: Currency
  paymentStatus: PaymentStatus
  checkoutUrl: string
}

export interface MockBusinessOrderStatus {
  businessOrderId: string
  businessStatus: 'PENDING_PAYMENT' | 'PAID'
  paymentStatus: PaymentStatus
  serviceStatus: 'NOT_ACTIVATED' | 'ACTIVE'
}
