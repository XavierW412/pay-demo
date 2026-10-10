import { useEffect, useState, type FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { FileClock, Search, X } from 'lucide-react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getOperatorAudit } from './api'
import { auditActionLabel, auditOutcomeLabel, formatTime } from './format'

const pageSize = 25
const daysBetween = (from: string, to: string): number => Math.floor((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)
const actionOptions = [
  ['', '全部操作'], ['REFRESH_PROVIDER_STATUS', '核实支付状态'], ['REFRESH_REFUND_STATUS', '核实退款状态'],
  ['RETRY_NOTIFICATION', '重试星辙通知'], ['ADD_EXCEPTION_NOTE', '添加处理备注'], ['REFUND_CONFIRM_AND_SUBMIT', '确认并发起退款'],
  ['ACKNOWLEDGE_DIAGNOSTIC', '确认诊断事件'], ['ADD_DIAGNOSTIC_NOTE', '添加诊断备注'], ['RESOLVE_DIAGNOSTIC', '完成诊断处理'],
  ['INVITE_OPERATOR', '创建员工邀请'], ['REVOKE_OPERATOR_INVITATION', '撤销员工邀请'], ['DISABLE_OPERATOR', '停用员工账号'], ['RESTORE_OPERATOR', '恢复员工账号'],
]
const outcomeOptions = [['', '全部结果'], ['SUCCEEDED', '成功'], ['FAILED', '失败'], ['BLOCKED', '已拦截'], ['REJECTED', '已拒绝'], ['CONFIRMED', '已确认'], ['NOT_FOUND', '未找到'], ['MISMATCH', '不匹配'], ['PROVIDER_UNAVAILABLE', '服务商暂不可用']]

export function OperationsAuditPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const location = useLocation()
  const from = searchParams.get('from') ?? ''
  const to = searchParams.get('to') ?? ''
  const actor = searchParams.get('actor') ?? ''
  const action = searchParams.get('action') ?? ''
  const outcome = searchParams.get('outcome') ?? ''
  const businessOrderId = searchParams.get('businessOrderId') ?? ''
  const paymentOrderId = searchParams.get('paymentOrderId') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const [draft, setDraft] = useState({ from, to, actor, action, outcome, businessOrderId, paymentOrderId })
  useEffect(() => setDraft({ from, to, actor, action, outcome, businessOrderId, paymentOrderId }), [from, to, actor, action, outcome, businessOrderId, paymentOrderId])

  const audit = useQuery({
    queryKey: ['operations-audit', { from, to, actor, action, outcome, businessOrderId, paymentOrderId, page, size: pageSize }],
    queryFn: () => getOperatorAudit({ from: from || undefined, to: to || undefined, actor: actor || undefined, action: action || undefined, outcome: outcome || undefined, businessOrderId: businessOrderId || undefined, paymentOrderId: paymentOrderId || undefined, page, size: pageSize }),
    placeholderData: keepPreviousData,
  })
  const result = audit.data
  const totalPages = result ? Math.max(1, Math.ceil(result.total / result.size)) : 0
  const hasFilters = Boolean(from || to || actor || action || outcome || businessOrderId || paymentOrderId)
  const returnTo = `${location.pathname}${location.search}`
  const dateRangeValid = (!draft.from && !draft.to) || (Boolean(draft.from) && Boolean(draft.to) && draft.to >= draft.from && daysBetween(draft.from, draft.to) <= 92)

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const next = new URLSearchParams()
    Object.entries(draft).forEach(([key, value]) => { if (value) next.set(key, value.trim()) })
    setSearchParams(next)
  }
  const clear = () => {
    setDraft({ from: '', to: '', actor: '', action: '', outcome: '', businessOrderId: '', paymentOrderId: '' })
    setSearchParams({})
  }
  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    next.set('page', String(nextPage))
    setSearchParams(next)
  }

  return <section>
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">AUDIT TRAIL</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">操作记录</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">按时间、操作人、动作、结果及关联订单查询。审计记录只读，不可编辑或删除。</p>
    <form className="mt-7 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-2 xl:grid-cols-4" onSubmit={submit}>
      <label className="grid gap-1.5 text-xs text-muted-foreground">开始日期<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={draft.from} max={draft.to || undefined} onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">结束日期<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" type="date" value={draft.to} min={draft.from || undefined} onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">操作人<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={draft.actor} placeholder="员工账号" onChange={(event) => setDraft((current) => ({ ...current, actor: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">操作类型<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={draft.action} onChange={(event) => setDraft((current) => ({ ...current, action: event.target.value }))}>{actionOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">操作结果<select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={draft.outcome} onChange={(event) => setDraft((current) => ({ ...current, outcome: event.target.value }))}>{outcomeOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">星辙业务订单号<input className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={draft.businessOrderId} placeholder="业务订单号" onChange={(event) => setDraft((current) => ({ ...current, businessOrderId: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground sm:col-span-2">本项目支付单号<input className="h-10 rounded-md border border-input bg-background px-3 font-mono text-sm text-foreground" value={draft.paymentOrderId} placeholder="支付单编号" onChange={(event) => setDraft((current) => ({ ...current, paymentOrderId: event.target.value }))} /></label>
      <div className="flex flex-wrap items-end gap-2"><Button type="submit" disabled={!dateRangeValid}><Search className="size-4" aria-hidden="true" />查询</Button>{hasFilters && <Button type="button" variant="ghost" onClick={clear}><X className="size-4" aria-hidden="true" />清除</Button>}{!dateRangeValid && <p className="w-full text-xs text-amber-800">请选择完整日期范围，且最多查询连续 93 天。</p>}</div>
    </form>

    <div className="mt-5 flex flex-wrap items-end justify-between gap-3"><div><h2 className="text-base font-semibold">审计事件</h2><p className="mt-1 text-xs text-muted-foreground">{result ? `共 ${result.total.toLocaleString('zh-CN')} 条 · 最新记录优先` : '服务端分页读取'}</p></div><FileClock className="size-5 text-muted-foreground" aria-hidden="true" /></div>
    <div className="mt-3 overflow-hidden rounded-xl border border-border bg-card"><div className="overflow-x-auto"><table className="w-full min-w-[900px] text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4 font-medium">时间</th><th className="px-4 py-4 font-medium">操作人</th><th className="px-4 py-4 font-medium">动作与结果</th><th className="px-4 py-4 font-medium">关联订单 / 支付单</th><th className="px-5 py-4 font-medium">说明</th></tr></thead><tbody className="divide-y divide-border">{result?.items.map((item) => <tr key={item.id}><td className="whitespace-nowrap px-5 py-4 text-muted-foreground">{formatTime(item.createdAt)}</td><td className="px-4 py-4 font-medium">{item.actor}</td><td className="px-4 py-4">{auditActionLabel(item.action)}<span className="ml-2 inline-block rounded-full bg-muted px-2 py-1 text-xs">{auditOutcomeLabel(item.outcome)}</span></td><td className="px-4 py-4">{item.paymentOrderId ? <><span className="block text-xs text-muted-foreground">{item.businessOrderId ?? '—'}</span><Link className="font-mono text-xs text-primary hover:underline" to={`/operations/transactions/${encodeURIComponent(item.paymentOrderId)}`} state={{ returnTo }}>{item.paymentOrderId}</Link></> : <span className="text-xs text-muted-foreground">无交易关联</span>}</td><td className="max-w-sm whitespace-normal px-5 py-4 text-muted-foreground">{item.detail ?? '—'}</td></tr>)}</tbody></table></div>
      {audit.isPending && <p className="p-10 text-center text-sm text-muted-foreground" role="status">正在读取操作记录…</p>}
      {audit.isError && <div className="p-8 text-center text-sm text-destructive" role="alert"><p>操作记录读取失败；筛选条件已保留。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void audit.refetch()}>重试</Button></div>}
      {result?.items.length === 0 && <p className="p-10 text-center text-sm text-muted-foreground">当前条件下没有操作记录。</p>}
    </div>
    {result && result.total > 0 && <div className="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground"><span>第 {page + 1} / {totalPages} 页 · 共 {result.total.toLocaleString('zh-CN')} 条</span><div className="flex gap-2"><Button variant="outline" size="sm" disabled={page === 0 || audit.isFetching} onClick={() => changePage(page - 1)}>上一页</Button><Button variant="outline" size="sm" disabled={page + 1 >= totalPages || audit.isFetching} onClick={() => changePage(page + 1)}>下一页</Button></div></div>}
  </section>
}
