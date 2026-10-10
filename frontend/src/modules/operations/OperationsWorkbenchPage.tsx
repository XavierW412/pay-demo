import { useQuery } from '@tanstack/react-query'
import { Activity, AlertTriangle, ArrowRight, Bell, CalendarClock, CircleDollarSign, Clock3, RefreshCw, RotateCcw, WalletCards } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useOutletContext } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { apiClient } from '@/shared/api/client'
import type { OperatorSession } from './api'
import { getFinanceOverview, type FinanceCurrencySummary } from './api'
import { formatMoney, formatTime } from './format'

type ExceptionType = 'REFUND_CONFIRMATION' | 'REFUND_REVIEW' | 'PAYMENT_DIFFERENCE' | 'NOTIFICATION_FAILURE'

interface WorkbenchException {
  type: ExceptionType
  paymentOrderId: string
  businessOrderId: string
  amount: string
  currency: string
  status: string
  createdAt: string | null
  updatedAt: string | null
  checkedAt?: string | null
  eventId?: string | null
  attemptCount?: number | null
  issueCodes?: string | null
}

interface WorkbenchQueue {
  type: ExceptionType
  label: string
  count: number
  items: WorkbenchException[]
}

interface WorkbenchActivity {
  id: string | number
  type: string
  title: string
  status: string | null
  actor: string | null
  occurredAt: string
  paymentOrderId: string | null
}

interface WorkbenchResponse {
  generatedAt: string
  queues: WorkbenchQueue[]
  recentActivity: WorkbenchActivity[]
}

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

const queueDefinitions: { type: ExceptionType; title: string; description: string; icon: typeof RotateCcw; tone: string }[] = [
  { type: 'REFUND_CONFIRMATION', title: '退款待确认', description: '星辙已批准，等待支付侧管理员确认执行', icon: RotateCcw, tone: 'text-amber-700 bg-amber-500/10' },
  { type: 'REFUND_REVIEW', title: '退款待核实 / 异常', description: '查询结果未知、退款被拦截或需要核对', icon: AlertTriangle, tone: 'text-rose-700 bg-rose-500/10' },
  { type: 'PAYMENT_DIFFERENCE', title: '支付核查差异', description: '本项目记录与 Airwallex 支付状态存在差异', icon: CircleDollarSign, tone: 'text-amber-700 bg-amber-500/10' },
  { type: 'NOTIFICATION_FAILURE', title: '星辙通知失败', description: '支付或退款结果尚未成功送达星辙', icon: Bell, tone: 'text-sky-700 bg-sky-500/10' },
]

const getWorkbench = async (from: string, to: string): Promise<WorkbenchResponse> => {
  const response = await apiClient.get<WorkbenchResponse>('/api/v1/operations/workbench', { params: { from, to } })
  return response.data
}

const queueHref = (type: ExceptionType): string => `/operations/reconciliation?type=${type}`

const statusLabel = (status: string): string => ({
  PENDING_OPERATOR_CONFIRMATION: '待管理员确认',
  NEEDS_REVIEW: '待核实',
  BLOCKED: '已拦截',
  DIFFERENCE: '发现差异',
  QUERY_FAILED: '查询失败',
  RETRY: '等待重试',
  PENDING: '待发送',
}[status] ?? status)

function formatAge(value: string): string {
  const elapsedMinutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60_000))
  if (!Number.isFinite(elapsedMinutes)) return '时间未知'
  if (elapsedMinutes < 60) return `等待 ${elapsedMinutes} 分钟`
  if (elapsedMinutes < 60 * 24) return `等待 ${Math.floor(elapsedMinutes / 60)} 小时`
  return `等待 ${Math.floor(elapsedMinutes / (60 * 24))} 天`
}

