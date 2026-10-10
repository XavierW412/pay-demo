import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowRight, Bell, CircleDollarSign, RotateCcw, ScanSearch } from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getOperationsExceptions, type ExceptionItem } from './api'
import { formatMoney, formatTime, reconciliationIssueLabel, refundLabel } from './format'

const pageSize = 20
const exceptionTypes = [
  { value: 'REFUND_CONFIRMATION', label: '退款待确认', icon: RotateCcw },
  { value: 'REFUND_REVIEW', label: '退款异常', icon: RotateCcw },
  { value: 'PAYMENT_DIFFERENCE', label: '支付核查', icon: CircleDollarSign },
  { value: 'NOTIFICATION_FAILURE', label: '通知失败', icon: Bell },
] as const
type ExceptionType = typeof exceptionTypes[number]['value']

const isExceptionType = (value: string | null): value is ExceptionType => exceptionTypes.some((item) => item.value === value)
const safeTime = (value: string | null): string => value ? formatTime(value) : '暂无时间记录'
const statusLabel = (item: ExceptionItem): string => {
  if (item.type === 'REFUND_CONFIRMATION' || item.type === 'REFUND_REVIEW') return refundLabel(item.status)
  if (item.type === 'PAYMENT_DIFFERENCE') return ({ DIFFERENCE: '发现差异', QUERY_FAILED: '查询失败', NOT_CHECKABLE: '暂无法核查' }[item.status] ?? item.status)
  return ({ PENDING: '待发送', RETRY: '等待重试', FAILED: '发送失败' }[item.deliveryStatus ?? item.status] ?? item.deliveryStatus ?? item.status)
}

function ExceptionDetails({ item }: { item: ExceptionItem }) {
  if (item.type === 'NOTIFICATION_FAILURE') return <div className="mt-2 space-y-1 text-xs text-muted-foreground"><p>通知事件：<span className="font-mono">{item.eventId ?? '未提供事件编号'}</span></p><p>投递状态：{item.deliveryStatus ?? item.status} · 尝试次数：{item.attemptCount ?? '未知'}{item.nextAttemptAt ? ` · 下次尝试 ${safeTime(item.nextAttemptAt)}` : ''}</p></div>
  if (item.type === 'PAYMENT_DIFFERENCE') {
    const issues = item.issueCodes?.split(/[;,]/).map((issue) => issue.trim()).filter(Boolean) ?? []
    return <div className="mt-2 space-y-1 text-xs text-muted-foreground"><p>发现问题：{issues.length ? issues.map(reconciliationIssueLabel).join('、') : '需查看核查详情'}</p><p>最近核查：{safeTime(item.checkedAt)}</p></div>
  }
  return <div className="mt-2 space-y-1 text-xs text-muted-foreground"><p>星辙退款指令：<span className="font-mono">{item.refundRequestId ?? '未提供'}</span></p>{item.refundId && <p>支付项目退款号：<span className="font-mono">{item.refundId}</span></p>}{item.providerRefundId && <p>Airwallex 退款号：<span className="font-mono">{item.providerRefundId}</span></p>}</div>
}

