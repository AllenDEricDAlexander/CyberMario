import {Alert, Button, Card, Form, Input, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {VisualBackdrop} from '../../../components/VisualBackdrop'
import {voidify} from '../../../utils/async'
import {completeAccountActivation} from '../authService'

type ActivationForm = {
    password: string
    confirmPassword: string
}

const INVALID_LINK_MESSAGE = '激活链接无效或已过期，请联系管理员重新发送'

export function ActivationPage() {
    const navigate = useNavigate()
    const [token, setToken] = useState(() => {
        const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
        return params.get('token') ?? ''
    })
    const [error, setError] = useState(token ? '' : INVALID_LINK_MESSAGE)
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (window.location.hash) {
            window.history.replaceState(window.history.state, '',
                `${window.location.pathname}${window.location.search}`)
        }
    }, [])

    async function handleFinish(values: ActivationForm) {
        if (!token) {
            setError(INVALID_LINK_MESSAGE)
            return
        }
        if (values.password !== values.confirmPassword) {
            setError('两次输入的密码不一致')
            return
        }
        setSubmitting(true)
        setError('')
        try {
            await completeAccountActivation(token, values.password)
            setToken('')
            void navigate('/login?activated=1', {replace: true})
        } catch {
            setError(INVALID_LINK_MESSAGE)
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="auth-page">
            <VisualBackdrop particleCount={24} variant="auth"/>
            <section className="auth-hero" aria-label="CyberMario 账号激活">
                <Card className="auth-card">
                    <Typography.Title level={2}>激活账号</Typography.Title>
                    <Typography.Paragraph type="secondary">
                        设置首个登录密码，完成后返回登录页。
                    </Typography.Paragraph>
                    {error && <Alert showIcon className="auth-alert" message={error} type="error"/>}
                    <Form<ActivationForm> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                        <Form.Item
                            label="新密码"
                            name="password"
                            rules={[
                                {required: true, message: '请输入新密码'},
                                {min: 8, max: 128, message: '密码长度必须为 8–128 位'},
                            ]}
                        >
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                        <Form.Item
                            label="确认密码"
                            name="confirmPassword"
                            rules={[{required: true, message: '请再次输入新密码'}]}
                        >
                            <Input.Password autoComplete="new-password"/>
                        </Form.Item>
                        <Button block disabled={!token} htmlType="submit" loading={submitting} type="primary">
                            完成激活
                        </Button>
                    </Form>
                </Card>
            </section>
        </div>
    )
}