function CurrencySummary({ summary }: { summary: FinanceCurrencySummary }) {
  return (
    <section className="overflow-hidden rounded-xl border border-border bg-card">
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <div><h3 className="text-sm font-semibold">{summary.currency}</h3><p className="mt-0.5 text-[11px] text-muted-foreground">Airwallex 记录 · 不做汇率换算</p></div>
        <span className="rounded-full bg-muted px-2.5 py-1 text-[11px] font-medium text-muted-foreground">支付与退款</span>
      </div>
      <div className="divide-y divide-border">
        <div className="flex items-center justify-between gap-3 px-4 py-3"><div><p className="text-xs text-muted-foreground">目前支付成功</p><p className="mt-1 text-[11px] text-muted-foreground">创建期内 {summary.paymentSucceededCount} 笔</p></div><p className="text-sm font-semibold tabular-nums">{formatMoney(summary.paymentSucceededAmount, summary.currency)}</p></div>
        <div className="flex items-center justify-between gap-3 px-4 py-3"><div><p className="text-xs text-muted-foreground">待处理退款</p><p className="mt-1 text-[11px] text-muted-foreground">待确认 + 处理中</p></div><p className="text-sm font-semibold tabular-nums">{formatMoney(addAmounts(summary.refundAwaitingConfirmationAmount, summary.refundProcessingAmount), summary.currency)}</p></div>
        <div className="flex items-center justify-between gap-3 px-4 py-3"><div><p className="text-xs text-muted-foreground">业务退款成功</p><p className="mt-1 text-[11px] text-muted-foreground">渠道已接受 + 已结算</p></div><p className="text-sm font-semibold tabular-nums">{formatMoney(summary.refundBusinessSucceededAmount, summary.currency)}</p></div>
        <div className="flex items-center justify-between gap-3 px-4 py-3"><div><p className="text-xs text-muted-foreground">渠道待结算 / 已结算</p><p className="mt-1 text-[11px] text-muted-foreground">成功金额的互斥分项，不重复累加</p></div><p className="text-right text-sm font-semibold tabular-nums">{formatMoney(summary.refundChannelPendingSettlementAmount, summary.currency)}<span className="block text-xs font-normal text-muted-foreground">已结算 {formatMoney(summary.refundChannelSettledAmount, summary.currency)}</span></p></div>
      </div>
      <div className="flex items-start gap-2.5 border-t border-border bg-amber-500/5 px-4 py-3"><WalletCards className="mt-0.5 size-4 shrink-0 text-amber-700" aria-hidden="true" /><p className="text-xs leading-5"><span className="font-medium">实际入账与手续费：待接入</span><span className="block text-muted-foreground">不根据支付金额减退款金额估算。</span></p></div>
    </section>
  )
}

function addAmounts(first: string, second: string): string {
  const toMinorUnits = (amount: string) => {
    const [whole = '0', fraction = ''] = amount.split('.')
    return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2))
  }
  const total = toMinorUnits(first) + toMinorUnits(second)
  return `${total / 100n}.${String(total % 100n).padStart(2, '0')}`
}

