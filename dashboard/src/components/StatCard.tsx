// ============================================================
// FILE: XRC/dashboard/src/components/StatCard.tsx
// ============================================================
import { motion } from 'framer-motion'
import { ReactNode } from 'react'

interface StatCardProps {
  title: string
  value: string | number
  icon: ReactNode
  color: 'cyan' | 'crimson' | 'mint' | 'orange'
  subtitle?: string
  trend?: 'up' | 'down' | 'neutral'
  onClick?: () => void
}

const colorMap = {
  cyan: { bg: 'bg-xrc-cyan/10', border: 'border-xrc-cyan/20', text: 'text-xrc-cyan', glow: 'rgba(0,229,255,0.3)' },
  crimson: { bg: 'bg-xrc-crimson/10', border: 'border-xrc-crimson/20', text: 'text-xrc-crimson', glow: 'rgba(255,23,68,0.3)' },
  mint: { bg: 'bg-xrc-mint/10', border: 'border-xrc-mint/20', text: 'text-xrc-mint', glow: 'rgba(0,230,118,0.3)' },
  orange: { bg: 'bg-xrc-orange/10', border: 'border-xrc-orange/20', text: 'text-xrc-orange', glow: 'rgba(255,145,0,0.3)' },
}

export default function StatCard({ title, value, icon, color, subtitle, trend, onClick }: StatCardProps) {
  const c = colorMap[color]

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className="glass-card p-5 cursor-pointer hover:border-xrc-cyan/40 transition-all duration-300"
      onClick={onClick}
      whileHover={{ scale: 1.02 }}
    >
      <div className="flex items-start justify-between mb-3">
        <div className={`p-2.5 rounded-lg ${c.bg} ${c.text}`}>
          {icon}
        </div>
        {trend && (
          <span className={`text-xs font-medium ${trend === 'up' ? 'text-xrc-mint' : trend === 'down' ? 'text-xrc-crimson' : 'text-xrc-text-muted'}`}>
            {trend === 'up' ? '↑' : trend === 'down' ? '↓' : '→'}
          </span>
        )}
      </div>
      <p className="text-2xl font-bold text-white mono">{value}</p>
      <p className="text-xs text-xrc-text-muted mt-1">{title}</p>
      {subtitle && <p className="text-xs text-xrc-text-muted mt-0.5">{subtitle}</p>}
    </motion.div>
  )
}
