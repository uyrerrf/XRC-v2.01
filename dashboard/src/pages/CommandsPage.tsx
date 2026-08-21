// ============================================================
// FILE: XRC/dashboard/src/pages/CommandsPage.tsx
// ============================================================
import { useEffect } from 'react'
import { useCommandStore } from '@/stores/commandStore'
import DataTable from '@/components/DataTable'
import { RefreshCw, Clock, Send, Filter } from 'lucide-react'
import { motion } from 'framer-motion'

export default function CommandsPage() {
  const { commands, isLoading, fetchCommands, totalCount } = useCommandStore()

  useEffect(() => {
    fetchCommands()
    const interval = setInterval(() => fetchCommands(), 10000)
    return () => clearInterval(interval)
  }, [])

  const columns = [
    { key: 'created_at', label: 'Time', sortable: true,
      render: (c: any) => (
        <span className="text-xs text-xrc-text-muted mono">{new Date(c.created_at * 1000).toLocaleString()}</span>
      )
    },
    { key: 'device_id', label: 'Device ID', sortable: true,
      render: (c: any) => <span className="mono text-xs">{c.device_id.slice(0, 16)}</span>
    },
    { key: 'action', label: 'Action', sortable: true,
      render: (c: any) => <span className="font-medium text-xrc-text">{c.action}</span>
    },
    { key: 'status', label: 'Status', sortable: true,
      render: (c: any) => {
        const colors: Record<string, string> = {
          pending: 'bg-xrc-orange/10 text-xrc-orange',
          sent: 'bg-xrc-cyan/10 text-xrc-cyan',
          executing: 'bg-xrc-cyan/10 text-xrc-cyan',
          completed: 'bg-xrc-mint/10 text-xrc-mint',
          failed: 'bg-xrc-crimson/10 text-xrc-crimson',
        }
        return <span className={`px-2 py-0.5 text-xs rounded-full ${colors[c.status] || 'bg-xrc-text-muted/10 text-xrc-text-muted'}`}>{c.status}</span>
      }
    },
    { key: 'priority', label: 'Priority', sortable: true,
      render: (c: any) => {
        const colors: Record<string, string> = {
          low: 'text-xrc-text-muted',
          normal: 'text-xrc-text',
          high: 'text-xrc-orange',
          critical: 'text-xrc-crimson font-bold',
        }
        return <span className={`text-xs ${colors[c.priority]}`}>{c.priority}</span>
      }
    },
    { key: 'result', label: 'Result',
      render: (c: any) => c.result ? <span className="text-xs text-xrc-text-muted mono">{String(c.result).slice(0, 50)}</span> : <span className="text-xs text-xrc-text-muted">-</span>
    },
    { key: 'error', label: 'Error',
      render: (c: any) => c.error ? <span className="text-xs text-xrc-crimson">{String(c.error).slice(0, 40)}</span> : '-'
    },
  ]

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white mono">Commands</h1>
          <p className="text-xrc-text-muted text-sm mt-1">{totalCount} total</p>
        </div>
        <button onClick={() => fetchCommands()} className="btn-outline flex items-center gap-2" disabled={isLoading}>
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      <div className="glass-card p-5">
        <DataTable
          columns={columns}
          data={commands}
          searchable
          searchKeys={['device_id', 'action', 'status', 'result']}
          pageSize={25}
          emptyMessage="No commands have been issued yet"
        />
      </div>
    </motion.div>
  )
}
