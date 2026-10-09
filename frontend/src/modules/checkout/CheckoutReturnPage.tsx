import { useEffect, useState } from 'react'
import { CircleDashed, LoaderCircle } from 'lucide-react'

export function CheckoutReturnPage() {
  const [received, setReceived] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => setReceived(true), 1200)
    return () => window.clearTimeout(timer)
  }, [])

  return (
    <main className="flex min-h-svh items-center justify-center bg-background px-4 py-10 text-foreground sm:px-6">
      <section className="w-full max-w-lg rounded-2xl border border-border bg-card p-6 text-center shadow-sm sm:p-8" aria-labelledby="return-title" aria-live="polite">
        <div className="mx-auto flex size-12 items-center justify-center rounded-2xl bg-secondary text-secondary-foreground">
          {received ? <CircleDashed className="size-6" aria-hidden="true" /> : <LoaderCircle className="size-6 animate-spin motion-reduce:animate-none" aria-hidden="true" />}
        </div>
        <p className="mt-5 text-sm font-medium text-muted-foreground">星辙支付服务</p>
        <h1 id="return-title" className="mt-2 text-2xl font-semibold tracking-tight">
          {received ? '已收到付款页面返回' : '正在核实付款结果'}
        </h1>
        <p className="mx-auto mt-3 max-w-sm text-sm leading-6 text-muted-foreground">
          {received
            ? '支付服务正在核实本次付款。此页面不根据浏览器返回信息判定付款成功，请以星辙业务平台后续显示为准。'
            : '请稍候，支付服务正在核对付款状态。浏览器返回本身不代表付款成功。'}
        </p>
        <div className="mt-6 flex items-start gap-3 rounded-xl bg-muted p-4 text-left">
          <CircleDashed className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
          <p className="text-xs leading-5 text-muted-foreground">如状态暂未更新，请返回星辙业务平台查看订单；无需在此页面重复付款。</p>
        </div>
        <div className="sr-only" role="status">{received ? '已收到结果，等待服务端核实' : '正在核实付款结果'}</div>
      </section>
    </main>
  )
}
