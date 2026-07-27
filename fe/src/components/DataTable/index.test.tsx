import {render, screen} from '@testing-library/react'
import {describe, expect, test} from 'vitest'
import {DataTable} from './index'

type Row = {
    id: number
    name: string
}

const columns = [{title: '名称', dataIndex: 'name'}]

describe('DataTable', () => {
    test('renders the heading strip with the record count', () => {
        render(
            <DataTable<Row>
                columns={columns}
                count={128}
                dataSource={[{id: 1, name: '文档 A'}]}
                rowKey="id"
                title="用户列表"
            />,
        )

        expect(screen.getByText('用户列表')).toBeTruthy()
        expect(screen.getByText('共 128 条')).toBeTruthy()
        expect(screen.getByText('文档 A')).toBeTruthy()
    })

    test('omits the heading strip when neither title nor toolbar is given', () => {
        const {container} = render(
            <DataTable<Row> columns={columns} dataSource={[]} rowKey="id"/>,
        )

        expect(container.querySelector('.data-table-header')).toBeNull()
        expect(container.querySelector('.data-table-card')).toBeTruthy()
    })

    test('replaces the default placeholder with an actionable empty state', () => {
        render(
            <DataTable<Row>
                columns={columns}
                dataSource={[]}
                emptyDescription="调整筛选条件后重试。"
                emptyTitle="没有匹配的记录"
                rowKey="id"
            />,
        )

        expect(screen.getByText('没有匹配的记录')).toBeTruthy()
        expect(screen.getByText('调整筛选条件后重试。')).toBeTruthy()
        expect(screen.queryByText('暂无数据')).toBeNull()
    })

    test('caller pagination overrides the shared defaults', () => {
        render(
            <DataTable<Row>
                columns={columns}
                dataSource={[{id: 1, name: '文档 A'}]}
                pagination={{current: 2, pageSize: 10, total: 24}}
                rowKey="id"
            />,
        )

        // `showTotal` comes from the shared default, current/total from the caller.
        expect(screen.getByText('共 24 条')).toBeTruthy()
        expect(screen.getByTitle('2')).toBeTruthy()
    })

    test('pagination can still be disabled entirely', () => {
        const {container} = render(
            <DataTable<Row> columns={columns} dataSource={[]} pagination={false} rowKey="id"/>,
        )

        expect(container.querySelector('.ant-pagination')).toBeNull()
    })
})