function QueueSection({ queue, type, returnTo }: { queue: WorkbenchQueue | undefined; type: ExceptionType; returnTo: string }) {
  const definition = queueDefinitions.find((item) => item.type === type)!
  const Icon = definition.icon
  const items = queue?.items ?? []

  return (
    <section className="overflow-hidden rounded-xl border border-border bg-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-4 sm:px-5">
        <div className="flex min-w-0 items-center gap-3">
          <span className={`grid size-9 shrink-0 place-items-center rounded-lg ${definition.tone}`}><Icon className="size-4" aria-hidden="true" /></span>
          <div><h2 className="text-sm font-semibold">{definition.title}</h2><p className="mt-1 text-xs text-muted-foreground">{definition.description}</p></div>
        </div>
        <div className="flex items-center gap-3"><span className="min-w-8 text-right text-xl font-semibold tabular-nums">{queue?.count.toLocaleString('zh-CN') ?? '—'}</span><Link className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline" to={queueHref(type)}>查看队列<ArrowRight className="size-3.5" aria-hidden="true" /></Link></div>
      </div>
      {items.length > 0 ? <div className="divide-y divide-border">
        {items.slice(0, 5).map((item) => <article key={`${type}-${item.paymentOrderId}-${item.eventId ?? item.updatedAt}`} className="flex flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div className="min-w-0"><Link className="block truncate text-sm font-medium text-foreground hover:text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.paymentOrderId)}`} state={{ returnTo }}>{item.businessOrderId}</Link><p className="mt-1 font-mono text-[11px] text-muted-foreground">{item.paymentOrderId}</p></div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs"><span className="font-semibold tabular-nums">{formatMoney(item.amount, item.currency)}</span><span className="rounded-full bg-muted px-2.5 py-1 text-muted-foreground">{statusLabel(item.status)}</span><span className="inline-flex items-center gap-1.5 text-muted-foreground" title={`最近更新：${item.updatedAt ? formatTime(item.updatedAt) : '未知'}`}><Clock3 className="size-3.5" aria-hidden="true" />{formatAge(item.updatedAt ?? item.createdAt ?? '')}</span></div>
        </article>)}
      </div> : <p className="px-5 py-5 text-sm text-muted-foreground">{queue ? '当前没有待处理记录。' : '此类队列暂不可用。'}</p>}
    </section>
  )
}

export function OperationsWorkbenchPage() {
  const session = useOutletContext<OperatorSession>()
  const location = useLocation()
  const [range, setRange] = useState(initialRange)
  const [draft, setDraft] = useState(range)
  const workbench = useQuery({ queryKey: ['operations-workbench', range.from, range.to], queryFn: () => getWorkbench(range.from, range.to) })
  const finance = useQuery({ queryKey: ['operations-finance-overview', range.from, range.to], queryFn: () => getFinanceOverview(range.from, range.to) })
  const queues = workbench.data?.queues ?? []
  const returnTo = `${location.pathname}${location.search}`

  const submitRange = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setRange(draft)
  }

  return <section>
    <div className="flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
      <div>
        <p className="text-xs font-semibold tracking-[0.16em] text-primary">OPERATIONS</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">运营工作台</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted-foreground">先处理需要关注的退款、支付核查和通知，再按币种查看支付与退款记录。</p>
      </div>
      <form className="flex flex-wrap items-end gap-2.5" onSubmit={submitRange}>
        <label className="grid gap-1 text-[11px] text-muted-foreground">开始日期<input className="h-9 rounded-md border border-input bg-card px-2.5 text-sm text-foreground" type="date" value={draft.from} max={draft.to} onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))} /></label>
        <label className="grid gap-1 text-[11px] text-muted-foreground">结束日期<input className="h-9 rounded-md border border-input bg-card px-2.5 text-sm text-foreground" type="date" value={draft.to} min={draft.from} max={localDateValue(new Date())} onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))} /></label>
        <Button type="submit" size="sm" disabled={workbench.isFetching || finance.isFetching}><RefreshCw className="size-3.5" aria-hidden="true" />更新</Button>
      </form>
    </div>

    {session.role === 'OWNER_READONLY' && <div className="mt-5 flex items-start gap-2.5 rounded-lg border border-border bg-muted/35 px-4 py-3 text-sm"><ShieldNotice /><p><strong>只读账号。</strong><span className="text-muted-foreground"> 你可以查看交易和操作记录；需要处理时由有权限的星辙员工操作。</span></p></div>}

    {workbench.isError && <div className="mt-6 rounded-xl border border-destructive/30 bg-destructive/5 p-5 text-sm text-destructive" role="alert"><p className="font-medium">待办队列读取失败</p><p className="mt-1 text-xs">当前无法确认待处理事项数量，请重试后再判断队列是否为空。</p><Button className="mt-3" variant="outline" size="sm" onClick={() => void workbench.refetch()}>重试读取</Button></div>}

    <div className="mt-6 grid items-start gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
      <div className="min-w-0 space-y-4">
        <div className="flex items-end justify-between gap-3"><div><h2 className="text-base font-semibold">待处理事项</h2><p className="mt-1 text-xs text-muted-foreground">按需要员工关注的事项排列 · 最新记录优先</p></div>{workbench.data?.generatedAt && <span className="text-[11px] text-muted-foreground">更新于 {formatTime(workbench.data.generatedAt)}</span>}</div>
        {workbench.isPending ? <div className="rounded-xl border border-border bg-card px-5 py-12 text-center text-sm text-muted-foreground" role="status">正在读取待处理事项…</div> : <div className="space-y-3">{queueDefinitions.map(({ type }) => <QueueSection key={type} queue={queues.find((queue) => queue.type === type)} type={type} returnTo={returnTo} />)}</div>}
      </div>

      <aside className="space-y-4">
        <div className="flex items-end justify-between gap-3"><div><h2 className="text-base font-semibold">资金摘要</h2><p className="mt-1 text-xs text-muted-foreground">{range.from} 至 {range.to}</p></div><Link className="text-xs font-medium text-primary hover:underline" to="/operations/finance">详细查看</Link></div>
        <div className="flex items-start gap-2.5 rounded-lg border border-amber-600/20 bg-amber-500/5 px-3.5 py-3"><WalletCards className="mt-0.5 size-4 shrink-0 text-amber-700" aria-hidden="true" /><p className="text-xs leading-5"><span className="font-medium">结算报告尚未接入。</span><span className="text-muted-foreground"> 入账金额和手续费不做推算。</span></p></div>
        {finance.isPending && <p className="rounded-xl border border-border bg-card p-6 text-center text-sm text-muted-foreground" role="status">正在读取资金摘要…</p>}
        {finance.isError && <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive" role="alert"><p>资金摘要读取失败；待办队列仍可单独查看。</p><Button className="mt-2" variant="outline" size="sm" onClick={() => void finance.refetch()}>重试读取</Button></div>}
        {finance.data?.currencies.map((summary) => <CurrencySummary key={summary.currency} summary={summary} />)}
        {finance.data?.currencies.length === 0 && <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">所选时间范围暂无资金记录。</p>}
        <ActivitySection activity={workbench.data?.recentActivity ?? []} isError={workbench.isError} returnTo={returnTo} />
      </aside>
    </div>
  </section>
}

function ActivitySection({ activity, isError, returnTo }: { activity: WorkbenchActivity[]; isError: boolean; returnTo: string }) {
  return <section className="overflow-hidden rounded-xl border border-border bg-card">
    <div className="flex items-center gap-2 border-b border-border px-4 py-3"><Activity className="size-4 text-muted-foreground" aria-hidden="true" /><h2 className="text-sm font-semibold">近期状态变化</h2></div>
    {isError ? <p className="px-4 py-4 text-xs text-muted-foreground">近期动态暂不可用。</p> : activity.length ? <ol className="divide-y divide-border">{activity.slice(0, 6).map((item) => <li key={item.id} className="flex gap-3 px-4 py-3"><CalendarClock className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden="true" /><div className="min-w-0"><p className="text-xs font-medium">{item.title}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">{[item.status, item.actor].filter(Boolean).join(' · ') || '状态记录已更新'}</p><p className="mt-1 text-[10px] text-muted-foreground">{formatTime(item.occurredAt)}</p>{item.paymentOrderId && <Link className="mt-1 inline-flex items-center gap-1 text-xs text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.paymentOrderId)}`} state={{ returnTo }}>查看支付单<ArrowRight className="size-3" aria-hidden="true" /></Link>}</div></li>)}</ol> : <p className="px-4 py-4 text-xs text-muted-foreground">暂无可追溯的近期状态变化。</p>}
  </section>
}

function ShieldNotice() {
  return <span className="grid size-5 shrink-0 place-items-center rounded-full border border-border text-[11px] font-semibold text-muted-foreground" aria-hidden="true">只</span>
}
