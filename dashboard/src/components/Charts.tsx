// ============================================================
// FILE: XRC/dashboard/src/components/Charts.tsx
// ============================================================
import {
  LineChart, Line, AreaChart, Area, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts'

const tooltipStyle = {
  contentStyle: {
    background: '#131B2E',
    border: '1px solid #1E293B',
    borderRadius: '8px',
    fontSize: '12px',
    fontFamily: 'JetBrains Mono',
  },
  labelStyle: { color: '#94A3B8' },
}

const CustomTooltip = ({ active, payload, label }: any) => {
  if (!active || !payload) return null
  return (
    <div style={tooltipStyle.contentStyle}>
      <p style={{ color: '#E2E8F0', margin: 0 }}>{label}</p>
      {payload.map((p: any, i: number) => (
        <p key={i} style={{ color: p.color, margin: '4px 0' }}>
          {p.name}: {p.value}
        </p>
      ))}
    </div>
  )
}

export function OnlineTimeline({ data }: { data: { time: string; online: number; offline: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={200}>
      <AreaChart data={data}>
        <defs>
          <linearGradient id="onlineGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#00E676" stopOpacity={0.3}/>
            <stop offset="95%" stopColor="#00E676" stopOpacity={0}/>
          </linearGradient>
          <linearGradient id="offlineGrad" x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor="#FF1744" stopOpacity={0.3}/>
            <stop offset="95%" stopColor="#FF1744" stopOpacity={0}/>
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
        <XAxis dataKey="time" stroke="#475569" tick={{ fontSize: 11 }} />
        <YAxis stroke="#475569" tick={{ fontSize: 11 }} />
        <Tooltip content={<CustomTooltip />} />
        <Area type="monotone" dataKey="online" stroke="#00E676" fill="url(#onlineGrad)" strokeWidth={2} />
        <Area type="monotone" dataKey="offline" stroke="#FF1744" fill="url(#offlineGrad)" strokeWidth={2} />
      </AreaChart>
    </ResponsiveContainer>
  )
}

export function CommandActivity({ data }: { data: { date: string; commands: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
        <XAxis dataKey="date" stroke="#475569" tick={{ fontSize: 11 }} />
        <YAxis stroke="#475569" tick={{ fontSize: 11 }} />
        <Tooltip content={<CustomTooltip />} />
        <Bar dataKey="commands" fill="#00E5FF" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}

export function ExfilBreakdown({ data }: { data: { name: string; value: number; color: string }[] }) {
  return (
    <ResponsiveContainer width="100%" height={200}>
      <PieChart>
        <Pie
          data={data}
          cx="50%"
          cy="50%"
          innerRadius={50}
          outerRadius={80}
          paddingAngle={4}
          dataKey="value"
          stroke="none"
        >
          {data.map((entry, index) => (
            <Cell key={index} fill={entry.color} />
          ))}
        </Pie>
        <Tooltip content={<CustomTooltip />} />
      </PieChart>
    </ResponsiveContainer>
  )
}

export function DeviceTimeline({ data }: { data: { time: string; count: number }[] }) {
  return (
    <ResponsiveContainer width="100%" height={200}>
      <LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1E293B" />
        <XAxis dataKey="time" stroke="#475569" tick={{ fontSize: 11 }} />
        <YAxis stroke="#475569" tick={{ fontSize: 11 }} />
        <Tooltip content={<CustomTooltip />} />
        <Line type="monotone" dataKey="count" stroke="#00E5FF" strokeWidth={2} dot={{ fill: '#00E5FF' }} />
      </LineChart>
    </ResponsiveContainer>
  )
}
