import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { CheckoutPage, CheckoutReturnPage } from '@/modules/checkout'
import { MockXingzhePage } from '@/modules/mock-xingzhe/MockXingzhePage'

function PageNotFound() {
  return (
    <main className="flex min-h-svh flex-col items-center justify-center gap-3 bg-background px-6 text-center text-foreground">
      <p className="text-sm font-medium text-muted-foreground">页面不存在</p>
      <Link className="text-sm font-medium text-primary underline underline-offset-4" to="/mock-xingzhe">
        返回本地模拟星辙
      </Link>
    </main>
  )
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/mock-xingzhe" replace />} />
        <Route path="/mock-xingzhe/*" element={<MockXingzhePage />} />
        <Route path="/checkout/return" element={<CheckoutReturnPage />} />
        <Route path="/checkout/:opaqueToken" element={<CheckoutPage />} />
        <Route path="*" element={<PageNotFound />} />
      </Routes>
    </BrowserRouter>
  )
}
