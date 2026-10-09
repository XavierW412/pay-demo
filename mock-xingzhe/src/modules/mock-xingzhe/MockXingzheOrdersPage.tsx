import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowRight, RefreshCw } from 'lucide-react'
import { getMockBusinessOrders } from './api'
import './mock-xingzhe.css'

const statusLabel: Record<string, string> = {
  CREATING: '准备付款',
  AWAITING_PAYMENT: '等待付款',
  PROCESSING: '处理中',
  REQUIRES_REVIEW: '待核实',
  SUCCEEDED: '已支付',
  FAILED: '支付失败',
  CANCELLED: '已取消',
}

export function MockXingzheOrdersPage() {
  const [page, setPage] = useState(0)
  const [paymentStatus, setPaymentStatus] = useState('')
  const ordersQuery = useQuery({
    queryKey: ['mock-orders', page, paymentStatus],
    queryFn: () => getMockBusinessOrders(page, 20, paymentStatus || undefined),
    retry: false,
  })
  const orders = ordersQuery.data?.items ?? []

  return (
    <main className="mock-xingzhe-page">
      <div className="mock-xingzhe-shell">
        <header className="mock-xingzhe-header">
          <Link className="mock-xingzhe-brand" to="/mock-xingzhe">
            <span className="mock-xingzhe-mark" aria-hidden="true">星</span><span>星辙 · 服务选购</span>
          </Link>
          <span className="mock-xingzhe-env">本地开发环境</span>
        </header>
        <section className="mock-xingzhe-notice" role="note">
          <span className="mock-xingzhe-notice-icon" aria-hidden="true">!</span>
          <p><strong>本地模拟订单记录</strong><br />这里只显示本地开发产生的业务订单，不代表真实星辙数据。</p>
        </section>
        <div className="mock-xingzhe-heading">
          <p className="mock-xingzhe-eyebrow">订单历史</p>
          <h1>我的订单</h1>
          <p>订单状态以模拟星辙收到的支付服务通知为准。</p>
        </div>
        <section className="mock-xingzhe-order" aria-live="polite">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-3">
              <p className="text-sm text-muted-foreground">共 {ordersQuery.data?.total ?? 0} 笔订单</p>
              <label className="text-sm">支付状态
                <select className="ml-2 rounded-lg border border-input bg-background px-3 py-2" value={paymentStatus} onChange={(event) => { setPaymentStatus(event.target.value); setPage(0) }}>
                  <option value="">全部</option>
                  {Object.entries(statusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
            </div>
            <button className="mock-xingzhe-button secondary" type="button" disabled={ordersQuery.isFetching} onClick={() => void ordersQuery.refetch()}>
              <RefreshCw className={`size-4 ${ordersQuery.isFetching ? 'animate-spin' : ''}`} aria-hidden="true" />刷新
            </button>
          </div>
          {ordersQuery.isPending && <p role="status">正在读取订单…</p>}
          {ordersQuery.isError && <div className="mock-xingzhe-error" role="alert"><p>暂时无法读取订单历史，请确认本地服务已启动。</p></div>}
          {!ordersQuery.isPending && !ordersQuery.isError && orders.length === 0 && (
            <div className="mock-xingzhe-state">还没有订单，先选择一项示例服务。</div>
          )}
          <div className="space-y-3">
            {orders.map((order) => (
              <Link
                key={order.businessOrderId}
                className="block rounded-xl border border-border bg-card p-4 transition hover:border-primary/50"
                to={`/mock-xingzhe/order/${encodeURIComponent(order.businessOrderId)}`}
              >
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="font-semibold">{order.displayName}</p>
                    <p className="mt-1 break-all text-xs text-muted-foreground">{order.businessOrderId}</p>
                    <p className="mt-2 text-xs text-muted-foreground">{new Date(order.createdAt).toLocaleString('zh-CN')}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3 text-right">
                    <div>
                      <p className="font-semibold">{order.currency} {order.amount}</p>
                      <p className="mt-1 text-sm text-muted-foreground">{statusLabel[order.paymentStatus] ?? order.paymentStatus}</p>
                    </div>
                    <ArrowRight className="size-4 text-muted-foreground" aria-hidden="true" />
                  </div>
                </div>
              </Link>
            ))}
          </div>
          {ordersQuery.data && ordersQuery.data.total > ordersQuery.data.size && <div className="mt-5 flex items-center justify-between gap-3">
            <button className="mock-xingzhe-button secondary" type="button" disabled={page === 0} onClick={() => setPage((current) => Math.max(0, current - 1))}>上一页</button>
            <span className="text-sm text-muted-foreground">第 {page + 1} 页</span>
            <button className="mock-xingzhe-button secondary" type="button" disabled={(page + 1) * ordersQuery.data.size >= ordersQuery.data.total} onClick={() => setPage((current) => current + 1)}>下一页</button>
          </div>}
          <div className="mt-5 flex gap-3">
            <Link className="mock-xingzhe-button primary" to="/mock-xingzhe">返回服务选购</Link>
          </div>
        </section>
        <footer className="mock-xingzhe-footer">本地模拟页面 · 订单记录保存在本地开发数据库</footer>
      </div>
    </main>
  )
}
