// ============================================================
// FILE: XRC/dashboard/src/components/DeviceCard.tsx
// ============================================================
import { motion } from 'framer-motion'
import { Smartphone, Battery, Wifi, MapPin, Monitor } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

interface DeviceCardProps {
  device: {
    device_id: string
    alias: string | null
    model: string
    manufacturer: string
    android_version: string
    battery_level: number
    is_online: number
    ip_address: string
    country: string | null
    network_type: string
    last_seen: number
  }
}

export default function DeviceCard({ device }: DeviceCardProps) {
  const navigate = useNavigate()
  const isOnline = device.is_online === 1
  const lastSeen = new Date(device.last_seen * 1000)
  const timeAgo = Math.floor((Date.now() - lastSeen.getTime()) / 1000)

  const getTimeAgo = () => {
    if (timeAgo < 60) return `${timeAgo}s ago`
    if (timeAgo < 3600) return `${Math.floor(timeAgo / 60)}m ago`
    if (timeAgo < 86400) return `${Math.floor(timeAgo / 3600)}h ago`
    return `${Math.floor(timeAgo / 86400)}d ago`
  }

  const getBatteryColor = () => {
    if (device.battery_level > 60) return 'text-xrc-mint'
    if (device.battery_level > 20) return 'text-xrc-orange'
    return 'text-xrc-crimson'
  }

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.95 }}
      animate={{ opacity: 1, scale: 1 }}
      className={`glass-card p-5 cursor-pointer transition-all duration-300 ${
        isOnline ? 'border-xrc-mint/20 hover:border-xrc-mint/40' : 'hover:border-xrc-border/60'
      }`}
      onClick={() => navigate(`/devices/${device.device_id}`)}
      whileHover={{ scale: 1.02, y: -2 }}
    >
      {/* Header */}
      <div className="flex items-center gap-3 mb-4">
        <div className={`w-3 h-3 rounded-full ${isOnline ? 'status-online' : 'status-offline'}`} />
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-white truncate mono">
            {device.alias || device.device_id.slice(0, 16)}
          </p>
          <p className="text-xs text-xrc-text-muted truncate">{device.model || 'Unknown Model'}</p>
        </div>
        <div className="flex items-center gap-1">
          <Battery className={`w-4 h-4 ${getBatteryColor()}`} />
          <span className={`text-xs font-mono ${getBatteryColor()}`}>{device.battery_level}%</span>
        </div>
      </div>

      {/* Details grid */}
      <div className="grid grid-cols-2 gap-3 text-xs text-xrc-text-muted">
        <div className="flex items-center gap-1.5">
          <Smartphone className="w-3.5 h-3.5" />
          <span>{device.manufacturer || 'Unknown'}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Monitor className="w-3.5 h-3.5" />
          <span>Android {device.android_version || '?'}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Wifi className="w-3.5 h-3.5" />
          <span>{device.network_type || 'N/A'}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <MapPin className="w-3.5 h-3.5" />
          <span>{device.country || device.ip_address || 'Unknown'}</span>
        </div>
      </div>

      {/* Footer */}
      <div className="mt-4 pt-3 border-t border-xrc-border flex justify-between items-center">
        <span className={`text-xs ${isOnline ? 'text-xrc-mint' : 'text-xrc-text-muted'}`}>
          {isOnline ? 'Online now' : getTimeAgo()}
        </span>
        <span className="text-xs text-xrc-text-muted">{device.ip_address || 'No IP'}</span>
      </div>
    </motion.div>
  )
}
