import {Button, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
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
            width: 210,
            render: (_, record) => (
                <Space>
                    <Button disabled={!onCopy} onClick={() => onCopy?.(record)} size="small">
                        复制到编辑器
                    </Button>
                    <Button
                        disabled={!onSave}
                        loading={savingCandidateId === record.candidateId}
                        onClick={() => void onSave?.(record)}
                        size="small"
                        type="primary"
                    >
                        保存
                    </Button>
                </Space>
            ),
        },
    ]

    return (
        <Table
            columns={columns}
            dataSource={candidates}
            loading={loading}
            pagination={false}
            rowKey="candidateId"
            scroll={{x: 1120}}
        />
    )
}
