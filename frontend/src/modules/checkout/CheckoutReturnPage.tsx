import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { AlertCircle, LoaderCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { CheckoutStatus } from '@/shared/api/contracts'
import { getCheckoutReturnStatus, refreshCheckoutReturn } from './api'

export function CheckoutReturnPage() {
  const { opaqueToken = '' } = useParams()
  return <CheckoutReturnContent key={opaqueToken} opaqueToken={opaqueToken} />
}

function CheckoutReturnContent({ opaqueToken }: { opaqueToken: string }) {
  const [status, setStatus] = useState<CheckoutStatus | null>(null)
  const [refreshError, setRefreshError] = useState('')
  const [refreshing, setRefreshing] = useState(false)
  const [returning, setReturning] = useState(false)
  const initialRefreshRef = useRef<{
    token: string
    promise: ReturnType<typeof refreshCheckoutReturn>
  } | null>(null)
  const mountedRef = useRef(false)
  const statusQuery = useQuery({
    queryKey: ['checkout-return-status', opaqueToken],
    queryFn: () => getCheckoutReturnStatus(opaqueToken),
    enabled: Boolean(opaqueToken),
    retry: false,
  })
  const { refetch: refetchStatus } = statusQuery

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  useEffect(() => {
    if (!opaqueToken || !statusQuery.data) return
    let cancelled = false
    let redirectTimer: number | undefined

    let request = initialRefreshRef.current
    if (!request || request.token !== opaqueToken) {
      request = {
        token: opaqueToken,
        promise: refreshCheckoutReturn(opaqueToken),
      }
      initialRefreshRef.current = request
    }

    void request.promise
      .then((refreshedStatus) => {
        if (cancelled || !mountedRef.current) return
        setStatus(refreshedStatus)
        setRefreshError('')
        setReturning(true)
        redirectTimer = window.setTimeout(() => {
          if (!cancelled && mountedRef.current) {
            window.location.assign(refreshedStatus.businessReturnUrl)
          }
        }, 700)
      })
      .catch(() => {
        if (cancelled || !mountedRef.current) return
        setRefreshError('暂时无法完成主动核实。浏览器返回不代表付款成功。')
      })

    return () => {
      cancelled = true
      if (redirectTimer !== undefined) window.clearTimeout(redirectTimer)
    }
  }, [opaqueToken, statusQuery.data])

  const retryRefresh = async () => {
    if (refreshing || !opaqueToken || !mountedRef.current) return
    setRefreshing(true)
    setRefreshError('')
    try {
      const refreshedStatus = await refreshCheckoutReturn(opaqueToken)
      if (!mountedRef.current) return
      setStatus(refreshedStatus)
      setReturning(true)
      window.location.assign(refreshedStatus.businessReturnUrl)
    } catch {
      if (mountedRef.current) {
        setRefreshError('本次主动核实未完成，原支付状态不会因此改变。')
      }
    } finally {
      if (mountedRef.current) setRefreshing(false)
    }
  }

  const visibleStatus = status ?? statusQuery.data
  const statusLabel = visibleStatus?.paymentStatus === 'SUCCEEDED'
    ? '服务端已核实付款成功'
    : visibleStatus?.paymentStatus === 'FAILED' || visibleStatus?.paymentStatus === 'CANCELLED'
      ? '服务端记录本次付款未完成'
      : visibleStatus?.paymentStatus
        ? '服务端记录付款仍在处理中'
        : ''
  const loading = statusQuery.isPending || (!status && !refreshError)

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10 text-foreground">
      <section className="w-full max-w-md rounded-2xl border border-border bg-card p-6 text-center shadow-sm sm:p-8" aria-live="polite">
        <div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-secondary text-secondary-foreground">
          {refreshError ? <AlertCircle className="size-5" aria-hidden="true" /> : <LoaderCircle className="size-5 animate-spin motion-reduce:animate-none" aria-hidden="true" />}
        </div>
        <p className="mt-5 text-sm font-medium text-muted-foreground">星辙支付服务</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">{refreshError ? '暂时无法核实' : '正在核实付款状态'}</h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          {statusLabel || (returning ? '正在返回星辙订单…' : '正在读取支付服务记录并向 Airwallex 核实。')}
        </p>
        {loading && <p className="mt-4 text-sm text-muted-foreground">正在读取服务端状态…</p>}
        {statusQuery.isError && <p className="mt-4 text-sm text-destructive" role="alert">暂时无法读取返回状态，无法安全确认订单地址。</p>}
        {refreshError && <p className="mt-4 text-sm text-destructive" role="alert">{refreshError}</p>}

        <div className="mt-6 flex flex-col gap-3">
          {statusQuery.isError && (
            <Button variant="outline" disabled={statusQuery.isFetching} onClick={() => void refetchStatus()}>
              {statusQuery.isFetching ? '正在读取…' : '重新读取服务端状态'}
            </Button>
          )}
          {refreshError && (
            <Button variant="outline" disabled={refreshing || !visibleStatus} onClick={() => void retryRefresh()}>
              {refreshing ? '正在核实…' : '重新核实付款状态'}
            </Button>
          )}
          {visibleStatus?.businessReturnUrl && (
            <a className="inline-flex min-h-10 items-center justify-center text-sm text-primary underline-offset-4 hover:underline" href={visibleStatus.businessReturnUrl}>
              返回星辙订单
            </a>
          )}
        </div>
        <p className="mt-5 text-xs leading-5 text-muted-foreground">支付结果以服务端核实状态为准，服务开通情况由星辙处理。</p>
      </section>
    </main>
  )
}
