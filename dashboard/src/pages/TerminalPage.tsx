// ============================================================
// FILE: XRC/dashboard/src/pages/TerminalPage.tsx
// ============================================================
import { useState, useRef, useEffect } from 'react'
import api from '@/lib/api'
import { Terminal, Send, Trash2 } from 'lucide-react'
import { motion } from 'framer-motion'
import toast from 'react-hot-toast'

interface LogEntry {
  id: string
  timestamp: string
  level: string
  source: string
  message: string
}

export default function TerminalPage() {
  const [input, setInput] = useState('')
  const [output, setOutput] = useState<string[]>([])
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [deviceId, setDeviceId] = useState('')
  const [connected, setConnected] = useState(false)
  const [ws, setWs] = useState<WebSocket | null>(null)
  const outputRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  // Fetch recent logs
  useEffect(() => {
    const fetchLogs = async () => {
      try {
        const res = await api.get('/dashboard/logs?limit=50')
        setLogs(res.data.logs || [])
      } catch {}
    }
    fetchLogs()
    const interval = setInterval(fetchLogs, 5000)
    return () => clearInterval(interval)
  }, [])

  // Auto-scroll
  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight
    }
  }, [output])

  const connectWebSocket = () => {
    if (!deviceId) {
      toast.error('Enter a device ID')
      return
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/ws/terminal?device_id=${deviceId}&token=${localStorage.getItem('xrc_token')}`
    
    const socket = new WebSocket(wsUrl)
    socket.onopen = () => {
      setConnected(true)
      setOutput(prev => [...prev, `[XRC] Connected to device ${deviceId.slice(0, 16)}...`])
      toast.success('Terminal connected')
    }
    socket.onmessage = (e) => {
      setOutput(prev => [...prev, `[Device] ${e.data}`])
    }
    socket.onerror = () => {
      setOutput(prev => [...prev, '[XRC] WebSocket error'])
    }
    socket.onclose = () => {
      setConnected(false)
      setOutput(prev => [...prev, '[XRC] Disconnected'])
    }
    setWs(socket)
  }

  const sendCommand = () => {
    if (!input.trim() || !ws || !connected) return
    ws.send(JSON.stringify({ action: 'shell', payload: input.trim() }))
    setOutput(prev => [...prev, `> ${input.trim()}`])
    setInput('')
  }

  const disconnect = () => {
    ws?.close()
    setWs(null)
    setConnected(false)
  }

  const clearOutput = () => setOutput([])

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') sendCommand()
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-white mono">Terminal</h1>
        <p className="text-xrc-text-muted text-sm mt-1">Interactive device shell</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Terminal output */}
        <div className="lg:col-span-2 glass-card p-0 overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 bg-xrc-dark/50 border-b border-xrc-border">
            <div className="flex items-center gap-2">
              <Terminal className="w-4 h-4 text-xrc-cyan" />
              <span className="text-sm text-white mono">xrc-terminal</span>
              {connected && <span className="status-dot status-online" />}
            </div>
            <div className="flex items-center gap-2">
              <button onClick={clearOutput} className="text-xrc-text-muted hover:text-xrc-text p-1" title="Clear">
                <Trash2 className="w-4 h-4" />
              </button>
              {connected && (
                <button onClick={disconnect} className="text-xs text-xrc-crimson hover:text-red-400">Disconnect</button>
              )}
            </div>
          </div>
          <div
            ref={outputRef}
            className="p-4 h-[400px] overflow-y-auto bg-black/40"
            style={{ fontFamily: "'JetBrains Mono', monospace", fontSize: '13px', lineHeight: '1.6' }}
          >
            {output.length === 0 ? (
              <div className="text-xrc-text-muted text-center pt-20">
                <p>Connect to a device to begin</p>
                <p className="text-xs mt-2">Enter a device ID and click Connect</p>
              </div>
            ) : (
              output.map((line, i) => (
                <div key={i} className="text-xrc-text">
                  {line.startsWith('> ') ? (
                    <span><span className="text-xrc-cyan">$</span> <span className="text-white">{line.slice(2)}</span></span>
                  ) : line.startsWith('[XRC]') ? (
                    <span className="text-xrc-cyan">{line}</span>
                  ) : line.startsWith('[Device]') ? (
                    <span className="text-xrc-mint">{line}</span>
                  ) : (
                    <span className="text-xrc-text">{line}</span>
                  )}
                </div>
              ))
            )}
            <div id="term-cursor" className="inline-block w-2 h-4 bg-xrc-cyan animate-pulse ml-1" />
          </div>
          <div className="flex items-center gap-2 px-4 py-3 bg-xrc-dark/50 border-t border-xrc-border">
            <span className="text-xrc-cyan text-sm mono">$</span>
            <input
              ref={inputRef}
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={connected ? 'Enter shell command...' : 'Connect to a device first'}
              className="flex-1 bg-transparent text-white text-sm mono outline-none placeholder-xrc-text-muted"
              disabled={!connected}
            />
            <button
              onClick={sendCommand}
              disabled={!connected || !input.trim()}
              className="text-xrc-cyan hover:text-cyan-300 disabled:opacity-30 disabled:cursor-not-allowed"
            >
              <Send className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Side panel */}
        <div className="space-y-4">
          {/* Connection */}
          <div className="glass-card p-4">
            <h3 className="text-sm font-semibold text-white mb-3">Connection</h3>
            <div className="space-y-3">
              <input
                type="text"
                value={deviceId}
                onChange={(e) => setDeviceId(e.target.value)}
                className="input-field w-full"
                placeholder="Device ID"
              />
              {!connected ? (
                <button onClick={connectWebSocket} className="btn-cyan w-full" disabled={!deviceId}>
                  Connect
                </button>
              ) : (
                <button onClick={disconnect} className="btn-crimson w-full">
                  Disconnect
                </button>
              )}
            </div>
          </div>

          {/* Recent logs */}
          <div className="glass-card p-4">
            <h3 className="text-sm font-semibold text-white mb-3">Recent Logs</h3>
            <div className="space-y-2 max-h-64 overflow-y-auto pr-1">
              {logs.length === 0 ? (
                <p className="text-xs text-xrc-text-muted text-center py-4">No logs yet</p>
              ) : (
                logs.slice(0, 20).map((log) => (
                  <div key={log.id} className="text-xs border-l-2 border-xrc-cyan/30 pl-2 py-1">
                    <span className="text-xrc-text-muted">{new Date(parseInt(log.timestamp) * 1000).toLocaleTimeString()}</span>
                    <span className={`ml-2 ${
                      log.level === 'error' ? 'text-xrc-crimson' :
                      log.level === 'warn' ? 'text-xrc-orange' :
                      'text-xrc-text-muted'
                    }`}>
                      [{log.level?.toUpperCase()}]
                    </span>
                    <span className="text-xrc-text ml-1">{log.message}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </motion.div>
  )
}
