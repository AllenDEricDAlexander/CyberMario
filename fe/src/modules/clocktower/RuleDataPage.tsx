import {BookOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Card, Form, Input, Select, Tabs, Tag, Typography} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useMemo, useRef, useState} from 'react'
import {reportGlobalError} from '../../app/globalError'
import {DataTable} from '../../components/DataTable'
import {FilterBar} from '../../components/FilterBar'
import {PageSection, PageStack} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {StackedCell} from '../../components/StackedCell'
import {voidify} from '../../utils/async'
import {enumCode, enumDesc} from '../../utils/enum'
import {
    getClocktowerGroupedNightOrder,
    getClocktowerJinxRules,
    getClocktowerRoles,
    getClocktowerScripts,
    getClocktowerTerms,
} from './clocktowerService'
import type {
    ClocktowerJinxRuleResponse,
    ClocktowerNightOrderGroupResponse,
    ClocktowerNightOrderResponse,
    ClocktowerRoleResponse,
    ClocktowerRoleType,
    ClocktowerRoleTypeCode,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
    ClocktowerTermResponse,
} from './clocktowerTypes'
import {RoleTypeTag} from './components/RoleTypeTag'
import './clocktower.css'

const roleTypeOptions: Array<{ label: string; value: ClocktowerRoleTypeCode }> = [
    {label: '镇民', value: 'TOWNSFOLK'},
    {label: '外来者', value: 'OUTSIDER'},
    {label: '爪牙', value: 'MINION'},
    {label: '恶魔', value: 'DEMON'},
    {label: '旅行者', value: 'TRAVELER'},
    {label: '传奇', value: 'FABLED'},
]

const emptyNightOrderGroup: ClocktowerNightOrderGroupResponse = {firstNight: [], otherNight: []}

type TermFilterValues = {
    keyword?: string
    category?: string
}

type JinxRuleFilterValues = {
    roleCode?: string
    severity?: string
}

function RuleDataPage() {
    const [termForm] = Form.useForm<TermFilterValues>()
    const [jinxRuleForm] = Form.useForm<JinxRuleFilterValues>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [roles, setRoles] = useState<ClocktowerRoleResponse[]>([])
    const [nightOrder, setNightOrder] = useState<ClocktowerNightOrderGroupResponse>(emptyNightOrderGroup)
    const [terms, setTerms] = useState<ClocktowerTermResponse[]>([])
    const [jinxRules, setJinxRules] = useState<ClocktowerJinxRuleResponse[]>([])
    const [scriptCode, setScriptCode] = useState<ClocktowerScriptCode>()
    const [roleType, setRoleType] = useState<ClocktowerRoleTypeCode>()
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
            reportGlobalError(caught)
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
                reportGlobalError(caught)
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
                reportGlobalError(caught)
            }
        } finally {
            if (roleRequestId === roleRequestIdRef.current) {
                setRoleLoading(false)
            }
        }
    }

    async function loadNightOrderData(selectedScriptCode = scriptCode) {
        if (!selectedScriptCode) {
            return emptyNightOrderGroup
        }
        return getClocktowerGroupedNightOrder(selectedScriptCode)
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
            reportGlobalError(caught)
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
            reportGlobalError(caught)
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
                icon={<BookOutlined/>}
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
        <PageStack>
            <FilterBar<TermFilterValues>
                form={form}
                loading={loading}
                onReset={voidify(onSearch)}
                onSearch={voidify(onSearch)}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="术语或说明"/>
                </Form.Item>
                <Form.Item label="分类" name="category">
                    <Input allowClear placeholder="按分类精确过滤"/>
                </Form.Item>
            </FilterBar>
            <TermTable loading={loading} terms={terms}/>
        </PageStack>
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
        <PageStack>
            <FilterBar<JinxRuleFilterValues>
                form={form}
                loading={loading}
                onReset={voidify(onSearch)}
                onSearch={voidify(onSearch)}
            >
                <Form.Item label="角色代码" name="roleCode">
                    <Input allowClear placeholder="角色代码"/>
                </Form.Item>
                <Form.Item label="严重级别" name="severity">
                    <Input allowClear placeholder="INFO / WARNING / BLOCKER"/>
                </Form.Item>
            </FilterBar>
            <JinxRuleTable jinxRules={jinxRules} loading={loading}/>
        </PageStack>
    )
}

type ScriptRulePanelProps = {
    nightOrderLoading: boolean
    nightOrder: ClocktowerNightOrderGroupResponse
    onRoleTypeChange: (value?: ClocktowerRoleTypeCode) => void
    onScriptChange: (value: ClocktowerScriptCode) => void
    roleLoading: boolean
    roleType?: ClocktowerRoleTypeCode
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
        <PageStack>
            {/* Instant filters: changing either select reloads the script data, so
                this row deliberately stays outside `FilterBar`'s submit flow. */}
            <PageSection
                actions={selectedScript && (
                    <>
                        <Tag>{selectedScript.edition}</Tag>
                        <Tag>{selectedScript.minPlayers}-{selectedScript.maxPlayers} 人</Tag>
                        <Tag>{selectedScript.roleCount} 个角色</Tag>
                    </>
                )}
            >
                <div className="clocktower-field-row">
                    <label>
                        <Typography.Text type="secondary">剧本</Typography.Text>
                        <Select
                            loading={nightOrderLoading || roleLoading}
                            onChange={onScriptChange}
                            options={scriptOptions}
                            placeholder="选择剧本"
                            value={scriptCode}
                        />
                    </label>
                    <label>
                        <Typography.Text type="secondary">角色类型</Typography.Text>
                        <Select
                            allowClear
                            onChange={onRoleTypeChange}
                            options={roleTypeOptions}
                            placeholder="全部类型"
                            value={roleType}
                        />
                    </label>
                </div>
            </PageSection>
            <RoleTable loading={roleLoading} roles={roles}/>
            <NightOrderTables loading={nightOrderLoading} nightOrder={nightOrder}/>
        </PageStack>
    )
}

