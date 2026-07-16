import {act, render, screen, waitFor} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {
    InvestmentWorkspaceProvider,
    useInvestmentWorkspace,
} from './useInvestmentWorkspace'

const service = vi.hoisted(() => ({
    list: vi.fn(),
    create: vi.fn(),
}))

vi.mock('../services/investmentWorkspaceService', () => ({
    listInvestmentWorkspaces: service.list,
    createInvestmentWorkspace: service.create,
}))

const workspaceOne = workspace(1, '研究一号')
const workspaceTwo = workspace(2, '研究二号')

describe('useInvestmentWorkspace', () => {
    test('ignores a stale refresh response that arrives after a newer request', async () => {
        const first = deferred<ReturnType<typeof page>>()
        const second = deferred<ReturnType<typeof page>>()
        service.list.mockReset().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
        render(<InvestmentWorkspaceProvider><WorkspaceProbe/></InvestmentWorkspaceProvider>)
        await waitFor(() => expect(service.list).toHaveBeenCalledTimes(1))

        await userEvent.click(screen.getByRole('button', {name: '刷新'}))
        await waitFor(() => expect(service.list).toHaveBeenCalledTimes(2))
        await act(async () => second.resolve(page([workspaceTwo])))
        expect(await screen.findByText('研究二号')).toBeTruthy()

        await act(async () => first.resolve(page([workspaceOne])))
        expect(screen.queryByText('研究一号')).toBeNull()
        expect(screen.getByText('研究二号')).toBeTruthy()
    })

    test('selects a created workspace and clears the paper account when workspace changes', async () => {
        service.list.mockReset().mockResolvedValue(page([workspaceOne, workspaceTwo]))
        service.create.mockReset().mockResolvedValue(workspace(3, '新工作区'))
        render(<InvestmentWorkspaceProvider><WorkspaceProbe/></InvestmentWorkspaceProvider>)
        expect(await screen.findByText('研究一号,研究二号')).toBeTruthy()

        await userEvent.click(screen.getByRole('button', {name: '选一'}))
        await userEvent.click(screen.getByRole('button', {name: '设账户'}))
        expect(screen.getByTestId('account').textContent).toBe('101')

        await userEvent.click(screen.getByRole('button', {name: '选二'}))
        expect(screen.getByTestId('account').textContent).toBe('none')

        await userEvent.click(screen.getByRole('button', {name: '创建'}))
        expect(await screen.findByText('新工作区,研究一号,研究二号')).toBeTruthy()
        expect(screen.getByTestId('current').textContent).toBe('新工作区')
        expect(service.create).toHaveBeenCalledWith({name: '新工作区'})
    })
})

function WorkspaceProbe() {
    const state = useInvestmentWorkspace()
    return (
        <div>
            <div>{state.workspaces.map(({name}) => name).join(',')}</div>
            <div data-testid="current">{state.currentWorkspace?.name ?? 'none'}</div>
            <div data-testid="account">{state.currentPaperAccount?.id ?? 'none'}</div>
            <button onClick={() => void state.refreshWorkspaces()}>刷新</button>
            <button onClick={() => state.selectWorkspace(1)}>选一</button>
            <button onClick={() => state.selectWorkspace(2)}>选二</button>
            <button onClick={() => state.setCurrentPaperAccount({
                id: 101,
                workspaceId: 1,
                name: '模拟账户',
                baseCurrency: 'USDT',
                status: 'ACTIVE',
            })}>设账户</button>
            <button onClick={() => void state.createWorkspace(' 新工作区 ')}>创建</button>
        </div>
    )
}

function workspace(id: number, name: string) {
    return {
        id,
        name,
        baseCurrency: 'USDT',
        timezone: 'UTC',
        status: 'ACTIVE',
        createdAt: '2026-07-16T00:00:00Z',
    }
}

function page(records: ReturnType<typeof workspace>[]) {
    return {records, page: 1, size: 100, total: records.length, totalPages: records.length ? 1 : 0}
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((resolver) => {
        resolve = resolver
    })
    return {promise, resolve}
}
