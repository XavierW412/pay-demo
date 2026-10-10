import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { AppRouter } from '@/app/router'
import { queryClient } from '@/app/query-client'
import '@/styles/index.css'
import { DiagnosticErrorBoundary } from '@/shared/diagnostics/DiagnosticErrorBoundary'
import { installGlobalErrorCapture } from '@/shared/diagnostics/client-diagnostics'

const disposeErrorCapture = installGlobalErrorCapture()
if (import.meta.hot) import.meta.hot.dispose(disposeErrorCapture)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DiagnosticErrorBoundary>
    <QueryClientProvider client={queryClient}>
      <AppRouter />
    </QueryClientProvider>
    </DiagnosticErrorBoundary>
  </StrictMode>,
)
