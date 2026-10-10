import type { OperatorRole } from './api'

const paymentLabels: Record<string, string> = {
  CREATING: '创建中',
  AWAITING_PAYMENT: '等待付款',
  PROCESSING: '处理中',
  SUCCEEDED: '已支付',
  CANCELLED: '已取消',
  FAILED: '失败',
  REQUIRES_REVIEW: '待核实',
}

const notificationLabels: Record<string, string> = {
  NOT_REQUIRED: '尚无通知',
  PENDING: '待发送',
  RETRY: '重试中',
  DELIVERED: '已送达',
}

const auditActions: Record<string, string> = {
  REFRESH_PROVIDER_STATUS: '核实支付状态',
  REFRESH_REFUND_STATUS: '核实退款状态',
  RETRY_NOTIFICATION: '重试星辙通知',
  ADD_EXCEPTION_NOTE: '添加处理备注',
  INVITE_OPERATOR: '创建员工邀请',
  REVOKE_OPERATOR_INVITATION: '撤销员工邀请',
  DISABLE_OPERATOR: '停用员工账号',
  RESTORE_OPERATOR: '恢复员工账号',
  REGISTER_OPERATOR: '注册员工账号',
  CHECK_PAYMENT_RECONCILIATION: '执行支付核查',
  REFUND_CONFIRM_AND_SUBMIT: '确认并发起退款',
}

const auditOutcomes: Record<string, string> = {
  SUCCEEDED: '成功',
  FAILED: '失败',
  REQUIRES_REVIEW: '需核查',
  VERIFIED: '已核实',
  NOT_FOUND: '未找到',
  MISMATCH: '不匹配',
  PROVIDER_UNAVAILABLE: '服务商暂不可用',
  NOT_SUPPORTED: '不支持',
  BLOCKED: '已拦截',
  REJECTED: '已拒绝',
  CONFIRMED: '已确认',
}

export const paymentLabel = (status: string): string => paymentLabels[status] ?? status
export const notificationLabel = (status: string): string => notificationLabels[status] ?? status
export const auditActionLabel = (action: string): string => auditActions[action] ?? action
export const auditOutcomeLabel = (outcome: string): string => auditOutcomes[outcome] ?? outcome

const reconciliationOutcomes: Record<string, string> = {
  MATCH: '核查一致',
  DIFFERENCE: '发现差异',
  QUERY_FAILED: '查询失败',
  NOT_CHECKABLE: '暂无法核查',
}

const reconciliationIssues: Record<string, string> = {
  MISSING_PROVIDER_INTENT_ID: '缺少空中云汇支付单号',
  PROVIDER_QUERY_FAILED: '查询空中云汇失败',
  PROVIDER_INTENT_ID_MISMATCH: '支付单号不一致',
  MERCHANT_ORDER_ID_MISMATCH: '星辙订单号不一致',
  AMOUNT_MISMATCH: '金额不一致',
  CURRENCY_MISMATCH: '币种不一致',
  UNKNOWN_PROVIDER_STATUS: '空中云汇状态无法识别',
  PAYMENT_STATUS_MISMATCH: '支付状态不一致',
  PROVIDER_STATUS_MISMATCH: '原始状态不一致',
  NOTIFICATION_UNDELIVERED: '结果尚未送达星辙',
}

export const reconciliationOutcomeLabel = (outcome: string): string => reconciliationOutcomes[outcome] ?? outcome
export const reconciliationIssueLabel = (issue: string): string => reconciliationIssues[issue] ?? issue

const refundLabels: Record<string, string> = {
  NOT_REQUESTED: '未收到指令',
  SUBMITTING: '提交中',
  RECEIVED: '已受理',
  ACCEPTED: '处理中',
  SUCCEEDED: '退款成功',
  FAILED: '退款失败',
  NEEDS_REVIEW: '待人工核实',
  PENDING_OPERATOR_CONFIRMATION: '待支付侧管理员确认',
  BLOCKED: '核对异常，已拦截',
}

export const refundLabel = (status: string): string => refundLabels[status] ?? status

export const roleLabel = (role: OperatorRole): string => ({
  OWNER_READONLY: '项目所有者 · 只读',
  STAFF_ADMIN: '星辙员工管理员',
  STAFF_OPERATOR: '星辙运营员工',
})[role]

export const formatMoney = (amount: string, currency: string): string => {
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${currency} ${amount}`
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value)
}

export const formatTime = (value: string): string => new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
  hour12: false,
}).format(new Date(value))
