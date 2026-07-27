import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {Form, Input} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {FilterBar} from './index'

type Filters = {
    keyword?: string
}

function renderBar(props: {onSearch?: (values: Filters) => void; onReset?: () => void} = {}) {
    render(
        <FilterBar<Filters> onReset={props.onReset} onSearch={props.onSearch}>
            <Form.Item label="关键词" name="keyword">
                <Input/>
            </Form.Item>
        </FilterBar>,
    )
}

describe('FilterBar', () => {
    test('submits the current field values', async () => {
        const onSearch = vi.fn()
        renderBar({onSearch})

        await userEvent.type(screen.getByLabelText('关键词'), 'mario')
        await userEvent.click(screen.getByRole('button', {name: /查\s*询/}))

        expect(onSearch).toHaveBeenCalledWith({keyword: 'mario'})
    })

    test('submitting from a field applies the filter without the button', async () => {
        const onSearch = vi.fn()
        renderBar({onSearch})

        await userEvent.type(screen.getByLabelText('关键词'), 'mario{Enter}')

        expect(onSearch).toHaveBeenCalledWith({keyword: 'mario'})
    })

    test('reset clears the fields and notifies the caller', async () => {
        const onReset = vi.fn()
        const onSearch = vi.fn()
        renderBar({onReset, onSearch})

        await userEvent.type(screen.getByLabelText('关键词'), 'mario')
        await userEvent.click(screen.getByRole('button', {name: /重\s*置/}))

        // Reset remounts the fields, so the node must be queried again.
        expect(screen.getByLabelText<HTMLInputElement>('关键词').value).toBe('')
        expect(onReset).toHaveBeenCalledOnce()
        // `onReset` owns the follow-up, so no duplicate search is fired.
        expect(onSearch).not.toHaveBeenCalled()
    })

    test('without onReset the cleared values are searched instead', async () => {
        const onSearch = vi.fn()
        renderBar({onSearch})

        await userEvent.type(screen.getByLabelText('关键词'), 'mario')
        await userEvent.click(screen.getByRole('button', {name: /重\s*置/}))

        expect(onSearch).toHaveBeenCalledWith({keyword: undefined})
    })

    test('hides the search button when the bar filters on change', () => {
        render(
            <FilterBar<Filters>>
                <Form.Item label="关键词" name="keyword">
                    <Input/>
                </Form.Item>
            </FilterBar>,
        )

        expect(screen.queryByRole('button', {name: /查\s*询/})).toBeNull()
        expect(screen.getByRole('button', {name: /重\s*置/})).toBeTruthy()
    })
})
