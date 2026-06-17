import {Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ClocktowerBoardCandidateResponse} from '../clocktowerTypes'

type BoardCandidateTableProps = {
    candidates: ClocktowerBoardCandidateResponse[]
    loading?: boolean
}

export function BoardCandidateTable({candidates, loading}: BoardCandidateTableProps) {
    const columns: ColumnsType<ClocktowerBoardCandidateResponse> = [
        {title: '候选', dataIndex: 'candidateId', width: 160, render: (value) => <Tag>{value}</Tag>},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[]) => (
                <span>
                    {roleCodes.map((roleCode) => <Tag key={roleCode}>{roleCode}</Tag>)}
                </span>
            ),
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
            render: (_, record) => record.validation.valid ? <Tag color="success">通过</Tag> : <Tag color="error">有问题</Tag>,
        },
    ]

    return (
        <Table
            columns={columns}
            dataSource={candidates}
            loading={loading}
            pagination={false}
            rowKey="candidateId"
            scroll={{x: 900}}
        />
    )
}
