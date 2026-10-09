import { useEffect } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { CheckoutPage, CheckoutReturnPage } from '@/modules/checkout'
import { OperationsLayout } from '@/modules/operations/OperationsLayout'
import { OperationsLoginPage } from '@/modules/operations/OperationsLoginPage'
import { OperationsRegisterPage } from '@/modules/operations/OperationsRegisterPage'
import { OperationsTransactionListPage } from '@/modules/operations/OperationsTransactionListPage'
import { OperationsTransactionDetailPage } from '@/modules/operations/OperationsTransactionDetailPage'
import { OperationsAuditPage } from '@/modules/operations/OperationsAuditPage'
import { OperationsTeamPage } from '@/modules/operations/OperationsTeamPage'
import { OperationsReconciliationPage } from '@/modules/operations/OperationsReconciliationPage'
import { OperationsFinanceOverviewPage } from '@/modules/operations/OperationsFinanceOverviewPage'

function PageNotFound() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-3 bg-background px-6 text-center text-foreground">
      <p className="text-sm font-medium text-muted-foreground">页面不存在</p>
      <Link className="text-sm font-medium text-primary underline underline-offset-4" to="/operations">
        返回首页
      </Link>
    </main>
  )
}

function LegacyMockXingzheOrderReturnPage() {
  const { businessOrderId = '' } = useParams()
  const returnUrl = `${window.location.protocol}//${window.location.hostname}:5175/mock-xingzhe/order/${encodeURIComponent(businessOrderId)}${window.location.search}${window.location.hash}`

  useEffect(() => {
    window.location.replace(returnUrl)
  }, [returnUrl])

  return (
    <main className="grid min-h-svh place-items-center bg-background px-6 text-center text-foreground">
      <div>
        <p className="text-sm font-medium">正在返回星辙订单…</p>
        <a className="mt-3 inline-block text-sm text-primary underline underline-offset-4" href={returnUrl}>
          如果没有自动跳转，请点此返回订单
        </a>
      </div>
    </main>
  )
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/operations" replace />} />
        <Route path="/checkout/:opaqueToken/result" element={<CheckoutReturnPage />} />
        <Route path="/checkout/:opaqueToken" element={<CheckoutPage />} />
        {import.meta.env.DEV && <Route path="/mock-xingzhe/order/:businessOrderId" element={<LegacyMockXingzheOrderReturnPage />} />}
        <Route path="/operations/login" element={<OperationsLoginPage />} />
        <Route path="/operations/register/:token" element={<OperationsRegisterPage />} />
        <Route path="/operations" element={<OperationsLayout />}>
          <Route index element={<OperationsFinanceOverviewPage />} />
          <Route path="finance" element={<OperationsFinanceOverviewPage />} />
          <Route path="transactions" element={<OperationsTransactionListPage />} />
          <Route path="transactions/:paymentOrderId" element={<OperationsTransactionDetailPage />} />
          <Route path="audit" element={<OperationsAuditPage />} />
          <Route path="reconciliation" element={<OperationsReconciliationPage />} />
          <Route path="team" element={<OperationsTeamPage />} />
        </Route>
        <Route path="*" element={<PageNotFound />} />
      </Routes>
    </BrowserRouter>
  )
}
