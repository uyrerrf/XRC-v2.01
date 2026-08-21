// ============================================================
// FILE: XRC/dashboard/src/stores/commandStore.ts
// ============================================================
import { create } from 'zustand'
import api from '@/lib/api'

interface Command {
  id: string
  device_id: string
  action: string
  payload: string | null
  status: 'pending' | 'sent' | 'executing' | 'completed' | 'failed'
  priority: 'low' | 'normal' | 'high' | 'critical'
  created_at: number
  executed_at: number | null
  completed_at: number | null
  result: string | null
  error: string | null
}

interface CommandState {
  commands: Command[]
  isLoading: boolean
  totalCount: number
  fetchCommands: (deviceId?: string, limit?: number) => Promise<void>
  sendCommand: (deviceId: string, action: string, payload?: string, priority?: string) => Promise<void>
  cancelCommand: (commandId: string) => Promise<void>
  clearHistory: () => void
}

export const useCommandStore = create<CommandState>((set) => ({
  commands: [],
  isLoading: false,
  totalCount: 0,

  fetchCommands: async (deviceId?: string, limit = 100) => {
    set({ isLoading: true })
    try {
      const params: Record<string, string> = { limit: String(limit) }
      if (deviceId) params.device_id = deviceId
      const res = await api.get('/commands', { params })
      set({
        commands: res.data.commands,
        totalCount: res.data.totalCount,
        isLoading: false,
      })
    } catch {
      set({ isLoading: false })
    }
  },

  sendCommand: async (deviceId: string, action: string, payload?: string, priority = 'normal') => {
    const res = await api.post('/commands', { device_id: deviceId, action, payload, priority })
    set((state) => ({
      commands: [res.data.command, ...state.commands],
      totalCount: state.totalCount + 1,
    }))
  },

  cancelCommand: async (commandId: string) => {
    await api.delete(`/commands/${commandId}`)
    set((state) => ({
      commands: state.commands.filter((c) => c.id !== commandId),
      totalCount: state.totalCount - 1,
    }))
  },

  clearHistory: () => set({ commands: [], totalCount: 0 }),
}))
