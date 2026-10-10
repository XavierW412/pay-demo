import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClientProvider } from '@tanstack/react-query'
import { AppRouter } from '@/app/router'
import { queryClient } from '@/app/query-client'
import '@/styles/index.css'
import { ApplicationErrorBoundary } from '@/shared/components/ApplicationErrorBoundary'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ApplicationErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AppRouter />
      </QueryClientProvider>
    </ApplicationErrorBoundary>
  </StrictMode>,
)
