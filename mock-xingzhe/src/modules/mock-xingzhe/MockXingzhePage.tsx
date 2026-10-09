import axios from 'axios'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type {
  MockBusinessOrder,
  MockBusinessOrderStatus,
  MockProduct,
  MockPaymentMode,
} from '@/shared/api/contracts'
import {
  createMockBusinessOrder,
  getMockBusinessOrderStatus,
  getMockProducts,
  initiateMockBusinessOrderPayment,
} from './api'
import './mock-xingzhe.css'

type PageState = 'loading' | 'ready' | 'load-error'
type OrderView =
  | { kind: 'checking'; order: MockBusinessOrder }
  | { kind: 'unconfirmed'; order: MockBusinessOrder; message: string }
  | { kind: 'confirmed'; order: MockBusinessOrder; status: MockBusinessOrderStatus }

const formatAmount = (amount: string, currency: string): string => {
  const symbol = currency === 'CNY' ? '¥' : '$'
  return `${symbol}${amount} ${currency}`
}

const isFinalStatus = (status: MockBusinessOrderStatus): boolean =>
  status.businessStatus === 'PAID' ||
  ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status.paymentStatus)

const resolveStatus = (status: MockBusinessOrderStatus): { label: string; tone: string; detail: string } => {
  if (status.businessStatus === 'PAID' && status.serviceStatus === 'ACTIVE') {
    return { label: '服务已开通', tone: 'success', detail: '付款已由服务端核实，示例服务已开通。' }
  }
  if (status.paymentStatus === 'REQUIRES_REVIEW') {
    return { label: '等待核实', tone: 'warning', detail: '支付结果正在进一步核实，请等待服务端更新。' }
  }
  if (status.paymentStatus === 'FAILED' || status.paymentStatus === 'CANCELLED') {
    return { label: status.paymentStatus === 'FAILED' ? '付款失败' : '付款已取消', tone: 'danger', detail: '服务尚未开通。你可以重新选择服务创建订单。' }
  }
  if (status.paymentStatus === 'SUCCEEDED' || status.businessStatus === 'PAID') {
    return { label: '付款处理中', tone: 'processing', detail: '付款已收到，正在等待模拟业务系统完成服务开通。' }
  }
  if (status.paymentStatus === 'PROCESSING') {
    return { label: '等待付款结果', tone: 'processing', detail: '付款正在处理中，请等待服务端确认结果。' }
  }
  if (status.paymentStatus === 'CREATING') {
    return { label: '正在准备付款', tone: 'processing', detail: '支付服务正在准备付款入口，请稍后查询。' }
  }
  return { label: '等待付款', tone: 'pending', detail: '订单已创建。完成付款后，本页会自动更新服务状态。' }
}

