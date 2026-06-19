import {EditOutlined, ExperimentOutlined, ReloadOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Form, Input, InputNumber, Popconfirm, Select, Space, Switch, Table, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useState} from 'react'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    deleteClocktowerBoard,
    generateClocktowerBoard,
    getClocktowerRoles,
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
    ClocktowerBoardQuery,
    ClocktowerRoleResponse,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
} from './clocktowerTypes'
import {BoardCandidateTable} from './components/BoardCandidateTable'
import {RoleTreeSelect, selectedRoleCountText} from './components/RoleTreeSelect'
import {RoleSummaryTags} from './components/RoleSummaryTags'

type BoardEditorFormValues = ClocktowerBoardGenerateRequest & {
    roleCodes: string[]
}

type BoardLibraryFilterValues = Pick<ClocktowerBoardQuery, 'scriptCode' | 'playerCount' | 'valid'>

const defaultScriptCode = 'TROUBLE_BREWING' satisfies ClocktowerScriptCode
export const boardGenerateFieldNames: Array<keyof BoardEditorFormValues> = [
    'scriptCode',
    'playerCount',
    'difficulty',
    'chaos',
    'evilPressure',
    'newbieFriendly',
    'candidateCount',
    'bannedRoleCodes',
    'lockedRoleCodes',
    'seed',
]

