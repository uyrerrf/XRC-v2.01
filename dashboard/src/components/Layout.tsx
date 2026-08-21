// ============================================================
// FILE: XRC/dashboard/src/components/Layout.tsx
// ============================================================
import { Outlet, NavLink, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import ParticleBackground from './ParticleBackground'
import {
  LayoutDashboard,
  Smartphone,
  Terminal,
  Database,
  Send,
  Settings,
  LogOut,
  Radio
} from 'lucide-react'

const navItems = [
  { path: '/', label: 'Dashboard', icon: LayoutDashboard, exact: true },
  { path: '/devices', label: 'Devices', icon: Smartphone },
  { path: '/commands', label: 'Commands', icon: Send },
  { path: '/exfil', label: 'Exfiltrated', icon: Database },
  { path: '/terminal', label: 'Terminal', icon: Terminal },
  { path: '/settings', label: 'Settings', icon: Settings },
]

export default function Layout() {
  const { user, logout } = useAuthStore()
  const location = useLocation()

  return (
    <div className="min-h-screen bg-xrc-black flex">
      <ParticleBackground />

      {/* Sidebar */}
      <aside className="w-64 bg-xrc-dark/80 backdrop-blur-xl border-r border-xrc-border flex flex-col z-10">
        {/* Logo */}
        <div className="p-6 border-b border-xrc-border">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-xrc-cyan to-xrc-crimson flex items-center justify-center">
              <Radio className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="text-lg font-bold text-white mono">XRC</h1>
              <p className="text-xs text-xrc-text-muted">Red Cell Rat</p>
            </div>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => {
            const isActive = item.exact
              ? location.pathname === item.path
              : location.pathname.startsWith(item.path)
            return (
              <NavLink
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 ${
                  isActive
                    ? 'bg-xrc-cyan/10 text-xrc-cyan border border-xrc-cyan/20'
                    : 'text-xrc-text-muted hover:text-xrc-text hover:bg-xrc-card/50'
                }`}
              >
                <item.icon className="w-5 h-5" />
                {item.label}
              </NavLink>
            )
          })}
        </nav>

        {/* User info */}
        <div className="p-4 border-t border-xrc-border">
          <div className="flex items-center gap-3 px-4 py-3">
            <div className="w-8 h-8 rounded-full bg-xrc-cyan/20 flex items-center justify-center">
              <span className="text-xrc-cyan font-bold text-sm">
                {user?.username?.charAt(0).toUpperCase() || '?'}
              </span>
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm text-xrc-text truncate">{user?.username || 'Admin'}</p>
              <p className="text-xs text-xrc-text-muted">{user?.role || 'Operator'}</p>
            </div>
            <button
              onClick={logout}
              className="text-xrc-text-muted hover:text-xrc-crimson transition-colors"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto z-10">
        <div className="p-6 max-w-7xl mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
