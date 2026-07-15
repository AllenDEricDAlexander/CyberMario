import {Client, type ClientConfig} from 'pg'
import {createClient} from 'redis'
import type {AutoEnvironment} from './autoEnvironment'

export type AutoStateAdapter = {
    resetPostgres: () => Promise<void>
    dropPostgres: () => Promise<void>
    flushRedis: () => Promise<void>
}

export async function prepareAutoState(adapter: AutoStateAdapter) {
    await adapter.resetPostgres()
    await adapter.flushRedis()
}

export async function cleanupAutoState(adapter: AutoStateAdapter) {
    const results = await Promise.allSettled([
        adapter.dropPostgres(),
        adapter.flushRedis(),
    ])
    const failures = results
        .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
        .map((result) => result.reason)
    if (failures.length > 0) {
        throw new AggregateError(failures, 'Auto state cleanup failed')
    }
}

export function realAutoStateAdapter(environment: AutoEnvironment): AutoStateAdapter {
    return {
        resetPostgres: () => withPostgres(environment, async (client) => {
            const schema = quoteIdentifier(environment.postgres.schema)
            await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`)
            await client.query(`CREATE SCHEMA ${schema}`)
        }),
        dropPostgres: () => withPostgres(environment, async (client) => {
            const schema = quoteIdentifier(environment.postgres.schema)
            await client.query(`DROP SCHEMA IF EXISTS ${schema} CASCADE`)
        }),
        flushRedis: () => flushRedis(environment),
    }
}

async function withPostgres(
    environment: AutoEnvironment,
    action: (client: Client) => Promise<void>,
) {
    const client = new Client(postgresClientConfig(environment))
    await client.connect()
    try {
        const result = await client.query<{current_database: string}>('SELECT current_database()')
        if (result.rows[0]?.current_database !== environment.postgres.database) {
            throw new Error('Connected PostgreSQL database does not match the validated Auto target')
        }
        await action(client)
    } finally {
        await client.end()
    }
}

function postgresClientConfig(environment: AutoEnvironment): ClientConfig {
    const url = new URL(environment.postgres.jdbcUrl.substring('jdbc:'.length))
    return {
        host: url.hostname,
        port: Number(url.port || '5432'),
        database: environment.postgres.database,
        user: environment.postgres.username,
        password: environment.postgres.password,
        ssl: url.searchParams.get('sslmode') === 'require'
            ? {rejectUnauthorized: false}
            : undefined,
    }
}

async function flushRedis(environment: AutoEnvironment) {
    const client = createClient({
        socket: {
            host: environment.redis.host,
            port: environment.redis.port,
        },
        password: environment.redis.password || undefined,
        database: environment.redis.database,
    })
    client.on('error', () => undefined)
    await client.connect()
    try {
        await client.flushDb()
    } finally {
        await client.quit()
    }
}

function quoteIdentifier(identifier: string) {
    return `"${identifier.replaceAll('"', '""')}"`
}
