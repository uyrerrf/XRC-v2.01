// ============================================================
// FILE: XRC/dashboard/src/pages/SettingsPage.tsx
// ============================================================
import { useState } from 'react'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/authStore'
import { Settings, Key, AlertTriangle, RefreshCw, Shield, Users, Bell } from 'lucide-react'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'

export default function SettingsPage() {
  const { user } = useAuthStore()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [changingPass, setChangingPass] = useState(false)
  const [serverInfo, setServerInfo] = useState<any>(null)
  const [loadingInfo, setLoadingInfo] = useState(false)

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault()
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match')
      return
    }
    if (newPassword.length < 8) {
      toast.error('Password must be at least 8 characters')
      return
    }
    setChangingPass(true)
    try {
      await api.post('/auth/change-password', {
        currentPassword,
        newPassword,
      })
      toast.success('Password changed successfully')
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch {
      toast.error('Failed to change password')
    }
    setChangingPass(false)
  }

  const fetchServerInfo = async () => {
    setLoadingInfo(true)
    try {
      const res = await api.get('/dashboard/info')
      setServerInfo(res.data)
    } catch {
      toast.error('Failed to fetch server info')
    }
    setLoadingInfo(false)
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white mono">Settings</h1>
        <p className="text-xrc-text-muted text-sm mt-1">C2 configuration</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Change password */}
        <div className="glass-card p-6">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Key className="w-4 h-4 text-xrc-cyan" />
            Change Password
          </h3>
          <form onSubmit={handlePasswordChange} className="space-y-4">
            <div>
              <label className="block text-xs text-xrc-text-muted mb-1.5">Current Password</label>
              <input
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="input-field w-full"
                required
              />
            </div>
            <div>
              <label className="block text-xs text-xrc-text-muted mb-1.5">New Password</label>
              <input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="input-field w-full"
                required
                minLength={8}
              />
            </div>
            <div>
              <label className="block text-xs text-xrc-text-muted mb-1.5">Confirm New Password</label>
              <input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="input-field w-full"
                required
              />
            </div>
            <button
              type="submit"
              disabled={changingPass}
              className="btn-cyan w-full disabled:opacity-50"
            >
              {changingPass ? 'Changing...' : 'Update Password'}
            </button>
          </form>
        </div>

        {/* Account info */}
        <div className="glass-card p-6">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Users className="w-4 h-4 text-xrc-cyan" />
            Account
          </h3>
          <div className="space-y-3">
            <div className="flex justify-between py-2 border-b border-xrc-border">
              <span className="text-xrc-text-muted text-sm">Username</span>
              <span className="text-white text-sm mono">{user?.username || 'Unknown'}</span>
            </div>
            <div className="flex justify-between py-2 border-b border-xrc-border">
              <span className="text-xrc-text-muted text-sm">Role</span>
              <span className="text-xrc-cyan text-sm">{user?.role || 'Operator'}</span>
            </div>
            <div className="flex justify-between py-2 border-b border-xrc-border">
              <span className="text-xrc-text-muted text-sm">User ID</span>
              <span className="text-xrc-text-muted text-xs mono">{user?.id?.slice(0, 16) || '-'}</span>
            </div>
          </div>
        </div>

        {/* Server info */}
        <div className="glass-card p-6">
          <h3 className="text-sm font-semibold text-white mb-4 flex items-center gap-2">
            <Shield className="w-4 h-4 text-xrc-cyan" />
            Server Information
          </h3>
          {!serverInfo ? (
            <div className="text-center py-6">
              <button onClick={fetchServerInfo} className="btn-outline flex items-center gap-2 mx-auto" disabled={loadingInfo}>
                <RefreshCw className={`w-4 h-4 ${loadingInfo ? 'animate-spin' : ''}`} />
                Load Server Info
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {[
                { label: 'Version', value: serverInfo.version },
                { label: 'Uptime', value: serverInfo.uptime },
                { label: 'Node.js', value: serverInfo.nodeVersion },
                { label: 'Platform', value: serverInfo.platform },
                { label: 'Database', value: serverInfo.dbSize ? `${(serverInfo.dbSize / 1024 / 1024).toFixed(1)} MB` : 'N/A' },
                { label: 'Devices', value: serverInfo.deviceCount },
                { label: 'Commands', value: serverInfo.commandCount },
              ].map((item) => (
                <div key={item.label} className="flex justify-between py-1.5 border-b border-xrc-border last:border-0">
                  <span className="text-xrc-text-muted text-xs">{item.label}</span>
                  <span className="text-xrc-text text-xs mono">{item.value || '-'}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Danger zone */}
        <div className="glass-card p-6 border-xrc-crimson/30">
          <h3 className="text-sm font-semibold text-xrc-crimson mb-4 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" />
            Danger Zone
          </h3>
          <p className="text-xs text-xrc-text-muted mb-4">
            Destructive actions that cannot be undone.
          </p>
          <div className="space-y-3">
            <button
              onClick={async () => {
                if (window.confirm('Clear ALL command history? This cannot be undone.')) {
                  try {
                    await api.delete('/commands/all')
                    toast.success('Command history cleared')
                  } catch { toast.error('Failed') }
                }
              }}
              className="btn-outline w-full border-xrc-crimson text-xrc-crimson hover:bg-xrc-crimson/10"
            >
              Clear All Command History
            </button>
            <button
              onClick={async () => {
                if (window.confirm('Delete ALL exfiltrated data? This cannot be undone.')) {
                  try {
                    await api.delete('/exfil/all')
                    toast.success('Exfil data cleared')
                  } catch { toast.error('Failed') }
                }
              }}
              className="btn-outline w-full border-xrc-crimson text-xrc-crimson hover:bg-xrc-crimson/10"
            >
              Delete All Exfiltrated Data
            </button>
            <button
              onClick={async () => {
                if (window.confirm('Disconnect ALL devices from C2?')) {
                  try {
                    await api.post('/devices/disconnect-all')
                    toast.success('All devices disconnected')
                  } catch { toast.error('Failed') }
                }
              }}
              className="btn-crimson w-full"
            >
              Disconnect All Devices
            </button>
          </div>
        </div>
      </div>
    </motion.div>
  )
}
