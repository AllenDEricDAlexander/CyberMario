import {LockOutlined, MailOutlined, MobileOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Form, Input, Typography} from 'antd'
import {useState} from 'react'
import {Link, Navigate, useLocation, useNavigate} from 'react-router'
import {VisualBackdrop} from '../../../components/VisualBackdrop'
import {resolveErrorMessage} from '../../../services/request'
import {voidify} from '../../../utils/async'
import {useAuth} from '../authStore'
import type {RegisterRequest} from '../authTypes'

type RegisterFormValues = RegisterRequest & {
    confirmPassword: string
}

type LocationState = {
    from?: {
        pathname?: string
    }
}

export function RegisterPage() {
    const auth = useAuth()
    const navigate = useNavigate()
    const location = useLocation()
    const [error, setError] = useState('')

    const state = location.state as LocationState | null
    const redirectTo = state?.from?.pathname || '/chat'

    if (auth.authenticated) {
        return <Navigate replace to={redirectTo}/>
    }

    async function handleFinish(values: RegisterFormValues) {
        setError('')
        try {
            const {confirmPassword: _confirmPassword, ...request} = values
            await auth.register(request)
            void navigate(redirectTo, {replace: true})
        } catch (requestError) {
            setError(resolveErrorMessage(requestError))
        }
    }

    return (
        <div className="auth-page">
            <VisualBackdrop particleCount={24} variant="auth"/>
            <section className="auth-hero" aria-label="CyberMario 注册">
                <div className="auth-copy">
                    <Typography.Text className="auth-brand">CyberMario</Typography.Text>
                    <Typography.Title level={1}>Agent Control Workspace</Typography.Title>
                    <Typography.Paragraph>
                        注册后默认获得 Chat 和 RAG 查看能力，进入统一的智能工作台。
                    </Typography.Paragraph>
                    <div className="auth-orbit" aria-hidden="true">
                        <span/>
                        <span/>
                        <span/>
                    </div>
                </div>

                <Card className="auth-card">
                    <Typography.Text className="auth-panel-label">Create Account</Typography.Text>
                    <Typography.Title level={2}>注册</Typography.Title>
                    <Typography.Paragraph type="secondary">
                        无需验证，创建账号后直接进入工作台。
                    </Typography.Paragraph>

                    {error && <Alert showIcon className="auth-alert" message={error} type="error"/>}

                    <Form<RegisterFormValues> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                        <Form.Item
                            label="用户名"
                            name="username"
                            rules={[{required: true, message: '请输入用户名'}]}
                        >
                            <Input autoComplete="username" prefix={<UserOutlined/>} placeholder="mario"/>
                        </Form.Item>
                        <Form.Item label="昵称" name="nickname">
                            <Input prefix={<UserOutlined/>} placeholder="用于界面展示"/>
                        </Form.Item>
                        <Form.Item
                            label="密码"
                            name="password"
                            rules={[
                                {required: true, message: '请输入密码'},
                                {min: 8, message: '密码至少 8 位'},
                            ]}
                        >
                            <Input.Password
                                autoComplete="new-password"
                                prefix={<LockOutlined/>}
                                placeholder="至少 8 位"
                            />
                        </Form.Item>
                        <Form.Item
                            dependencies={['password']}
                            label="确认密码"
                            name="confirmPassword"
                            rules={[
                                {required: true, message: '请再次输入密码'},
                                ({getFieldValue}) => ({
                                    validator(_, value) {
                                        if (!value || getFieldValue('password') === value) {
                                            return Promise.resolve()
                                        }
                                        return Promise.reject(new Error('两次输入的密码不一致'))
                                    },
                                }),
                            ]}
                        >
                            <Input.Password
                                autoComplete="new-password"
                                prefix={<LockOutlined/>}
                                placeholder="再次输入密码"
                            />
                        </Form.Item>
                        <Form.Item label="邮箱" name="email">
                            <Input autoComplete="email" prefix={<MailOutlined/>} placeholder="name@example.com"/>
                        </Form.Item>
                        <Form.Item label="手机" name="mobile">
                            <Input autoComplete="tel" prefix={<MobileOutlined/>} placeholder="手机号"/>
                        </Form.Item>
                        <Button block htmlType="submit" type="primary">
                            注册并进入
                        </Button>
                        <Typography.Paragraph className="auth-switch" type="secondary">
                            已有账号？<Link to="/login">返回登录</Link>
                        </Typography.Paragraph>
                    </Form>
                </Card>
            </section>
        </div>
    )
}
