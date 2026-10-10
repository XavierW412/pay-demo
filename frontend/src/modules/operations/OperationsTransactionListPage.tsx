import { useEffect, useState, type FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowRight, Search, X } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getTransactions } from './api'
import { formatMoney, formatTime, notificationLabel, paymentLabel, refundLabel } from './format'

const pageSize = 20

const statusOptions = [
  ['', '全部支付状态'], ['CREATING', '创建中'], ['AWAITING_PAYMENT', '等待付款'], ['PROCESSING', '处理中'],
  ['SUCCEEDED', '已支付'], ['FAILED', '失败'], ['REQUIRES_REVIEW', '待核实'], ['CANCELLED', '已取消'],
]
const refundOptions = [
  ['', '全部退款关注状态'], ['NOT_REQUESTED', '未收到退款指令'], ['PENDING_OPERATOR_CONFIRMATION', '待管理员确认'],
  ['SUBMITTING', '提交中'], ['RECEIVED', '请求已创建'], ['ACCEPTED', '业务退款成功 · 渠道已接受'], ['NEEDS_REVIEW', '待核实'],
  ['BLOCKED', '已拦截'], ['SUCCEEDED', '业务退款成功 · 渠道已结算'], ['FAILED', '退款失败'],
]
const notificationOptions = [['', '全部通知状态'], ['NOT_REQUIRED', '尚无通知'], ['PENDING', '待发送'], ['RETRY', '重试中'], ['DELIVERED', '已送达']]

