import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, RefreshCw, Send } from 'lucide-react'
import { Link, useOutletContext, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { OperatorSession } from './api'
import { addOperatorNote, confirmRefundExecution, getReconciliationHistory, getRefundsForPayment, getTransactionDetail, refreshProviderStatus, retryNotification, runReconciliationCheck } from './api'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import { auditActionLabel, auditOutcomeLabel, formatMoney, formatTime, notificationLabel, paymentLabel, reconciliationIssueLabel, reconciliationOutcomeLabel, refundLabel } from './format'

type Action = 'refresh' | 'retry' | 'reconcile'

export function OperationsTransactionDetailPage() {
  const { paymentOrderId = '' } = useParams()
  const session = useOutletContext<OperatorSession>()
  const queryClient = useQueryClient()
  const [action, setAction] = useState<Action | null>(null)
  const [refundConfirmationId, setRefundConfirmationId] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [note, setNote] = useState('')
  const [message, setMessage] = useState('')
  const detail = useQuery({
    queryKey: ['operations-transaction', paymentOrderId],
    queryFn: () => getTransactionDetail(paymentOrderId),
    enabled: Boolean(paymentOrderId),
  })
  const checks = useQuery({
    queryKey: ['operations-reconciliation-history', paymentOrderId],
    queryFn: () => getReconciliationHistory(paymentOrderId),
    enabled: Boolean(paymentOrderId),
  })
  const refunds = useQuery({
    queryKey: ['operations-refunds', paymentOrderId],
    queryFn: () => getRefundsForPayment(paymentOrderId),
    enabled: Boolean(paymentOrderId),
  })
  const transaction = detail.data?.transaction
  const canOperate = session.role !== 'OWNER_READONLY'
  const canConfirmRefund = session.role === 'STAFF_ADMIN'

  const reload = async () => {
    await queryClient.invalidateQueries({ queryKey: ['operations-transaction', paymentOrderId] })
    await queryClient.invalidateQueries({ queryKey: ['operations-transactions'] })
    await queryClient.invalidateQueries({ queryKey: ['operations-audit'] })
    await queryClient.invalidateQueries({ queryKey: ['operations-reconciliation-history', paymentOrderId] })
    await queryClient.invalidateQueries({ queryKey: ['operations-reconciliation-differences'] })
    await queryClient.invalidateQueries({ queryKey: ['operations-refunds', paymentOrderId] })
  }

  const confirmRefund = async () => {
    if (!refundConfirmationId || pending) return
    setPending(true)
    setMessage('')
    try {
      const result = await confirmRefundExecution(refundConfirmationId)
      await reload()
      setMessage(result.status === 'BLOCKED'
        ? '复核发现差异，退款已拦截；请查看异常说明并核实。'
        : '已提交退款请求，最终状态以 Airwallex 通知或服务端核实为准。')
      setRefundConfirmationId(null)
    } catch {
      await reload()
      setMessage('退款未能确认发起。请刷新记录，核对当前状态后再处理。')
      setRefundConfirmationId(null)
    } finally {
      setPending(false)
    }
  }

  const confirmAction = async () => {
    if (!action || pending) return
    setPending(true)
    setMessage('')
    try {
      if (action === 'refresh') await refreshProviderStatus(paymentOrderId)
      else if (action === 'retry') await retryNotification(paymentOrderId)
      else await runReconciliationCheck(paymentOrderId)
      await reload()
      setMessage(action === 'refresh' ? '已向空中云汇核实，页面显示最新记录。' : action === 'retry' ? '已提交通知重试，请稍后查看送达状态。' : '已保存本次支付核查结果。')
      setAction(null)
    } catch {
      setMessage('操作未完成，请查看当前状态或联系管理员。')
    } finally {
      setPending(false)
    }
  }

  const submitNote = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!note.trim() || pending) return
    setPending(true)
    setMessage('')
    try {
      await addOperatorNote(paymentOrderId, note.trim())
      setNote('')
      await reload()
      setMessage('备注已保存。')
    } catch {
      setMessage('备注保存失败，请重试。')
    } finally {
      setPending(false)
    }
  }

  return (
    <section>
      <Link className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground" to="/operations/transactions"><ArrowLeft className="size-4" aria-hidden="true" />返回交易列表</Link>
      <div className="mt-6 flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-semibold tracking-[0.16em] text-primary">PAYMENT DETAIL</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">交易详情</h1></div>{transaction && <span className="rounded-full border border-border bg-card px-3 py-1.5 text-sm font-medium">{paymentLabel(transaction.paymentStatus)}</span>}</div>
      {detail.isPending && <p className="mt-8 text-sm text-muted-foreground" role="status">正在读取交易详情…</p>}
      {detail.isError && <p className="mt-8 text-sm text-destructive" role="alert">交易详情读取失败，或该支付单不存在。</p>}
      {transaction && <>
        <div className="mt-7 grid gap-4 md:grid-cols-3">
          <div className="rounded-xl border border-border bg-card p-5"><p className="text-xs text-muted-foreground">收款金额</p><p className="mt-3 text-2xl font-semibold tabular-nums">{formatMoney(transaction.amount, transaction.currency)}</p></div>
          <div className="rounded-xl border border-border bg-card p-5"><p className="text-xs text-muted-foreground">{transaction.provider === 'LOCAL_SIMULATOR' ? '本地模拟支付状态' : '空中云汇支付状态'}</p><p className="mt-3 text-xl font-semibold">{paymentLabel(transaction.paymentStatus)}</p></div>
          <div className="rounded-xl border border-border bg-card p-5"><p className="text-xs text-muted-foreground">星辙通知状态</p><p className="mt-3 text-xl font-semibold">{notificationLabel(transaction.notificationStatus)}</p></div>
        </div>
        <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-5">
            <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">订单信息</h2><dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2">
              <div><dt className="text-xs text-muted-foreground">星辙业务订单号</dt><dd className="mt-1 break-all font-medium">{transaction.businessOrderId}</dd></div>
              <div><dt className="text-xs text-muted-foreground">本项目支付单号</dt><dd className="mt-1 break-all font-mono text-xs">{transaction.id}</dd></div>
              <div><dt className="text-xs text-muted-foreground">支付来源</dt><dd className="mt-1">{transaction.provider === 'LOCAL_SIMULATOR' ? '本地模拟结果（不会调用 Airwallex）' : 'Airwallex 支付单'}</dd></div>
              {transaction.provider === 'AIRWALLEX' && <div><dt className="text-xs text-muted-foreground">Airwallex Payment Intent</dt><dd className="mt-1 break-all font-mono text-xs">{transaction.providerPaymentIntentId ?? '创建中'}</dd></div>}
              <div><dt className="text-xs text-muted-foreground">创建时间</dt><dd className="mt-1">{formatTime(transaction.createdAt)}</dd></div>
              <div><dt className="text-xs text-muted-foreground">最近更新时间</dt><dd className="mt-1">{formatTime(transaction.updatedAt)}</dd></div>
            </dl></section>
            <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">处理备注</h2><p className="mt-1 text-sm text-muted-foreground">备注只用于留痕，不会更改支付结果。</p>
              {canOperate && <form className="mt-4" onSubmit={(event) => void submitNote(event)}><label className="sr-only" htmlFor="operator-note">处理备注</label><textarea id="operator-note" className="min-h-24 w-full rounded-lg border border-input bg-background p-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" maxLength={1000} placeholder="记录已核实的问题、处理依据或沟通结果" value={note} onChange={(event) => setNote(event.target.value)} /><div className="mt-2 flex justify-end"><Button type="submit" size="sm" disabled={!note.trim() || pending}>保存备注</Button></div></form>}
              <div className="mt-5 space-y-3">{detail.data?.notes.length ? detail.data.notes.map((item) => <div className="rounded-lg bg-muted/40 p-3" key={item.id}><p className="whitespace-pre-wrap text-sm">{item.body}</p><p className="mt-2 text-xs text-muted-foreground">{item.actor} · {formatTime(item.createdAt)}</p></div>) : <p className="text-sm text-muted-foreground">暂无处理备注。</p>}</div>
            </section>
            <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">此笔交易的操作记录</h2><div className="mt-4 space-y-3">{detail.data?.audit.length ? detail.data.audit.map((item) => <div className="border-l-2 border-primary/30 pl-3 text-sm" key={item.id}><p className="font-medium">{item.actor} · {auditActionLabel(item.action)} · {auditOutcomeLabel(item.outcome)}</p><p className="mt-1 text-xs text-muted-foreground">{formatTime(item.createdAt)}{item.detail ? ` · ${item.detail}` : ''}</p></div>) : <p className="text-sm text-muted-foreground">暂无人工操作记录。</p>}</div></section>
            <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">支付核查历史</h2><p className="mt-1 text-sm text-muted-foreground">逐笔比较本项目与空中云汇，并记录通知是否送达；不代表结算对账。</p><div className="mt-4 space-y-3">{checks.isPending && <p className="text-sm text-muted-foreground">正在读取核查历史…</p>}{checks.isError && <p className="text-sm text-destructive">核查历史读取失败。</p>}{checks.data?.length === 0 && <p className="text-sm text-muted-foreground">尚未生成核查记录。</p>}{checks.data?.map(({ snapshot }) => <div className="rounded-lg bg-muted/40 p-3 text-sm" key={snapshot.id}><p className="font-medium">{reconciliationOutcomeLabel(snapshot.outcome)} · {formatTime(snapshot.checkedAt)}</p><p className="mt-1 text-xs leading-5 text-muted-foreground">{snapshot.issueCodes.length ? snapshot.issueCodes.map(reconciliationIssueLabel).join('、') : '订单、金额、币种和状态一致'}</p></div>)}</div></section>
            <section className="rounded-xl border border-border bg-card p-5 sm:p-6"><h2 className="text-base font-semibold">退款复核与执行</h2><p className="mt-1 text-sm text-muted-foreground">退款由星辙业务方批准。支付侧管理员复核无误后发起，不能手工标成退款成功。</p><div className="mt-4 space-y-3">{refunds.isPending && <p className="text-sm text-muted-foreground">正在读取退款记录…</p>}{refunds.isError && <p className="text-sm text-destructive">退款记录读取失败。</p>}{refunds.data?.length === 0 && <p className="text-sm text-muted-foreground">本项目尚未收到这笔支付的退款指令。</p>}{refunds.data?.map((refund) => <div className="rounded-lg bg-muted/40 p-3 text-sm" key={refund.id}><div className="flex flex-wrap justify-between gap-2"><strong>{formatMoney(refund.amount, refund.currency)}</strong><span>{refundLabel(refund.status)}</span></div><p className="mt-2 break-all font-mono text-xs text-muted-foreground">指令编号：{refund.refundRequestId}</p><p className="mt-1 break-all font-mono text-xs text-muted-foreground">Airwallex 退款编号：{refund.providerRefundId ?? '尚未返回'}</p>{refund.reviewReason && <p className="mt-2 rounded-md border border-destructive/30 bg-destructive/5 p-2 text-sm text-destructive">核对说明：{refund.reviewReason}</p>}<p className="mt-1 text-xs text-muted-foreground">星辙通知：{notificationLabel(refund.notificationStatus)} · {formatTime(refund.updatedAt)}</p>{canConfirmRefund && refund.status === 'PENDING_OPERATOR_CONFIRMATION' && <Button className="mt-3" type="button" size="sm" disabled={pending} onClick={() => setRefundConfirmationId(refund.id)}>确认并发起退款</Button>}</div>)}</div>{!canConfirmRefund && refunds.data?.some((refund) => refund.status === 'PENDING_OPERATOR_CONFIRMATION') && <p className="mt-3 rounded-lg bg-muted p-3 text-sm text-muted-foreground">此退款等待支付侧管理员确认；当前账号不能发起。</p>}</section>
          </div>
          <aside className="space-y-5">
            <section className="rounded-xl border border-border bg-card p-5"><h2 className="text-base font-semibold">异常处理</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">{transaction.provider === 'LOCAL_SIMULATOR' ? '本地模拟交易不执行服务商查询、退款或资金对账；仅保留通知状态与操作留痕。' : '核实会查询空中云汇的真实支付状态；通知重试仅适用于待重试的记录。'}</p>{canOperate ? <div className="mt-5 grid gap-2">{transaction.provider === 'AIRWALLEX' && <><Button type="button" variant="outline" onClick={() => setAction('refresh')}><RefreshCw className="size-4" aria-hidden="true" />核实并更新支付状态</Button><Button type="button" variant="outline" onClick={() => setAction('reconcile')}>记录一次支付核查</Button></>}<Button type="button" variant="outline" disabled={transaction.notificationStatus !== 'RETRY'} onClick={() => setAction('retry')}><Send className="size-4" aria-hidden="true" />重试星辙通知</Button></div> : <p className="mt-4 rounded-lg bg-muted p-3 text-sm text-muted-foreground">当前账号仅能查看记录，不能执行操作。</p>}</section>
            <section className="rounded-xl border border-border bg-card p-5"><h2 className="text-base font-semibold">结算对账</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">空中云汇结算报表与星辙真实业务账本尚未接入。上方支付核查不代表资金已结算。</p></section>
          </aside>
        </div>
      </>}
      {message && <p className="mt-5 rounded-lg border border-border bg-card p-3 text-sm" role="status">{message}</p>}
      {action && <ConfirmActionDialog title={action === 'refresh' ? '核实支付状态？' : action === 'retry' ? '重试星辙通知？' : '记录一次支付核查？'} description={action === 'refresh' ? '系统将读取空中云汇的支付单，并核对订单、金额和币种。只有核实一致才会更新本地记录。' : action === 'retry' ? '系统将重新发送尚未送达的支付结果给星辙。此操作会写入审计记录。' : '系统将查询空中云汇并保存一份差异快照，不会直接更改支付状态。这不是结算对账。'} pending={pending} onCancel={() => setAction(null)} onConfirm={() => void confirmAction()} />}
      {refundConfirmationId && <ConfirmActionDialog title="确认发起这笔退款？" description="系统会再次核对 Airwallex 原支付、金额、币种和累计退款额。核对通过后才向 Airwallex 提交退款；如果请求结果未知，会保留待核实状态，不会重复创建。" pending={pending} onCancel={() => setRefundConfirmationId(null)} onConfirm={() => void confirmRefund()} />}
    </section>
  )
}
