import {mkdir, rm} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'
import {loadAutoEnvironment, safeEnvironmentSummary} from '../support/autoEnvironment'
import {cleanupAutoState, prepareAutoState, realAutoStateAdapter} from '../support/autoState'
import {acquireLaneLock} from '../support/laneLock'

const qualityRoot = fileURLToPath(new URL('..', import.meta.url))

export function playwrightArguments(arguments_: string[]) {
    const [suite, ...flags] = arguments_
    if (suite !== 'auth') {
        throw new Error('Only the auth suite is available in the initial quality gate')
    }
    const hasFileFilter = flags.some((argument) => argument.startsWith('tests/'))
    return ['test', ...(hasFileFilter ? [] : ['tests/auth']), ...flags]
}

export async function run(arguments_: string[] = process.argv.slice(2)) {
    const environment = loadAutoEnvironment()
    const lane = `${environment.postgres.schema}-redis-${environment.redis.database}`
    const lock = await acquireLaneLock(path.join(qualityRoot, '.runtime'), lane)
    const adapter = realAutoStateAdapter(environment)
    let child: ReturnType<typeof Bun.spawn> | undefined
    let result = 1
    let interrupted = false

    const interrupt = () => {
        interrupted = true
        child?.kill()
    }
    process.once('SIGINT', interrupt)
    process.once('SIGTERM', interrupt)

    try {
        await prepareOutputDirectories()
        console.info('Auto environment:', safeEnvironmentSummary(environment))
        await prepareAutoState(adapter)

        child = Bun.spawn(
            ['bunx', 'playwright', ...playwrightArguments(arguments_)],
            {
                cwd: qualityRoot,
                env: {
                    ...process.env,
                    QUALITY_RUN_ID: environment.runId,
                    AUTO_DB_URL: environment.postgres.jdbcUrl,
                    AUTO_DB_USERNAME: environment.postgres.username,
                    AUTO_DB_PASSWORD: environment.postgres.password,
                    AUTO_DB_SCHEMA: environment.postgres.schema,
                    AUTO_REDIS_HOST: environment.redis.host,
                    AUTO_REDIS_PORT: String(environment.redis.port),
                    AUTO_REDIS_PASSWORD: environment.redis.password,
                    AUTO_REDIS_DATABASE: String(environment.redis.database),
                    AUTO_JWT_SECRET: environment.jwtSecret,
                    AUTO_ADMIN_PASSWORD: environment.adminPassword,
                    AUTO_BACKEND_PORT: String(environment.backendPort),
                    AUTO_FRONTEND_PORT: String(environment.frontendPort),
                },
                stdout: 'inherit',
                stderr: 'inherit',
            },
        )
        result = await child.exited
        if (interrupted && result === 0) {
            result = 130
        }
    } catch (error) {
        console.error('Quality runner failed:', error)
        result = 1
    } finally {
        try {
            await cleanupAutoState(adapter)
        } catch (cleanupError) {
            console.error('Quality cleanup failed:', cleanupError)
            result = 1
        }
        await lock.release()
        process.removeListener('SIGINT', interrupt)
        process.removeListener('SIGTERM', interrupt)
    }

    return result
}

async function prepareOutputDirectories() {
    await Promise.all([
        rm(path.join(qualityRoot, 'artifacts'), {recursive: true, force: true}),
        rm(path.join(qualityRoot, 'playwright-report'), {recursive: true, force: true}),
        rm(path.join(qualityRoot, 'test-results'), {recursive: true, force: true}),
    ])
    await mkdir(path.join(qualityRoot, 'artifacts', 'process-logs'), {recursive: true})
}

if (import.meta.main) {
    process.exitCode = await run()
}