function BoardBuilderPage() {
    const {message} = App.useApp()
    const [form] = Form.useForm<BoardEditorFormValues>()
    const [filterForm] = Form.useForm<BoardLibraryFilterValues>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [roles, setRoles] = useState<ClocktowerRoleResponse[]>([])
    const [candidates, setCandidates] = useState<ClocktowerBoardCandidateResponse[]>([])
    const [validation, setValidation] = useState<BoardValidationResponse>()
    const [boardQuery, setBoardQuery] = useState<ClocktowerBoardQuery>({})
    const [loading, setLoading] = useState(false)
    const [rolesLoading, setRolesLoading] = useState(false)
    const [validating, setValidating] = useState(false)
    const [savingEditor, setSavingEditor] = useState(false)
    const [savingCandidateId, setSavingCandidateId] = useState<string>()
    const [error, setError] = useState<string>()
    const selectedScriptCode = Form.useWatch('scriptCode', form) ?? defaultScriptCode
    const selectedPlayerCount = Form.useWatch('playerCount', form) ?? 5
    const selectedRoleCodes = Form.useWatch('roleCodes', form) ?? []

    const loadSavedBoardPage = useCallback(
        (request: { page: number; size: number }) => listClocktowerBoards({...boardQuery, ...request}),
        [boardQuery],
    )
    const {
        loading: savedBoardsLoading,
        records: savedBoards,
        page,
        size,
        total,
        load: loadSavedBoards,
    } = usePageData<ClocktowerBoardConfigResponse>(loadSavedBoardPage, {initialSize: 10, enabled: false})

    const reloadSavedBoards = useCallback(async (nextPage?: number, nextSize?: number) => {
        try {
            await loadSavedBoards(nextPage, nextSize)
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        }
    }, [loadSavedBoards])

    const loadScripts = useCallback(async () => {
        try {
            setScripts(await getClocktowerScripts())
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        }
    }, [])

    useEffect(() => {
        void loadScripts()
    }, [loadScripts])

    useEffect(() => {
        void reloadSavedBoards(1)
    }, [reloadSavedBoards])

    useEffect(() => {
        let active = true
        setRolesLoading(true)
        getClocktowerRoles(selectedScriptCode, {enabled: true})
            .then((response) => {
                if (!active) {
                    return
                }
                setRoles(response)
                const availableRoleCodes = new Set(response.map((role) => role.roleCode))
                const rawRoleCodes: unknown = form.getFieldValue('roleCodes')
                const currentRoleCodes = Array.isArray(rawRoleCodes)
                    ? rawRoleCodes.filter((roleCode): roleCode is string => typeof roleCode === 'string')
                    : []
                const nextRoleCodes = currentRoleCodes.filter((roleCode) => availableRoleCodes.has(roleCode))
                if (nextRoleCodes.length !== currentRoleCodes.length) {
                    form.setFieldValue('roleCodes', nextRoleCodes)
                    setValidation(undefined)
                }
            })
            .catch((caught) => {
                if (active) {
                    setError(resolveErrorMessage(caught))
                }
            })
            .finally(() => {
                if (active) {
                    setRolesLoading(false)
                }
            })
        return () => {
            active = false
        }
    }, [form, selectedScriptCode])

    async function loadInitialData() {
        await Promise.all([loadScripts(), reloadSavedBoards()])
    }

    async function generate() {
        const values = await form.validateFields(boardGenerateFieldNames)
        setLoading(true)
        setError(undefined)
        try {
            const response = await generateClocktowerBoard({
                scriptCode: values.scriptCode,
                playerCount: values.playerCount,
                difficulty: values.difficulty,
                chaos: values.chaos,
                evilPressure: values.evilPressure,
                newbieFriendly: values.newbieFriendly,
                candidateCount: values.candidateCount,
                bannedRoleCodes: values.bannedRoleCodes ?? [],
                lockedRoleCodes: values.lockedRoleCodes ?? [],
                seed: values.seed,
            })
            setCandidates(response.candidates)
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    async function validateCurrentBoard() {
        const values = await form.validateFields(['scriptCode', 'playerCount', 'roleCodes'])
        setValidating(true)
        setError(undefined)
        setValidation(undefined)
        try {
            const response = await validateClocktowerBoard({
                scriptCode: values.scriptCode,
                playerCount: values.playerCount,
                roleCodes: values.roleCodes,
            })
            setValidation(response)
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setValidating(false)
        }
    }

    async function saveCurrentBoard() {
        const values = await form.validateFields()
        setSavingEditor(true)
        setError(undefined)
        try {
            await saveClocktowerBoard(toSaveRequest(values))
            message.success('配板已保存')
            await reloadSavedBoards()
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        } finally {
            setSavingEditor(false)
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
            })
            message.success('配板已保存')
            await reloadSavedBoards()
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
            await reloadSavedBoards()
        } catch (caught) {
            setError(resolveErrorMessage(caught))
        }
    }

    function copyCandidateToEditor(candidate: ClocktowerBoardCandidateResponse) {
        copyToEditor(candidate)
    }

    function editSavedBoard(board: ClocktowerBoardConfigResponse) {
        copyToEditor(board)
    }

    function copyToEditor(board: Pick<ClocktowerBoardConfigResponse, 'scriptCode' | 'playerCount' | 'roleCodes'>) {
        form.setFieldsValue({
            scriptCode: board.scriptCode,
            playerCount: board.playerCount,
            roleCodes: board.roleCodes,
        })
        setValidation(undefined)
    }

    async function querySavedBoards() {
        const values = await filterForm.validateFields()
        setBoardQuery(cleanBoardQuery(values))
    }

    function resetSavedBoardQuery() {
        filterForm.resetFields()
        setBoardQuery({})
    }

    function handleEditorValuesChange(changedValues: Partial<BoardEditorFormValues>) {
        if ('scriptCode' in changedValues || 'playerCount' in changedValues || 'roleCodes' in changedValues) {
            setValidation(undefined)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={() => void loadInitialData()}>刷新</Button>}
                description="为说书人生成、编辑并校验一局可用配板。"
                title="钟楼配板"
            />
            {error && <Alert showIcon style={{marginBottom: 16}} title={error} type="error"/>}
            <Form
                form={form}
                initialValues={{
                    scriptCode: defaultScriptCode,
                    playerCount: 5,
                    difficulty: 2,
                    chaos: 2,
                    evilPressure: 2,
                    newbieFriendly: true,
                    candidateCount: 2,
                    roleCodes: [],
                }}
                layout="vertical"
                onValuesChange={handleEditorValuesChange}
            >
                <Card>
                    <Space align="end" wrap>
                        <Form.Item label="剧本" name="scriptCode" rules={[{required: true, message: '请选择剧本'}]}>
                            <Select options={scriptOptions(scripts)} style={{width: 220}}/>
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
                </Card>
                <Card
                    extra={
                        <Space wrap>
                            <Typography.Text type="secondary">
                                {selectedRoleCountText(selectedRoleCodes, selectedPlayerCount)}
                            </Typography.Text>
                            <Button loading={validating} onClick={voidify(validateCurrentBoard)}>手动校验</Button>
                            <Button loading={savingEditor} onClick={voidify(saveCurrentBoard)} type="primary">
                                保存当前配板
                            </Button>
                        </Space>
                    }
                    style={{marginTop: 16}}
                    title="配板编辑器"
                >
                    <Form.Item
                        label="角色"
                        name="roleCodes"
                        rules={[{
                            validator: (_, value: string[] = []) => value.length
                                ? Promise.resolve()
                                : Promise.reject(new Error('请选择角色')),
                        }]}
                    >
                        <RoleTreeSelect loading={rolesLoading} roles={roles} style={{width: '100%'}}/>
                    </Form.Item>
                    {validation ? (
                        <Alert
                            showIcon
                            title={`校验结果：${validation.valid ? '校验通过' : '校验未通过'}`}
                            type={validation.valid ? 'success' : 'warning'}
                            description={
                                <div style={{display: 'flex', flexDirection: 'column', gap: 4}}>
                                    <span>{countSummary(validation.typeCounts)}</span>
                                    {validation.issues.map((issue) => (
                                        <span key={issue.code}>{issue.severity}：{issue.message}</span>
                                    ))}
                                </div>
                            }
                        />
                    ) : (
                        <Typography.Text type="secondary">校验结果：尚未校验</Typography.Text>
                    )}
                </Card>
            </Form>
            <Card style={{marginTop: 16}} title="候选配板">
                <BoardCandidateTable
                    candidates={candidates}
                    loading={loading}
                    onCopy={copyCandidateToEditor}
                    onSave={saveCandidate}
                    savingCandidateId={savingCandidateId}
                />
            </Card>
            <Card style={{marginTop: 16}} title="我的配板库">
                <Form form={filterForm} layout="inline" onFinish={voidify(querySavedBoards)} style={{marginBottom: 16}}>
                    <Form.Item label="剧本" name="scriptCode">
                        <Select allowClear options={scriptOptions(scripts)} style={{width: 220}}/>
                    </Form.Item>
                    <Form.Item label="人数" name="playerCount">
                        <InputNumber min={5} max={15}/>
                    </Form.Item>
                    <Form.Item label="校验" name="valid">
                        <Select
                            allowClear
                            options={[
                                {label: '通过', value: true},
                                {label: '未通过', value: false},
                            ]}
                            style={{width: 130}}
                        />
                    </Form.Item>
                    <Form.Item>
                        <Space>
                            <Button htmlType="submit" type="primary">查询</Button>
                            <Button onClick={resetSavedBoardQuery}>重置</Button>
                        </Space>
                    </Form.Item>
                </Form>
                <Table<ClocktowerBoardConfigResponse>
                    columns={savedBoardColumns(deleteSavedBoard, editSavedBoard)}
                    dataSource={savedBoards}
                    loading={savedBoardsLoading}
                    pagination={{
                        current: page,
                        pageSize: size,
                        total,
                        showSizeChanger: true,
                        onChange: (nextPage, nextSize) => void reloadSavedBoards(nextPage, nextSize),
                    }}
                    rowKey="boardId"
                    scroll={{x: 1100}}
                />
            </Card>
        </>
    )
}

