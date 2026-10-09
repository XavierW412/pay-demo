import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getOperatorAudit } from './api'
import { auditActionLabel, auditOutcomeLabel, formatTime } from './format'

export function OperationsAuditPage() {
  const audit = useQuery({ queryKey: ['operations-audit'], queryFn: getOperatorAudit })
  return <section>
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">AUDIT TRAIL</p>
    <h1 className="mt-2 text-3xl font-semibold tracking-tight">操作记录</h1>
    <p className="mt-2 text-sm text-muted-foreground">记录员工对支付单的核实、通知重试和备注操作。最多显示最近 100 条。</p>
    <div className="mt-8 overflow-x-auto rounded-xl border border-border bg-card"><table className="w-full min-w-[760px] text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4 font-medium">时间</th><th className="px-4 py-4 font-medium">操作人</th><th className="px-4 py-4 font-medium">动作与结果</th><th className="px-4 py-4 font-medium">关联支付单</th><th className="px-5 py-4 font-medium">说明</th></tr></thead><tbody className="divide-y divide-border">{audit.data?.map((item) => <tr key={item.id}><td className="whitespace-nowrap px-5 py-4 text-muted-foreground">{formatTime(item.createdAt)}</td><td className="px-4 py-4 font-medium">{item.actor}</td><td className="px-4 py-4">{auditActionLabel(item.action)}<span className="ml-2 rounded-full bg-muted px-2 py-1 text-xs">{auditOutcomeLabel(item.outcome)}</span></td><td className="px-4 py-4 font-mono text-xs">{item.paymentOrderId ? <Link className="text-primary hover:underline" to={`/operations/transactions/${item.paymentOrderId}`}>{item.paymentOrderId}</Link> : '—'}</td><td className="px-5 py-4 text-muted-foreground">{item.detail ?? '—'}</td></tr>)}</tbody></table>{audit.isPending && <p className="p-8 text-center text-sm text-muted-foreground" role="status">正在读取操作记录…</p>}{audit.isError && <p className="p-8 text-center text-sm text-destructive" role="alert">操作记录读取失败。</p>}{audit.data?.length === 0 && <p className="p-8 text-center text-sm text-muted-foreground">暂无操作记录。</p>}</div>
  </section>
}
