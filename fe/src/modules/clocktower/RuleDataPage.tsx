import {ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {App, Button, Card, Empty, Form, Input, Select, Space, Table, Tabs, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useMemo, useRef, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerJinxRules,
    getClocktowerNightOrder,
    getClocktowerRoles,
    getClocktowerScripts,
    getClocktowerTerms,
} from './clocktowerService'
import type {
    ClocktowerJinxRuleResponse,
    ClocktowerNightOrderResponse,
    ClocktowerRoleResponse,
    ClocktowerRoleType,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
    ClocktowerTermResponse,
} from './clocktowerTypes'
import {RoleTypeTag} from './components/RoleTypeTag'

const roleTypeOptions: Array<{ label: string; value: ClocktowerRoleType }> = [
    {label: '镇民', value: 'TOWNSFOLK'},
    {label: '外来者', value: 'OUTSIDER'},
    {label: '爪牙', value: 'MINION'},
    {label: '恶魔', value: 'DEMON'},
    {label: '旅行者', value: 'TRAVELER'},
    {label: '传奇', value: 'FABLED'},
]

type TermFilterValues = {
    keyword?: string
    category?: string
}

type JinxRuleFilterValues = {
    roleCode?: string
    severity?: string
}

function RuleDataPage() {
    const {message} = App.useApp()
    const [termForm] = Form.useForm<TermFilterValues>()
    const [jinxRuleForm] = Form.useForm<JinxRuleFilterValues>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [roles, setRoles] = useState<ClocktowerRoleResponse[]>([])
    const [nightOrder, setNightOrder] = useState<ClocktowerNightOrderResponse[]>([])
    const [terms, setTerms] = useState<ClocktowerTermResponse[]>([])
    const [jinxRules, setJinxRules] = useState<ClocktowerJinxRuleResponse[]>([])
    const [scriptCode, setScriptCode] = useState<ClocktowerScriptCode>()
    const [roleType, setRoleType] = useState<ClocktowerRoleType>()
    const [initialLoading, setInitialLoading] = useState(false)
    const [scriptLoading, setScriptLoading] = useState(false)
    const [roleLoading, setRoleLoading] = useState(false)
    const [termLoading, setTermLoading] = useState(false)
    const [jinxRuleLoading, setJinxRuleLoading] = useState(false)
    const scriptRequestIdRef = useRef(0)
    const roleRequestIdRef = useRef(0)
    const previousScriptCodeRef = useRef<ClocktowerScriptCode | undefined>(undefined)

    useEffect(() => {
        void loadInitialData()
    }, [])

    useEffect(() => {
        if (scriptCode) {
            if (previousScriptCodeRef.current === scriptCode) {
                void loadRoleData(scriptCode, roleType)
            } else {
                previousScriptCodeRef.current = scriptCode
                void loadScriptData(scriptCode, roleType)
            }
        }
        if (!scriptCode) {
            previousScriptCodeRef.current = undefined
        }
    }, [scriptCode, roleType])

    async function loadInitialData() {
        setInitialLoading(true)
        try {
            const [scriptResponse, termResponse, jinxRuleResponse] = await Promise.all([
                getClocktowerScripts(),
                getClocktowerTerms(),
                getClocktowerJinxRules(),
            ])
            setScripts(scriptResponse)
            setTerms(termResponse)
            setJinxRules(jinxRuleResponse)
            setScriptCode((current) => current ?? scriptResponse[0]?.scriptCode)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setInitialLoading(false)
        }
    }

    async function loadScriptData(selectedScriptCode = scriptCode, selectedRoleType = roleType) {
        if (!selectedScriptCode) {
            return
        }
        const scriptRequestId = scriptRequestIdRef.current + 1
        scriptRequestIdRef.current = scriptRequestId
        setScriptLoading(true)
        try {
            const [, nightOrderResponse] = await Promise.all([
                loadRoleData(selectedScriptCode, selectedRoleType),
                loadNightOrderData(selectedScriptCode),
            ])
            if (scriptRequestId !== scriptRequestIdRef.current) {
                return
            }
            setNightOrder(nightOrderResponse)
        } catch (caught) {
            if (scriptRequestId === scriptRequestIdRef.current) {
                message.error(resolveErrorMessage(caught))
            }
        } finally {
            if (scriptRequestId === scriptRequestIdRef.current) {
                setScriptLoading(false)
            }
        }
    }

    async function loadRoleData(selectedScriptCode = scriptCode, selectedRoleType = roleType) {
        if (!selectedScriptCode) {
            return undefined
        }
        const roleRequestId = roleRequestIdRef.current + 1
        roleRequestIdRef.current = roleRequestId
        setRoleLoading(true)
        try {
            const response = await getClocktowerRoles(selectedScriptCode, {
                roleType: selectedRoleType,
                enabled: true,
            })
            if (roleRequestId !== roleRequestIdRef.current) {
                return
            }
            setRoles(response)
        } catch (caught) {
            if (roleRequestId === roleRequestIdRef.current) {
                message.error(resolveErrorMessage(caught))
            }
        } finally {
            if (roleRequestId === roleRequestIdRef.current) {
                setRoleLoading(false)
            }
        }
    }

    async function loadNightOrderData(selectedScriptCode = scriptCode) {
        if (!selectedScriptCode) {
            return []
        }
        return getClocktowerNightOrder(selectedScriptCode)
    }

    async function loadTerms() {
        setTermLoading(true)
        try {
            const values = termForm.getFieldsValue()
            const response = await getClocktowerTerms({
                keyword: normalizeFilterValue(values.keyword),
                category: normalizeFilterValue(values.category),
            })
            setTerms(response)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setTermLoading(false)
        }
    }

    async function loadJinxRules() {
        setJinxRuleLoading(true)
        try {
            const values = jinxRuleForm.getFieldsValue()
            const response = await getClocktowerJinxRules({
                roleCode: normalizeFilterValue(values.roleCode),
                severity: normalizeFilterValue(values.severity),
            })
            setJinxRules(response)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setJinxRuleLoading(false)
        }
    }

    const scriptOptions = useMemo(
        () =>
            scripts.map((script) => ({
                label: `${script.name} (${script.minPlayers}-${script.maxPlayers}人)`,
                value: script.scriptCode,
            })),
        [scripts],
    )

    return (
        <>
            <PageToolbar
                actions={
                    <Button
                        icon={<ReloadOutlined/>}
                        loading={initialLoading || scriptLoading}
                        onClick={voidify(async () => {
                            await loadInitialData()
                            await loadScriptData()
                        })}
                    >
                        刷新
                    </Button>
                }
                description="查看剧本角色、夜晚顺序、术语和相克规则。"
                title="钟楼规则"
            />
            <Card>
                <Tabs
                    items={[
                        {
                            key: 'script',
                            label: '剧本规则',
                            children: (
                                <ScriptRulePanel
                                    nightOrderLoading={initialLoading || scriptLoading}
                                    nightOrder={nightOrder}
                                    onRoleTypeChange={setRoleType}
                                    onScriptChange={setScriptCode}
                                    roleLoading={initialLoading || scriptLoading || roleLoading}
                                    roleType={roleType}
                                    roles={roles}
                                    scriptCode={scriptCode}
                                    scripts={scripts}
                                    scriptOptions={scriptOptions}
                                />
                            ),
                        },
                        {
                            key: 'terms',
                            label: '术语',
                            forceRender: true,
                            children: (
                                <TermRulePanel
                                    form={termForm}
                                    loading={initialLoading || termLoading}
                                    onSearch={loadTerms}
                                    terms={terms}
                                />
                            ),
                        },
                        {
                            key: 'jinx',
                            label: '相克规则',
                            forceRender: true,
                            children: (
                                <JinxRulePanel
                                    form={jinxRuleForm}
                                    jinxRules={jinxRules}
                                    loading={initialLoading || jinxRuleLoading}
                                    onSearch={loadJinxRules}
                                />
                            ),
                        },
                    ]}
                />
            </Card>
        </>
    )
}

type TermRulePanelProps = {
    form: ReturnType<typeof Form.useForm<TermFilterValues>>[0]
    loading: boolean
    onSearch: () => Promise<void>
    terms: ClocktowerTermResponse[]
}

function TermRulePanel({form, loading, onSearch, terms}: TermRulePanelProps) {
    return (
        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
            <Form form={form} layout="inline">
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="术语或说明" style={{width: 200}}/>
                </Form.Item>
                <Form.Item label="分类" name="category">
                    <Input allowClear placeholder="分类" style={{width: 160}}/>
                </Form.Item>
                <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(onSearch)} type="primary">
                    查询
                </Button>
            </Form>
            <TermTable loading={loading} terms={terms}/>
        </Space>
    )
}

