import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { init } from '@airwallex/components-sdk'
import { AlertCircle, LoaderCircle, LockKeyhole } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getCheckoutContext } from './api'

type CheckoutState = 'loading' | 'redirecting' | 'error'

export function CheckoutPage() {
  const { opaqueToken = '' } = useParams()
  const [retryKey, setRetryKey] = useState(0)
  const [checkoutState, setCheckoutState] = useState<CheckoutState>('loading')
  const [errorMessage, setErrorMessage] = useState('')
  const redirectStarted = useRef(false)
  const contextQuery = useQuery({
    queryKey: ['checkout-context', opaqueToken],
    queryFn: () => getCheckoutContext(opaqueToken),
    enabled: Boolean(opaqueToken),
    gcTime: 0,
    retry: false,
  })
  const context = contextQuery.data

  useEffect(() => {
    if (!context) return
    let cancelled = false
    let timeoutId: ReturnType<typeof setTimeout> | undefined

    const fail = (message: string) => {
      if (cancelled) return
      if (timeoutId) clearTimeout(timeoutId)
      setCheckoutState('error')
      setErrorMessage(message)
    }

    const startHostedCheckout = async () => {
      setCheckoutState('loading')
      setErrorMessage('')
      timeoutId = setTimeout(() => {
        fail('暂时没有打开 Airwallex 托管收银台。可重试，或返回星辙订单查看状态。')
      }, 20_000)

      try {
        const { payments } = await init({
          env: context.environment === 'sandbox' ? 'demo' : 'prod',
          enabledElements: ['payments'],
        })
        if (cancelled) return
        if (!payments) throw new Error('托管付款服务未初始化')
        if (redirectStarted.current) return

        redirectStarted.current = true
        setCheckoutState('redirecting')
        const checkoutOptions: Parameters<typeof payments.redirectToCheckout>[0] = {
          intent_id: context.intentId,
          client_secret: context.clientSecret,
          currency: context.currency,
          ...(context.countryCode ? { country_code: context.countryCode } : {}),
          successUrl: context.businessReturnUrl,
        }
        const result = payments.redirectToCheckout(checkoutOptions)
        if (typeof result === 'string') {
          const redirectUrl = new URL(result)
          if (redirectUrl.protocol !== 'https:') {
            throw new Error('Airwallex 托管地址无效')
          }
          window.location.assign(redirectUrl.toString())
        }
      } catch {
        redirectStarted.current = false
        fail('暂时无法打开托管收银台。付款状态不会因此改变，请稍后重试。')
      }
    }

    void startHostedCheckout()
    return () => {
      cancelled = true
      if (timeoutId) clearTimeout(timeoutId)
    }
  }, [context, retryKey])

  const retryCheckout = () => {
    redirectStarted.current = false
    setRetryKey((key) => key + 1)
  }

  const loading = contextQuery.isPending || checkoutState === 'loading'
  const failed = contextQuery.isError || checkoutState === 'error'

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-md rounded-2xl border border-border bg-card p-6 text-center shadow-sm sm:p-8" aria-live="polite">
        <div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
          {failed ? <AlertCircle className="size-5" aria-hidden="true" /> : <LockKeyhole className="size-5" aria-hidden="true" />}
        </div>
        <p className="mt-5 text-sm font-medium text-muted-foreground">星辙支付服务</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">{failed ? '无法打开托管收银台' : '正在前往安全收银台'}</h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          {failed ? errorMessage || '付款信息暂时不可用，请返回星辙订单重新发起。' : '正在安全连接 Airwallex，请稍候。'}
        </p>

        {loading && (
          <p className="mt-6 flex items-center justify-center gap-2 text-sm text-muted-foreground" role="status">
            <LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />正在准备跳转…
          </p>
        )}

        {failed && (
          <div className="mt-6 flex flex-col gap-3">
            {!contextQuery.isError && <Button onClick={retryCheckout}>重试</Button>}
            {contextQuery.isError && <Button onClick={() => void contextQuery.refetch()}>重新读取付款信息</Button>}
            {context?.businessReturnUrl && (
              <a className="inline-flex min-h-10 items-center justify-center text-sm text-primary underline-offset-4 hover:underline" href={context.businessReturnUrl}>
                返回星辙订单
              </a>
            )}
            {!context && <Link className="text-sm text-primary underline-offset-4 hover:underline" to="/">返回首页</Link>}
          </div>
        )}
      </section>
    </main>
  )
}
