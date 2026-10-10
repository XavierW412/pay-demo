import { useEffect, useState, type FormEvent } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Search, X } from 'lucide-react'
import { Link, useLocation, useOutletContext, useParams, useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import {
  getDiagnosticDetail, getDiagnostics, handleDiagnostic,
  type DiagnosticModule, type DiagnosticSeverity, type DiagnosticStatus, type OperatorSession,
} from './api'
import { formatTime } from './format'

const pageSize = 20
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const severityLabels: Record<DiagnosticSeverity, string> = { ERROR: '错误', WARNING: '警告', INFO: '提示' }
const moduleLabels: Record<DiagnosticModule, string> = { CHECKOUT: '收银台', OPERATIONS: '运营后台', SIMULATOR: '本地模拟器' }
const statusLabels: Record<DiagnosticStatus, string> = { OPEN: '待处理', ACKNOWLEDGED: '已确认', RESOLVED: '已完成' }
const actionLabels = { ACKNOWLEDGE: '确认事件', NOTE: '添加备注', RESOLVE: '完成处理' }
const inputClass = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'
const validDate = (value: string): boolean => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}
const validChoice = (value: string, labels: Record<string, string>): boolean => !value || Object.hasOwn(labels, value)

const dateStart = (value: string): string | undefined => value ? new Date(`${value}T00:00:00`).toISOString() : undefined
const dateEnd = (value: string): string | undefined => {
  if (!value) return undefined
  const nextDay = new Date(`${value}T00:00:00`)
  nextDay.setDate(nextDay.getDate() + 1)
  return nextDay.toISOString()
}
const pageNumber = (value: string | null): number | null => {
  if (value === null) return 0
  if (!/^(0|[1-9]\d*)$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed <= 100000 ? parsed : null
}

function DiagnosticList() {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const applied = {
    diagnosticId: params.get('diagnosticId') ?? '',
    severity: params.get('severity') ?? '',
    module: params.get('module') ?? '',
    status: params.get('status') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
  }
  const [draft, setDraft] = useState(applied)
  useEffect(() => setDraft(applied), [applied.diagnosticId, applied.severity, applied.module, applied.status, applied.from, applied.to])
  const requestedPage = pageNumber(params.get('page'))
  const page = requestedPage ?? 0
  const invalidId = Boolean(applied.diagnosticId && !uuidPattern.test(applied.diagnosticId))
  const invalidRange = Boolean(applied.from && applied.to && applied.from > applied.to)
  const invalidFilters = requestedPage === null || invalidId || invalidRange || Boolean(
    (applied.from && !validDate(applied.from)) || (applied.to && !validDate(applied.to))
    || !validChoice(applied.severity, severityLabels) || !validChoice(applied.module, moduleLabels)
    || !validChoice(applied.status, statusLabels),
  )
  const list = useQuery({
    queryKey: ['operations-diagnostics', applied, page],
    queryFn: () => getDiagnostics({
      diagnosticId: applied.diagnosticId || undefined,
      severity: (applied.severity || undefined) as DiagnosticSeverity | undefined,
      module: (applied.module || undefined) as DiagnosticModule | undefined,
      status: (applied.status || undefined) as DiagnosticStatus | undefined,
      from: dateStart(applied.from), to: dateEnd(applied.to), page, size: pageSize,
    }),
    enabled: !invalidFilters,
    placeholderData: keepPreviousData,
  })
  const result = list.data
  const totalPages = result ? Math.max(1, Math.ceil(result.total / result.size)) : 0
  const draftValid = (!draft.diagnosticId || uuidPattern.test(draft.diagnosticId.trim()))
    && (!draft.from || validDate(draft.from)) && (!draft.to || validDate(draft.to))
    && (!draft.from || !draft.to || draft.from <= draft.to)
    && validChoice(draft.severity, severityLabels) && validChoice(draft.module, moduleLabels)
    && validChoice(draft.status, statusLabels)
  const hasFilters = Object.values(applied).some(Boolean) || params.has('page')
  const returnTo = `${location.pathname}${location.search}`

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!draftValid) return
    const next = new URLSearchParams()
    Object.entries(draft).forEach(([key, value]) => { if (value.trim()) next.set(key, value.trim()) })
    setParams(next)
  }
  const changePage = (nextPage: number) => {
    const next = new URLSearchParams(params)
    next.set('page', String(nextPage))
    setParams(next)
  }

  return <section>
    <h1 className="text-3xl font-semibold tracking-tight">技术诊断</h1>
    <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">查询支付接入过程中的技术事件与处理记录。这里的状态只表示诊断处理进度，不代表支付、退款或资金状态。</p>

    <form className="mt-7 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-2 xl:grid-cols-3" onSubmit={submit}>
      <label className="grid gap-1.5 text-xs text-muted-foreground">诊断编号<input className={inputClass} value={draft.diagnosticId} placeholder="输入完整诊断编号" onChange={event => setDraft(current => ({ ...current, diagnosticId: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">严重程度<select className={inputClass} value={draft.severity} onChange={event => setDraft(current => ({ ...current, severity: event.target.value }))}><option value="">全部程度</option>{Object.entries(severityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">模块<select className={inputClass} value={draft.module} onChange={event => setDraft(current => ({ ...current, module: event.target.value }))}><option value="">全部模块</option>{Object.entries(moduleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">处理状态<select className={inputClass} value={draft.status} onChange={event => setDraft(current => ({ ...current, status: event.target.value }))}><option value="">全部状态</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">最近上报日期起<input className={inputClass} type="date" value={draft.from} max={draft.to || undefined} onChange={event => setDraft(current => ({ ...current, from: event.target.value }))} /></label>
      <label className="grid gap-1.5 text-xs text-muted-foreground">最近上报日期止<input className={inputClass} type="date" value={draft.to} min={draft.from || undefined} onChange={event => setDraft(current => ({ ...current, to: event.target.value }))} /></label>
      <div className="flex flex-wrap items-center gap-2 sm:col-span-2 xl:col-span-3"><Button type="submit" disabled={!draftValid}><Search className="size-4" aria-hidden="true" />查询</Button>{hasFilters && <Button type="button" variant="ghost" onClick={() => { setDraft({ diagnosticId: '', severity: '', module: '', status: '', from: '', to: '' }); setParams({}) }}><X className="size-4" aria-hidden="true" />清除</Button>}{!draftValid && <span className="text-xs text-destructive" role="alert">请输入完整诊断编号，并确保结束日期不早于开始日期。</span>}</div>
    </form>

    <div className="mt-7 flex items-end justify-between gap-3"><div><h2 className="text-lg font-semibold">事件列表</h2><p className="mt-1 text-xs text-muted-foreground">{result ? `共 ${result.total.toLocaleString('zh-CN')} 个事件组 · 按最近上报时间倒序` : '从服务端分页读取'}</p></div></div>
    <div className="mt-3 divide-y divide-border overflow-hidden rounded-xl border border-border bg-card">
      {list.isPending && !invalidFilters && <p className="p-10 text-center text-sm text-muted-foreground" role="status">正在读取诊断事件…</p>}
      {invalidFilters && <p className="p-8 text-sm text-destructive" role="alert">当前链接的筛选参数无效，请修正后查询。</p>}
      {list.isError && <div className="p-8 text-center text-sm text-destructive" role="alert"><p>诊断事件读取失败或当前账号无权查看；不能据此判断没有事件。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void list.refetch()}>重试</Button></div>}
      {!list.isError && !invalidFilters && result?.items.map(item => <article key={item.diagnosticId} className="grid gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center"><div className="min-w-0"><Link className="break-all font-mono text-sm font-medium text-primary underline-offset-4 hover:underline" to={`/operations/diagnostics/${encodeURIComponent(item.diagnosticId)}`} state={{ returnTo }}>{item.diagnosticId}</Link><p className="mt-1 break-words text-sm">{item.summary}</p><p className="mt-2 text-xs text-muted-foreground">{moduleLabels[item.module] ?? item.module} · {item.category} · {item.occurrenceCount.toLocaleString('zh-CN')} 次 · 最近上报 {formatTime(item.latestReceivedAt)}</p></div><div className="flex flex-wrap items-center gap-2 text-xs lg:justify-end"><span className="rounded-full bg-muted px-2.5 py-1 font-medium">{severityLabels[item.severity] ?? item.severity}</span><span className="rounded-full bg-muted px-2.5 py-1 font-medium">{statusLabels[item.status] ?? item.status}</span><Link className="inline-flex items-center gap-1 text-primary hover:underline" to={`/operations/diagnostics/${encodeURIComponent(item.diagnosticId)}`} state={{ returnTo }}>查看详情<ArrowRight className="size-3.5" aria-hidden="true" /></Link></div></article>)}
      {!list.isError && !invalidFilters && result?.items.length === 0 && <p className="p-10 text-center text-sm text-muted-foreground">当前筛选条件下没有诊断事件。</p>}
    </div>
    {result && !invalidFilters && result.total > 0 && <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground"><span>第 {page + 1} / {totalPages} 页 · 共 {result.total.toLocaleString('zh-CN')} 个事件组</span><div className="flex gap-2"><Button variant="outline" size="sm" disabled={page === 0 || list.isFetching} onClick={() => changePage(page - 1)}>上一页</Button><Button variant="outline" size="sm" disabled={page + 1 >= totalPages || list.isFetching} onClick={() => changePage(page + 1)}>下一页</Button></div></div>}
  </section>
}

function DiagnosticDetailPage({ diagnosticId }: { diagnosticId: string }) {
  const session = useOutletContext<OperatorSession>()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [note, setNote] = useState('')
  const [pending, setPending] = useState(false)
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; message: string } | null>(null)
  const validId = uuidPattern.test(diagnosticId)
  const detail = useQuery({ queryKey: ['operations-diagnostic', diagnosticId], queryFn: () => getDiagnosticDetail(diagnosticId), enabled: validId })
  const incident = detail.data?.incident
  const canOperate = session.role === 'STAFF_ADMIN' || session.role === 'STAFF_OPERATOR' || session.role === 'TEMP_TESTER'
  const canViewFreeText = canOperate
  const backPath = (location.state as { returnTo?: string } | null)?.returnTo
  const returnTo = backPath?.startsWith('/operations/diagnostics') && !backPath.startsWith('/operations/diagnostics/') ? backPath : '/operations/diagnostics'

  const act = async (action: 'acknowledge' | 'notes' | 'resolve') => {
    if (!canOperate || pending || !incident || (action === 'notes' && !note.trim())) return
    setPending(true)
    setFeedback(null)
    try {
      const next = await handleDiagnostic(diagnosticId, action, note.trim())
      queryClient.setQueryData(['operations-diagnostic', diagnosticId], next)
      void queryClient.invalidateQueries({ queryKey: ['operations-diagnostics'] })
      setNote('')
      setFeedback({ kind: 'success', message: action === 'acknowledge' ? '事件已确认，处理历史已更新。' : action === 'resolve' ? '事件已标记完成，处理历史已更新。' : '备注已保存，处理历史已更新。' })
    } catch {
      void detail.refetch()
      setFeedback({ kind: 'error', message: '操作结果暂无法确认。请重新读取事件状态和处理历史后再操作。' })
    } finally {
      setPending(false)
    }
  }

  return <section>
    <Link className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground" to={returnTo}><ArrowLeft className="size-4" aria-hidden="true" />返回诊断列表</Link>
    <h1 className="mt-6 text-3xl font-semibold tracking-tight">诊断详情</h1>
    <p className="mt-2 break-all font-mono text-sm text-muted-foreground">{diagnosticId}</p>
    {!validId && <p className="mt-7 text-sm text-destructive" role="alert">诊断编号格式无效。请返回列表重新查询。</p>}
    {validId && detail.isPending && <p className="mt-7 text-sm text-muted-foreground" role="status">正在读取诊断详情…</p>}
    {detail.isError && <div className="mt-7 text-sm text-destructive" role="alert"><p>诊断详情读取失败、事件不存在或当前账号无权查看。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void detail.refetch()}>重试</Button></div>}
    {incident && <>
      <div className="mt-6 flex flex-wrap items-center gap-2 text-xs"><span className="rounded-full bg-muted px-2.5 py-1 font-medium">{severityLabels[incident.severity] ?? incident.severity}</span><span className="rounded-full bg-muted px-2.5 py-1 font-medium">{statusLabels[incident.status] ?? incident.status}</span><span className="text-muted-foreground">{moduleLabels[incident.module] ?? incident.module} · {incident.category}</span></div>
      <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-5">
          <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">事件摘要</h2><p className="mt-4 whitespace-pre-wrap break-words text-sm leading-6">{incident.summary}</p><dl className="mt-6 grid gap-4 text-sm sm:grid-cols-2">
            <div><dt className="text-xs text-muted-foreground">首次发生时间</dt><dd className="mt-1">{formatTime(incident.occurredAt)}</dd></div><div><dt className="text-xs text-muted-foreground">首次接收时间</dt><dd className="mt-1">{formatTime(incident.receivedAt)}</dd></div><div><dt className="text-xs text-muted-foreground">发生次数</dt><dd className="mt-1">{incident.occurrenceCount.toLocaleString('zh-CN')}</dd></div><div><dt className="text-xs text-muted-foreground">最近上报时间</dt><dd className="mt-1">{formatTime(incident.latestReceivedAt)}</dd></div>
            <div><dt className="text-xs text-muted-foreground">环境 / 版本</dt><dd className="mt-1 break-all">{incident.environment} · {incident.appVersion}</dd></div><div><dt className="text-xs text-muted-foreground">浏览器类别</dt><dd className="mt-1">{incident.browserFamily}</dd></div>
            <div><dt className="text-xs text-muted-foreground">路由模板</dt><dd className="mt-1 break-all font-mono text-xs">{incident.routeTemplate}</dd></div><div><dt className="text-xs text-muted-foreground">请求编号</dt><dd className="mt-1 break-all font-mono text-xs">{incident.requestId ?? '未记录'}</dd></div>
            <div><dt className="text-xs text-muted-foreground">关联诊断编号</dt><dd className="mt-1 break-all font-mono text-xs">{incident.relatedDiagnosticId ?? '无'}</dd></div>
            <div><dt className="text-xs text-muted-foreground">关联支付单</dt><dd className="mt-1 break-all">{incident.paymentOrderId ? <Link className="font-mono text-xs text-primary underline-offset-4 hover:underline" to={`/operations/transactions/${encodeURIComponent(incident.paymentOrderId)}`} state={{ returnTo: location.pathname }}>查看交易详情 · {incident.paymentOrderId}</Link> : '无关联支付单'}</dd></div>
          </dl></section>
          <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">追踪</h2><p className="mt-3 break-all font-mono text-xs">{incident.traceId ?? '未记录追踪编号'}</p><p className="mt-2 text-xs text-muted-foreground">追踪存储未配置</p></section>
          {canViewFreeText && incident.userDescription && <details className="rounded-xl border border-border bg-card p-5 sm:p-6"><summary className="cursor-pointer text-sm font-semibold">用户说明（默认隐藏）</summary><p className="mt-4 whitespace-pre-wrap break-words text-sm leading-6">{incident.userDescription}</p></details>}
          {!canViewFreeText && incident.userDescription && <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">用户说明已隐藏。</p>}
          <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">处理历史</h2>{detail.data?.history.length ? <ol className="mt-4 divide-y divide-border">{detail.data.history.map(entry => <li key={entry.id} className="py-4 text-sm"><div className="flex flex-wrap justify-between gap-2"><span className="font-medium">{actionLabels[entry.action] ?? entry.action} · {entry.actor}</span><time className="text-xs text-muted-foreground">{formatTime(entry.createdAt)}</time></div>{canViewFreeText && entry.note && <details className="mt-2"><summary className="cursor-pointer text-xs text-muted-foreground">查看处理备注</summary><p className="mt-2 whitespace-pre-wrap break-words text-sm">{entry.note}</p></details>}{!canViewFreeText && entry.note && <p className="mt-2 text-xs text-muted-foreground">处理备注已隐藏。</p>}</li>)}</ol> : <p className="mt-4 text-sm text-muted-foreground">暂无处理历史。</p>}</section>
        </div>
        <aside className="self-start rounded-xl border border-border bg-card p-5"><h2 className="text-base font-semibold">处理事件</h2><p className="mt-2 text-xs leading-5 text-muted-foreground">此处操作仅改变技术诊断状态，不改变交易或退款状态。</p>{canOperate ? <><div className="mt-5 flex flex-wrap gap-2">{incident.status === 'OPEN' && <Button disabled={pending} onClick={() => void act('acknowledge')}>确认事件</Button>}{incident.status !== 'RESOLVED' && <Button variant="outline" disabled={pending} onClick={() => void act('resolve')}>完成处理</Button>}</div><label className="mt-5 grid gap-2 text-xs text-muted-foreground">处理备注<textarea className="min-h-28 w-full resize-y rounded-md border border-input bg-background p-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" maxLength={1000} value={note} placeholder="记录技术处理经过，勿填写密钥或付款资料" onChange={event => setNote(event.target.value)} /></label><Button className="mt-3" variant="outline" disabled={pending || !note.trim()} onClick={() => void act('notes')}>保存备注</Button></> : <p className="mt-5 text-sm text-muted-foreground">当前账号为只读角色，可查看诊断记录，无处理权限。</p>}{feedback && <p className={`mt-4 text-sm ${feedback.kind === 'error' ? 'text-destructive' : 'text-foreground'}`} role={feedback.kind === 'error' ? 'alert' : 'status'}>{feedback.message}</p>}</aside>
      </div>
    </>}
  </section>
}

export function OperationsDiagnosticsPage() {
  const { diagnosticId } = useParams()
  return diagnosticId ? <DiagnosticDetailPage key={diagnosticId} diagnosticId={diagnosticId} /> : <DiagnosticList />
}
