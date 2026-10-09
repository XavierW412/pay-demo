import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getTransactions } from './api'
import { formatMoney, formatTime, notificationLabel, paymentLabel, refundLabel } from './format'

const statuses = [
  { value: '', label: '全部状态' },
  { value: 'AWAITING_PAYMENT', label: '等待付款' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'SUCCEEDED', label: '已支付' },
  { value: 'FAILED', label: '失败' },
  { value: 'REQUIRES_REVIEW', label: '待核实' },
  { value: 'CANCELLED', label: '已取消' },
]

export function OperationsTransactionListPage() {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const transactions = useQuery({
    queryKey: ['operations-transactions', query, status, page],
    queryFn: () => getTransactions({ query, status, page }),
  })
  const result = transactions.data
  const totalPages = result ? Math.ceil(result.total / result.size) : 0

  return (
    <section>
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div>
          <p className="text-xs font-semibold tracking-[0.16em] text-primary">TRANSACTIONS</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">交易记录</h1>
          <p className="mt-2 text-sm text-muted-foreground">逐笔查看支付结果与星辙通知进度。</p>
        </div>
        <div className="rounded-xl border border-border bg-card px-5 py-3 text-right">
          <p className="text-xs text-muted-foreground">符合条件的支付单</p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">{result?.total ?? '—'}</p>
        </div>
      </div>

      <form className="mt-8 flex flex-wrap gap-3" onSubmit={(event) => { event.preventDefault(); setPage(0); setQuery(input.trim()) }}>
        <label className="flex min-h-11 min-w-[240px] flex-1 items-center gap-2 rounded-lg border border-input bg-card px-3 focus-within:ring-2 focus-within:ring-ring">
          <Search className="size-4 text-muted-foreground" aria-hidden="true" />
          <span className="sr-only">搜索订单编号</span>
          <input className="w-full bg-transparent text-sm outline-none" placeholder="搜索星辙订单号或支付单号" value={input} onChange={(event) => setInput(event.target.value)} />
        </label>
        <select aria-label="支付状态" className="min-h-11 rounded-lg border border-input bg-card px-3 text-sm" value={status} onChange={(event) => { setPage(0); setStatus(event.target.value) }}>
          {statuses.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
        <Button className="min-h-11" type="submit">查询</Button>
      </form>

      <div className="mt-5 overflow-hidden rounded-xl border border-border bg-card">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1080px] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-xs text-muted-foreground">
              <tr><th className="px-5 py-4 font-medium">星辙订单 / 支付单</th><th className="px-4 py-4 font-medium">来源</th><th className="px-4 py-4 font-medium">金额</th><th className="px-4 py-4 font-medium">支付状态</th><th className="px-4 py-4 font-medium">通知状态</th><th className="px-4 py-4 font-medium">最近退款</th><th className="px-4 py-4 font-medium">创建时间</th><th className="px-5 py-4 text-right font-medium">详情</th></tr>
            </thead>
            <tbody className="divide-y divide-border">
              {result?.items.map((item) => (
                <tr key={item.id} className="hover:bg-muted/30">
                  <td className="px-5 py-4"><span className="block font-medium">{item.businessOrderId}</span><span className="mt-1 block font-mono text-xs text-muted-foreground">{item.id}</span></td>
                  <td className="px-4 py-4"><span className={item.provider === 'LOCAL_SIMULATOR' ? 'rounded-full bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground' : 'rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary'}>{item.provider === 'LOCAL_SIMULATOR' ? '本地模拟' : 'Airwallex'}</span></td>
                  <td className="px-4 py-4 whitespace-nowrap font-semibold tabular-nums">{formatMoney(item.amount, item.currency)}</td>
                  <td className="px-4 py-4"><span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">{paymentLabel(item.paymentStatus)}</span></td>
                  <td className="px-4 py-4 text-muted-foreground">{notificationLabel(item.notificationStatus)}</td>
                  <td className="px-4 py-4 text-muted-foreground">{refundLabel(item.refundStatus)}</td>
                  <td className="px-4 py-4 whitespace-nowrap text-muted-foreground">{formatTime(item.createdAt)}</td>
                  <td className="px-5 py-4 text-right"><Link className="inline-flex items-center gap-1 font-medium text-primary hover:underline" to={`/operations/transactions/${item.id}`}>查看<ArrowRight className="size-3.5" aria-hidden="true" /></Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {transactions.isPending && <p className="px-5 py-12 text-center text-sm text-muted-foreground" role="status">正在读取交易…</p>}
        {transactions.isError && <p className="px-5 py-12 text-center text-sm text-destructive" role="alert">交易读取失败，请稍后重试。</p>}
        {result?.items.length === 0 && <p className="px-5 py-12 text-center text-sm text-muted-foreground">当前条件下没有交易记录。</p>}
      </div>
      {result && result.total > 0 && <div className="mt-4 flex items-center justify-between gap-3 text-sm text-muted-foreground"><span>第 {page + 1} / {totalPages} 页</span><div className="flex gap-2"><Button type="button" variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>上一页</Button><Button type="button" variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>下一页</Button></div></div>}
    </section>
  )
}
