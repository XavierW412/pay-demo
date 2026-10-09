import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'

interface ConfirmActionDialogProps {
  title: string
  description: string
  pending: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function ConfirmActionDialog({ title, description, pending, onCancel, onConfirm }: ConfirmActionDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialogRef.current?.showModal() }, [])
  return (
    <dialog ref={dialogRef} className="m-auto w-[min(92vw,430px)] rounded-2xl border border-border bg-card p-6 text-foreground shadow-xl backdrop:bg-slate-950/50" onCancel={(event) => { event.preventDefault(); if (!pending) onCancel() }}>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-3 text-sm leading-6 text-muted-foreground">{description}</p>
      <div className="mt-6 flex justify-end gap-2"><Button type="button" variant="outline" disabled={pending} onClick={onCancel}>取消</Button><Button type="button" disabled={pending} onClick={onConfirm}>{pending ? '处理中…' : '确认'}</Button></div>
    </dialog>
  )
}
