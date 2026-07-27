import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {App} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {RowActions, type RowAction} from './index'

function renderActions(actions: RowAction[], maxInline?: number) {
    return render(
        <App>
            <RowActions actions={actions} maxInline={maxInline}/>
        </App>,
    )
}

describe('RowActions', () => {
    test('renders the placeholder when every action is hidden', () => {
        renderActions([{key: 'edit', label: '编辑', hidden: true}])

        expect(screen.queryByRole('button', {name: '编辑'})).toBeNull()
        expect(screen.getByText('-')).toBeTruthy()
    })

    test('keeps one extra action inline rather than hiding a single item', () => {
        renderActions([
            {key: 'a', label: '编辑'},
            {key: 'b', label: '角色'},
            {key: 'c', label: '权限'},
        ], 2)

        expect(screen.getByRole('button', {name: '权限'})).toBeTruthy()
        expect(screen.queryByRole('button', {name: '更多操作'})).toBeNull()
    })

    test('collapses the tail into the overflow menu past the inline budget', async () => {
        const onDelete = vi.fn()
        renderActions([
            {key: 'a', label: '编辑'},
            {key: 'b', label: '角色'},
            {key: 'c', label: '权限'},
            {key: 'd', label: '删除', onClick: onDelete},
        ], 2)

        expect(screen.getByRole('button', {name: '编辑'})).toBeTruthy()
        expect(screen.getByRole('button', {name: '角色'})).toBeTruthy()
        expect(screen.queryByRole('button', {name: '权限'})).toBeNull()

        await userEvent.click(screen.getByRole('button', {name: '更多操作'}))
        await userEvent.click(await screen.findByText('删除'))

        expect(onDelete).toHaveBeenCalledOnce()
    })

    test('an inline action with confirm waits for the popconfirm', async () => {
        const onDelete = vi.fn()
        renderActions([{key: 'delete', label: '删除', danger: true, confirm: '确认删除？', onClick: onDelete}])

        await userEvent.click(screen.getByRole('button', {name: '删除'}))
        expect(onDelete).not.toHaveBeenCalled()

        // Ant Design inserts a hair space between two CJK characters.
        await userEvent.click(await screen.findByRole('button', {name: /确\s*认/}))
        expect(onDelete).toHaveBeenCalledOnce()
    })

    test('hidden actions do not consume an inline slot', () => {
        renderActions([
            {key: 'a', label: '编辑'},
            {key: 'b', label: '角色', hidden: true},
            {key: 'c', label: '权限', hidden: true},
            {key: 'd', label: '删除'},
        ], 2)

        expect(screen.getByRole('button', {name: '编辑'})).toBeTruthy()
        expect(screen.getByRole('button', {name: '删除'})).toBeTruthy()
        expect(screen.queryByRole('button', {name: '更多操作'})).toBeNull()
    })
})
