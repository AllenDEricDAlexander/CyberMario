import {ExperimentOutlined, ReloadOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    deleteClocktowerBoard,
    generateClocktowerBoard,
    getClocktowerScripts,
    listClocktowerBoards,
    saveClocktowerBoard,
    validateClocktowerBoard,
} from './clocktowerService'
import type {
    BoardValidationResponse,
    ClocktowerBoardCandidateResponse,
    ClocktowerBoardConfigResponse,
    ClocktowerBoardGenerateRequest,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
} from './clocktowerTypes'
import {BoardCandidateTable} from './components/BoardCandidateTable'
import {RoleSummaryTags} from './components/RoleSummaryTags'

type ValidateFormValues = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    roleCodesText?: string
}

function BoardBuilderPage() {
    const {message} = App.useApp()
    const [form] = Form.useForm<ClocktowerBoardGenerateRequest>()
    const [validateForm] = Form.useForm<ValidateFormValues>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [candidates, setCandidates] = useState<ClocktowerBoardCandidateResponse[]>([])
    const [savedBoards, setSavedBoards] = useState<ClocktowerBoardConfigResponse[]>([])
    const [validation, setValidation] = useState<BoardValidationResponse>()
    const [loading, setLoading] = useState(false)
    const [validating, setValidating] = useState(false)
    const [savingCandidateId, setSavingCandidateId] = useState<string>()
    const [savedBoardsLoading, setSavedBoardsLoading] = useState(false)
    const [error, setError] = useState<string>()

    useEffect(() => {
        void loadInitialData()
    }, [])

    async function loadInitialData() {
        await Promise.all([loadScripts(), loadSavedBoards()])
    }

    async function loadScripts() {
        try {
            setScripts(await getClocktowerScripts())
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        }
    }

    async function loadSavedBoards() {
        setSavedBoardsLoading(true)
        try {
            setSavedBoards(await listClocktowerBoards())
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setSavedBoardsLoading(false)
        }
    }

    async function generate() {
        const values = await form.validateFields()
        setLoading(true)
        setError(undefined)
        try {
            const response = await generateClocktowerBoard({
                ...values,
                bannedRoleCodes: values.bannedRoleCodes ?? [],
                lockedRoleCodes: values.lockedRoleCodes ?? [],
            })
            setCandidates(response.candidates)
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function validateManualBoard() {
        const values = await validateForm.validateFields()
        setValidating(true)
        setError(undefined)
        setValidation(undefined)
        try {
            const response = await validateClocktowerBoard({
                scriptCode: values.scriptCode,
                playerCount: values.playerCount,
                roleCodes: parseRoleCodes(values.roleCodesText),
            })
            setValidation(response)
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setValidating(false)
        }
    }

    async function saveCandidate(candidate: ClocktowerBoardCandidateResponse) {
        const values = form.getFieldsValue()
        setSavingCandidateId(candidate.candidateId)
        setError(undefined)
        try {
            await saveClocktowerBoard({
                scriptCode: candidate.scriptCode,
                playerCount: candidate.playerCount,
                difficulty: values.difficulty,
                chaos: values.chaos,
                evilPressure: values.evilPressure,
                newbieFriendly: values.newbieFriendly,
                seed: values.seed,
                roleCodes: candidate.roleCodes,
                validation: candidate.validation,
            })
            message.success('配板已保存')
            await loadSavedBoards()
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setSavingCandidateId(undefined)
        }
    }

    async function deleteSavedBoard(boardId: number) {
        setError(undefined)
        try {
            await deleteClocktowerBoard(boardId)
            message.success('配板已删除')
            await loadSavedBoards()
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={() => void loadInitialData()}>刷新剧本</Button>}
                description="为说书人生成并校验一局可用配板。"
                title="钟楼配板"
            />
            {error && <Alert showIcon style={{marginBottom: 16}} title={error} type="error"/>}
            <Card>
                <Form
                    form={form}
                    initialValues={{
                        scriptCode: 'TROUBLE_BREWING',
                        playerCount: 5,
                        difficulty: 2,
                        chaos: 2,
                        evilPressure: 2,
                        newbieFriendly: true,
                        candidateCount: 2,
                    }}
                    layout="vertical"
                >
                    <Space align="end" wrap>
                        <Form.Item label="剧本" name="scriptCode" rules={[{required: true, message: '请选择剧本'}]}>
                            <Select
                                options={scriptOptions(scripts)}
                                style={{width: 220}}
                            />
                        </Form.Item>
                        <Form.Item label="人数" name="playerCount" rules={[{required: true}]}>
                            <InputNumber min={5} max={15}/>
                        </Form.Item>
                        <Form.Item label="难度" name="difficulty">
                            <InputNumber min={1} max={5}/>
                        </Form.Item>
                        <Form.Item label="混乱度" name="chaos">
                            <InputNumber min={1} max={5}/>
                        </Form.Item>
                        <Form.Item label="邪恶压力" name="evilPressure">
                            <InputNumber min={1} max={5}/>
                        </Form.Item>
                        <Form.Item label="新手友好" name="newbieFriendly" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="候选数" name="candidateCount">
                            <InputNumber min={1} max={5}/>
                        </Form.Item>
                        <Form.Item label="随机种子" name="seed">
                            <Input style={{width: 160}}/>
                        </Form.Item>
                        <Button icon={<ExperimentOutlined/>} loading={loading} onClick={voidify(generate)}
                                type="primary">
                            生成配板
                        </Button>
                    </Space>
                </Form>
            </Card>
            <Card style={{marginTop: 16}} title="手动校验">
                <Form
                    form={validateForm}
                    initialValues={{
                        scriptCode: 'TROUBLE_BREWING',
                        playerCount: 5,
                    }}
                    layout="vertical"
                >
                    <Space align="end" wrap>
                        <Form.Item label="剧本" name="scriptCode" rules={[{required: true, message: '请选择剧本'}]}>
                            <Select
                                options={scriptOptions(scripts)}
                                style={{width: 220}}
                            />
                        </Form.Item>
                        <Form.Item label="人数" name="playerCount" rules={[{required: true}]}>
                            <InputNumber min={5} max={15}/>
                        </Form.Item>
                        <Form.Item label="角色代码" name="roleCodesText">
                            <Input.TextArea
                                autoSize={{minRows: 2, maxRows: 4}}
                                placeholder="CHEF,EMPATH,MONK,POISONER,IMP"
                                style={{width: 520}}
                            />
                        </Form.Item>
                        <Button loading={validating} onClick={voidify(validateManualBoard)} type="primary">
                            手动校验
                        </Button>
                    </Space>
                </Form>
                {validation && (
                    <Alert
                        showIcon
                        style={{marginTop: 16}}
                        title={validation.valid ? '校验通过' : '校验未通过'}
                        type={validation.valid ? 'success' : 'warning'}
                        description={
                            <Space direction="vertical" size={4}>
                                <span>{countSummary(validation.typeCounts)}</span>
                                {validation.issues.map((issue) => (
                                    <span key={issue.code}>{issue.severity}：{issue.message}</span>
                                ))}
                            </Space>
                        }
                    />
                )}
            </Card>
            <Card style={{marginTop: 16}} title="候选配板">
                <BoardCandidateTable
                    candidates={candidates}
                    loading={loading}
                    onSave={saveCandidate}
                    savingCandidateId={savingCandidateId}
                />
            </Card>
            <Card style={{marginTop: 16}} title="已保存配板">
                <Table
                    columns={savedBoardColumns(deleteSavedBoard)}
                    dataSource={savedBoards}
                    loading={savedBoardsLoading}
                    pagination={false}
                    rowKey="boardId"
                    scroll={{x: 900}}
                />
            </Card>
        </>
    )
}

function scriptOptions(scripts: ClocktowerScriptResponse[]) {
    if (scripts.length === 0) {
        return [{label: '暗流涌动', value: 'TROUBLE_BREWING' satisfies ClocktowerScriptCode}]
    }
    return scripts.map((script) => ({label: script.name, value: script.scriptCode}))
}

function parseRoleCodes(text?: string) {
    return (text ?? '')
        .split(/[\s,，]+/)
        .map((roleCode) => roleCode.trim())
        .filter(Boolean)
}

function countSummary(counts: BoardValidationResponse['typeCounts']) {
    return `镇民 ${counts.townsfolk} / 外来者 ${counts.outsider} / 爪牙 ${counts.minion} / 恶魔 ${counts.demon} / 旅行者 ${counts.traveler} / 传奇 ${counts.fabled}`
}

export function savedBoardColumns(onDelete: (boardId: number) => Promise<void>): ColumnsType<ClocktowerBoardConfigResponse> {
    return [
        {title: '编号', dataIndex: 'boardCode', width: 180, render: (value) => <Tag>{value}</Tag>},
        {title: '剧本', dataIndex: 'scriptCode', width: 160},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[], record) => <RoleSummaryTags roleCodes={roleCodes} roles={record.roles}/>,
        },
        {
            title: '校验',
            dataIndex: 'validation',
            width: 120,
            render: (_, record) => record.validation.valid ? <Tag color="success">通过</Tag> :
                <Tag color="error">有问题</Tag>,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 110,
            render: (_, record) => (
                <Popconfirm
                    cancelText="取消"
                    okText="删除"
                    okType="danger"
                    onConfirm={() => void onDelete(record.boardId)}
                    title="删除已保存配板？"
                >
                    <Button danger size="small">删除</Button>
                </Popconfirm>
            ),
        },
    ]
}

export const Component = BoardBuilderPage
