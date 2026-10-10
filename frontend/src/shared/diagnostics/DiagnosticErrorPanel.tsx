import { useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { submitDiagnosticReport } from './client-diagnostics'
import type { DiagnosticNotice } from './client-diagnostics'

type Props = { notice: DiagnosticNotice; onRetry?: () => void; onDismiss?: () => void }

export const DiagnosticErrorPanel = ({ notice, onRetry, onDismiss }: Props) => {
  const descriptionId = useId()
  const [description, setDescription] = useState('')
  const [submittedId, setSubmittedId] = useState('')
  const [sending, setSending] = useState(false)
  const [feedback, setFeedback] = useState('')
  const supportId = submittedId || notice.supportId
  const submit = async () => {
    if (sending || !description.trim()) return
    setSending(true)
    setFeedback('')
    try {
      const receipt = await submitDiagnosticReport({ ...notice.report, occurredAt: new Date().toISOString(),
        category: 'USER_REPORT', severity: 'INFO', relatedDiagnosticId: notice.state === 'local' ? undefined : notice.supportId,
        userDescription: description.trim().slice(0, 1000) })
      setSubmittedId(receipt.diagnosticId)
      setDescription('')
      setFeedback('说明已上报。敏感内容会由服务端过滤。')
    } catch {
      setFeedback('说明尚未上报，请保留当前编号。不会自动重试。')
    } finally { setSending(false) }
  }
  const copy = async () => {
    try { await navigator.clipboard.writeText(supportId); setFeedback('编号已复制。') }
    catch { setFeedback('未能复制，请选择下方编号手动复制。') }
  }
  return (
    <section className="rounded-xl border border-border bg-card p-5 text-foreground shadow-sm" aria-label="故障说明">
      <h2 className="text-base font-semibold">暂时未能完成此操作</h2>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">付款状态以服务端核实结果为准。请保留编号，便于工作人员核查。</p>
      <p className="mt-3 text-sm" role="status">
        {submittedId || notice.state === 'reported' ? '已上报' : notice.state === 'server' ? '服务端诊断编号' : notice.state === 'sending' ? '正在上报…' : '本地编号 · 尚未上报'}
      </p>
      <p className="mt-1 break-all select-text font-mono text-xs leading-5">{supportId}</p>
      <div className="mt-4 flex flex-wrap gap-2">
        <Button variant="outline" className="min-h-10" onClick={() => void copy()}>复制编号</Button>
        {onRetry && <Button className="min-h-10" onClick={onRetry}>重新加载页面</Button>}
        {onDismiss && <Button variant="ghost" className="min-h-10" onClick={onDismiss}>返回当前页面</Button>}
      </div>
      <form className="mt-5 border-t border-border pt-4" onSubmit={(event) => { event.preventDefault(); void submit() }}>
        <label htmlFor={descriptionId} className="text-sm font-medium">补充说明（可选）</label>
        <p id={`${descriptionId}-help`} className="mt-1 text-xs leading-5 text-muted-foreground">只描述操作和问题。不要填写银行卡、密码、密钥、订单资料或个人信息。</p>
        <textarea id={descriptionId} aria-describedby={`${descriptionId}-help ${descriptionId}-count`} value={description}
          onChange={(event) => setDescription(event.target.value.slice(0, 1000))} maxLength={1000} disabled={sending}
          className="mt-2 min-h-24 w-full resize-y rounded-lg border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/50 disabled:opacity-50" />
        <p id={`${descriptionId}-count`} className="mt-1 text-right text-xs text-muted-foreground">{description.length}/1000</p>
        <Button variant="outline" className="mt-2 min-h-10" type="submit" disabled={sending || !description.trim() || notice.state === 'sending'}>{sending ? '正在提交…' : '提交说明'}</Button>
      </form>
      {feedback && <p className="mt-3 text-sm leading-6 text-muted-foreground" role="status">{feedback}</p>}
    </section>
  )
}
