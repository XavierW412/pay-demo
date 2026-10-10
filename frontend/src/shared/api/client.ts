import axios from 'axios'
import type { AxiosError } from 'axios'
import { captureClientError, isDiagnosticEndpoint, showServerDiagnostic } from '@/shared/diagnostics/client-diagnostics'

export type DiagnosticApiError = AxiosError & { diagnosticId?: string; requestId?: string }

export const apiClient = axios.create({
  baseURL: '/',
  timeout: 10_000,
  withCredentials: true,
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.response.use((response) => response, (error: unknown) => {
  if (axios.isAxiosError(error)) {
    if (isDiagnosticEndpoint(error.config?.url)) return Promise.reject(error)
    const diagnosticError = error as DiagnosticApiError
    const body: unknown = error.response?.data
    const validId = (value: unknown): value is string => typeof value === 'string'
      && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
    const requestId: unknown = error.response?.headers['x-request-id']
    if (validId(requestId)) diagnosticError.requestId = requestId
    if (body && typeof body === 'object' && 'diagnosticId' in body && validId(body.diagnosticId)) {
      diagnosticError.diagnosticId = body.diagnosticId
    }
    const context = { requestId: diagnosticError.requestId, relatedDiagnosticId: diagnosticError.diagnosticId }
    if (error.response && error.response.status >= 400 && error.response.status < 500) {
      // 参数与业务拒绝仅展示现有服务端编号，不生成系统故障事件。
      showServerDiagnostic(context)
    } else {
      void captureClientError(error, { ...context, category: error.response ? 'API_ERROR' : 'NETWORK_ERROR' })
    }
  }
  return Promise.reject(error)
})
