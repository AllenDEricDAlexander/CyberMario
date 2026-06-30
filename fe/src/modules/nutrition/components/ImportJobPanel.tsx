import {Button, Card, Space, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {NutritionImportErrorResponse, NutritionImportJobResponse} from '../nutritionTypes'

type ImportJobPanelProps = {
    job?: NutritionImportJobResponse | null
    loading?: boolean
    confirming?: boolean
    onConfirm?: (jobId: number) => void
}

const errorColumns: ColumnsType<NutritionImportErrorResponse> = [
    {title: '行号', dataIndex: 'rowNo', width: 90},
    {title: '字段', dataIndex: 'columnName', width: 140, render: (_, record) => record.columnName || '-'},
    {title: '级别', dataIndex: 'severity', width: 110, render: (_, record) => <Tag color={severityColor(record.severity)}>{record.severity}</Tag>},
    {title: '编码', dataIndex: 'errorCode', width: 160},
    {title: '问题', dataIndex: 'errorMessage'},
]

export function ImportJobPanel({job, loading, confirming, onConfirm}: ImportJobPanelProps) {
    const canConfirm = Boolean(job && job.status === 'PREVIEW_READY' && job.failedRows === 0)
    return (
        <Card
            extra={job && (
                <Button
                    disabled={!canConfirm}
                    loading={confirming}
                    onClick={() => job && onConfirm?.(job.id)}
                    type="primary"
                >
                    确认导入
                </Button>
            )}
            title="导入校验"
        >
            <div style={{display: 'flex', flexDirection: 'column', gap: 16, width: '100%'}}>
                {job ? (
                    <>
                        <Space wrap>
                            <Tag color={statusColor(job.status)}>{job.status}</Tag>
                            <Typography.Text>总行数：{job.totalRows}</Typography.Text>
                            <Typography.Text type="success">成功行：{job.successRows}</Typography.Text>
                            <Typography.Text type={job.failedRows > 0 ? 'danger' : undefined}>
                                失败行：{job.failedRows}
                            </Typography.Text>
                            <Typography.Text type={job.warningRows > 0 ? 'warning' : undefined}>
                                警告行：{job.warningRows}
                            </Typography.Text>
                        </Space>
                        {job.errorSummary && <Typography.Text type="danger">{job.errorSummary}</Typography.Text>}
                        <Table
                            columns={errorColumns}
                            dataSource={job.errors}
                            loading={loading}
                            pagination={false}
                            rowKey="id"
                            size="small"
                        />
                    </>
                ) : (
                    <Typography.Text type="secondary">暂无导入任务</Typography.Text>
                )}
            </div>
        </Card>
    )
}

function statusColor(status: NutritionImportJobResponse['status']) {
    if (status === 'COMPLETED' || status === 'CONFIRMED') {
        return 'success'
    }
    if (status === 'FAILED' || status === 'CANCELLED') {
        return 'error'
    }
    if (status === 'PREVIEW_READY') {
        return 'processing'
    }
    return 'default'
}

function severityColor(severity: string) {
    if (severity === 'ERROR') {
        return 'error'
    }
    if (severity === 'WARN' || severity === 'WARNING') {
        return 'warning'
    }
    return 'default'
}
