// ============================================================
// FILE: XRC/server/src/routes/commands.js
// ============================================================
const express = require('express');
const { getDatabase } = require('../db/database');

const router = express.Router();

/**
 * GET /api/commands
 * List all commands with optional filters.
 */
router.get('/', (req, res) => {
    try {
        const db = getDatabase();
        const { device_id, status, action, limit, offset } = req.query;

        let query = `
            SELECT c.*, d.alias as device_alias, d.model as device_model
            FROM commands c
            LEFT JOIN devices d ON c.device_id = d.device_id
            WHERE 1=1
        `;
        const params = [];

        if (device_id) { query += ' AND c.device_id = ?'; params.push(device_id); }
        if (status) { query += ' AND c.status = ?'; params.push(status); }
        if (action) { query += ' AND c.action LIKE ?'; params.push(`%${action}%`); }

        query += ' ORDER BY c.created_at DESC';

        const countQuery = query.replace('SELECT c.*, d.alias as device_alias, d.model as device_model', 'SELECT COUNT(*) as count');
        const total = db.prepare(countQuery).get(...params);

        const pageLimit = parseInt(limit) || 50;
        const pageOffset = parseInt(offset) || 0;
        query += ` LIMIT ${pageLimit} OFFSET ${pageOffset}`;

        const commands = db.prepare(query).all(...params);

        res.json({
            commands,
            total: total.count,
            limit: pageLimit,
            offset: pageOffset
        });
    } catch (err) {
        console.error('List commands error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/commands/:commandId
 * Get command details.
 */
router.get('/:commandId', (req, res) => {
    try {
        const db = getDatabase();
        const command = db.prepare(`
            SELECT c.*, d.alias as device_alias, d.model
            FROM commands c
            LEFT JOIN devices d ON c.device_id = d.device_id
            WHERE c.id = ?
        `).get(req.params.commandId);

        if (!command) {
            return res.status(404).json({ error: 'Command not found' });
        }

        res.json(command);
    } catch (err) {
        console.error('Get command error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * POST /api/commands/batch
 * Send multiple commands (broadcast to devices).
 */
router.post('/batch', (req, res) => {
    try {
        const db = getDatabase();
        const { device_ids, action, payload, priority } = req.body;

        if (!device_ids || !Array.isArray(device_ids) || device_ids.length === 0) {
            return res.status(400).json({ error: 'device_ids array required' });
        }
        if (!action) {
            return res.status(400).json({ error: 'Action required' });
        }

        const { v4: uuidv4 } = require('uuid');
        const { sendToDevice } = require('../ws/websocket');
        const created = [];
        const now = Math.floor(Date.now() / 1000);

        const insert = db.prepare(`
            INSERT INTO commands (id, device_id, action, payload, status, priority, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `);

        for (const deviceId of device_ids) {
            const command = {
                id: uuidv4(),
                device_id: deviceId,
                action,
                payload: payload || '',
                status: 'pending',
                priority: priority || 'normal',
                created_at: now
            };

            insert.run(command.id, command.device_id, command.action, command.payload,
                command.status, command.priority, command.created_at);

            sendToDevice(command.device_id, {
                type: 'cmd',
                id: command.id,
                device_id: command.device_id,
                ts: command.created_at * 1000,
                payload: JSON.stringify(command)
            });

            created.push(command);
        }

        res.status(201).json({ count: created.length, commands: created });
    } catch (err) {
        console.error('Batch command error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * DELETE /api/commands/:commandId
 * Cancel a pending command.
 */
router.delete('/:commandId', (req, res) => {
    try {
        const db = getDatabase();
        const command = db.prepare('SELECT * FROM commands WHERE id = ?').get(req.params.commandId);

        if (!command) {
            return res.status(404).json({ error: 'Command not found' });
        }

        if (command.status !== 'pending') {
            return res.status(400).json({ error: 'Can only cancel pending commands' });
        }

        db.prepare('UPDATE commands SET status = ? WHERE id = ?').run('cancelled', req.params.commandId);
        res.json({ message: 'Command cancelled' });
    } catch (err) {
        console.error('Cancel command error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
