import {SearchOutlined} from '@ant-design/icons'
import {App, Button, Card, Col, Form, InputNumber, Row, Select, Space, Tag, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {useParams} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {getClocktowerReplay} from './clocktowerService'
import type {ClocktowerEventResponse, ClocktowerReplayResponse} from './clocktowerTypes'
import {EventTimeline} from './components/EventTimeline'

type ReplayFormValues = {
    mode?: string
    fromSeq?: number
    toSeq?: number
}

function ReplayPage() {
    const {roomId} = useParams()
    const {message} = App.useApp()
    const [form] = Form.useForm<ReplayFormValues>()
    const [replay, setReplay] = useState<ClocktowerReplayResponse | null>(null)
    const [selected, setSelected] = useState<ClocktowerEventResponse | null>(null)
    const [loading, setLoading] = useState(false)
    const numericRoomId = Number(roomId)

    useEffect(() => {
        void loadReplay()
    }, [roomId])

    async function loadReplay() {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            const values = form.getFieldsValue()
            const response = await getClocktowerReplay(numericRoomId, values)
            setReplay(response)
            setSelected(response.events[0] ?? null)
        } catch (caught) {
            message.error(resolveErrorMessage(caught))
        } finally {
            setLoading(false)
        }
    }

    return (
        <>
            <PageToolbar
                description="按事件序号查看公开或说书人视角的基础回放。"
                title="钟楼回放"
            />
            <Card style={{marginBottom: 16}}>
                <Form form={form} initialValues={{mode: 'PUBLIC'}} layout="inline">
                    <Form.Item label="模式" name="mode">
                        <Select
                            options={[
                                {label: '公开', value: 'PUBLIC'},
                                {label: '说书人', value: 'STORYTELLER'},
                                {label: '审计', value: 'AUDIT'},
                            ]}
                            style={{width: 140}}
                        />
                    </Form.Item>
                    <Form.Item label="起始序号" name="fromSeq">
                        <InputNumber min={1}/>
                    </Form.Item>
                    <Form.Item label="结束序号" name="toSeq">
                        <InputNumber min={1}/>
                    </Form.Item>
                    <Button icon={<SearchOutlined/>} loading={loading} onClick={voidify(loadReplay)} type="primary">
                        查询
                    </Button>
                </Form>
            </Card>
            <Row gutter={[16, 16]}>
                <Col lg={15} xs={24}>
                    <Card loading={loading} title="事件时间线">
                        <EventTimeline events={replay?.events ?? []}/>
                    </Card>
                </Col>
                <Col lg={9} xs={24}>
                    <Card title="事件详情">
                        {selected ? (
                            <Space orientation="vertical">
                                <Space wrap>
                                    <Tag>#{selected.seqNo}</Tag>
                                    <Tag color="blue">{selected.eventType}</Tag>
                                    <Tag>{selected.visibility}</Tag>
                                </Space>
                                <Typography.Text type="secondary">
                                    {selected.phase} · 第 {selected.dayNo} 天 / 第 {selected.nightNo} 夜
                                </Typography.Text>
                                <Typography.Paragraph copyable code>
                                    {JSON.stringify(selected.payload, null, 2)}
                                </Typography.Paragraph>
                            </Space>
                        ) : (
                            <Typography.Text type="secondary">暂无选中事件</Typography.Text>
                        )}
                    </Card>
                </Col>
            </Row>
        </>
    )
}

export const Component = ReplayPage
