import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { OperatorSession } from './api'
import { issueInvitation } from './api'

export function OperationsTeamPage() {
  const session = useOutletContext<OperatorSession>()
  const [link, setLink] = useState('')
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')

  const issue = async () => {
    setPending(true)
    setMessage('')
    try {
      const token = await issueInvitation()
      setLink(`${window.location.origin}/operations/register/${encodeURIComponent(token)}`)
      setMessage('邀请链接已生成，24 小时内可使用一次。请通过你信任的渠道交给星辙员工。')
    } catch {
      setMessage('邀请生成失败，请稍后重试。')
    } finally {
      setPending(false)
    }
  }

  if (session.role !== 'STAFF_ADMIN') return <p className="text-sm text-destructive">只有员工管理员可以创建邀请。</p>
  return <section className="max-w-2xl">
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">TEAM ACCESS</p>
    <h1 className="mt-2 text-3xl font-semibold tracking-tight">员工邀请</h1>
    <p className="mt-2 text-sm leading-6 text-muted-foreground">星辙员工在邀请页面设置自己的登录账号。邀请链接不会由软件自动发送。</p>
    <div className="mt-8 rounded-xl border border-border bg-card p-6"><h2 className="text-base font-semibold">创建一次性邀请</h2><p className="mt-2 text-sm leading-6 text-muted-foreground">新员工可查看交易、核实支付和处理通知异常；不能创建新的邀请。链接有效期为 24 小时。</p><Button className="mt-5" type="button" disabled={pending} onClick={() => void issue()}>{pending ? '正在生成…' : '生成邀请链接'}</Button>
      {link && <div className="mt-5"><label className="block text-xs text-muted-foreground" htmlFor="invitation-link">仅显示在当前页面，请妥善保管</label><input id="invitation-link" className="mt-2 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm" readOnly value={link} onFocus={(event) => event.target.select()} /><Button className="mt-2" type="button" size="sm" variant="outline" onClick={() => void navigator.clipboard.writeText(link)}>复制链接</Button></div>}
      {message && <p className="mt-4 text-sm text-muted-foreground" role="status">{message}</p>}
    </div>
  </section>
}
