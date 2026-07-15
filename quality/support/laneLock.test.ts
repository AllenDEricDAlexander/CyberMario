import {expect, test} from 'bun:test'
import {mkdtemp, rm, writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import path from 'node:path'
import {acquireLaneLock} from './laneLock'

test('rejects a second owner of the same Auto lane', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'cybermario-quality-'))
    const first = await acquireLaneLock(directory, 'auto_local-redis-15')

    try {
        await expect(acquireLaneLock(directory, 'auto_local-redis-15'))
            .rejects.toThrow('already in use')
    } finally {
        await first.release()
        await rm(directory, {recursive: true, force: true})
    }
})

test('reclaims a lock left by a dead process', async () => {
    const directory = await mkdtemp(path.join(tmpdir(), 'cybermario-quality-'))
    const lane = 'auto_local-redis-15'
    await writeFile(path.join(directory, `${lane}.lock`), '999999')

    const lock = await acquireLaneLock(directory, lane)

    await lock.release()
    await rm(directory, {recursive: true, force: true})
})
