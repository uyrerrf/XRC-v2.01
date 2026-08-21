// ============================================================
// FILE: XRC/server/src/middleware/deviceAuth.js
// ============================================================
const { getDatabase } = require('../db/database');

/**
 * Authenticate device by device_id header.
 * Used for device-to-server communication (not dashboard).
 */
function authenticateDevice(req, res, next) {
    const deviceId = req.headers['x-device-id'];

    if (!deviceId) {
        return res.status(400).json({ error: 'X-Device-Id header required' });
    }

    const db = getDatabase();
    const device = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(deviceId);

    if (!device) {
        // Unknown device — still allow registration
        req.device = { device_id: deviceId, is_registered: false };
    } else {
        req.device = device;
    }

    next();
}

module.exports = { authenticateDevice };
