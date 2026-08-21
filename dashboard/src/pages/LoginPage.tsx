// ============================================================
// FILE: XRC/dashboard/src/pages/LoginPage.tsx
// ============================================================
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { Radio, Eye, EyeOff, AlertCircle } from 'lucide-react'
import toast from 'react-hot-toast'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPass, setShowPass] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuthStore()
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username || !password) {
      setError('Enter both username and password')
      return
    }
    setLoading(true)
    setError('')
    try {
      const success = await login(username, password)
      if (success) {
        toast.success('Connected to XRC C2')
        navigate('/')
      } else {
        setError('Invalid credentials')
      }
    } catch {
      setError('Connection failed')
    }
    setLoading(false)
  }

  return (
    <div className="min-h-screen bg-xrc-black flex items-center justify-center relative">
      {/* Particle canvas */}
      <canvas id="login-particles" className="fixed inset-0 pointer-events-none" />

      <div className="relative z-10 w-full max-w-md p-8">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-gradient-to-br from-xrc-cyan to-xrc-crimson mb-4 shadow-[0_0_30px_rgba(0,229,255,0.3)]">
            <Radio className="w-10 h-10 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white mono tracking-wider">XRC</h1>
          <p className="text-xrc-cyan text-sm mt-1 font-medium">Red Cell Rat C2</p>
        </div>

        {/* Login card */}
        <div className="glass-card p-8">
          <h2 className="text-lg font-semibold text-white mb-6">Operator Authentication</h2>

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label className="block text-xs text-xrc-text-muted mb-1.5 font-medium">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="input-field w-full"
                placeholder="Enter username"
                autoComplete="username"
                autoFocus
              />
            </div>

            <div>
              <label className="block text-xs text-xrc-text-muted mb-1.5 font-medium">Password</label>
              <div className="relative">
                <input
                  type={showPass ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field w-full pr-10"
                  placeholder="Enter password"
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  onClick={() => setShowPass(!showPass)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-xrc-text-muted hover:text-xrc-text"
                >
                  {showPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {error && (
              <div className="flex items-center gap-2 text-xrc-crimson text-xs bg-xrc-crimson/10 px-3 py-2 rounded-lg">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                {error}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="btn-cyan w-full py-3 disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {loading ? (
                <>
                  <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Connecting...
                </>
              ) : (
                'Authenticate'
              )}
            </button>
          </form>
        </div>

        <p className="text-center text-xs text-xrc-text-muted mt-6">
          XRC C2 v1.0 · Red Cell Rat
        </p>
      </div>

      {/* Particle login effect */}
      <script
        dangerouslySetInnerHTML={{
          __html: `
            document.addEventListener('DOMContentLoaded', function() {
              var c = document.getElementById('login-particles');
              var ctx = c.getContext('2d');
              c.width = window.innerWidth;
              c.height = window.innerHeight;
              var particles = [];
              for (var i = 0; i < 60; i++) {
                particles.push({
                  x: Math.random() * c.width,
                  y: Math.random() * c.height,
                  vx: (Math.random() - 0.5) * 0.3,
                  vy: (Math.random() - 0.5) * 0.3,
                  s: Math.random() * 2 + 1,
                  a: Math.random() * 0.3 + 0.1
                });
              }
              function draw() {
                ctx.clearRect(0, 0, c.width, c.height);
                particles.forEach(function(p, i) {
                  p.x += p.vx; p.y += p.vy;
                  if (p.x < 0) p.x = c.width; if (p.x > c.width) p.x = 0;
                  if (p.y < 0) p.y = c.height; if (p.y > c.height) p.y = 0;
                  ctx.beginPath(); ctx.arc(p.x, p.y, p.s, 0, Math.PI * 2);
                  ctx.fillStyle = 'rgba(0,229,255,' + p.a + ')'; ctx.fill();
                  for (var j = i + 1; j < particles.length; j++) {
                    var dx = p.x - particles[j].x, dy = p.y - particles[j].y;
                    var d = Math.sqrt(dx * dx + dy * dy);
                    if (d < 150) {
                      ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(particles[j].x, particles[j].y);
                      ctx.strokeStyle = 'rgba(0,229,255,' + (1 - d / 150) * 0.1 + ')';
                      ctx.lineWidth = 0.5; ctx.stroke();
                    }
                  }
                });
                requestAnimationFrame(draw);
              }
              draw();
              window.addEventListener('resize', function() {
                c.width = window.innerWidth; c.height = window.innerHeight;
              });
            });
          `
        }}
      />
    </div>
  )
}
