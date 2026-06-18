import {CheckOutlined, ReloadOutlined, SendOutlined} from '@ant-design/icons'
import {App, Button, Card, Col, Empty, Form, Input, List, Row, Select, Space, Tabs, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {useParams} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {
    getClocktowerGrimoire,
    getClocktowerNightChecklist,
    submitClocktowerStorytellerAction,
} from './clocktowerService'
import type {
    ClocktowerGrimoireResponse,
    ClocktowerNightChecklistResponse,
    ClocktowerStorytellerActionRequest,
} from './clocktowerTypes'
import {EventTimeline} from './components/EventTimeline'
import {NightChecklist} from './components/NightChecklist'
import {RoleTypeTag} from './components/RoleTypeTag'

function StorytellerGrimoirePage() {
    const {roomId} = useParams()
    const {message} = App.useApp()
    const [form] = Form.useForm<ClocktowerStorytellerActionRequest>()
    const [grimoire, setGrimoire] = useState<ClocktowerGrimoireResponse | null>(null)
    const [checklist, setChecklist] = useState<ClocktowerNightChecklistResponse | null>(null)
    const [loading, setLoading] = useState(false)
    const [submitting, setSubmitting] = useState(false)
    const [resolvingTaskId, setResolvingTaskId] = useState<number | null>(null)
    const numericRoomId = Number(roomId)

    const load = useCallback(async () => {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            const [grimoireResponse, checklistResponse] = await Promise.all([
                getClocktowerGrimoire(numericRoomId),
                getClocktowerNightChecklist(numericRoomId),
            ])
            setGrimoire(grimoireResponse)
            setChecklist(checklistResponse)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }, [message, numericRoomId])

    useEffect(() => {
        void load()
    }, [load])

    async function submitAction() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        const values = await form.validateFields()
        setSubmitting(true)
        try {
            const response = await submitClocktowerStorytellerAction(numericRoomId, {
                ...values,
                targetSeatIds: values.targetSeatIds ?? [],
                payload: {},
            })
            setGrimoire(response.grimoire)
            message.success(response.accepted ? '说书人操作已提交' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setSubmitting(false)
        }
    }

    async function resolveTask(taskId: number) {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setResolvingTaskId(taskId)
        try {
            const response = await submitClocktowerStorytellerAction(numericRoomId, {
                actionType: 'RESOLVE_TASK',
                targetSeatIds: [],
                note: null,
                payload: {taskId},
            })
            const checklistResponse = await getClocktowerNightChecklist(numericRoomId)
            setGrimoire(response.grimoire)
            setChecklist(checklistResponse)
            message.success(response.accepted ? '任务已处理' : `操作被拒绝：${response.rejectedCode ?? '-'}`)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setResolvingTaskId(null)
        }
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>}
                description={grimoire ? `${grimoire.phase.phase} · 第 ${grimoire.phase.dayNo} 天 / 第 ${grimoire.phase.nightNo} 夜` : '管理身份、标记、夜晚顺序和说书人裁定。'}
                title="说书人魔典"
            />
            <Row gutter={[16, 16]}>
                <Col lg={15} xs={24}>
                    <Card loading={loading} title="魔典座位">
                        <GrimoireSeatList grimoire={grimoire}/>
                    </Card>
                </Col>
                <Col lg={9} xs={24}>
                    <Card>
                        <Tabs
                            items={[
                                {
                                    key: 'tasks',
                                    label: '待处理任务',
                                    children: (
                                        <TaskList
                                            grimoire={grimoire}
                                            onResolve={resolveTask}
                                            resolvingTaskId={resolvingTaskId}
                                        />
                                    ),
                                },
                                {
                                    key: 'night',
                                    label: '夜晚顺序',
                                    children: <NightChecklist checklist={checklist} loading={loading}/>,
                                },
                                {
                                    key: 'action',
                                    label: '说书人动作',
                                    children: (
                                        <StorytellerActionForm
                                            form={form}
                                            grimoire={grimoire}
                                            loading={submitting}
                                            onSubmit={submitAction}
                                        />
                                    ),
                                },
                                {
                                    key: 'events',
                                    label: '事件',
                                    children: <EventTimeline events={[]}/>,
                                },
                            ]}
                        />
                    </Card>
                </Col>
            </Row>
        </>
    )
}