function scriptOptions(scripts: ClocktowerScriptResponse[]) {
    if (scripts.length === 0) {
        return [{label: '暗流涌动', value: defaultScriptCode}]
    }
    return scripts.map((script) => ({label: script.name, value: script.scriptCode}))
}

function countSummary(counts: BoardValidationResponse['typeCounts']) {
    return `镇民 ${counts.townsfolk} / 外来者 ${counts.outsider} / 爪牙 ${counts.minion} / 恶魔 ${counts.demon} / 旅行者 ${counts.traveler} / 传奇 ${counts.fabled}`
}

function toSaveRequest(values: BoardEditorFormValues) {
    return {
        scriptCode: values.scriptCode,
        playerCount: values.playerCount,
        difficulty: values.difficulty,
        chaos: values.chaos,
        evilPressure: values.evilPressure,
        newbieFriendly: values.newbieFriendly,
        seed: values.seed,
        roleCodes: values.roleCodes,
    }
}

function cleanBoardQuery(values: BoardLibraryFilterValues): ClocktowerBoardQuery {
    return {
        scriptCode: values.scriptCode,
        playerCount: values.playerCount,
        valid: values.valid,
    }
}

export function savedBoardColumns(
    onDelete: (boardId: number) => Promise<void>,
    onEdit?: (board: ClocktowerBoardConfigResponse) => void,
): ColumnsType<ClocktowerBoardConfigResponse> {
    return [
        {title: '配板编号', dataIndex: 'boardCode', width: 180, render: (value) => <Tag>{value}</Tag>},
        {title: '剧本', dataIndex: 'scriptCode', width: 160},
        {title: '人数', dataIndex: 'playerCount', width: 90},
        {
            title: '角色',
            dataIndex: 'roleCodes',
            render: (roleCodes: string[], record) => <RoleSummaryTags roleCodes={roleCodes} roles={record.roles}/>,
        },
        {
            title: '校验',
            dataIndex: 'valid',
            width: 120,
            render: (_, record) => record.valid
                ? <Tag color="success">校验通过</Tag>
                : <Tag color="error">校验未通过</Tag>,
        },
        {
            title: '创建时间',
            dataIndex: 'createdAt',
            width: 180,
            render: (value?: string | null) => <DateTimeText value={value}/>,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 160,
            render: (_, record) => (
                <Space>
                    <Button
                        disabled={!onEdit}
                        icon={<EditOutlined/>}
                        onClick={() => onEdit?.(record)}
                        size="small"
                    >
                        编辑
                    </Button>
                    <Popconfirm
                        cancelText="取消"
                        okText="删除"
                        okType="danger"
                        onConfirm={() => void onDelete(record.boardId)}
                        title="删除已保存配板？"
                    >
                        <Button danger size="small">删除</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ]
}

export const Component = BoardBuilderPage
