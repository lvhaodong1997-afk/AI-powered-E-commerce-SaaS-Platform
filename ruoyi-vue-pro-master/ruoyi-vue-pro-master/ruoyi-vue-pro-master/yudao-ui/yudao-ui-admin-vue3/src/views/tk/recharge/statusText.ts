export function paymentStatusText(status: string, english: boolean) {
  const values: Record<string, [string, string]> = {
    created: ['报价待恢复', 'Quote needs recovery'],
    quoted: ['待确认报价', 'Quote ready'],
    payment_pending: ['待支付确认', 'Payment pending'],
    paid: ['已支付', 'Paid'],
    canceled: ['已取消', 'Canceled'],
    refund_pending: ['退款处理中', 'Refund pending'],
    refunded: ['已退款', 'Refunded'],
    review: ['人工核查中', 'Under review']
  }
  return values[status]?.[english ? 1 : 0] ?? status
}
export function creditStatusText(status: string, english: boolean) {
  const values: Record<string, [string, string]> = {
    PENDING: ['积分待入账', 'Credits pending'],
    CREDITED: ['积分已入账', 'Credits allocated'],
    REFUND_PENDING: ['积分退款处理中', 'Credit refund pending'],
    REFUNDED: ['积分已退回', 'Credits refunded'],
    REVIEW: ['积分核查中', 'Credits under review']
  }
  return values[status]?.[english ? 1 : 0] ?? status
}
