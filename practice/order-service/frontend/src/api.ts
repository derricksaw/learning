export type Replica = 'a' | 'b'
export type Item = {
  productId: number
  name: string
  category: string
  available: number
  reorderPoint: number
  version: number
}
export type Order = {
  id: string
  productId: number
  productName: string
  quantity: number
  status: string
  createdAt: string
  hasInvoice: boolean
}
export type Invoice = {
  id: string
  orderId: string
  productName: string
  totalMinor: number
  currency: string
  hasDocument: boolean
}
export type Summary = { orders: number; units: number; lowStock: number; invoices: number }
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message)
  }
}
export async function request<T>(
  replica: Replica,
  path: string,
  method = 'GET',
  body?: unknown,
): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api/${replica}${path}`, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(20000),
    })
  } catch {
    throw new ApiError(
      0,
      'CONNECTION_ERROR',
      `Cannot reach instance ${replica.toUpperCase()}. Check that the workspace is running.`,
    )
  }
  const data = await response.json().catch(() => null)
  if (!response.ok)
    throw new ApiError(
      response.status,
      data?.code ?? 'REQUEST_FAILED',
      data?.message ?? 'The server could not complete this request.',
    )
  return data as T
}
export const shortId = (id: string) => id.slice(0, 8).toUpperCase()

export const sku = (id: number) => `OL-${String(id).padStart(4, '0')}`
