import {ExperimentOutlined, ReloadOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Form, InputNumber, Select, Space, Switch} from 'antd'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {resolveErrorMessage} from '../../services/request'
import {voidify} from '../../utils/async'
import {generateClocktowerBoard, getClocktowerScripts} from './clocktowerService'
import type {
    ClocktowerBoardCandidateResponse,
    ClocktowerBoardGenerateRequest,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
} from './clocktowerTypes'
import {BoardCandidateTable} from './components/BoardCandidateTable'

function BoardBuilderPage() {
    const [form] = Form.useForm<ClocktowerBoardGenerateRequest>()
    const [scripts, setScripts] = useState<ClocktowerScriptResponse[]>([])
    const [candidates, setCandidates] = useState<ClocktowerBoardCandidateResponse[]>([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState<string>()

    useEffect(() => {
        void loadScripts()
    }, [])

    async function loadScripts() {
        try {
            setScripts(await getClocktowerScripts())
        } catch (caught) {
            setError(resolveErrorMessage(caught))
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

    return (
        <>
            <PageToolbar
                actions={<Button icon={<ReloadOutlined/>} onClick={() => void loadScripts()}>刷新剧本</Button>}
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
                        <Button icon={<ExperimentOutlined/>} loading={loading} onClick={voidify(generate)} type="primary">
                            生成配板
                        </Button>
                    </Space>
                </Form>
            </Card>
            <Card style={{marginTop: 16}} title="候选配板">
                <BoardCandidateTable candidates={candidates} loading={loading}/>
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

export const Component = BoardBuilderPage