function RoleTable({loading, roles}: { loading: boolean; roles: ClocktowerRoleResponse[] }) {
    const columns: ColumnsType<ClocktowerRoleResponse> = [
        {
            title: '角色',
            dataIndex: 'roleCode',
            width: 200,
            render: (_, record) => (
                <StackedCell primary={nullableText(record.roleName ?? record.name)} secondary={record.roleCode}/>
            ),
        },
        {
            title: '类型',
            dataIndex: 'roleType',
            width: 120,
            render: (value: ClocktowerRoleType) => <RoleTypeTag value={value}/>,
        },
        {
            title: '阵营',
            dataIndex: 'alignment',
            width: 100,
            render: (value: ClocktowerRoleResponse['alignment']) => <Tag>{enumDesc(value)}</Tag>,
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
        <DataTable<ClocktowerRoleResponse>
            columns={columns}
            count={roles.length}
            dataSource={roles}
            emptyDescription="换一个剧本或清空角色类型筛选后再试。"
            emptyTitle="暂无角色规则"
            loading={loading}
            pagination={false}
            rowKey="roleCode"
            scroll={{x: 1000}}
            title="角色"
        />
    )
}

function NightOrderTables({loading, nightOrder}: { loading: boolean; nightOrder: ClocktowerNightOrderGroupResponse }) {
    return (
        <PageSection description="按说书人唤醒顺序排列，未参与夜晚的角色不会出现在这里。" title="夜晚顺序">
            <PageStack>
                <NightOrderTable loading={loading} nightOrder={nightOrder.firstNight} title="首夜"/>
                <NightOrderTable loading={loading} nightOrder={nightOrder.otherNight} title="其他夜晚"/>
            </PageStack>
        </PageSection>
    )
}

function NightOrderTable({loading, nightOrder, title}: {
    loading: boolean
    nightOrder: ClocktowerNightOrderResponse[]
    title: string
}) {
    const columns: ColumnsType<ClocktowerNightOrderResponse> = [
        {title: '顺序', dataIndex: 'orderNo', width: 90, render: (_, record) => record.orderNo ?? record.sortOrder},
        {
            title: '角色',
            dataIndex: 'roleCode',
            width: 200,
            render: (_, record) => <StackedCell primary={record.roleName} secondary={record.roleCode}/>,
        },
        {
            title: '类型',
            dataIndex: 'roleType',
            width: 120,
            render: (value: ClocktowerRoleType) => <RoleTypeTag value={value}/>,
        },
        {title: '提醒', dataIndex: 'reminderText', render: nullableText},
    ]

    return (
        <DataTable<ClocktowerNightOrderResponse>
            columns={columns}
            count={nightOrder.length}
            dataSource={nightOrder}
            emptyDescription="该剧本在此夜晚没有需要唤醒的角色。"
            emptyTitle="暂无夜晚顺序"
            loading={loading}
            pagination={false}
            rowKey={(record) => `${enumCode(record.nightType)}-${record.orderNo ?? record.sortOrder}-${record.roleCode}`}
            scroll={{x: 720}}
            title={title}
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
        <DataTable<ClocktowerTermResponse>
            columns={columns}
            count={terms.length}
            dataSource={terms}
            emptyDescription="没有匹配的术语，换一个关键词或清空分类后重试。"
            emptyTitle="暂无术语"
            loading={loading}
            pagination={false}
            rowKey={(record) => `${record.category}-${record.term}`}
            scroll={{x: 720}}
            title="术语表"
        />
    )
}

function JinxRuleTable({jinxRules, loading}: { jinxRules: ClocktowerJinxRuleResponse[]; loading: boolean }) {
    const columns: ColumnsType<ClocktowerJinxRuleResponse> = [
        {
            title: '角色组合',
            dataIndex: 'roleACode',
            width: 220,
            render: (_, record) => (
                <StackedCell primary={`${record.roleACode} × ${record.roleBCode}`} secondary={record.effectType}/>
            ),
        },
        {
            title: '范围',
            dataIndex: 'scope',
            width: 120,
            render: (value: string) => <Tag>{value}</Tag>,
        },
        {title: '严重度', dataIndex: 'severity', width: 120, render: severityTag},
        {title: '规则', dataIndex: 'ruleText'},
    ]

    return (
        <DataTable<ClocktowerJinxRuleResponse>
            columns={columns}
            count={jinxRules.length}
            dataSource={jinxRules}
            emptyDescription="没有匹配的相克规则，换一个角色代码或严重级别后重试。"
            emptyTitle="暂无相克规则"
            loading={loading}
            pagination={false}
            rowKey={(record) => `${record.roleACode}-${record.roleBCode}-${record.effectType}-${record.scope}-${record.severity}-${record.ruleText}`}
            scroll={{x: 900}}
            title="相克规则"
        />
    )
}

function nullableNumber(value?: number | null) {
    return value ?? '-'
}

function nullableText(value?: string | null) {
    return value || '-'
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
