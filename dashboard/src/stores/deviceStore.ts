// ============================================================
// FILE: XRC/dashboard/src/stores/deviceStore.ts
// ============================================================
import { create } from 'zustand'
import api from '@/lib/api'

interface Device {
  id: string
  device_id: string
  alias: string | null
  model: string
  manufacturer: string
  android_version: string
  sdk_version: number
  first_seen: number
  last_seen: number
  ip_address: string
  country: string | null
  network_type: string
  battery_level: number
  is_online: number
  tags: string
  notes: string | null
  group_name: string
}

interface DeviceState {
  devices: Device[]
  totalCount: number
  onlineCount: number
  isLoading: boolean
  selectedDevice: Device | null
  fetchDevices: () => Promise<void>
  fetchDevice: (deviceId: string) => Promise<Device | null>
  updateDevice: (deviceId: string, data: Partial<Device>) => Promise<void>
  deleteDevice: (deviceId: string) => Promise<void>
  setSelectedDevice: (device: Device | null) => void
}

export const useDeviceStore = create<DeviceState>((set) => ({
  devices: [],
  totalCount: 0,
  onlineCount: 0,
  isLoading: false,
  selectedDevice: null,

  fetchDevices: async () => {
    set({ isLoading: true })
    try {
      const res = await api.get('/devices')
      set({
        devices: res.data.devices,
        totalCount: res.data.totalCount,
        onlineCount: res.data.onlineCount,
        isLoading: false,
      })
    } catch {
      set({ isLoading: false })
    }
  },

  fetchDevice: async (deviceId: string) => {
    try {
      const res = await api.get(`/devices/${deviceId}`)
      set({ selectedDevice: res.data.device })
      return res.data.device
    } catch {
      return null
    }
  },

  updateDevice: async (deviceId: string, data: Partial<Device>) => {
    await api.put(`/devices/${deviceId}`, data)
    set((state) => ({
      devices: state.devices.map((d) =>
        d.device_id === deviceId ? { ...d, ...data } : d
      ),
    }))
  },

  deleteDevice: async (deviceId: string) => {
    await api.delete(`/devices/${deviceId}`)
    set((state) => ({
      devices: state.devices.filter((d) => d.device_id !== deviceId),
      totalCount: state.totalCount - 1,
    }))
  },

  setSelectedDevice: (device) => set({ selectedDevice: device }),
}))
