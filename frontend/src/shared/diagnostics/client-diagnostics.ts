import { isAxiosError } from 'axios'

export type DiagnosticCategory = 'UNHANDLED_ERROR' | 'PAGE_CRASH' | 'NETWORK_ERROR' | 'API_ERROR' | 'USER_REPORT'
export type DiagnosticContext = {
  category?: DiagnosticCategory
  requestId?: string
  traceId?: string
  relatedDiagnosticId?: string
  userDescription?: string
}
export type DiagnosticReport = DiagnosticContext & {
  occurredAt: string
  appVersion: string
  category: DiagnosticCategory
  severity: 'ERROR' | 'WARNING' | 'INFO'
  module: 'CHECKOUT' | 'OPERATIONS' | 'SIMULATOR'
  routeTemplate: string
  browserFamily: 'CHROMIUM' | 'FIREFOX' | 'SAFARI' | 'OTHER' | 'UNKNOWN'
  summary: string
}
export type DiagnosticNotice = {
  eventId: string
  supportId: string
  state: 'sending' | 'reported' | 'local' | 'server'
  report: DiagnosticReport
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const trace = /^[0-9a-f]{32}$/
export const isDiagnosticEndpoint = (url: string | undefined): boolean =>
  /^(?:https?:\/\/[^/]+)?\/api\/v1\/diagnostics\/client-events(?:[?#]|$)/.test(url ?? '')
const localSupportId = (): string => {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
const listeners = new Set<(notice: DiagnosticNotice) => void>()
const captured = new WeakMap<object, Promise<DiagnosticNotice>>()
const categories: DiagnosticCategory[] = ['UNHANDLED_ERROR', 'PAGE_CRASH', 'NETWORK_ERROR', 'API_ERROR', 'USER_REPORT']
const summaries: Record<DiagnosticCategory, string> = {
  UNHANDLED_ERROR: '页面发生未处理错误', PAGE_CRASH: '页面无法正常显示',
  NETWORK_ERROR: '服务连接未完成', API_ERROR: '服务请求未完成', USER_REPORT: '用户补充故障说明',
}

export const routeTemplateFor = (pathname: string): string => {
  const path = pathname.split(/[?#]/, 1)[0]
  const fixed = ['/', '/operations', '/operations/login', '/operations/finance', '/operations/transactions',
    '/operations/audit', '/operations/reconciliation', '/operations/team', '/operations/diagnostics']
  if (fixed.includes(path)) return path
  const patterns: [RegExp, string][] = [
    [/^\/checkout\/[^/]+\/result$/, '/checkout/:opaqueToken/result'],
    [/^\/checkout\/return\/[^/]+$/, '/checkout/return/:opaqueToken'],
    [/^\/checkout\/[^/]+$/, '/checkout/:opaqueToken'],
    [/^\/operations\/register\/[^/]+$/, '/operations/register/:token'],
    [/^\/operations\/transactions\/[^/]+$/, '/operations/transactions/:paymentOrderId'],
    [/^\/operations\/diagnostics\/[^/]+$/, '/operations/diagnostics/:diagnosticId'],
  ]
  return patterns.find(([pattern]) => pattern.test(path))?.[1] ?? '/unknown'
}
const browserFamily = (): DiagnosticReport['browserFamily'] => {
  const agent = navigator.userAgent
  if (!agent) return 'UNKNOWN'
  if (/Firefox|FxiOS/.test(agent)) return 'FIREFOX'
  if (/Chrome|Chromium|CriOS|Edg|OPR/.test(agent)) return 'CHROMIUM'
  return /Safari/.test(agent) ? 'SAFARI' : 'OTHER'
}
const createReport = (context: DiagnosticContext): DiagnosticReport => {
  const routeTemplate = routeTemplateFor(window.location.pathname)
  const category = context.category ?? 'UNHANDLED_ERROR'
  return {
    occurredAt: new Date().toISOString(), appVersion: import.meta.env.VITE_APP_VERSION,
    category, severity: category === 'USER_REPORT' ? 'INFO' : 'ERROR',
    module: routeTemplate.startsWith('/checkout/') ? 'CHECKOUT' : 'OPERATIONS',
    routeTemplate, browserFamily: browserFamily(), summary: summaries[category],
    requestId: context.requestId, traceId: context.traceId,
    relatedDiagnosticId: context.relatedDiagnosticId,
  }
}
const publish = (notice: DiagnosticNotice) => {
  listeners.forEach((listener) => { try { listener(notice) } catch { /* 诊断界面故障不影响原请求。 */ } })
  return notice
}
export const subscribeDiagnostics = (listener: (notice: DiagnosticNotice) => void) => {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export const submitDiagnosticReport = async (report: DiagnosticReport): Promise<{ diagnosticId: string; receivedAt: string }> => {
  // 逐项重建白名单，调用方的额外属性不会进入请求。
  const body = {
    occurredAt: report.occurredAt, appVersion: report.appVersion,
    category: categories.includes(report.category) ? report.category : 'UNHANDLED_ERROR',
    severity: ['ERROR', 'WARNING', 'INFO'].includes(report.severity) ? report.severity : 'ERROR',
    module: ['CHECKOUT', 'OPERATIONS', 'SIMULATOR'].includes(report.module) ? report.module : 'OPERATIONS',
    routeTemplate: routeTemplateFor(report.routeTemplate),
    browserFamily: ['CHROMIUM', 'FIREFOX', 'SAFARI', 'OTHER', 'UNKNOWN'].includes(report.browserFamily) ? report.browserFamily : 'UNKNOWN',
    ...(uuid.test(report.requestId ?? '') ? { requestId: report.requestId } : {}),
    ...(trace.test(report.traceId ?? '') ? { traceId: report.traceId } : {}),
    ...(uuid.test(report.relatedDiagnosticId ?? '') ? { relatedDiagnosticId: report.relatedDiagnosticId } : {}),
    summary: summaries[report.category] ?? summaries.UNHANDLED_ERROR,
    ...(report.userDescription ? { userDescription: report.userDescription.slice(0, 1000) } : {}),
  }
  const json = JSON.stringify(body)
  if (new TextEncoder().encode(json).length > 8192) throw new Error('诊断说明超出长度限制')
  const response = await fetch('/api/v1/diagnostics/client-events', {
    method: 'POST', credentials: 'omit', referrerPolicy: 'no-referrer', headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: json, signal: AbortSignal.timeout(10_000),
  })
  if (response.status !== 202) throw new Error('诊断说明尚未上报')
  const receipt: unknown = await response.json()
  if (!receipt || typeof receipt !== 'object' || !('diagnosticId' in receipt) || !('receivedAt' in receipt)
    || typeof receipt.diagnosticId !== 'string' || !uuid.test(receipt.diagnosticId) || typeof receipt.receivedAt !== 'string') {
    throw new Error('未取得诊断回执')
  }
  return { diagnosticId: receipt.diagnosticId, receivedAt: receipt.receivedAt }
}

export const showServerDiagnostic = (context: DiagnosticContext): void => {
  if (!uuid.test(context.relatedDiagnosticId ?? '')) return
  publish({ eventId: localSupportId(), supportId: context.relatedDiagnosticId!, state: 'server', report: createReport({ ...context, category: 'API_ERROR' }) })
}
export const captureClientError = (error: unknown, context: DiagnosticContext = {}): Promise<DiagnosticNotice | undefined> => {
  if (isAxiosError(error)) {
    if (error.code === 'ERR_CANCELED' || isDiagnosticEndpoint(error.config?.url)) return Promise.resolve(undefined)
    if (error.response && error.response.status >= 400 && error.response.status < 500) return Promise.resolve(undefined)
  }
  if (error instanceof Error && ['AbortError', 'ValidationError'].includes(error.name)) return Promise.resolve(undefined)
  if (typeof error === 'object' && error !== null && captured.has(error)) return captured.get(error)!
  const report = createReport(context)
  const eventId = localSupportId()
  const initial: DiagnosticNotice = { eventId, supportId: eventId, state: 'sending', report }
  publish(initial)
  const pending = submitDiagnosticReport(report)
    .then((receipt) => publish({ ...initial, supportId: receipt.diagnosticId, state: 'reported' }))
    .catch(() => publish({ ...initial, state: 'local' }))
  if (typeof error === 'object' && error !== null) captured.set(error, pending)
  return pending
}
export const installGlobalErrorCapture = (): (() => void) => {
  const onError = (event: ErrorEvent) => { void captureClientError(event.error ?? event, { category: 'UNHANDLED_ERROR' }) }
  const onRejection = (event: PromiseRejectionEvent) => { void captureClientError(event.reason, { category: 'UNHANDLED_ERROR' }) }
  window.addEventListener('error', onError)
  window.addEventListener('unhandledrejection', onRejection)
  return () => { window.removeEventListener('error', onError); window.removeEventListener('unhandledrejection', onRejection) }
}
