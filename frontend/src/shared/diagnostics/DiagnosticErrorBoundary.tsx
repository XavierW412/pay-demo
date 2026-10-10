import { Component, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { captureClientError, subscribeDiagnostics } from './client-diagnostics'
import type { DiagnosticNotice } from './client-diagnostics'
import { DiagnosticErrorPanel } from './DiagnosticErrorPanel'
import { Button } from '@/components/ui/button'

export class DiagnosticErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean; notice?: DiagnosticNotice; diagnosticFinished?: boolean }> {
  state: { failed: boolean; notice?: DiagnosticNotice; diagnosticFinished?: boolean } = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  componentDidCatch(error: unknown) {
    try {
      void captureClientError(error, { category: 'PAGE_CRASH' })
        .then((notice) => { this.setState({ notice, diagnosticFinished: true }) })
        .catch(() => { this.setState({ diagnosticFinished: true }) })
    } catch {
      this.setState({ diagnosticFinished: true })
    }
  }
  render() {
    if (!this.state.failed) return this.props.children
    return <main className="grid min-h-svh place-items-center bg-background px-4 py-10">
      <div className="w-full max-w-lg">{this.state.notice /* eventId 在服务端回执更新时保持不变。 */
        ? <DiagnosticErrorPanel key={this.state.notice.eventId} notice={this.state.notice} />
        : <p className="text-sm text-foreground" role="status">{this.state.diagnosticFinished ? '页面暂时无法显示，未取得故障编号。' : '页面暂时无法显示，正在准备故障编号…'}</p>}
        <Button className="mt-4 min-h-10" onClick={() => window.location.reload()}>重新加载页面</Button>
      </div>
    </main>
  }
}

export const DiagnosticNoticeHost = () => {
  const [notice, setNotice] = useState<DiagnosticNotice>()
  useEffect(() => subscribeDiagnostics((incoming) => {
    setNotice((current) => {
      // 新事件可以替换当前面板；异步回执只能更新同一 eventId。
      // 关闭后 current 为空，因此旧事件的回执不能覆盖新事件或重新打开面板。
      if (incoming.state === 'sending' || incoming.state === 'server') return incoming
      return current?.eventId === incoming.eventId ? incoming : current
    })
  }), [])
  if (!notice) return null
  return <aside className="fixed right-3 bottom-3 z-50 max-h-[85svh] w-[calc(100%-1.5rem)] max-w-md overflow-y-auto" aria-label="操作故障反馈">
    <DiagnosticErrorPanel key={notice.eventId} notice={notice} onDismiss={() => setNotice(undefined)} />
  </aside>
}