type JinxRulePanelProps = {
    form: ReturnType<typeof Form.useForm<JinxRuleFilterValues>>[0]
    jinxRules: ClocktowerJinxRuleResponse[]
    loading: boolean
    onSearch: () => Promise<void>
}

function JinxRulePanel({form, jinxRules, loading, onSearch}: JinxRulePanelProps) {
    return (
        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
            <Form form={form} layout="inline">
                <Form.Item label="角色代码" name="roleCode">
                    <Input allowClear placeholder="角色代码" style={{width: 200}}/>
                </Form.Item>
                <Form.Item label="严重级别" name="severity">
                    <Input allowClear placeholder="INFO / WARNING / BLOCKER" style={{width: 220}}/>
                </Form.Item>
                <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(onSearch)} type="primary">
                    查询
                </Button>
            </Form>
            <JinxRuleTable jinxRules={jinxRules} loading={loading}/>
        </Space>
    )
}

type ScriptRulePanelProps = {
    nightOrderLoading: boolean
    nightOrder: ClocktowerNightOrderResponse[]
    onRoleTypeChange: (value?: ClocktowerRoleType) => void
    onScriptChange: (value: ClocktowerScriptCode) => void
    roleLoading: boolean
    roleType?: ClocktowerRoleType
    roles: ClocktowerRoleResponse[]
    scriptCode?: ClocktowerScriptCode
    scripts: ClocktowerScriptResponse[]
    scriptOptions: Array<{ label: string; value: ClocktowerScriptCode }>
}

