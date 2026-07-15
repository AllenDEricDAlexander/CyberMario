import {mkdir} from 'node:fs/promises'
import path from 'node:path'
import {fileURLToPath} from 'node:url'

type ServerTarget = 'backend' | 'frontend'

type ServerDefinition = {
    command: string[]
    cwd: string
}

const qualityRoot = fileURLToPath(new URL('..', import.meta.url))

export function serverDefinition(
    target: ServerTarget,
    root = qualityRoot,
    environment: NodeJS.ProcessEnv = process.env,
): ServerDefinition {
    if (target === 'backend') {
        return {
            command: ['./mvnw', '-Dmaven.build.cache.enabled=false', 'spring-boot:run'],
            cwd: path.resolve(root, '../be'),
        }
    }

    const frontendPort = environment.AUTO_FRONTEND_PORT || '5174'
    return {
        command: [
            'bun',
            'run',
            'dev',
            '--',
            '--host',
            '127.0.0.1',
            '--port',
            frontendPort,
            '--strictPort',
        ],
        cwd: path.resolve(root, '../fe'),
    }
}

export function serverEnvironment(
    target: ServerTarget,
    environment: NodeJS.ProcessEnv = process.env,
) {
    const defined = Object.fromEntries(
        Object.entries(environment)
            .filter((entry): entry is [string, string] => entry[1] !== undefined),
    )
    if (target === 'backend') {
        return defined
    }
    const allowed = new Set([
        'PATH',
        'HOME',
        'CI',
        'NODE_ENV',
        'TMPDIR',
        'TMP',
        'TEMP',
        'AUTO_FRONTEND_PORT',
        'VITE_BACKEND_TARGET',
    ])
    return Object.fromEntries(
        Object.entries(defined).filter(([key]) => allowed.has(key) || key.startsWith('VITE_')),
    )
}

async function start(target: ServerTarget) {
    const definition = serverDefinition(target)
    const logDirectory = path.join(qualityRoot, 'artifacts', 'process-logs')
    await mkdir(logDirectory, {recursive: true})
    const writer = Bun.file(path.join(logDirectory, `${target}.log`)).writer()
    const child = Bun.spawn(definition.command, {
        cwd: definition.cwd,
        env: serverEnvironment(target),
        stdout: 'pipe',
        stderr: 'pipe',
    })

    const forward = async (
        stream: ReadableStream<Uint8Array>,
        output: NodeJS.WriteStream,
    ) => {
        const reader = stream.getReader()
        while (true) {
            const {done, value} = await reader.read()
            if (done) break
            writer.write(value)
            output.write(value)
        }
    }

    const stop = () => child.kill()
    process.once('SIGINT', stop)
    process.once('SIGTERM', stop)
    await Promise.all([
        forward(child.stdout, process.stdout),
        forward(child.stderr, process.stderr),
        child.exited,
    ])
    await writer.end()
    process.removeListener('SIGINT', stop)
    process.removeListener('SIGTERM', stop)
    return child.exitCode
}

if (import.meta.main) {
    const target = process.argv[2]
    if (target !== 'backend' && target !== 'frontend') {
        throw new Error('Server target must be backend or frontend')
    }
    process.exitCode = (await start(target)) ?? 1
}