export const MockXingzhePage = () => {
  const [pageState, setPageState] = useState<PageState>('loading')
  const [products, setProducts] = useState<MockProduct[]>([])
  const [selectedCode, setSelectedCode] = useState('')
  const [paymentMode, setPaymentMode] = useState<MockPaymentMode>('AIRWALLEX_SANDBOX')
  const [loadError, setLoadError] = useState('')
  const [creating, setCreating] = useState(false)
  const [paymentPending, setPaymentPending] = useState(false)
  const [createError, setCreateError] = useState('')
  const [orderView, setOrderView] = useState<OrderView | null>(null)
  const [refreshing, setRefreshing] = useState(false)

  const fetchProducts = async () => {
    try {
      const items = await getMockProducts()
      setProducts(items)
      setSelectedCode((current) => items.some((item) => item.productCode === current)
        ? current
        : items[0]?.productCode ?? '')
      setPageState('ready')
    } catch {
      setLoadError('暂时无法读取示例服务，请检查本地服务后重试。')
      setPageState('load-error')
    }
  }

  const refreshOrder = async (order: MockBusinessOrder) => {
    setRefreshing(true)
    try {
      const status = await getMockBusinessOrderStatus(order.businessOrderId)
      setOrderView({ kind: 'confirmed', order, status })
    } catch {
      setOrderView({
        kind: 'unconfirmed',
        order,
        message: '目前无法连接本地服务确认订单。订单结果可能仍在处理中，请稍后重试。',
      })
    } finally {
      setRefreshing(false)
    }
  }

  const continuePayment = async (order: MockBusinessOrder) => {
    if (paymentPending) return
    setPaymentPending(true)
    try {
      const payment = await initiateMockBusinessOrderPayment(order.businessOrderId)
      if (payment.paymentMode === 'LOCAL_SIMULATOR') {
        const status = await getMockBusinessOrderStatus(order.businessOrderId)
        setOrderView({ kind: 'confirmed', order: payment, status })
        return
      }
      if (!['CREATING', 'AWAITING_PAYMENT'].includes(payment.paymentStatus)) {
        const status = await getMockBusinessOrderStatus(order.businessOrderId)
        setOrderView({ kind: 'confirmed', order: payment, status })
        return
      }
      setOrderView({ kind: 'checking', order: payment })
      if (!payment.checkoutUrl) throw new Error('付款入口缺失')
      const checkoutUrl = new URL(payment.checkoutUrl)
      if (!['http:', 'https:'].includes(checkoutUrl.protocol)) throw new Error('付款入口协议无效')
      window.location.assign(checkoutUrl.href)
    } catch {
      try {
        const status = await getMockBusinessOrderStatus(order.businessOrderId)
        setOrderView({ kind: 'confirmed', order, status })
      } catch {
        setOrderView({
          kind: 'unconfirmed',
          order,
          message: '付款入口暂时无法确认。订单编号已保留，可在订单页继续付款或稍后重试。',
        })
      }
    } finally {
      setPaymentPending(false)
    }
  }

  useEffect(() => {
    const timer = window.setTimeout(() => void fetchProducts(), 0)
    return () => window.clearTimeout(timer)
  }, [])

  useEffect(() => {
    if (!orderView || orderView.kind !== 'confirmed' || isFinalStatus(orderView.status)) return
    const timer = window.setTimeout(() => {
      void refreshOrder(orderView.order)
    }, 4000)
    return () => window.clearTimeout(timer)
  }, [orderView])

  const handleCreateOrder = async () => {
    if (!selectedCode || creating) return
    setCreating(true)
    setCreateError('')
    try {
      const order = await createMockBusinessOrder(selectedCode, paymentMode)
      setOrderView({ kind: 'checking', order })
      await continuePayment(order)
    } catch (error) {
      if (axios.isAxiosError<{ code?: string }>(error)
        && error.response?.data.code === 'PAYMENT_PROVIDER_NOT_CONFIGURED') {
        setCreateError('Airwallex 沙盒凭据尚未配置，请完成本机支付配置后重试。')
      } else {
        setCreateError('订单暂未创建成功，请检查本地服务后重试。')
      }
    } finally {
      setCreating(false)
    }
  }

  const selectedProduct = products.find((product) => product.productCode === selectedCode)
  const statusInfo = orderView?.kind === 'confirmed' ? resolveStatus(orderView.status) : null

  return (
    <main className="mock-xingzhe-page">
      <div className="mock-xingzhe-shell">
        <header className="mock-xingzhe-header">
          <a className="mock-xingzhe-brand" href="#main" aria-label="星辙示例服务首页">
            <span className="mock-xingzhe-mark" aria-hidden="true">星</span>
            <span>星辙 · 服务选购</span>
          </a>
          <span className="mock-xingzhe-env">本地开发环境</span>
          <Link className="mock-xingzhe-button secondary" to="/mock-xingzhe/orders">我的订单</Link>
        </header>

        <section className="mock-xingzhe-notice" role="note">
          <span className="mock-xingzhe-notice-icon" aria-hidden="true">!</span>
          <p><strong>本地开发模拟数据，不代表真实星辙业务</strong><br />以下服务与价格仅供本地支付链路联调使用。</p>
        </section>

        <div className="mock-xingzhe-heading" id="main">
          <p className="mock-xingzhe-eyebrow">服务目录</p>
          <h1>选择需要的示例服务</h1>
          <p>订单由本地模拟服务创建，金额以服务端目录为准。</p>
        </div>

        {pageState === 'loading' && (
          <div className="mock-xingzhe-state" role="status" aria-live="polite">
            <span className="mock-xingzhe-spinner" aria-hidden="true" />
            <span>正在读取示例服务…</span>
          </div>
        )}

        {pageState === 'load-error' && (
          <div className="mock-xingzhe-error" role="alert">
            <div><strong>服务目录暂不可用</strong><p>{loadError}</p></div>
            <button className="mock-xingzhe-button secondary" type="button" onClick={() => { setPageState('loading'); setLoadError(''); void fetchProducts() }}>重新加载</button>
          </div>
        )}

        {pageState === 'ready' && products.length === 0 && (
          <div className="mock-xingzhe-state">当前没有可选的示例服务。</div>
        )}

        {pageState === 'ready' && products.length > 0 && (
          <div className="mock-xingzhe-content">
            <section className="mock-xingzhe-products" aria-label="示例服务列表">
              {products.slice(0, 2).map((product) => (
                <label className={`mock-xingzhe-product ${selectedCode === product.productCode ? 'selected' : ''}`} key={product.productCode}>
                  <input
                    type="radio"
                    name="mock-product"
                    value={product.productCode}
                    checked={selectedCode === product.productCode}
                    onChange={() => setSelectedCode(product.productCode)}
                  />
                  <span className="mock-xingzhe-product-main">
                    <span className="mock-xingzhe-product-title">{product.displayName}</span>
                    <span className="mock-xingzhe-product-description">{product.description}</span>
                    <span className="mock-xingzhe-sample-tag">本地示例</span>
                  </span>
                  <span className="mock-xingzhe-price">{formatAmount(product.amount, product.currency)}</span>
                </label>
              ))}
              {products.length > 2 && <p className="mock-xingzhe-muted">仅显示前两条示例服务。</p>}
            </section>

            <aside className="mock-xingzhe-summary">
              <h2>订单摘要</h2>
              {selectedProduct ? (
                <>
                  <div className="mock-xingzhe-summary-line"><span>示例服务</span><strong>{selectedProduct.displayName}</strong></div>
                  <div className="mock-xingzhe-summary-line"><span>订单金额</span><strong className="mock-xingzhe-summary-price">{formatAmount(selectedProduct.amount, selectedProduct.currency)}</strong></div>
              <p className="mock-xingzhe-summary-note">提交后由本地服务创建订单。金额不会由浏览器修改。</p>
                </>
              ) : <p className="mock-xingzhe-muted">请选择一项服务。</p>}
              {createError && <p className="mock-xingzhe-inline-error" role="alert">{createError}</p>}
              <fieldset className="mock-xingzhe-payment-mode">
                <legend>开发付款方式</legend>
                <label><input type="radio" name="payment-mode" checked={paymentMode === 'AIRWALLEX_SANDBOX'} onChange={() => setPaymentMode('AIRWALLEX_SANDBOX')} />Airwallex 沙盒收银台</label>
                <label><input type="radio" name="payment-mode" checked={paymentMode === 'LOCAL_SIMULATOR'} onChange={() => setPaymentMode('LOCAL_SIMULATOR')} />本地模拟付款结果</label>
                {paymentMode === 'LOCAL_SIMULATOR' && <p>仅本地开发使用；不会调用 Airwallex，也不代表真实收款。</p>}
              </fieldset>
              <button className="mock-xingzhe-button primary" type="button" disabled={!selectedCode || creating} onClick={() => void handleCreateOrder()}>
                {creating ? '正在创建订单…' : '创建订单'}
                {!creating && <span aria-hidden="true">→</span>}
              </button>
              <p className="mock-xingzhe-secure-note">{paymentMode === 'LOCAL_SIMULATOR' ? '创建后可在本地订单页提交模拟结果' : '继续后将进入 Airwallex 托管收银台'}</p>
            </aside>
          </div>
        )}

        {orderView && (
          <section className="mock-xingzhe-order" aria-labelledby="order-heading" aria-live="polite">
            <div className="mock-xingzhe-order-heading">
              <div><p className="mock-xingzhe-eyebrow">订单状态</p><h2 id="order-heading">订单已创建</h2></div>
              {statusInfo && <span className={`mock-xingzhe-status ${statusInfo.tone}`}>{statusInfo.label}</span>}
              {orderView.kind === 'checking' && <span className="mock-xingzhe-status processing">正在查询</span>}
              {orderView.kind === 'unconfirmed' && <span className="mock-xingzhe-status warning">暂时无法确认</span>}
            </div>
            <dl className="mock-xingzhe-order-details">
              <div><dt>业务订单号</dt><dd>{orderView.order.businessOrderId}</dd></div>
              <div><dt>订单金额</dt><dd>{formatAmount(orderView.order.amount, orderView.order.currency)}</dd></div>
              <div><dt>支付状态</dt><dd>{statusInfo?.label ?? (orderView.kind === 'checking' ? '正在读取服务端状态' : '暂时无法确认')}</dd></div>
              {statusInfo && <div className="mock-xingzhe-status-detail"><dt>当前进度</dt><dd>{statusInfo.detail}</dd></div>}
              {orderView.kind === 'unconfirmed' && <div className="mock-xingzhe-status-detail"><dt>当前进度</dt><dd>{orderView.message}</dd></div>}
            </dl>
            {((orderView.kind === 'confirmed' && ['CREATING', 'AWAITING_PAYMENT'].includes(orderView.status.paymentStatus)) ||
              (orderView.kind === 'unconfirmed' && ['CREATING', 'AWAITING_PAYMENT'].includes(orderView.order.paymentStatus))) && (
              <button className="mock-xingzhe-button primary order-action" type="button" disabled={paymentPending} onClick={() => void continuePayment(orderView.order)}>
                {paymentPending ? '正在准备付款…' : '继续付款'}<span aria-hidden="true">→</span>
              </button>
            )}
            <Link className="mock-xingzhe-button secondary" to={`/mock-xingzhe/order/${encodeURIComponent(orderView.order.businessOrderId)}`}>打开订单页</Link>
            {(orderView.kind === 'unconfirmed' || orderView.kind === 'confirmed') && (
              <button className="mock-xingzhe-button secondary" type="button" disabled={refreshing} onClick={() => void refreshOrder(orderView.order)}>
                {refreshing ? '正在查询…' : '重新查询订单'}
              </button>
            )}
          </section>
        )}

        <footer className="mock-xingzhe-footer">本页面仅用于本地开发联调 · 服务状态以服务端核实结果为准</footer>
      </div>
    </main>
  )
}
