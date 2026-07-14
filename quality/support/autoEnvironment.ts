import {randomUUID} from 'node:crypto'

const AUTO_DATABASE = /(^|[_-])auto([_-]|$)/i
const AUTO_SCHEMA = /^auto_[a-z0-9_]+$/

export type AutoEnvironment = {
    runId: string
    ci: boolean
    backendPort: number
    frontendPort: number
    postgres: {
        jdbcUrl: string
        database: string
        username: string
        password: string
        schema: string
    }
    redis: {
        host: string
        port: number
        password: string
        database: number
    }
    jwtSecret: string
    adminPassword: string
}

export function loadAutoEnvironment(source: NodeJS.ProcessEnv = process.env): AutoEnvironment {
    if (source.AUTO_CLEANUP_ALLOWED !== 'true') {
        throw new Error('Auto runner requires AUTO_CLEANUP_ALLOWED=true')
    }

    const jdbcUrl = required(source, 'AUTO_DB_URL')
    if (!jdbcUrl.startsWith('jdbc:postgresql://')) {
        throw new Error('AUTO_DB_URL must be a PostgreSQL JDBC URL')
    }
    let databaseUrl: URL
    try {
        databaseUrl = new URL(jdbcUrl.substring('jdbc:'.length))
    } catch (error) {
        throw new Error('AUTO_DB_URL must be a valid PostgreSQL JDBC URL', {cause: error})
    }
    const database = databaseUrl.pathname.replace(/^\//, '')
    if (!AUTO_DATABASE.test(database)) {
        throw new Error('AUTO_DB_URL must target a dedicated Auto database')
    }

    const schema = required(source, 'AUTO_DB_SCHEMA').toLowerCase()
    if (!AUTO_SCHEMA.test(schema)) {
        throw new Error('AUTO_DB_SCHEMA must be an auto_* schema')
    }

    const redisDatabase = integer(source.AUTO_REDIS_DATABASE, 'AUTO_REDIS_DATABASE')
    if (redisDatabase <= 1) {
        throw new Error('AUTO_REDIS_DATABASE must use a dedicated Auto Redis database')
    }

    const jwtSecret = required(source, 'AUTO_JWT_SECRET')
    if (jwtSecret.length < 32) {
        throw new Error('AUTO_JWT_SECRET must contain at least 32 characters')
    }

    return {
        runId: source.QUALITY_RUN_ID || createRunId(),
        ci: source.CI === 'true',
        backendPort: integer(source.AUTO_BACKEND_PORT || '28081', 'AUTO_BACKEND_PORT'),
        frontendPort: integer(source.AUTO_FRONTEND_PORT || '5174', 'AUTO_FRONTEND_PORT'),
        postgres: {
            jdbcUrl,
            database,
            username: required(source, 'AUTO_DB_USERNAME'),
            password: required(source, 'AUTO_DB_PASSWORD'),
            schema,
        },
        redis: {
            host: required(source, 'AUTO_REDIS_HOST'),
            port: integer(source.AUTO_REDIS_PORT || '6379', 'AUTO_REDIS_PORT'),
            password: source.AUTO_REDIS_PASSWORD || '',
            database: redisDatabase,
        },
        jwtSecret,
        adminPassword: required(source, 'AUTO_ADMIN_PASSWORD'),
    }
}

export function safeEnvironmentSummary(environment: AutoEnvironment) {
    return {
        runId: environment.runId,
        ci: environment.ci,
        backendPort: environment.backendPort,
        frontendPort: environment.frontendPort,
        postgres: {
            database: environment.postgres.database,
            schema: environment.postgres.schema,
        },
        redis: {
            host: environment.redis.host,
            port: environment.redis.port,
            database: environment.redis.database,
        },
    }
}

function required(source: NodeJS.ProcessEnv, key: string) {
    const value = source[key]?.trim()
    if (!value) {
        throw new Error(`Missing required environment variable: ${key}`)
    }
    return value
}

function integer(value: string | undefined, key: string) {
    const parsed = Number(value)
    if (!Number.isInteger(parsed) || parsed <= 0) {
        throw new Error(`${key} must be a positive integer`)
    }
    return parsed
}

function createRunId() {
    return `quality-${Date.now().toString(36)}-${randomUUID().slice(0, 8)}`
}