function ScriptRulePanel({
    nightOrderLoading,
    nightOrder,
    onRoleTypeChange,
    onScriptChange,
    roleLoading,
    roleType,
    roles,
    scriptCode,
    scripts,
    scriptOptions,
}: ScriptRulePanelProps) {
    const selectedScript = scripts.find((script) => script.scriptCode === scriptCode)

    return (
        <Space orientation="vertical" size="large" style={{width: '100%'}}>
            <Space align="end" wrap>
                <Space orientation="vertical" size={4}>
                    <Typography.Text type="secondary">剧本</Typography.Text>
                    <Select
                        loading={nightOrderLoading || roleLoading}
                        onChange={onScriptChange}
                        options={scriptOptions}
                        placeholder="选择剧本"
                        style={{minWidth: 240}}
                        value={scriptCode}
                    />
                </Space>
                <Space orientation="vertical" size={4}>
                    <Typography.Text type="secondary">角色类型</Typography.Text>
                    <Select
                        allowClear
                        onChange={onRoleTypeChange}
                        options={roleTypeOptions}
                        placeholder="全部类型"
                        style={{minWidth: 160}}
                        value={roleType}
                    />
                </Space>
                {selectedScript && (
                    <Space wrap>
                        <Tag>{selectedScript.edition}</Tag>
                        <Tag>
                            {selectedScript.minPlayers}-{selectedScript.maxPlayers} 人
                        </Tag>
                        <Tag>{selectedScript.roleCount} 个角色</Tag>
                    </Space>
                )}
            </Space>
            <Card size="small" title="角色">
                <RoleTable loading={roleLoading} roles={roles}/>
            </Card>
            <Card size="small" title="夜晚顺序">
                <NightOrderTable loading={nightOrderLoading} nightOrder={nightOrder}/>
            </Card>
        </Space>
    )
}

function RoleTable({loading, roles}: { loading: boolean; roles: ClocktowerRoleResponse[] }) {
    const columns: ColumnsType<ClocktowerRoleResponse> = [
        {title: '角色代码', dataIndex: 'roleCode', width: 160},
        {title: '名称', dataIndex: 'name', width: 140},
        {
            title: '类型',
            dataIndex: 'roleType',
            width: 120,
            render: (value: ClocktowerRoleType) => <RoleTypeTag value={value}/>,
        },
        {title: '能力', dataIndex: 'abilityText'},
        {
            title: '首夜',
            dataIndex: 'firstNightOrder',
            width: 90,
            render: nullableNumber,
        },
        {
            title: '其他夜',
            dataIndex: 'otherNightOrder',
            width: 100,
            render: nullableNumber,
        },
        {
            title: '状态',
            dataIndex: 'enabled',
            width: 90,
            render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag>,
        },
    ]

    return (
        <Table<ClocktowerRoleResponse>
            columns={columns}
            dataSource={roles}
            loading={loading}
            locale={{emptyText: <Empty description="暂无角色规则"/>}}
            pagination={false}
            rowKey="roleCode"
            scroll={{x: 1100}}
        />
    )
}

