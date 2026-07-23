import {LockOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Form, Input, Typography} from 'antd'
import {useState} from 'react'
import {Link, Navigate, useLocation, useNavigate} from 'react-router'
import {VisualBackdrop} from '../../../components/VisualBackdrop'
import {resolveErrorMessage} from '../../../services/request'
import {voidify} from '../../../utils/async'
import {useAuth} from '../authStore'
import type {LoginRequest} from '../authTypes'

type LocationState = {
    from?: {
        pathname?: string
    }
}

export function LoginPage() {
    const auth = useAuth()
    const navigate = useNavigate()
    const location = useLocation()
    const [error, setError] = useState('')

    const state = location.state as LocationState | null
    const redirectTo = state?.from?.pathname || '/chat'
    const activated = new URLSearchParams(location.search).get('activated') === '1'

    if (auth.authenticated) {
        return <Navigate replace to={redirectTo}/>
    }

    async function handleFinish(values: LoginRequest) {
        setError('')
        try {
            await auth.login(values)
            void navigate(redirectTo, {replace: true})
        } catch (requestError) {
            setError(resolveErrorMessage(requestError))
        }
    }

    return (
        <div className="auth-page">
            <VisualBackdrop particleCount={24} variant="auth"/>
            <section className="auth-hero" aria-label="CyberMario 登录">
                <div className="auth-copy">
                    <Typography.Text className="auth-brand">CyberMario</Typography.Text>
                    <Typography.Title level={1}>Agent Control Workspace</Typography.Title>
                    <Typography.Paragraph>
                        统一管理 Agent、权限、知识库与会话工作流，让每一次自动化执行都安全、可控、可追踪。
                    </Typography.Paragraph>
                    <div className="auth-orbit" aria-hidden="true">
                        <span/>
                        <span/>
                        <span/>
                    </div>
                </div>

                <Card className="auth-card">
                    <Typography.Text className="auth-panel-label">Secure Access</Typography.Text>
                    <Typography.Title level={2}>欢迎回来</Typography.Title>
                    <Typography.Paragraph type="secondary">
                        使用账号登录，继续管理你的 Agent、权限与知识库配置。
                    </Typography.Paragraph>

                    {activated && (
                        <Alert showIcon className="auth-alert"
                               message="账号激活成功，请使用新密码登录" type="success"/>
                    )}
                    {error && <Alert showIcon className="auth-alert" message={error} type="error"/>}

                    <Form<LoginRequest> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                        <Form.Item
                            label="账号或邮箱"
                            name="account"
                            rules={[{required: true, message: '请输入账号或邮箱'}]}
                        >
                            <Input autoComplete="username" prefix={<UserOutlined/>} placeholder="请输入账号或邮箱"/>
                        </Form.Item>
                        <Form.Item
                            label="密码"
                            name="password"
                            rules={[{required: true, message: '请输入密码'}]}
                        >
                            <Input.Password
                                autoComplete="current-password"
                                prefix={<LockOutlined/>}
                                placeholder="请输入密码"
                            />
                        </Form.Item>
                        <Button block htmlType="submit" type="primary">
                            进入工作台
                        </Button>
                        <Typography.Paragraph className="auth-switch" type="secondary">
                            还没有账号？<Link to="/register">立即注册</Link>
                        </Typography.Paragraph>
                    </Form>
                </Card>
            </section>
        </div>
    )
}
