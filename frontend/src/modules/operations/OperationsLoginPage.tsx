import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { LockKeyhole } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { loginOperator } from './api'

export function OperationsLoginPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setErrorMessage('')
    try {
      const session = await loginOperator(username, password)
      queryClient.setQueryData(['operator-session'], session)
      navigate('/operations/transactions', { replace: true })
    } catch {
      setErrorMessage('登录失败，请检查账号、密码或网络连接。')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="grid min-h-svh place-items-center bg-background px-4 py-10 text-foreground">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <span className="mx-auto grid size-12 place-items-center rounded-2xl bg-primary text-primary-foreground"><LockKeyhole className="size-5" aria-hidden="true" /></span>
          <p className="mt-4 text-sm text-muted-foreground">星辙支付服务</p>
          <h1 className="mt-2 text-2xl font-semibold">登录运营后台</h1>
          <p className="mt-2 text-sm text-muted-foreground">查看交易、支付核实和操作记录。</p>
        </div>
        <form className="space-y-5 rounded-2xl border border-border bg-card p-6 shadow-sm sm:p-8" onSubmit={(event) => void submit(event)}>
          <div className="space-y-2">
            <label className="block text-sm font-medium" htmlFor="username">账号</label>
            <input id="username" className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} required />
          </div>
          <div className="space-y-2">
            <label className="block text-sm font-medium" htmlFor="password">密码</label>
            <input id="password" type="password" className="h-11 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </div>
          {errorMessage && <p className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive" role="alert">{errorMessage}</p>}
          <Button className="h-11 w-full" type="submit" disabled={submitting}>{submitting ? '正在登录…' : '登录'}</Button>
        </form>
      </div>
    </main>
  )
}