function ExceptionRow({ item, returnTo }: { item: ExceptionItem; returnTo: string }) {
  const timestamp = item.updatedAt ?? item.checkedAt ?? item.createdAt
  return <article className="grid gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,1fr)_auto_auto] lg:items-center">
    <div className="min-w-0"><Link className="block truncate text-sm font-medium hover:text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.paymentOrderId)}`} state={{ returnTo }}>{item.businessOrderId}</Link><p className="mt-1 break-all font-mono text-[11px] text-muted-foreground">支付单 {item.paymentOrderId}</p><ExceptionDetails item={item} /></div>
    <div className="flex flex-wrap items-center gap-3 text-xs"><span className="font-semibold tabular-nums">{formatMoney(item.amount, item.currency)}</span><span className="rounded-full bg-amber-500/10 px-2.5 py-1 font-medium text-amber-800">{statusLabel(item)}</span></div>
    <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground lg:justify-end"><span>{timestamp ? `最近更新 ${safeTime(timestamp)}` : '时间待补充'}</span><Link className="inline-flex shrink-0 items-center gap-1 font-medium text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.paymentOrderId)}`} state={{ returnTo }}>查看详情<ArrowRight className="size-3.5" aria-hidden="true" /></Link></div>
  </article>
}

export function OperationsReconciliationPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const requestedType = searchParams.get('type')
  const type: ExceptionType = isExceptionType(requestedType) ? requestedType : 'PAYMENT_DIFFERENCE'
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const exceptions = useQuery({
    queryKey: ['operations-exceptions', type, page, pageSize],
    queryFn: () => getOperationsExceptions({ type, page, size: pageSize }),
    placeholderData: keepPreviousData,
  })
  const result = exceptions.data
  const totalPages = result ? Math.max(1, Math.ceil(result.total / result.size)) : 0
  const returnTo = `${location.pathname}${location.search}`
  const changeType = (nextType: ExceptionType) => setSearchParams({ type: nextType })
  const changePage = (nextPage: number) => setSearchParams({ type, page: String(nextPage) })

  return <section>
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">EXCEPTIONS</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">对账与异常</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">按异常来源分队列查看真实记录。支付核查只比较本项目与 Airwallex；结算报告和星辙真实业务账本对账尚未接入。</p>
    <div className="mt-5 flex items-start gap-3 rounded-xl border border-border bg-card p-4 text-sm"><ScanSearch className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" /><p><strong>资金结算尚未接入。</strong><span className="text-muted-foreground"> 本页不代表银行到账，也不会用支付金额减退款金额估算入账。</span></p></div>

    <div className="mt-6 flex flex-wrap gap-2 border-b border-border" role="tablist" aria-label="异常队列">{exceptionTypes.map(({ value, label, icon: Icon }) => <button key={value} type="button" role="tab" aria-selected={type === value} className={`inline-flex min-h-11 items-center gap-2 border-b-2 px-3 text-sm font-medium transition-colors ${type === value ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground'}`} onClick={() => changeType(value)}><Icon className="size-4" aria-hidden="true" />{label}</button>)}</div>

    <div className="mt-5 flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-lg font-semibold">{exceptionTypes.find((item) => item.value === type)?.label}</h2><p className="mt-1 text-xs text-muted-foreground">{result ? `共 ${result.total.toLocaleString('zh-CN')} 条，按服务端返回顺序显示` : '读取此类异常的服务端记录'}</p></div>{result && result.total > 0 && <span className="text-sm text-muted-foreground">第 {page + 1} / {totalPages} 页</span>}</div>
    <div className="mt-4 divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
      {exceptions.isPending && <p className="p-10 text-center text-sm text-muted-foreground" role="status">正在读取异常记录…</p>}
      {exceptions.isError && <div className="p-8 text-center text-sm text-destructive" role="alert"><p>异常队列读取失败；不能据此判断当前没有异常。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void exceptions.refetch()}>重试</Button></div>}
      {result?.items.map((item, index) => <ExceptionRow key={`${item.paymentOrderId}-${item.refundId ?? item.eventId ?? index}`} item={item} returnTo={returnTo} />)}
      {result?.items.length === 0 && <p className="p-10 text-center text-sm text-muted-foreground">此队列当前没有符合条件的异常记录。</p>}
    </div>
    {result && result.total > 0 && <div className="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground"><span>第 {page + 1} / {totalPages} 页 · 共 {result.total.toLocaleString('zh-CN')} 条</span><div className="flex gap-2"><Button variant="outline" size="sm" disabled={page === 0 || exceptions.isFetching} onClick={() => changePage(page - 1)}>上一页</Button><Button variant="outline" size="sm" disabled={page + 1 >= totalPages || exceptions.isFetching} onClick={() => changePage(page + 1)}>下一页</Button></div></div>}
  </section>
}
