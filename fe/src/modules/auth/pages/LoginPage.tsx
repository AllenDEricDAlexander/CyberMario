import {LockOutlined, LoginOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, Button, Form, Input, Typography} from 'antd'
import {useState} from 'react'
import {Link, Navigate, useLocation, useNavigate} from 'react-router'
import {resolveErrorMessage} from '../../../services/request'
import {voidify} from '../../../utils/async'
import {useAuth} from '../authStore'
import type {LoginRequest} from '../authTypes'
import {AuthShell} from '../components/AuthShell'

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
    const [submitting, setSubmitting] = useState(false)

    const state = location.state as LocationState | null
    const redirectTo = state?.from?.pathname || '/chat'
    const activated = new URLSearchParams(location.search).get('activated') === '1'

    if (auth.authenticated) {
        return <Navigate replace to={redirectTo}/>
    }

    async function handleFinish(values: LoginRequest) {
        setError('')
        setSubmitting(true)
        try {
            await auth.login(values)
            void navigate(redirectTo, {replace: true})
        } catch (requestError) {
            setError(resolveErrorMessage(requestError))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <AuthShell
            highlights={['Agent 编排', 'RAG 知识库', 'RBAC 权限', '全链路审计']}
            intro="统一管理 Agent、权限、知识库与会话工作流，让每一次自动化执行都安全、可控、可追踪。"
            label="CyberMario 登录"
            panelLabel="Secure Access"
            subtitle="使用账号登录，继续管理你的 Agent、权限与知识库配置。"
            title="欢迎回来"
        >
            {activated && (
                <Alert
                    className="auth-alert"
                    message="账号激活成功，请使用新密码登录"
                    showIcon
                    type="success"
                />
            )}
            {error && <Alert className="auth-alert" message={error} showIcon type="error"/>}

            <Form<LoginRequest> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false} size="large">
                <Form.Item
                    label="账号或邮箱"
                    name="account"
                    rules={[{required: true, message: '请输入账号或邮箱'}]}
                >
                    <Input autoComplete="username" placeholder="请输入账号或邮箱" prefix={<UserOutlined/>}/>
                </Form.Item>
                <Form.Item
                    label="密码"
                    name="password"
                    rules={[{required: true, message: '请输入密码'}]}
                >
                    <Input.Password
                        autoComplete="current-password"
                        placeholder="请输入密码"
                        prefix={<LockOutlined/>}
                    />
                </Form.Item>
                <Button block htmlType="submit" icon={<LoginOutlined/>} loading={submitting} type="primary">
                    进入工作台
                </Button>
                <Typography.Paragraph className="auth-switch" type="secondary">
                    还没有账号？<Link to="/register">立即注册</Link>
                </Typography.Paragraph>
            </Form>
        </AuthShell>
    )
}
