// ============================================================
// FILE: XRC/server/knexfile.js
// ============================================================
module.exports = {
    development: {
        client: 'better-sqlite3',
        connection: {
            filename: './data/xrc.db'
        },
        useNullAsDefault: true,
        migrations: {
            directory: './src/db/migrations'
        },
        seeds: {
            directory: './src/db/seeds'
        }
    },
    production: {
        client: 'better-sqlite3',
        connection: {
            filename: './data/xrc.db'
        },
        useNullAsDefault: true,
        migrations: {
            directory: './src/db/migrations'
        }
    }
};
