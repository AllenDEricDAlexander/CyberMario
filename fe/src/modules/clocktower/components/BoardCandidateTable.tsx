import {CopyOutlined, SaveOutlined} from '@ant-design/icons'
import {Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {DataTable} from '../../../components/DataTable'
import {RowActions, type RowAction} from '../../../components/RowActions'
import type {ClocktowerBoardCandidateResponse} from '../clocktowerTypes'
import {RoleSummaryTags} from './RoleSummaryTags'

type BoardCandidateTableProps = {
    candidates: ClocktowerBoardCandidateResponse[]
    loading?: boolean
    savingCandidateId?: string
    onCopy?: (candidate: ClocktowerBoardCandidateResponse) => void
    onSave?: (candidate: ClocktowerBoardCandidateResponse) => Promise<void>
}

export function BoardCandidateTable({candidates, loading, savingCandidateId, onCopy, onSave}: BoardCandidateTableProps) {
    const columns: ColumnsType<ClocktowerBoardCandidateResponse> = [
        {title: '候选', dataIndex: 'candidateId', width: 160, render: (value) => <Tag>{value}</Tag>},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[], record) => <RoleSummaryTags roleCodes={roleCodes} roles={record.roles}/>,
        },
        {
            title: '评分',
            dataIndex: 'scores',
            width: 220,
            render: (_, record) => (
                <span>
                    {record.scores.map((score) => (
                        <Tag key={`${record.candidateId}-${score.scoreType}`}>{score.scoreType} {score.delta}</Tag>
                    ))}
                </span>
            ),
        },
        {
            title: '校验',
            dataIndex: 'validation',
            width: 120,
            render: (_, record) => record.validation.valid
                ? <Tag color="success">校验通过</Tag>
                : <Tag color="error">校验未通过</Tag>,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 200,
            render: (_, record) => {
                const actions: RowAction[] = [
                    {
                        key: 'copy',
                        label: '复制到编辑器',
                        icon: <CopyOutlined/>,
                        disabled: !onCopy,
                        onClick: () => onCopy?.(record),
                    },
                    {
                        key: 'save',
                        label: '保存',
                        icon: <SaveOutlined/>,
                        disabled: !onSave || savingCandidateId === record.candidateId,
                        onClick: () => void onSave?.(record),
                    },
                ]
                return <RowActions actions={actions}/>
            },
        },
    ]

    return (
        <DataTable<ClocktowerBoardCandidateResponse>
            columns={columns}
            count={candidates.length}
            dataSource={candidates}
            emptyDescription="设置生成参数后点击「生成配板」，候选方案会显示在这里。"
            emptyTitle="还没有候选配板"
            loading={loading}
            pagination={false}
            rowKey="candidateId"
            scroll={{x: 1000}}
            title="候选配板"
        />
    )
}
