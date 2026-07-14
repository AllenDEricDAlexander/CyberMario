import {mkdir, open, readFile, rm, type FileHandle} from 'node:fs/promises'
import path from 'node:path'

export type LaneLock = {
    release: () => Promise<void>
}

export async function acquireLaneLock(directory: string, lane: string): Promise<LaneLock> {
    await mkdir(directory, {recursive: true})
    const lockPath = path.join(directory, `${lane}.lock`)
    let handle: FileHandle
    try {
        handle = await open(lockPath, 'wx')
    } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'EEXIST') {
            throw error
        }
        if (await hasLiveOwner(lockPath)) {
            throw new Error(`Auto lane ${lane} is already in use`)
        }
        await rm(lockPath, {force: true})
        try {
            handle = await open(lockPath, 'wx')
        } catch (retryError) {
            if ((retryError as NodeJS.ErrnoException).code === 'EEXIST') {
                throw new Error(`Auto lane ${lane} is already in use`)
            }
            throw retryError
        }
    }
    await handle.writeFile(String(process.pid))

    return {
        release: async () => {
            await handle.close()
            await rm(lockPath, {force: true})
        },
    }
}

async function hasLiveOwner(lockPath: string) {
    const pid = Number((await readFile(lockPath, 'utf8')).trim())
    if (!Number.isInteger(pid) || pid <= 0) {
        return false
    }
    try {
        process.kill(pid, 0)
        return true
    } catch (error) {
        return (error as NodeJS.ErrnoException).code !== 'ESRCH'
    }
}
