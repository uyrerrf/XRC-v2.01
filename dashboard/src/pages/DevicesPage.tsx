// ============================================================
// FILE: XRC/dashboard/src/pages/DevicesPage.tsx
// ============================================================
import { useEffect, useState } from 'react'
import { useDeviceStore } from '@/stores/deviceStore'
import DeviceCard from '@/components/DeviceCard'
import { Search, RefreshCw, Filter, Grid3X3, List } from 'lucide-react'
import { motion } from 'framer-motion'

export default function DevicesPage() {
  const { devices, isLoading, fetchDevices, totalCount, onlineCount } = useDeviceStore()
  const [search, setSearch] = useState('')
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid')
  const [filterOnline, setFilterOnline] = useState<boolean | null>(null)

  useEffect(() => {
    fetchDevices()
    const interval = setInterval(fetchDevices, 10000)
    return () => clearInterval(interval)
  }, [])

  const filtered = devices.filter((d) => {
    if (filterOnline !== null && d.is_online !== (filterOnline ? 1 : 0)) return false
    if (search) {
      const q = search.toLowerCase()
      return (
        d.device_id.toLowerCase().includes(q) ||
        (d.alias?.toLowerCase() || '').includes(q) ||
        (d.model?.toLowerCase() || '').includes(q) ||
        (d.manufacturer?.toLowerCase() || '').includes(q) ||
        (d.ip_address?.toLowerCase() || '').includes(q)
      )
    }
    return true
  })

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white mono">Devices</h1>
          <p className="text-xrc-text-muted text-sm mt-1">
            {totalCount} total · {onlineCount} online
          </p>
        </div>
        <button onClick={fetchDevices} className="btn-outline flex items-center gap-2" disabled={isLoading}>
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Toolbar */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-xrc-text-muted" />
          <input
            type="text"
            placeholder="Search devices..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="input-field pl-10 w-full"
          />
        </div>
        <div className="flex items-center gap-2 bg-xrc-dark rounded-lg border border-xrc-border p-1">
          <button
            onClick={() => setViewMode('grid')}
            className={`p-2 rounded ${viewMode === 'grid' ? 'bg-xrc-cyan/10 text-xrc-cyan' : 'text-xrc-text-muted hover:text-xrc-text'}`}
          >
            <Grid3X3 className="w-4 h-4" />
          </button>
          <button
            onClick={() => setViewMode('list')}
            className={`p-2 rounded ${viewMode === 'list' ? 'bg-xrc-cyan/10 text-xrc-cyan' : 'text-xrc-text-muted hover:text-xrc-text'}`}
          >
            <List className="w-4 h-4" />
          </button>
        </div>
        <div className="flex items-center gap-1">
          {[null, true, false].map((val) => (
            <button
              key={String(val)}
              onClick={() => setFilterOnline(val)}
              className={`px-3 py-1.5 text-xs rounded-lg border transition-colors ${
                filterOnline === val
                  ? 'bg-xrc-cyan/10 border-xrc-cyan/30 text-xrc-cyan'
                  : 'border-xrc-border text-xrc-text-muted hover:text-xrc-text'
              }`}
            >
              {val === null ? 'All' : val ? 'Online' : 'Offline'}
            </button>
          ))}
        </div>
      </div>

      {/* Device grid */}
      {filtered.length === 0 ? (
        <div className="glass-card p-12 text-center text-xrc-text-muted">
          <Smartphone className="w-12 h-12 mx-auto mb-3 opacity-30" />
          <p>No devices found</p>
        </div>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filtered.map((device) => (
            <DeviceCard key={device.device_id} device={device} />
          ))}
        </div>
      ) : (
        <div className="glass-card overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-xrc-dark/50 border-b border-xrc-border">
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Status</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Device</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Model</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Android</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">IP</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Battery</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-xrc-text-muted uppercase">Last Seen</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-xrc-border">
              {filtered.map((d) => (
                <tr
                  key={d.device_id}
                  className="hover:bg-xrc-card/50 cursor-pointer transition-colors"
                  onClick={() => {/* navigate to detail handled by Layout routing */}}
                >
                  <td className="px-4 py-3">
                    <div className={`w-2 h-2 rounded-full ${d.is_online ? 'status-online' : 'status-offline'}`} />
                  </td>
                  <td className="px-4 py-3 text-xrc-text mono">{d.alias || d.device_id.slice(0, 16)}</td>
                  <td className="px-4 py-3 text-xrc-text">{d.model || '-'}</td>
                  <td className="px-4 py-3 text-xrc-text">Android {d.android_version || '?'}</td>
                  <td className="px-4 py-3 text-xrc-text-muted">{d.ip_address || '-'}</td>
                  <td className="px-4 py-3 font-mono">{d.battery_level}%</td>
                  <td className="px-4 py-3 text-xrc-text-muted">
                    {d.is_online ? 'Online' : new Date(d.last_seen * 1000).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </motion.div>
  )
}