function GrimoireSeatList({grimoire}: { grimoire: ClocktowerGrimoireResponse | null }) {
    if (!grimoire || grimoire.seats.length === 0) {
        return <Empty description="暂无座位"/>
    }
    return (
        <List
            dataSource={grimoire.seats}
            renderItem={(seat) => (
                <List.Item>
                    <Space wrap>
                        <Tag>{seat.seatNo}</Tag>
                        <Typography.Text strong>{seat.displayName}</Typography.Text>
                        <Tag>{seat.roleCode ?? '未分配'}</Tag>
                        <RoleTypeTag value={seat.roleType}/>
                        <Tag color={seat.alive ? 'success' : 'error'}>{seat.alive ? '存活' : '死亡'}</Tag>
                        <Tag color={seat.hasDeadVote ? 'warning' : 'default'}>
                            {seat.hasDeadVote ? '死票可用' : '死票已用'}
                        </Tag>
                        {seat.notes && <Typography.Text type="secondary">{seat.notes}</Typography.Text>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

export function TaskList({
                             grimoire,
                             onResolve,
                             resolvingTaskId,
                         }: {
    grimoire: ClocktowerGrimoireResponse | null
    onResolve: (taskId: number) => Promise<void>
    resolvingTaskId: number | null
}) {
    if (!grimoire || grimoire.pendingTasks.length === 0) {
        return <Empty description="暂无待处理任务"/>
    }
    return (
        <List
            dataSource={grimoire.pendingTasks}
            renderItem={(task) => (
                <List.Item
                    actions={[
                        <Button
                            disabled={task.status !== 'PENDING'}
                            icon={<CheckOutlined/>}
                            key="resolve"
                            loading={resolvingTaskId === task.taskId}
                            onClick={voidify(() => onResolve(task.taskId))}
                            size="small"
                            type="link"
                        >
                            完成
                        </Button>,
                    ]}
                >
                    <Space wrap>
                        <Tag>{taskTypeText(task.taskType)}</Tag>
                        <Tag color={task.status === 'DONE' ? 'success' : 'warning'}>{taskStatusText(task.status)}</Tag>
                        <Typography.Text>{task.roleCode ?? '-'}</Typography.Text>
                        {task.note && <Typography.Text type="secondary">{task.note}</Typography.Text>}
                    </Space>
                </List.Item>
            )}
        />
    )
}

function taskTypeText(taskType: string) {
    return taskType === 'WAKE_ROLE' ? '唤醒角色' : taskType
}

function taskStatusText(status: string) {
    if (status === 'PENDING') {
        return '待处理'
    }
    return status === 'DONE' ? '已完成' : status
}

function StorytellerActionForm({
                                   form,
                                   grimoire,
                                   loading,
                                   onSubmit,
                               }: {
    form: ReturnType<typeof Form.useForm<ClocktowerStorytellerActionRequest>>[0]
    grimoire: ClocktowerGrimoireResponse | null
    loading: boolean
    onSubmit: () => Promise<void>
}) {
    return (
        <Form form={form} layout="vertical">
            <Form.Item label="动作" name="actionType" rules={[{required: true, message: '请选择动作'}]}>
                <Select options={[
                    {label: '阶段推进', value: 'ADVANCE_PHASE'},
                    {label: '说书人裁定', value: 'STORYTELLER_RULING'},
                    {label: '添加标记', value: 'ADD_MARKER'},
                    {label: '移除标记', value: 'REMOVE_MARKER'},
                ]}/>
            </Form.Item>
            <Form.Item label="目标座位" name="targetSeatIds">
                <Select
                    mode="multiple"
                    options={(grimoire?.seats ?? []).map((seat) => ({
                        label: `${seat.seatNo} ${seat.displayName}`,
                        value: seat.seatId,
                    }))}
                    placeholder="可选"
                />
            </Form.Item>
            <Form.Item label="备注" name="note">
                <Input.TextArea rows={3}/>
            </Form.Item>
            <Button icon={<SendOutlined/>} loading={loading} onClick={voidify(onSubmit)} type="primary">
                提交说书人操作
            </Button>
        </Form>
    )
}

export const Component = StorytellerGrimoirePage
