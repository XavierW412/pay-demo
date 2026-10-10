import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowDownLeft, CalendarDays, CircleDollarSign, RefreshCw, WalletCards } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { getFinanceOverview, type FinanceCurrencySummary } from './api'
import { formatMoney } from './format'

const localDateValue = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const initialRange = () => {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 29)
  return { from: localDateValue(from), to: localDateValue(to) }
}

const sumAmounts = (...amounts: string[]): string => {
  const cents = amounts.reduce((total, amount) => {
    const [whole = '0', fraction = ''] = amount.split('.')
    return total + BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
  }, 0n)
  return `${cents / 100n}.${String(cents % 100n).padStart(2, '0')}`
}

function Metric({ label, value, detail, icon: Icon, tone = 'default' }: {
  label: string
  value: string
  detail: string
  icon: typeof CircleDollarSign
  tone?: 'default' | 'warning'
}) {
  return <article className="rounded-xl border border-border bg-card p-5">
    <div className="flex items-start justify-between gap-3">
      <div>
        <p className="text-sm text-muted-foreground">{label}</p>
        <p className="mt-3 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      </div>
      <span className={`grid size-10 place-items-center rounded-xl ${tone === 'warning' ? 'bg-amber-500/10 text-amber-700' : 'bg-primary/10 text-primary'}`}>
        <Icon className="size-5" aria-hidden="true" />
      </span>
    </div>
    <p className="mt-3 text-xs leading-5 text-muted-foreground">{detail}</p>
  </article>
}

function CurrencyPanel({ summary }: { summary: FinanceCurrencySummary }) {
  const inFlightRefundAmount = sumAmounts(summary.refundAwaitingConfirmationAmount, summary.refundProcessingAmount)

  return <section className="overflow-hidden rounded-2xl border border-border bg-card">
    <div className="flex items-center justify-between border-b border-border px-5 py-4 sm:px-6">
      <div>
        <h2 className="text-lg font-semibold">{summary.currency}</h2>
        <p className="mt-1 text-xs text-muted-foreground">此币种独立汇总，不做汇率换算</p>
      </div>
      <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">Airwallex</span>
    </div>
    <div className="grid gap-px bg-border sm:grid-cols-2 xl:grid-cols-4">
      <div className="bg-card p-5">
        <p className="text-xs text-muted-foreground">支付成功金额</p>
        <p className="mt-2 text-xl font-semibold tabular-nums">{formatMoney(summary.paymentSucceededAmount, summary.currency)}</p>
        <p className="mt-2 text-xs text-muted-foreground">{summary.paymentSucceededCount} 笔 · 按支付单创建日期筛选当前成功状态，不代表结算</p>
      </div>
      <div className="bg-card p-5">
        <p className="text-xs text-muted-foreground">待处理退款金额</p>
        <p className="mt-2 text-xl font-semibold tabular-nums">{formatMoney(inFlightRefundAmount, summary.currency)}</p>
        <p className="mt-2 text-xs text-muted-foreground">按退款指令创建日期筛选当前待确认、处理中状态</p>
      </div>
      <div className="bg-card p-5">
        <p className="text-xs text-muted-foreground">退款执行成功</p>
        <p className="mt-2 text-xl font-semibold tabular-nums">{formatMoney(summary.refundSucceededAmount, summary.currency)}</p>
        <p className="mt-2 text-xs text-muted-foreground">按退款指令创建日期筛选当前成功状态；结算抵扣待报表核对</p>
      </div>
      <div className="bg-card p-5">
        <p className="text-xs text-muted-foreground">需要关注</p>
        <p className="mt-2 text-xl font-semibold tabular-nums">{summary.paymentExceptionCount + summary.refundExceptionCount}</p>
        <p className="mt-2 text-xs text-muted-foreground">支付核查异常 {summary.paymentExceptionCount} · 退款拦截/待核实 {summary.refundExceptionCount}</p>
      </div>
    </div>
    <div className="flex flex-col gap-3 border-t border-border bg-muted/20 px-5 py-4 text-sm sm:flex-row sm:items-center sm:justify-between sm:px-6">
      <div className="flex items-start gap-3">
        <WalletCards className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
        <div><p className="font-medium">实际结算入账与手续费</p><p className="mt-1 text-xs text-muted-foreground">Airwallex 结算报告接入后显示；当前不以支付金额减退款金额估算。</p></div>
      </div>
      <span className="w-fit rounded-full border border-amber-600/20 bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-800">待接入</span>
    </div>
  </section>
}

export function OperationsFinanceOverviewPage() {
  const [range, setRange] = useState(initialRange)
  const [draft, setDraft] = useState(range)
  const overview = useQuery({
    queryKey: ['operations-finance-overview', range.from, range.to],
    queryFn: () => getFinanceOverview(range.from, range.to),
  })

  const submitRange = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setRange(draft)
  }

  return <section>
    <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">FINANCE OVERVIEW</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">资金总览</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">按订单创建日期查看支付与退款目前的状态，并分币种汇总。支付成功、退款执行和资金结算分别展示。</p>
      </div>
      <form className="flex flex-wrap items-end gap-3" onSubmit={submitRange}>
        <label className="grid gap-1.5 text-xs text-muted-foreground">开始日期
          <input className="h-9 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={draft.from} max={draft.to} onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))} />
        </label>
        <label className="grid gap-1.5 text-xs text-muted-foreground">结束日期
          <input className="h-9 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={draft.to} min={draft.from} max={localDateValue(new Date())} onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))} />
        </label>
        <Button type="submit" disabled={overview.isFetching}><CalendarDays className="size-4" aria-hidden="true" />查询</Button>
      </form>
    </div>

    <div className="mt-7 flex items-start gap-3 rounded-xl border border-amber-600/20 bg-amber-500/5 p-4 text-sm">
      <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-700" aria-hidden="true" />
      <p><strong>结算数据源尚未接入。</strong><span className="text-muted-foreground"> 当前显示支付项目已记录的 Airwallex 支付与退款状态；不代表 Airwallex 商户余额或银行账户实际到账。</span></p>
    </div>

    <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <Metric label="目前支付成功笔数" value={overview.data?.currencies.reduce((total, item) => total + item.paymentSucceededCount, 0).toLocaleString('zh-CN') ?? '—'} detail={`${range.from} 至 ${range.to} 创建的 Airwallex 支付单中，当前成功的订单`} icon={ArrowDownLeft} />
      <Metric label="结算报告接入状态" value="待接入" detail="实际结算金额与手续费等待 Airwallex 报告数据" icon={WalletCards} tone="warning" />
      <Metric label="支付核查差异" value={overview.data?.currencies.reduce((total, item) => total + item.paymentExceptionCount, 0).toLocaleString('zh-CN') ?? '—'} detail="每笔交易只按最近一次核查结果统计" icon={RefreshCw} tone="warning" />
      <Metric label="退款拦截或待核实" value={overview.data?.currencies.reduce((total, item) => total + item.refundExceptionCount, 0).toLocaleString('zh-CN') ?? '—'} detail="需员工查看记录并按既定流程处理" icon={AlertTriangle} tone="warning" />
    </div>

    <div className="mt-7 space-y-5">
      {overview.data?.currencies.map((summary) => <CurrencyPanel key={summary.currency} summary={summary} />)}
      {overview.isPending && <p className="rounded-xl border border-border bg-card p-10 text-center text-sm text-muted-foreground" role="status">正在读取资金记录…</p>}
      {overview.isError && <p className="rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-sm text-destructive" role="alert">资金记录读取失败，请稍后重试或查看运营记录。</p>}
    </div>
  </section>
}
