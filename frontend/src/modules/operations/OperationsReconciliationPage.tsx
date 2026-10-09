import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ScanSearch } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getReconciliationDifferences } from './api'
import { formatTime, reconciliationIssueLabel, reconciliationOutcomeLabel } from './format'

export function OperationsReconciliationPage() {
  const differences = useQuery({ queryKey: ['operations-reconciliation-differences'], queryFn: getReconciliationDifferences })
  return <section>
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">PAYMENT CHECKS</p>
    <h1 className="mt-2 text-3xl font-semibold tracking-tight">支付核查</h1>
    <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">这里显示逐笔比对本项目与空中云汇后发现的差异、查询失败及通知未送达记录。核查会保存快照，不会直接改写支付状态。</p>
    <div className="mt-6 flex gap-3 rounded-xl border border-border bg-card p-4 text-sm"><ScanSearch className="mt-0.5 size-5 shrink-0 text-primary" aria-hidden="true" /><p><strong>结算对账尚未接入。</strong><span className="text-muted-foreground"> 本页不能证明资金已经结算，也尚未比对星辙真实业务账本。</span></p></div>
    <div className="mt-6 overflow-hidden rounded-xl border border-border bg-card"><div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground"><tr><th className="px-5 py-4 font-medium">星辙订单</th><th className="px-4 py-4 font-medium">核查结果</th><th className="px-4 py-4 font-medium">发现的问题</th><th className="px-4 py-4 font-medium">核查时间</th><th className="px-5 py-4 text-right font-medium">详情</th></tr></thead><tbody className="divide-y divide-border">{differences.data?.map(({ snapshot }) => <tr key={snapshot.id}><td className="px-5 py-4 font-medium">{snapshot.localBusinessOrderId}</td><td className="px-4 py-4">{reconciliationOutcomeLabel(snapshot.outcome)}</td><td className="px-4 py-4 text-muted-foreground">{snapshot.issueCodes.length ? snapshot.issueCodes.map(reconciliationIssueLabel).join('、') : '待人工查看'}</td><td className="whitespace-nowrap px-4 py-4 text-muted-foreground">{formatTime(snapshot.checkedAt)}</td><td className="px-5 py-4 text-right"><Link className="inline-flex items-center gap-1 text-primary hover:underline" to={`/operations/transactions/${snapshot.paymentOrderId}`}>查看<ArrowRight className="size-3.5" aria-hidden="true" /></Link></td></tr>)}</tbody></table></div>{differences.isPending && <p className="p-8 text-center text-sm text-muted-foreground" role="status">正在读取核查记录…</p>}{differences.isError && <p className="p-8 text-center text-sm text-destructive" role="alert">核查记录读取失败。</p>}{differences.data?.length === 0 && <p className="p-8 text-center text-sm text-muted-foreground">暂无已记录的差异；新交易可能尚未完成首次核查。</p>}</div>
  </section>
}
