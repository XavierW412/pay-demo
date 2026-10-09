import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, CircleDashed, LoaderCircle } from 'lucide-react'
import { approveMockRefund, createMockRefundRequest, getMockBusinessOrderStatus, getMockPaymentHistory, getMockRefundEvents, getMockRefundRequests, initiateMockBusinessOrderPayment, rejectMockRefund, simulateMockPaymentResult } from './api'
import './mock-xingzhe.css'

export function MockXingzheOrderPage() {
  const { businessOrderId = '' } = useParams()
  const [refundAmount, setRefundAmount] = useState('')
  const [refundPending, setRefundPending] = useState(false)
  const [refundMessage, setRefundMessage] = useState('')
  const [paymentPending, setPaymentPending] = useState(false)
  const [paymentMessage, setPaymentMessage] = useState('')
  const [simulationPending, setSimulationPending] = useState(false)
  const orderQuery = useQuery({
    queryKey: ['mock-order-status', businessOrderId],
    queryFn: () => getMockBusinessOrderStatus(businessOrderId),
    enabled: Boolean(businessOrderId),
    refetchInterval: (query) => {
      const current = query.state.data
      if (!current) return 4000
      return current.paymentStatus === 'FAILED' || current.paymentStatus === 'CANCELLED'
        || current.businessStatus === 'PAID' ? false : 4000
    },
    retry: false,
  })
  const order = orderQuery.data
  const paymentHistory = useQuery({
    queryKey: ['mock-payment-history', businessOrderId],
    queryFn: () => getMockPaymentHistory(businessOrderId),
    enabled: Boolean(businessOrderId),
    refetchInterval: 4000,
    retry: false,
  })
  const refundEvents = useQuery({
    queryKey: ['mock-refund-events', businessOrderId],
    queryFn: () => getMockRefundEvents(businessOrderId),
    enabled: Boolean(businessOrderId) && orderQuery.data?.paymentMode === 'AIRWALLEX_SANDBOX',
    refetchInterval: 4000,
  })
  const refundRequests = useQuery({
    queryKey: ['mock-refund-requests', businessOrderId],
    queryFn: () => getMockRefundRequests(businessOrderId),
    enabled: Boolean(businessOrderId) && orderQuery.data?.paymentMode === 'AIRWALLEX_SANDBOX',
    refetchInterval: 4000,
    retry: false,
  })
  const active = order?.businessStatus === 'PAID' && order.serviceStatus === 'ACTIVE'
  const failed = order?.paymentStatus === 'FAILED' || order?.paymentStatus === 'CANCELLED'
  const canContinueAirwallexPayment = order
    && ['CREATING', 'AWAITING_PAYMENT'].includes(order.paymentStatus)
  const isLocalSimulation = order?.paymentMode === 'LOCAL_SIMULATOR'
  const canControlLocalSimulation = isLocalSimulation && order
    && ['AWAITING_PAYMENT', 'PROCESSING', 'REQUIRES_REVIEW'].includes(order.paymentStatus)

  const continuePayment = async () => {
    if (!order || paymentPending || !canContinueAirwallexPayment) return
    setPaymentPending(true)
    setPaymentMessage('')
    try {
      const payment = await initiateMockBusinessOrderPayment(order.businessOrderId)
      if (payment.paymentMode === 'LOCAL_SIMULATOR') {
        await orderQuery.refetch()
        return
      }
      if (!['CREATING', 'AWAITING_PAYMENT'].includes(payment.paymentStatus)) {
        await orderQuery.refetch()
        return
      }
      if (!payment.checkoutUrl) throw new Error('付款入口缺失')
      const checkoutUrl = new URL(payment.checkoutUrl)
      if (!['http:', 'https:'].includes(checkoutUrl.protocol)) throw new Error('付款入口协议无效')
      window.location.assign(checkoutUrl.href)
    } catch {
      setPaymentMessage('付款入口暂时无法确认。订单仍保留，可稍后使用同一订单重试。')
      await orderQuery.refetch()
    } finally {
      setPaymentPending(false)
    }
  }

  const simulateResult = async (result: 'SUCCEEDED' | 'INSUFFICIENT_FUNDS' | 'PROCESSING' | 'TIMEOUT') => {
    if (!order || simulationPending) return
    setSimulationPending(true)
    setPaymentMessage('')
    try {
      await simulateMockPaymentResult(order.businessOrderId, result)
      await orderQuery.refetch()
      await paymentHistory.refetch()
      const messages = {
        SUCCEEDED: '已提交模拟成功结果；等待支付服务通知模拟星辙并更新服务状态。',
        INSUFFICIENT_FUNDS: '已模拟资金不足导致付款失败；服务不会开通。',
        PROCESSING: '已模拟付款处理中；订单保持等待，不会重复创建支付单。',
        TIMEOUT: '已模拟等待超时；结果标记为待核实，不会当作付款失败。',
      }
      setPaymentMessage(messages[result])
    } catch {
      setPaymentMessage('模拟结果未能提交，请重新读取订单状态后重试。')
    } finally {
      setSimulationPending(false)
    }
  }

  const requestRefund = async () => {
    if (!order || refundPending) return
    const amount = refundAmount || order.amount
    setRefundPending(true)
    setRefundMessage('')
    try {
      const storageKey = `mock-refund-request:${order.businessOrderId}`
      const previous = sessionStorage.getItem(storageKey)
      const instruction = previous ? JSON.parse(previous) as { id: string; amount: string } : { id: crypto.randomUUID(), amount }
      if (!previous) sessionStorage.setItem(storageKey, JSON.stringify(instruction))
      if (instruction.amount !== amount) {
        setRefundMessage('这张模拟订单已有退款申请编号。请用原金额重试，避免重复申请。')
        return
      }
      await createMockRefundRequest(order.businessOrderId, instruction.id, instruction.amount)
      sessionStorage.removeItem(storageKey)
      setRefundMessage('退款申请已记录。下一步由模拟星辙业务方决定是否批准。')
      await refundRequests.refetch()
    } catch {
      setRefundMessage('申请结果暂时未知。再次提交会使用同一申请编号。')
    } finally {
      setRefundPending(false)
    }
  }

  const approveRefund = async (refundRequestId: string) => {
    if (!order || refundPending) return
    setRefundPending(true)
    setRefundMessage('')
    try {
      const result = await approveMockRefund(order.businessOrderId, refundRequestId)
      setRefundMessage(`模拟星辙已批准并发送退款指令，支付侧状态：${result.status}。尚未表示退款完成。`)
      await refundRequests.refetch()
      await refundEvents.refetch()
    } catch {
      setRefundMessage('发送结果暂时未知。再次操作会使用同一退款申请编号，不会新建退款指令。')
    } finally {
      setRefundPending(false)
    }
  }

  const rejectRefund = async (refundRequestId: string) => {
    if (!order || refundPending) return
    setRefundPending(true)
    setRefundMessage('')
    try {
      await rejectMockRefund(order.businessOrderId, refundRequestId)
      setRefundMessage('模拟星辙业务方已拒绝这笔申请，支付项目未收到退款指令。')
      await refundRequests.refetch()
    } catch {
      setRefundMessage('处理结果暂时无法确认，请刷新退款申请记录。')
    } finally {
      setRefundPending(false)
    }
  }

  return (
    <main className="mock-xingzhe-page">
      <div className="mock-xingzhe-shell">
        <header className="mock-xingzhe-header">
          <Link className="mock-xingzhe-brand" to="/mock-xingzhe">
            <span className="mock-xingzhe-mark" aria-hidden="true">星</span><span>星辙 · 服务选购</span>
          </Link>
          <span className="mock-xingzhe-env">本地开发环境</span>
          <Link className="mock-xingzhe-button secondary" to="/mock-xingzhe/orders">我的订单</Link>
        </header>
        <section className="mock-xingzhe-notice" role="note">
          <span className="mock-xingzhe-notice-icon" aria-hidden="true">!</span>
          <p><strong>本地模拟星辙订单</strong><br />此页面仅供开发联调，不代表真实星辙业务状态。</p>
        </section>
        <div className="mock-xingzhe-heading">
          <p className="mock-xingzhe-eyebrow">业务订单</p>
          <h1>服务状态</h1>
          <p>支付结果由独立支付服务核实并通知，本页展示模拟星辙收到结果后的状态。</p>
        </div>
        <section className="mock-xingzhe-order" aria-live="polite">
          {orderQuery.isPending && <p className="flex items-center gap-2"><LoaderCircle className="size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" />正在读取订单状态…</p>}
          {orderQuery.isError && <p role="alert">暂时无法读取订单。请检查本地服务或稍后重试。</p>}
          {order && (
            <>
              <div className="mock-xingzhe-order-heading">
                <div><p className="mock-xingzhe-eyebrow">{order.displayName}</p><h2 className="break-all">{order.businessOrderId}</h2></div>
                <span className={`mock-xingzhe-status ${active ? 'success' : failed ? 'danger' : order.paymentStatus === 'REQUIRES_REVIEW' ? 'warning' : 'processing'}`}>
                  {active ? '服务已开通' : failed ? '付款未成功' : order.paymentStatus === 'REQUIRES_REVIEW' ? '等待核实' : order.paymentStatus === 'PROCESSING' ? '等待付款结果' : '等待付款'}
                </span>
              </div>
              <dl className="mock-xingzhe-order-details">
                <div><dt>支付来源</dt><dd>{isLocalSimulation ? '本地模拟结果' : 'Airwallex 沙盒收银台'}</dd></div>
                <div><dt>支付状态</dt><dd>{order.paymentStatus === 'SUCCEEDED' ? '支付服务已核实成功' : failed ? '未成功' : order.paymentStatus === 'REQUIRES_REVIEW' ? '支付结果等待进一步核实' : order.paymentStatus === 'PROCESSING' ? '付款正在处理中，请等待服务端结果' : '等待付款'}</dd></div>
                <div><dt>服务状态</dt><dd>{active ? '已开通' : '尚未开通'}</dd></div>
                <div className="mock-xingzhe-status-detail"><dt>处理说明</dt><dd>{active ? '模拟星辙已收到可信支付通知并开通服务。' : '收到支付服务的成功通知后，模拟星辙才会开通服务。'}</dd></div>
              </dl>
            </>
          )}
          {(canContinueAirwallexPayment || canControlLocalSimulation) && <div className="mt-6 border-t border-current/10 pt-5">
            {isLocalSimulation ? <div className="flex flex-wrap gap-3">
              {(order.paymentStatus === 'AWAITING_PAYMENT' || order.paymentStatus === 'PROCESSING') && <>
                <button className="mock-xingzhe-button primary" type="button" disabled={simulationPending} onClick={() => void simulateResult('SUCCEEDED')}>{simulationPending ? '正在提交…' : '模拟付款成功'}</button>
                <button className="mock-xingzhe-button secondary" type="button" disabled={simulationPending} onClick={() => void simulateResult('INSUFFICIENT_FUNDS')}>模拟资金不足</button>
              </>}
              {order.paymentStatus === 'AWAITING_PAYMENT' && <>
                <button className="mock-xingzhe-button secondary" type="button" disabled={simulationPending} onClick={() => void simulateResult('PROCESSING')}>模拟付款处理中</button>
                <button className="mock-xingzhe-button secondary" type="button" disabled={simulationPending} onClick={() => void simulateResult('TIMEOUT')}>模拟等待超时</button>
              </>}
              {order.paymentStatus === 'PROCESSING' && <button className="mock-xingzhe-button secondary" type="button" disabled={simulationPending} onClick={() => void simulateResult('TIMEOUT')}>模拟等待超时</button>}
            </div> : <button className="mock-xingzhe-button primary" type="button" disabled={paymentPending} onClick={() => void continuePayment()}>
              {paymentPending ? '正在准备付款…' : '继续付款'}
            </button>}
            {isLocalSimulation && <p className="mt-3 text-xs leading-5 text-muted-foreground">只用于本地演练，不请求 Airwallex。超时是“结果未知”，之后可以补录模拟最终结果。</p>}
            {paymentMessage && <p className="mt-3 text-sm" role="status">{paymentMessage}</p>}
          </div>}
          {order && <section className="mt-8 border-t border-current/10 pt-6">
            <h3 className="text-base font-semibold">支付历史</h3>
            {paymentHistory.isPending && <p className="mt-3 text-sm text-muted-foreground">正在读取支付事件…</p>}
            {paymentHistory.isError && <p className="mt-3 text-sm text-muted-foreground">支付历史暂时无法读取。</p>}
            {paymentHistory.data?.events.length === 0 && <p className="mt-3 text-sm text-muted-foreground">还没有收到支付状态事件。</p>}
            <ol className="mt-3 space-y-3">
              {paymentHistory.data?.events.map((event) => <li className="border-l-2 border-primary/30 pl-3" key={event.eventId}>
                <p className="text-sm font-medium">{event.paymentStatus === 'FAILED' && event.eventType.endsWith('insufficient_funds') ? '模拟资金不足' : event.paymentStatus ?? '历史事件（状态未记录）'}{event.amount && event.currency ? ` · ${event.currency} ${event.amount}` : ''}</p>
                <p className="text-xs text-muted-foreground">{new Date(event.occurredAt).toLocaleString('zh-CN')}</p>
              </li>)}
            </ol>
          </section>}
          {active && order && !isLocalSimulation && <div className="mt-6 border-t border-current/10 pt-5">
            <h3 className="text-base font-semibold">星辙业务退款处理</h3>
            <p className="mt-2 text-sm leading-6 opacity-75">先模拟用户提出申请，再由星辙业务方批准或拒绝。只有批准后才发送签名指令给支付项目。</p>
            <div className="mt-4 flex flex-wrap items-end gap-3"><label className="text-sm">申请金额（{order.currency}）<input className="mt-1 block h-11 w-40 rounded-lg border border-current/20 bg-transparent px-3" type="number" min="0.01" max={order.amount} step="0.01" placeholder={order.amount} value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} /></label><button className="mock-xingzhe-button secondary" type="button" disabled={refundPending} onClick={() => void requestRefund()}>{refundPending ? '正在提交…' : '提交退款申请'}</button></div>
            {refundMessage && <p className="mt-3 text-sm" role="status">{refundMessage}</p>}
            {refundRequests.data?.requests.map((request) => <div className="mt-4 rounded-lg border border-current/10 p-4" key={request.refundRequestId}>
              <div className="flex flex-wrap justify-between gap-2 text-sm"><strong>{request.currency} {request.amount}</strong><span>业务处理：{request.businessDecision === 'REQUESTED' ? '待审批' : request.businessDecision === 'APPROVED' ? '已批准' : '已拒绝'}</span></div>
              <p className="mt-2 break-all font-mono text-xs opacity-70">申请编号：{request.refundRequestId}</p>
              {request.businessDecision === 'APPROVED' && <p className="mt-1 text-sm">支付项目状态：{request.paymentServiceStatus}</p>}
              {request.businessDecision === 'REQUESTED' && <div className="mt-3 flex flex-wrap gap-2">
                <button className="mock-xingzhe-button primary" type="button" disabled={refundPending} onClick={() => void approveRefund(request.refundRequestId)}>模拟星辙批准并发送</button>
                <button className="mock-xingzhe-button secondary" type="button" disabled={refundPending} onClick={() => void rejectRefund(request.refundRequestId)}>模拟星辙拒绝</button>
              </div>}
              {request.businessDecision === 'APPROVED' && ['NOT_SENT', 'PENDING_OPERATOR_CONFIRMATION'].includes(request.paymentServiceStatus) && <button className="mock-xingzhe-button secondary mt-3" type="button" disabled={refundPending} onClick={() => void approveRefund(request.refundRequestId)}>用同一编号重试发送</button>}
            </div>)}
            {refundEvents.data && refundEvents.data.length > 0 && <div className="mt-4 space-y-2"><p className="text-sm font-medium">模拟星辙收到的退款通知</p>{refundEvents.data.map((event) => <p className="text-sm" key={`${event.refundRequestId}:${event.status}`}>{event.currency} {event.amount} · {event.status} · {new Date(event.receivedAt).toLocaleString('zh-CN')}</p>)}</div>}
          </div>}
          <div className="mt-6 flex flex-wrap gap-3">
            <button className="mock-xingzhe-button secondary" type="button" onClick={() => void orderQuery.refetch()}>重新查询</button>
            <Link className="mock-xingzhe-button secondary" to="/mock-xingzhe"><ArrowLeft className="size-4" aria-hidden="true" />返回服务选购</Link>
          </div>
        </section>
        <footer className="mock-xingzhe-footer"><CircleDashed className="inline size-3" aria-hidden="true" /> 本地模拟页面 · 以服务端状态为准</footer>
      </div>
    </main>
  )
}
