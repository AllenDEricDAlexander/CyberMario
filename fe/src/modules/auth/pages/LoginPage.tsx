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
                        用统一的权限、知识库和对话工作台管理 Java agent 的运行入口。
                    </Typography.Paragraph>
                    <div className="auth-orbit" aria-hidden="true">
                        <span/>
                        <span/>
                        <span/>
                    </div>
                </div>

                <Card className="auth-card">
                    <Typography.Text className="auth-panel-label">Secure Access</Typography.Text>
                    <Typography.Title level={2}>登录</Typography.Title>
                    <Typography.Paragraph type="secondary">
                        使用 RBAC1 账号进入管理工作台。
                    </Typography.Paragraph>

                    {error && <Alert showIcon className="auth-alert" message={error} type="error"/>}

                    <Form<LoginRequest> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                        <Form.Item
                            label="用户名"
                            name="username"
                            rules={[{required: true, message: '请输入用户名'}]}
                        >
                            <Input autoComplete="username" prefix={<UserOutlined/>} placeholder="admin"/>
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
                            登录
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