function NightOrderTable({loading, nightOrder}: { loading: boolean; nightOrder: ClocktowerNightOrderResponse[] }) {
    const columns: ColumnsType<ClocktowerNightOrderResponse> = [
        {title: '顺序', dataIndex: 'sortOrder', width: 90},
        {
            title: '夜晚',
            dataIndex: 'nightType',
            width: 120,
            render: nightTypeLabel,
        },
        {title: '角色代码', dataIndex: 'roleCode', width: 160},
        {title: '名称', dataIndex: 'roleName', width: 140},
        {
            title: '类型',
            dataIndex: 'roleType',
            width: 120,
            render: (value: ClocktowerRoleType) => <RoleTypeTag value={value}/>,
        },
        {title: '提醒', dataIndex: 'reminderText', render: nullableText},
    ]

    return (
        <Table<ClocktowerNightOrderResponse>
            columns={columns}
            dataSource={nightOrder}
            loading={loading}
            locale={{emptyText: <Empty description="暂无夜晚顺序"/>}}
            pagination={false}
            rowKey={(record) => `${record.nightType}-${record.sortOrder}-${record.roleCode}`}
            scroll={{x: 900}}
        />
    )
}

function TermTable({loading, terms}: { loading: boolean; terms: ClocktowerTermResponse[] }) {
    const columns: ColumnsType<ClocktowerTermResponse> = [
        {title: '术语', dataIndex: 'term', width: 160},
        {
            title: '分类',
            dataIndex: 'category',
            width: 140,
            render: (value: string) => <Tag>{value}</Tag>,
        },
        {title: '说明', dataIndex: 'description'},
    ]

    return (
        <Table<ClocktowerTermResponse>
            columns={columns}
            dataSource={terms}
            loading={loading}
            locale={{emptyText: <Empty description="暂无术语"/>}}
            pagination={false}
            rowKey={(record) => `${record.category}-${record.term}`}
            scroll={{x: 720}}
        />
    )
}

function JinxRuleTable({jinxRules, loading}: { jinxRules: ClocktowerJinxRuleResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerJinxRuleResponse> = [
        {title: '角色 A', dataIndex: 'roleACode', width: 160},
        {title: '角色 B', dataIndex: 'roleBCode', width: 160},
        {
            title: '范围',
            dataIndex: 'scope',
            width: 120,
            render: (value: string) => <Tag>{value}</Tag>,
        },
        {title: '严重度', dataIndex: 'severity', width: 120, render: severityTag},
        {title: '效果', dataIndex: 'effectType', width: 140},
        {title: '规则', dataIndex: 'ruleText'},
    ]

    return (
        <Table<ClocktowerJinxRuleResponse>
            columns={columns}
            dataSource={jinxRules}
            loading={loading}
            locale={{emptyText: <Empty description="暂无相克规则"/>}}
            pagination={false}
            rowKey={(record) => `${record.roleACode}-${record.roleBCode}-${record.effectType}-${record.scope}-${record.severity}-${record.ruleText}`}
            scroll={{x: 980}}
        />
    )
}

function nullableNumber(value?: number | null) {
    return value ?? '-'
}

function nullableText(value?: string | null) {
    return value || '-'
}

function nightTypeLabel(value: string) {
    const labels: Record<string, string> = {
        FIRST_NIGHT: '首夜',
        OTHER_NIGHT: '其他夜',
    }
    return <Tag color="blue">{labels[value] ?? value}</Tag>
}

function severityTag(value: string) {
    const colors: Record<string, string> = {
        INFO: 'blue',
        WARNING: 'warning',
        BLOCKER: 'error',
    }
    return <Tag color={colors[value] ?? 'default'}>{value}</Tag>
}

function normalizeFilterValue(value?: string) {
    const trimmed = value?.trim()
    return trimmed || undefined
}

export const Component = RuleDataPage
