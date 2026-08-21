// ============================================================
// FILE: XRC/dashboard/src/stores/exfilStore.ts
// ============================================================
import { create } from 'zustand'
import api from '@/lib/api'

interface ExfilItem {
  id: string
  device_id: string
  type: string
  data: string | null
  file_path: string | null
  file_size: number | null
  checksum: string | null
  captured_at: number
  received_at: number
  tags: string
}

interface ExfilState {
  items: ExfilItem[]
  isLoading: boolean
  totalCount: number
  totalSize: number
  fetchExfil: (deviceId?: string, type?: string) => Promise<void>
  downloadExfil: (itemId: string) => Promise<void>
  deleteExfil: (itemId: string) => Promise<void>
}

export const useExfilStore = create<ExfilState>((set) => ({
  items: [],
  isLoading: false,
  totalCount: 0,
  totalSize: 0,

  fetchExfil: async (deviceId?: string, type?: string) => {
    set({ isLoading: true })
    try {
      const params: Record<string, string> = {}
      if (deviceId) params.device_id = deviceId
      if (type) params.type = type
      const res = await api.get('/exfil', { params })
      set({
        items: res.data.items,
        totalCount: res.data.totalCount,
        totalSize: res.data.totalSize,
        isLoading: false,
      })
    } catch {
      set({ isLoading: false })
    }
  },

  downloadExfil: async (itemId: string) => {
    const res = await api.get(`/exfil/${itemId}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `xrc-exfil-${itemId}`
    a.click()
    URL.revokeObjectURL(url)
  },

  deleteExfil: async (itemId: string) => {
    await api.delete(`/exfil/${itemId}`)
    set((state) => ({
      items: state.items.filter((i) => i.id !== itemId),
      totalCount: state.totalCount - 1,
    }))
  },
}))
