import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { ShoppingBag, Box, ClipboardList, Layers3, X } from 'lucide-react'
import { sku } from '../api'
import type { Item } from '../api'

export function Badge({ children, tone = 'green' }: { children: ReactNode; tone?: string }) {
  return <span className={`badge ${tone}`}>{children}</span>
}
export function Product({
  item,
  compact = false,
}: {
  item: Pick<Item, 'productId' | 'name'>
  compact?: boolean
}) {
  const icons = [ShoppingBag, Box, ClipboardList, Layers3]
  const Icon = icons[(item.productId - 1) % icons.length]
  return (
    <div className="product">
      <span className={`product-art art-${item.productId % 4} ${compact ? 'compact' : ''}`}>
        <Icon size={compact ? 19 : 23} strokeWidth={1.5} />
      </span>
      <div>
        <strong>{item.name}</strong>
        <small>{sku(item.productId)}</small>
      </div>
    </div>
  )
}
export function Dialog({
  title,
  onClose,
  children,
}: {
  title: string
  onClose: () => void
  children: ReactNode
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const el = ref.current!
    el.showModal()
    return () => el.close()
  }, [])
  return (
    <dialog
      ref={ref}
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="dialog-heading">
        <div>
          <p className="eyebrow">WORKSPACE ACTION</p>
          <h2>{title}</h2>
        </div>
        <button className="icon-button" onClick={onClose} aria-label="Close dialog">
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  )
}
