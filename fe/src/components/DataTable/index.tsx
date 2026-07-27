import {Table} from 'antd'
import type {TableProps} from 'antd'
import type {AnyObject} from 'antd/es/_util/type'
import type {ReactNode} from 'react'
import {EmptyState} from '../EmptyState'

/** `title` is re-purposed as a plain heading, so Ant Design's panel render is dropped. */
type DataTableProps<RecordType> = Omit<TableProps<RecordType>, 'title'> & {
    /** Optional heading strip above the table. */
    title?: ReactNode
    /** Rendered next to the title — usually filters or column toggles. */
    toolbar?: ReactNode
    /** Shown after the title as a muted count, e.g. `共 128 条`. */
    count?: number
    /** Replaces Ant Design's default empty illustration. */
    emptyTitle?: string
    emptyDescription?: ReactNode
    emptyAction?: ReactNode
}

/**
 * Table wrapper that gives every list page the same frame: a card surface, a
 * consistent pagination config, sticky header on long lists and a real empty
 * state instead of Ant Design's bare "暂无数据".
 */
export function DataTable<RecordType extends AnyObject = AnyObject>({
    title,
    toolbar,
    count,
    emptyTitle = '暂无数据',
    emptyDescription,
    emptyAction,
    pagination,
    locale,
    ...tableProps
}: DataTableProps<RecordType>) {
    const hasHeader = Boolean(title || toolbar)

    return (
        <div className="data-table-card">
            {hasHeader && (
                <div className="data-table-header">
                    <div className="data-table-header-main">
                        {title && <span className="data-table-title">{title}</span>}
                        {typeof count === 'number' && <span className="data-table-count">共 {count} 条</span>}
                    </div>
                    {toolbar && <div className="data-table-header-actions">{toolbar}</div>}
                </div>
            )}
            <div className="data-table-body">
                <Table<RecordType>
                    locale={{
                        emptyText: (
                            <EmptyState action={emptyAction} description={emptyDescription} title={emptyTitle}/>
                        ),
                        ...locale,
                    }}
                    pagination={
                        pagination === false
                            ? false
                            : {
                                showSizeChanger: true,
                                showTotal: (totalCount: number) => `共 ${totalCount} 条`,
                                ...pagination,
                            }
                    }
                    sticky
                    {...tableProps}
                />
            </div>
        </div>
    )
}
