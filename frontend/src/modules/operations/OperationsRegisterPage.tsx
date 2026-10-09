import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { registerOperator } from './api'

export function OperationsRegisterPage() {
  const { token = '' } = useParams()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (pending) return
    setError('')
    setPending(true)
    try {
      await registerOperator(token, username.trim(), password)
      navigate('/operations/login', { replace: true })
    } catch {
      setError('注册未完成。请核对账号、密码与邀请链接是否有效。')
    } finally {
      setPending(false)
    }
  }

  return <main className="grid min-h-svh place-items-center bg-background px-4 py-10"><div className="w-full max-w-md"><p className="text-sm font-medium text-primary">星辙支付 · 员工访问</p><h1 className="mt-3 text-3xl font-semibold">设置运营账号</h1><p className="mt-2 text-sm text-muted-foreground">请使用星辙员工管理员提供的一次性邀请链接。</p>
    <form className="mt-7 space-y-4 rounded-2xl border border-border bg-card p-6 shadow-sm" onSubmit={(event) => void submit(event)}><div><label className="block text-sm font-medium" htmlFor="register-username">账号</label><input id="register-username" className="mt-2 h-11 w-full rounded-lg border border-input bg-background px-3 text-sm" autoComplete="username" minLength={3} maxLength={80} required value={username} onChange={(event) => setUsername(event.target.value)} /></div><div><label className="block text-sm font-medium" htmlFor="register-password">密码</label><input id="register-password" type="password" className="mt-2 h-11 w-full rounded-lg border border-input bg-background px-3 text-sm" autoComplete="new-password" minLength={12} required value={password} onChange={(event) => setPassword(event.target.value)} /><p className="mt-1 text-xs text-muted-foreground">至少 12 位，请使用独立密码。</p></div>{error && <p className="text-sm text-destructive" role="alert">{error}</p>}<Button className="h-11 w-full" type="submit" disabled={pending || !token}>{pending ? '正在设置…' : '创建账号'}</Button></form><Link className="mt-5 inline-block text-sm text-primary hover:underline" to="/operations/login">已有账号？返回登录</Link>
  </div></main>
}
