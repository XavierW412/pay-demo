import { Component } from 'react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'

export class ApplicationErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }

  static getDerivedStateFromError() { return { failed: true } }

  render() {
    if (!this.state.failed) return this.props.children
    return <main className="grid min-h-svh place-items-center bg-background px-4 py-10">
      <div className="w-full max-w-lg">
        <h1 className="text-base font-semibold">页面暂时无法显示</h1>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">请刷新页面后重试；如果问题仍然存在，请稍后再试。</p>
        <Button className="mt-4 min-h-10" onClick={() => window.location.reload()}>刷新页面</Button>
      </div>
    </main>
  }
}
