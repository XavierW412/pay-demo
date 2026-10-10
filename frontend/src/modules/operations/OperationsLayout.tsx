import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import { ArrowRight, ClipboardList, FileClock, LayoutDashboard, LogOut, RefreshCw, ScanSearch, ShieldCheck, Users } from 'lucide-react'
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { getCurrentOperator, logoutOperator } from './api'
import { roleLabel } from './format'

export function OperationsLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)
  const refreshInProgress = useRef(false)
  const sessionQuery = useQuery({ queryKey: ['operator-session'], queryFn: getCurrentOperator, retry: false })
  const session = sessionQuery.data
  const isTransactionDetail = /^\/operations\/transactions\/[^/]+$/.test(location.pathname)
  const refreshIntervalMs = isTransactionDetail ? 3_000 : 10_000

  const refreshActiveQueries = useCallback(async (manual = false) => {
    if (document.visibilityState !== 'visible' || refreshInProgress.current) return
    if (!manual && queryClient.isFetching({
      type: 'active',
      predicate: query => query.queryKey[0] !== 'operator-session',
    }) > 0) return

    refreshInProgress.current = true
    if (manual) setIsRefreshing(true)
    try {
      await queryClient.refetchQueries({
        type: 'active',
        predicate: query => query.queryKey[0] !== 'operator-session',
      })
      setLastUpdatedAt(new Date())
    } finally {
      refreshInProgress.current = false
      if (manual) setIsRefreshing(false)
    }
  }, [queryClient])

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refreshActiveQueries()
    }, refreshIntervalMs)
    return () => window.clearInterval(timer)
  }, [refreshActiveQueries, refreshIntervalMs])

  const logout = async () => {
    try {
      await logoutOperator()
    } finally {
      queryClient.removeQueries({ queryKey: ['operator-session'] })
      navigate('/operations/login', { replace: true })
    }
  }

  if (sessionQuery.isPending) {
    return <main className="grid min-h-svh place-items-center bg-background text-sm text-muted-foreground" role="status">正在读取运营账号…</main>
  }
  if (sessionQuery.isError || !session) {
    return <Navigate to="/operations/login" replace />
  }

  const navigation = [
    { to: '/operations', label: '运营工作台', icon: LayoutDashboard },
    { to: '/operations/transactions', label: '交易', icon: ClipboardList },
    { to: '/operations/reconciliation', label: '对账与异常', icon: ScanSearch },
    { to: '/operations/audit', label: '操作记录', icon: FileClock },
    ...(session.role === 'STAFF_ADMIN' ? [{ to: '/operations/team', label: '员工与权限', icon: Users }] : []),
  ]

  return (
    <div className="min-h-svh bg-background text-foreground lg:grid lg:grid-cols-[240px_minmax(0,1fr)]">
      <aside className="border-b border-border bg-[#15364c] px-5 py-5 text-white lg:flex lg:min-h-svh lg:flex-col lg:border-b-0 lg:px-6 lg:py-8">
        <div className="flex items-center justify-between gap-4">
          <Link className="flex items-center gap-3" to="/operations">
            <span className="grid size-9 place-items-center rounded-xl bg-white/15 text-lg font-semibold">星</span>
            <span><strong className="block text-sm font-semibold">星辙支付</strong><small className="text-xs text-white/65">运营中心</small></span>
          </Link>
          <span className="rounded-full border border-white/25 px-2 py-1 text-[11px] text-white/75 lg:hidden">{session.role === 'OWNER_READONLY' ? '只读' : session.role === 'TEMP_TESTER' ? '临时测试' : '员工'}</span>
        </div>
        <nav className="mt-6 flex gap-2 overflow-x-auto lg:mt-12 lg:flex-col" aria-label="运营导航">
          {navigation.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/operations'} className={({ isActive }) => `inline-flex min-h-11 shrink-0 items-center gap-3 rounded-lg px-3 text-sm transition-colors ${isActive ? 'bg-white/15 font-semibold text-white' : 'text-white/70 hover:bg-white/10 hover:text-white'}`}>
              <Icon className="size-4" aria-hidden="true" />{label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-6 hidden border-t border-white/15 pt-5 lg:mt-auto lg:block">
          <p className="text-xs text-white/60">当前账号</p>
          <p className="mt-1 text-sm font-medium">{session.username}</p>
          <p className="mt-1 text-xs text-white/60">{roleLabel(session.role)}</p>
          <button className="mt-4 inline-flex min-h-10 items-center gap-2 text-sm text-white/75 hover:text-white" type="button" onClick={() => void logout()}><LogOut className="size-4" aria-hidden="true" />退出登录</button>
        </div>
      </aside>

      <div className="min-w-0">
        <header className="flex min-h-16 items-center justify-between gap-4 border-b border-border bg-card px-5 sm:px-8">
          <span className="inline-flex items-center gap-2 text-xs text-muted-foreground"><ShieldCheck className="size-4" aria-hidden="true" />支付运营 · 安全工作区</span>
          <div className="flex items-center gap-3">
            <span className="hidden text-xs text-muted-foreground md:inline" aria-live="polite">
              自动每{isTransactionDetail ? '3' : '10'}秒更新{lastUpdatedAt ? ` · 最近 ${lastUpdatedAt.toLocaleTimeString('zh-CN', { hour12: false })}` : ''}
            </span>
            <Button variant="outline" size="sm" onClick={() => void refreshActiveQueries(true)} disabled={isRefreshing}>
              <RefreshCw className={`size-4 ${isRefreshing ? 'animate-spin' : ''}`} aria-hidden="true" />
              <span className="hidden sm:inline">{isRefreshing ? '刷新中' : '刷新'}</span>
            </Button>
            <span className="hidden text-xs text-muted-foreground sm:inline">{roleLabel(session.role)}</span>
            <Button className="lg:hidden" variant="ghost" size="sm" onClick={() => void logout()}><LogOut className="size-4" aria-hidden="true" />退出</Button>
          </div>
        </header>
        <div className="mx-auto max-w-7xl px-5 py-8 sm:px-8 sm:py-10">
          <Outlet context={session} />
          <footer className="mt-12 border-t border-border pt-5 text-xs text-muted-foreground">
            支付状态、星辙通知、退款执行与资金结算分别记录。结算报告尚未接入。
            <Link className="ml-2 inline-flex items-center gap-1 text-primary underline-offset-4 hover:underline" to="/operations/transactions">查询交易<ArrowRight className="size-3" aria-hidden="true" /></Link>
          </footer>
        </div>
      </div>
    </div>
  )
}
