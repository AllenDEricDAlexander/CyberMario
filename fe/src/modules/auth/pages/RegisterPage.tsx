import {LockOutlined, MailOutlined, MobileOutlined, UserAddOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, Button, Form, Input, Typography} from 'antd'
import {useState} from 'react'
import {Link, Navigate, useLocation, useNavigate} from 'react-router'
import {resolveErrorMessage} from '../../../services/request'
import {voidify} from '../../../utils/async'
import {useAuth} from '../authStore'
import type {RegisterRequest} from '../authTypes'
import {AuthShell} from '../components/AuthShell'

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
    const [submitting, setSubmitting] = useState(false)

    const state = location.state as LocationState | null
    const redirectTo = state?.from?.pathname || '/chat'

    if (auth.authenticated) {
        return <Navigate replace to={redirectTo}/>
    }

    async function handleFinish(values: RegisterFormValues) {
        setError('')
        setSubmitting(true)
        try {
            const request: RegisterRequest = {
                accountNo: values.accountNo,
                email: values.email,
                mobile: values.mobile,
                nickname: values.nickname,
                password: values.password,
                username: values.username,
            }
            await auth.register(request)
            void navigate(redirectTo, {replace: true})
        } catch (requestError) {
            setError(resolveErrorMessage(requestError))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <AuthShell
            highlights={['开箱即用', '默认只读权限', '随时升级角色']}
            intro="注册后默认获得 Chat 和 RAG 查看能力，进入统一的智能工作台。"
            label="CyberMario 注册"
            panelLabel="Create Account"
            subtitle="无需邮箱验证，创建账号后直接进入工作台。"
            title="创建账号"
            wide
        >
            {error && <Alert className="auth-alert" message={error} showIcon type="error"/>}

            <Form<RegisterFormValues> layout="vertical" onFinish={voidify(handleFinish)} requiredMark={false}>
                {/* Paired fields keep the card from growing past the fold. */}
                <div className="auth-form-row">
                    <Form.Item
                        label="账号"
                        name="accountNo"
                        rules={[{required: true, message: '请输入账号'}]}
                    >
                        <Input autoComplete="username" placeholder="mario" prefix={<UserOutlined/>}/>
                    </Form.Item>
                    <Form.Item
                        label="用户名"
                        name="username"
                        rules={[{required: true, message: '请输入用户名'}]}
                    >
                        <Input placeholder="用于系统内展示" prefix={<UserOutlined/>}/>
                    </Form.Item>
                </div>
                <Form.Item label="昵称" name="nickname">
                    <Input placeholder="用于界面展示，可稍后补充" prefix={<UserOutlined/>}/>
                </Form.Item>
                <div className="auth-form-row">
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
                            placeholder="至少 8 位"
                            prefix={<LockOutlined/>}
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
                            placeholder="再次输入密码"
                            prefix={<LockOutlined/>}
                        />
                    </Form.Item>
                </div>
                <div className="auth-form-row">
                    <Form.Item
                        label="邮箱"
                        name="email"
                        rules={[{type: 'email', message: '请输入有效邮箱'}]}
                    >
                        <Input autoComplete="email" placeholder="name@example.com" prefix={<MailOutlined/>}/>
                    </Form.Item>
                    <Form.Item label="手机" name="mobile">
                        <Input autoComplete="tel" placeholder="手机号" prefix={<MobileOutlined/>}/>
                    </Form.Item>
                </div>
                <Button block htmlType="submit" icon={<UserAddOutlined/>} loading={submitting} type="primary">
                    注册并进入
                </Button>
                <Typography.Paragraph className="auth-switch" type="secondary">
                    已有账号？<Link to="/login">返回登录</Link>
                </Typography.Paragraph>
            </Form>
        </AuthShell>
    )
}