export function OperationsTransactionListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('query') ?? ''
  const status = searchParams.get('status') ?? ''
  const currency = searchParams.get('currency') ?? ''
  const provider = searchParams.get('provider') ?? ''
  const refundStatus = searchParams.get('refundStatus') ?? ''
  const notificationStatus = searchParams.get('notificationStatus') ?? ''
  const from = searchParams.get('from') ?? ''
  const to = searchParams.get('to') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const [input, setInput] = useState(query)
  const [dateDraft, setDateDraft] = useState({ from, to })

  useEffect(() => setInput(query), [query])
  useEffect(() => setDateDraft({ from, to }), [from, to])

  const transactions = useQuery({
    queryKey: ['operations-transactions', { query, status, currency, provider, refundStatus, notificationStatus, from, to, page, size: pageSize }],
    queryFn: () => getTransactions({ query: query || undefined, status: status || undefined, currency: currency || undefined, provider: provider || undefined, refundStatus: refundStatus || undefined, notificationStatus: notificationStatus || undefined, from: from || undefined, to: to || undefined, page, size: pageSize }),
    placeholderData: keepPreviousData,
  })
  const result = transactions.data
  const totalPages = result ? Math.max(1, Math.ceil(result.total / result.size)) : 0
  const hasFilters = Boolean(query || status || currency || provider || refundStatus || notificationStatus || from || to)

  const updateParams = (updates: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(updates).forEach(([key, value]) => {
      if (value === undefined || value === '') next.delete(key)
      else next.set(key, String(value))
    })
    setSearchParams(next)
  }
  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    updateParams({ query: input.trim() || undefined, page: undefined })
  }
  const updateFilter = (key: string, value: string) => updateParams({ [key]: value || undefined, page: undefined })
  const clearFilters = () => {
    setInput('')
    setDateDraft({ from: '', to: '' })
    setSearchParams({})
  }
  const applyDateRange = () => updateParams({ from: dateDraft.from || undefined, to: dateDraft.to || undefined, page: undefined })
  const dateRangeValid = (!dateDraft.from && !dateDraft.to) || (Boolean(dateDraft.from) && Boolean(dateDraft.to))

  return <section>
    <div className="flex flex-wrap items-end justify-between gap-5">
      <div><p className="text-xs font-semibold tracking-[0.16em] text-primary">TRANSACTIONS</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">交易</h1><p className="mt-2 text-sm text-muted-foreground">搜索星辙业务订单、本项目支付单、Airwallex 支付单或已关联退款编号。</p><p className="mt-1 text-xs text-muted-foreground">同一支付单可能有多笔部分退款；列表优先显示待处理或待核实状态，详情中可查看全部退款记录。</p></div>
      <div className="rounded-xl border border-border bg-card px-5 py-3 text-right"><p className="text-xs text-muted-foreground">符合条件的支付单</p><p className="mt-1 text-2xl font-semibold tabular-nums">{result?.total.toLocaleString('zh-CN') ?? '—'}</p></div>
    </div>

    <form className="mt-7 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-2 xl:grid-cols-4" onSubmit={submitSearch}>
      <label className="flex min-h-10 items-center gap-2 rounded-md border border-input bg-background px-3 focus-within:ring-2 focus-within:ring-ring sm:col-span-2"><Search className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" /><span className="sr-only">搜索订单或支付编号</span><input className="w-full bg-transparent text-sm outline-none" placeholder="订单号、支付单号、Payment Intent、退款编号" value={input} onChange={(event) => setInput(event.target.value)} /><Button type="submit" size="sm" variant="secondary">搜索</Button></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">支付状态<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={status} onChange={(event) => updateFilter('status', event.target.value)}>{statusOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">退款关注状态<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={refundStatus} onChange={(event) => updateFilter('refundStatus', event.target.value)}>{refundOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">星辙通知<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={notificationStatus} onChange={(event) => updateFilter('notificationStatus', event.target.value)}>{notificationOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">币种<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={currency} onChange={(event) => updateFilter('currency', event.target.value)}><option value="">全部币种</option><option value="CNY">人民币 · CNY</option><option value="USD">美元 · USD</option></select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">来源<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={provider} onChange={(event) => updateFilter('provider', event.target.value)}><option value="">全部来源</option><option value="AIRWALLEX">Airwallex</option><option value="LOCAL_SIMULATOR">本地模拟</option></select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">创建日期起<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={dateDraft.from} max={dateDraft.to || undefined} onChange={(event) => setDateDraft((current) => ({ ...current, from: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">创建日期止<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={dateDraft.to} min={dateDraft.from || undefined} onChange={(event) => setDateDraft((current) => ({ ...current, to: event.target.value }))} /></label>
      <div className="flex flex-wrap items-end gap-2 sm:col-span-2 xl:col-span-4"><Button type="button" variant="secondary" size="sm" disabled={!dateRangeValid} onClick={applyDateRange}>应用日期范围</Button>{!dateRangeValid && <span className="pb-2 text-xs text-muted-foreground">日期筛选需同时选择起止日期。</span>}{hasFilters && <Button type="button" variant="ghost" size="sm" onClick={clearFilters}><X className="size-3.5" aria-hidden="true" />清除筛选</Button>}</div>
    </form>

    <div className="mt-5 overflow-hidden rounded-xl border border-border bg-card">
      <div className="overflow-x-auto"><table className="w-full min-w-[1080px] text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4 font-medium">星辙订单 / 支付单</th><th className="px-4 py-4 font-medium">来源</th><th className="px-4 py-4 font-medium">金额</th><th className="px-4 py-4 font-medium">支付</th><th className="px-4 py-4 font-medium">退款关注状态</th><th className="px-4 py-4 font-medium">星辙通知</th><th className="px-4 py-4 font-medium">最近更新时间</th><th className="px-5 py-4 text-right font-medium">详情</th></tr></thead>
        <tbody className="divide-y divide-border">{result?.items.map((item) => <tr key={item.id} className="hover:bg-muted/30"><td className="px-5 py-4"><span className="block font-medium">{item.businessOrderId}</span><span className="mt-1 block font-mono text-xs text-muted-foreground">{item.id}</span></td><td className="px-4 py-4"><span className={item.provider === 'LOCAL_SIMULATOR' ? 'rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground' : 'rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary'}>{item.provider === 'LOCAL_SIMULATOR' ? '本地模拟' : 'Airwallex'}</span></td><td className="whitespace-nowrap px-4 py-4 font-semibold tabular-nums">{formatMoney(item.amount, item.currency)}</td><td className="px-4 py-4">{paymentLabel(item.paymentStatus)}</td><td className="px-4 py-4">{refundLabel(item.refundStatus)}</td><td className="px-4 py-4">{notificationLabel(item.notificationStatus)}</td><td className="whitespace-nowrap px-4 py-4 text-muted-foreground">{formatTime(item.updatedAt)}</td><td className="px-5 py-4 text-right"><Link className="inline-flex items-center gap-1 font-medium text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.id)}`} state={{ returnTo: `/operations/transactions?${searchParams.toString()}` }}>查看<ArrowRight className="size-3.5" aria-hidden="true" /></Link></td></tr>)}</tbody></table></div>
      {transactions.isPending && <p className="px-5 py-12 text-center text-sm text-muted-foreground" role="status">正在读取交易…</p>}
      {transactions.isError && <div className="px-5 py-12 text-center text-sm text-destructive" role="alert"><p>交易读取失败，筛选条件已保留。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void transactions.refetch()}>重试</Button></div>}
      {result?.items.length === 0 && <p className="px-5 py-12 text-center text-sm text-muted-foreground">{hasFilters ? '没有符合当前筛选条件的交易。' : '暂无交易记录。'}</p>}
    </div>
    {result && result.total > 0 && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground"><span>第 {page + 1} / {totalPages} 页 · 共 {result.total.toLocaleString('zh-CN')} 笔{transactions.isFetching ? ' · 正在更新' : ''}</span><div className="flex gap-2"><Button type="button" variant="outline" size="sm" disabled={page === 0 || transactions.isFetching} onClick={() => updateParams({ page: page - 1 })}>上一页</Button><Button type="button" variant="outline" size="sm" disabled={page + 1 >= totalPages || transactions.isFetching} onClick={() => updateParams({ page: page + 1 })}>下一页</Button></div></div>}
  </section>
}
