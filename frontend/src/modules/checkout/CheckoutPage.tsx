import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { init } from '@airwallex/components-sdk'
import { AlertCircle, ArrowUpRight, LoaderCircle, LockKeyhole, ShieldCheck } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getCheckoutContext } from './api'

type LoadState = 'loading' | 'ready' | 'error'

export function CheckoutPage() {
  const { opaqueToken = '' } = useParams()
  const [isRedirecting, setIsRedirecting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const contextQuery = useQuery({
    queryKey: ['checkout-context', opaqueToken],
    queryFn: () => getCheckoutContext(opaqueToken),
    enabled: Boolean(opaqueToken),
  })
  const context = contextQuery.data
  const loadState: LoadState = contextQuery.isPending
    ? 'loading'
    : contextQuery.isError
      ? 'error'
      : 'ready'

  const startCheckout = async () => {
    if (!context || isRedirecting) return

    setIsRedirecting(true)
    setErrorMessage('')

    try {
      const { payments } = await init({
        env: context.environment === 'sandbox' ? 'demo' : 'prod',
        enabledElements: ['payments'],
      })

      if (!payments) {
        throw new Error('Airwallex 付款组件未能初始化')
      }

      payments.redirectToCheckout({
        intent_id: context.intentId,
        client_secret: context.clientSecret,
        currency: context.currency,
        country_code: context.countryCode,
        successUrl: `${window.location.origin}/checkout/return`,
      })
    } catch {
      setIsRedirecting(false)
      setErrorMessage('暂时无法打开 Airwallex 托管收银台。付款状态不会因此变更，请检查网络后重试，或联系星辙业务平台。')
    }
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10 text-foreground sm:px-6">
      <section className="w-full max-w-lg" aria-labelledby="checkout-title">
        <header className="mb-8 text-center">
          <div className="mx-auto mb-4 flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
            <LockKeyhole aria-hidden="true" className="size-5" />
          </div>
          <p className="text-sm font-medium text-muted-foreground">星辙支付服务</p>
          <h1 id="checkout-title" className="mt-2 text-2xl font-semibold tracking-tight">确认付款</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            本页面仅负责将付款请求安全转交给 Airwallex。
            <br className="hidden sm:block" />银行卡等支付资料将在 Airwallex 托管收银台中填写。
          </p>
        </header>

        <div className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
          <div className="border-b border-border px-5 py-4 sm:px-6">
            <h2 className="text-sm font-semibold">付款摘要</h2>
            <p className="mt-1 text-xs text-muted-foreground">金额和订单信息由支付服务提供，不可在此修改。</p>
          </div>

          {loadState === 'loading' && (
            <div className="flex min-h-52 flex-col items-center justify-center gap-3 px-6 text-center" role="status" aria-live="polite">
              <LoaderCircle className="size-6 animate-spin text-primary motion-reduce:animate-none" aria-hidden="true" />
              <p className="text-sm text-muted-foreground">正在安全读取付款信息…</p>
            </div>
          )}

          {loadState === 'error' && (
            <div className="space-y-4 p-5 sm:p-6" role="alert">
              <div className="flex gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-4">
                <AlertCircle className="mt-0.5 size-5 shrink-0 text-destructive" aria-hidden="true" />
                <p className="text-sm leading-6">暂时无法读取本次付款信息。若当前正在进行本地沙盒联调，可能尚未配置沙盒凭据；请稍后重试，或联系星辙业务平台。</p>
              </div>
              <Button className="w-full" onClick={() => void contextQuery.refetch()}>
                重新读取付款信息
              </Button>
            </div>
          )}

          {loadState === 'ready' && context && (
            <div className="p-5 sm:p-6">
              <dl className="space-y-4">
                <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
                  <dt className="text-sm text-muted-foreground">应付金额</dt>
                  <dd className="break-all text-2xl font-semibold tabular-nums sm:text-right">
                    <span className="mr-2 text-base font-medium text-muted-foreground">{context.currency}</span>
                    {context.amount}
                  </dd>
                </div>
                <div className="grid gap-1 border-t border-border pt-4 sm:grid-cols-[112px_minmax(0,1fr)] sm:gap-4">
                  <dt className="text-sm text-muted-foreground">星辙业务订单号</dt>
                  <dd className="break-all font-mono text-sm leading-6 sm:text-right">{context.businessOrderId}</dd>
                </div>
                <div className="grid gap-1 border-t border-border pt-4 sm:grid-cols-[112px_minmax(0,1fr)] sm:gap-4">
                  <dt className="text-sm text-muted-foreground">支付单号</dt>
                  <dd className="break-all font-mono text-sm leading-6 sm:text-right">{context.paymentOrderId}</dd>
                </div>
              </dl>

              {errorMessage && (
                <div className="mt-5 rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm leading-6" role="alert">
                  {errorMessage}
                </div>
              )}

              <div className="mt-6 border-t border-border pt-5">
                <Button className="h-11 w-full gap-2" onClick={() => void startCheckout()} disabled={isRedirecting}>
                  {isRedirecting ? (
                    <><LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />正在连接 Airwallex…</>
                  ) : (
                    <>前往安全收银台<ArrowUpRight className="size-4" aria-hidden="true" /></>
                  )}
                </Button>
                <p className="mt-3 flex items-center justify-center gap-2 text-center text-xs leading-5 text-muted-foreground">
                  <ShieldCheck className="size-4 shrink-0" aria-hidden="true" />支付资料由 Airwallex 托管处理，不会经过本支付服务页面。
                </p>
              </div>
            </div>
          )}
        </div>

        <p className="mt-5 text-center text-xs leading-5 text-muted-foreground">
          完成付款后，支付服务仍需核实处理结果；请勿仅凭浏览器跳转判断付款成功。
        </p>
      </section>
    </main>
  )
}
