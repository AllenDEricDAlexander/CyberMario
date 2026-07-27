import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {Form, Input} from 'antd'
import {describe, expect, test, vi} from 'vitest'
import {FilterBar} from './index'

type Filters = {
    keyword?: string
}

describe('FilterBar instant mode', () => {
    test('applies on every change and hides the search button', async () => {
        const onSearch = vi.fn()
        render(
            <FilterBar<Filters> instant onSearch={onSearch}>
                <Form.Item label="关键词" name="keyword">
                    <Input/>
                </Form.Item>
            </FilterBar>,
        )

        expect(screen.queryByRole('button', {name: /查\s*询/})).toBeNull()

        await userEvent.type(screen.getByLabelText('关键词'), 'ab')

        expect(onSearch).toHaveBeenCalledTimes(2)
        expect(onSearch).toHaveBeenLastCalledWith({keyword: 'ab'})
    })

    test('does not apply on change when instant is off', async () => {
        const onSearch = vi.fn()
        render(
            <FilterBar<Filters> onSearch={onSearch}>
                <Form.Item label="关键词" name="keyword">
                    <Input/>
                </Form.Item>
            </FilterBar>,
        )

        await userEvent.type(screen.getByLabelText('关键词'), 'ab')

        expect(onSearch).not.toHaveBeenCalled()
        expect(screen.getByRole('button', {name: /查\s*询/})).toBeTruthy()
    })
})
