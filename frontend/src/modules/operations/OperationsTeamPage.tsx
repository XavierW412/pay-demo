import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, UserPlus, Users } from 'lucide-react'
import { useOutletContext } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import type { OperatorAccount, OperatorInvitation, OperatorSession, TemporaryOperatorCredentials } from './api'
import { createTemporaryOperatorAccount, getOperatorAccounts, getOperatorInvitations, issueInvitation, revokeOperatorInvitation, setOperatorActive } from './api'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import { formatTime, roleLabel } from './format'

const pageSize = 20
type PendingAction = { type: 'account'; account: OperatorAccount; active: boolean } | { type: 'invitation'; invitation: OperatorInvitation } | null

const invitationStatusLabel: Record<OperatorInvitation['status'], string> = { PENDING: '待使用', USED: '已使用', REVOKED: '已撤销', EXPIRED: '已过期' }

export function OperationsTeamPage() {
  const session = useOutletContext<OperatorSession>()
  const queryClient = useQueryClient()
  const [accountsPage, setAccountsPage] = useState(0)
  const [invitationsPage, setInvitationsPage] = useState(0)
  const [invitationLink, setInvitationLink] = useState('')
  const [temporaryCredentials, setTemporaryCredentials] = useState<TemporaryOperatorCredentials | null>(null)
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState('')
  const [pendingAction, setPendingAction] = useState<PendingAction>(null)
  const accounts = useQuery({ queryKey: ['operations-team-accounts', accountsPage], queryFn: () => getOperatorAccounts(accountsPage, pageSize) })
  const invitations = useQuery({ queryKey: ['operations-team-invitations', invitationsPage], queryFn: () => getOperatorInvitations(invitationsPage, pageSize) })
  const canManage = session.role === 'STAFF_ADMIN'

  const issue = async () => {
    setPending(true)
    setMessage('')
    setInvitationLink('')
    try {
      const token = await issueInvitation()
      setInvitationLink(`${window.location.origin}/operations/register/${encodeURIComponent(token)}`)
      setMessage('一次性邀请已创建，有效期 24 小时。请自行复制并通过可信渠道交给员工。')
      await queryClient.invalidateQueries({ queryKey: ['operations-team-invitations'] })
    } catch {
      setMessage('邀请创建失败，请稍后重试。')
    } finally {
      setPending(false)
    }
  }

  const createTemporaryAccount = async () => {
    setPending(true)
    setMessage('')
    setInvitationLink('')
    setTemporaryCredentials(null)
    try {
      const credentials = await createTemporaryOperatorAccount()
      setTemporaryCredentials(credentials)
      setMessage('临时测试账号已创建。密码只显示这一次，请安全交付给甲方测试人员。')
      await queryClient.invalidateQueries({ queryKey: ['operations-team-accounts'] })
    } catch {
      setMessage('创建失败。请确认后端使用 local 配置并连接 Airwallex 沙盒后重试。')
    } finally {
      setPending(false)
    }
  }

  const confirmAction = async () => {
    if (!pendingAction || pending) return
    setPending(true)
    setMessage('')
    try {
      if (pendingAction.type === 'account') {
        await setOperatorActive(pendingAction.account.id, pendingAction.active)
        setMessage(pendingAction.active
          ? '员工账号已恢复访问。'
          : pendingAction.account.role === 'TEMP_TESTER'
            ? '临时测试账号已停用，后续请求将被拒绝。'
            : '员工账号已停用，后续请求将被拒绝。')
        await queryClient.invalidateQueries({ queryKey: ['operations-team-accounts'] })
      } else {
        await revokeOperatorInvitation(pendingAction.invitation.id)
        setMessage('邀请已撤销，尚未使用的邀请链接将无法注册。')
        await queryClient.invalidateQueries({ queryKey: ['operations-team-invitations'] })
      }
      setPendingAction(null)
    } catch {
      setMessage('操作未完成。请刷新列表确认当前状态，再决定后续处理。')
      setPendingAction(null)
    } finally {
      setPending(false)
    }
  }

  const copyInvitation = async () => {
    try {
      await navigator.clipboard.writeText(invitationLink)
      setMessage('邀请链接已复制。请只发送给预期的员工。')
    } catch {
      setMessage('无法访问剪贴板，请手动选中并复制邀请链接。')
    }
  }

  const copyTemporaryCredentials = async () => {
    if (!temporaryCredentials) return
    try {
      await navigator.clipboard.writeText(
        `星辙支付服务临时测试账号\n用户名：${temporaryCredentials.username}\n初始密码：${temporaryCredentials.initialPassword}\n有效期至：${formatTime(temporaryCredentials.expiresAt)}`,
      )
      setMessage('临时账号凭证已复制。请通过可信渠道交付，并避免留存在聊天记录或截图中。')
    } catch {
      setMessage('无法访问剪贴板。请在当前页面手动选中凭证并复制；关闭后密码无法找回。')
    }
  }

  return <section>
    <p className="text-xs font-semibold tracking-[0.16em] text-primary">TEAM ACCESS</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">员工与权限</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">查看员工访问状态和一次性邀请。支付侧员工负责操作，项目所有者账号保持只读。</p>
    {!canManage && <div className="mt-5 rounded-lg border border-border bg-muted/35 p-4 text-sm"><strong>{session.role === 'TEMP_TESTER' ? '临时测试权限。' : '只读查看。'}</strong><span className="text-muted-foreground"> 当前账号不能创建或撤销邀请，也不能管理员工账号。</span></div>}

    {canManage && <section className="mt-6 rounded-xl border border-border bg-card p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4"><div className="flex items-start gap-3"><span className="grid size-10 place-items-center rounded-lg bg-amber-500/10 text-amber-700"><UserPlus className="size-5" aria-hidden="true" /></span><div><h2 className="text-base font-semibold">生成甲方临时测试账号</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">账号固定有效 6 小时，可执行运营操作和确认已核验的沙盒退款；不能管理员工。密码仅在生成成功后显示一次。</p></div></div><Button type="button" disabled={pending} onClick={() => void createTemporaryAccount()}>{pending ? '正在创建…' : '生成临时账号'}</Button></div>
      {temporaryCredentials && <div className="mt-5 rounded-lg border border-amber-500/35 bg-amber-500/5 p-4" role="status">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="text-sm font-semibold">请立即保存并安全交付</p><p className="mt-1 text-xs leading-5 text-muted-foreground">关闭此区域后，系统不会再次提供初始密码。确认退款会向当前 Airwallex 沙盒提交实际退款操作。</p></div><Button type="button" size="sm" variant="ghost" onClick={() => { setTemporaryCredentials(null); setMessage('凭证已从当前页面清除。初始密码无法再次查看，如遗失请停用该账号后重新生成。') }}>关闭并清除</Button></div>
        <dl className="mt-4 grid gap-3 sm:grid-cols-2">
          <div><dt className="text-xs text-muted-foreground">用户名</dt><dd className="mt-1 select-all rounded-md border border-border bg-background px-3 py-2 font-mono text-sm">{temporaryCredentials.username}</dd></div>
          <div><dt className="text-xs text-muted-foreground">一次性初始密码</dt><dd className="mt-1 select-all break-all rounded-md border border-border bg-background px-3 py-2 font-mono text-sm">{temporaryCredentials.initialPassword}</dd></div>
          <div><dt className="text-xs text-muted-foreground">有效期至</dt><dd className="mt-1 text-sm font-medium">{formatTime(temporaryCredentials.expiresAt)}</dd></div>
        </dl>
        <Button className="mt-4" type="button" size="sm" variant="outline" onClick={() => void copyTemporaryCredentials()}><Copy className="size-4" aria-hidden="true" />复制账号和密码</Button>
      </div>}
    </section>}

    <section className="mt-6 rounded-xl border border-border bg-card p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4"><div className="flex items-start gap-3"><span className="grid size-10 place-items-center rounded-lg bg-primary/10 text-primary"><UserPlus className="size-5" aria-hidden="true" /></span><div><h2 className="text-base font-semibold">邀请员工</h2><p className="mt-1 max-w-2xl text-sm leading-6 text-muted-foreground">邀请链接一次性使用、24 小时后过期。系统不会自动发送链接；生成后仅当前页面显示，请通过可信渠道交付。</p></div></div>{canManage && <Button type="button" disabled={pending} onClick={() => void issue()}>{pending ? '正在创建…' : '创建员工邀请'}</Button>}</div>
      {invitationLink && <div className="mt-5 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]"><label className="grid gap-1.5 text-xs text-muted-foreground" htmlFor="invitation-link">仅在当前页面展示的邀请链接<input id="invitation-link" className="h-10 min-w-0 rounded-md border border-input bg-background px-3 text-sm text-foreground" readOnly value={invitationLink} onFocus={(event) => event.target.select()} /></label><Button className="self-end" type="button" variant="outline" size="sm" onClick={() => void copyInvitation()}><Copy className="size-4" aria-hidden="true" />复制链接</Button></div>}
    </section>

    <div className="mt-7 grid gap-6 2xl:grid-cols-2">
      <section className="min-w-0 rounded-xl border border-border bg-card">
        <div className="flex items-center justify-between border-b border-border px-5 py-4"><div><div className="flex items-center gap-2"><Users className="size-4 text-primary" aria-hidden="true" /><h2 className="text-base font-semibold">员工账号</h2></div><p className="mt-1 text-xs text-muted-foreground">账号列表来自支付项目当前记录，不展示不存在的最近登录信息。</p></div><span className="text-sm tabular-nums text-muted-foreground">{accounts.data?.total ?? '—'}</span></div>
        {accounts.isPending && <p className="p-8 text-center text-sm text-muted-foreground" role="status">正在读取员工账号…</p>}
        {accounts.isError && <div className="p-8 text-center text-sm text-destructive" role="alert"><p>员工账号读取失败。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void accounts.refetch()}>重试</Button></div>}
        {accounts.data?.items.length === 0 && <p className="p-8 text-center text-sm text-muted-foreground">暂无员工账号。</p>}
        <div className="divide-y divide-border">{accounts.data?.items.map((account) => {
          const isTemporary = account.role === 'TEMP_TESTER'
          const isExpired = isTemporary && account.expiresAt !== null && new Date(account.expiresAt).getTime() <= Date.now()
          const accountStatus = !account.active ? '已停用' : isExpired ? '已过期' : isTemporary ? '有效' : '已启用'
          const manageable = canManage && account.username !== session.username
            && (account.role === 'STAFF_OPERATOR' || (isTemporary && account.active))
          return <article key={account.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="truncate text-sm font-semibold">{account.username}{account.username === session.username && <span className="ml-2 text-xs font-normal text-muted-foreground">当前账号</span>}</p><p className="mt-1 text-xs text-muted-foreground">{roleLabel(account.role)} · 创建于 {formatTime(account.createdAt)}</p>{account.expiresAt && <p className="mt-1 text-xs text-muted-foreground">{isExpired ? '有效期已结束于' : '有效期至'} {formatTime(account.expiresAt)}</p>}<span className={`mt-2 inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${account.active && !isExpired ? 'bg-emerald-600/10 text-emerald-800' : 'bg-muted text-muted-foreground'}`}>{accountStatus}</span></div>{manageable && <Button className="w-fit" type="button" size="sm" variant="outline" onClick={() => setPendingAction({ type: 'account', account, active: false })}>{isTemporary ? '提前停用' : account.active ? '停用账号' : '恢复访问'}</Button>}</article>
        })}</div>
        {accounts.data && accounts.data.total > accounts.data.size && <Pagination page={accountsPage} size={accounts.data.size} total={accounts.data.total} pending={accounts.isFetching} onPage={setAccountsPage} />}
      </section>

      <section className="min-w-0 rounded-xl border border-border bg-card">
        <div className="flex items-center justify-between border-b border-border px-5 py-4"><div><h2 className="text-base font-semibold">待处理邀请</h2><p className="mt-1 text-xs text-muted-foreground">撤销仅适用于未使用且未过期的邀请。</p></div><span className="text-sm tabular-nums text-muted-foreground">{invitations.data?.total ?? '—'}</span></div>
        {invitations.isPending && <p className="p-8 text-center text-sm text-muted-foreground" role="status">正在读取邀请记录…</p>}
        {invitations.isError && <div className="p-8 text-center text-sm text-destructive" role="alert"><p>邀请记录读取失败。</p><Button className="mt-3" size="sm" variant="outline" onClick={() => void invitations.refetch()}>重试</Button></div>}
        {invitations.data?.items.length === 0 && <p className="p-8 text-center text-sm text-muted-foreground">暂无邀请记录。</p>}
        <div className="divide-y divide-border">{invitations.data?.items.map((invitation) => {
          const revocable = canManage && invitation.status === 'PENDING' && new Date(invitation.expiresAt).getTime() > Date.now()
          return <article key={invitation.id} className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><p className="text-sm font-semibold">{invitationStatusLabel[invitation.status]}</p><p className="mt-1 text-xs text-muted-foreground">邀请人 {invitation.issuedBy} · 创建于 {formatTime(invitation.createdAt)}</p><p className="mt-1 text-xs text-muted-foreground">{invitation.status === 'USED' && invitation.usedAt ? `使用于 ${formatTime(invitation.usedAt)}` : `过期于 ${formatTime(invitation.expiresAt)}`}{invitation.revokedAt ? ` · 撤销于 ${formatTime(invitation.revokedAt)}` : ''}</p></div>{revocable && <Button className="w-fit" type="button" size="sm" variant="outline" onClick={() => setPendingAction({ type: 'invitation', invitation })}>撤销邀请</Button>}</article>
        })}</div>
        {invitations.data && invitations.data.total > invitations.data.size && <Pagination page={invitationsPage} size={invitations.data.size} total={invitations.data.total} pending={invitations.isFetching} onPage={setInvitationsPage} />}
      </section>
    </div>

    {message && <p className="mt-5 rounded-lg border border-border bg-card p-3 text-sm" role="status">{message}</p>}
    {pendingAction && <ConfirmActionDialog title={pendingAction.type === 'account' ? (pendingAction.active ? '恢复员工访问？' : pendingAction.account.role === 'TEMP_TESTER' ? '提前停用临时账号？' : '停用员工账号？') : '撤销员工邀请？'} description={pendingAction.type === 'account' ? (pendingAction.active ? `将恢复 ${pendingAction.account.username} 的运营后台访问。` : `将停用 ${pendingAction.account.username}。该账号的后续请求将被拒绝，现有会话也应失效。`) : `将撤销由 ${pendingAction.invitation.issuedBy} 创建的邀请。未使用的邀请链接将不能继续注册。`} pending={pending} onCancel={() => setPendingAction(null)} onConfirm={() => void confirmAction()} />}
  </section>
}

function Pagination({ page, size, total, pending, onPage }: { page: number; size: number; total: number; pending: boolean; onPage: (page: number) => void }) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  return <div className="flex items-center justify-between gap-3 border-t border-border px-5 py-3 text-xs text-muted-foreground"><span>第 {page + 1} / {totalPages} 页</span><div className="flex gap-2"><Button size="sm" variant="outline" disabled={page === 0 || pending} onClick={() => onPage(page - 1)}>上一页</Button><Button size="sm" variant="outline" disabled={page + 1 >= totalPages || pending} onClick={() => onPage(page + 1)}>下一页</Button></div></div>
}
