import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { MockXingzheOrderPage } from '@/modules/mock-xingzhe/MockXingzheOrderPage'
import { MockXingzhePage } from '@/modules/mock-xingzhe/MockXingzhePage'
import { MockXingzheOrdersPage } from '@/modules/mock-xingzhe/MockXingzheOrdersPage'

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/mock-xingzhe" replace />} />
        <Route path="/mock-xingzhe/order/:businessOrderId" element={<MockXingzheOrderPage />} />
        <Route path="/mock-xingzhe/orders" element={<MockXingzheOrdersPage />} />
        <Route path="/mock-xingzhe/*" element={<MockXingzhePage />} />
        <Route path="*" element={<Navigate to="/mock-xingzhe" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
