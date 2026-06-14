import {KeyOutlined, SaveOutlined, UserOutlined} from '@ant-design/icons'
import {App, Button, Card, Col, Form, Input, Row, Space, Typography} from 'antd'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {voidify} from '../../../utils/async'
import {useAuth} from '../../auth/authStore'
import {changeCurrentUserPassword, updateCurrentUserProfile} from '../accountService'
import type {ChangeCurrentUserPasswordRequest, UpdateCurrentUserProfileRequest} from '../accountTypes'

function AccountSettingsPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [profileForm] = Form.useForm<UpdateCurrentUserProfileRequest>()
    const [passwordForm] = Form.useForm<ChangeCurrentUserPasswordRequest>()
    const [savingProfile, setSavingProfile] = useState(false)
    const [savingPassword, setSavingPassword] = useState(false)

    useEffect(() => {
        profileForm.setFieldsValue({
            nickname: auth.user?.nickname,
            email: auth.user?.email,
            mobile: auth.user?.mobile,
            avatarUrl: auth.user?.avatarUrl,
        })
    }, [auth.user, profileForm])

    async function handleProfileSubmit(values: UpdateCurrentUserProfileRequest) {
        setSavingProfile(true)
        try {
            await updateCurrentUserProfile(values)
            await auth.reload()
            message.success('个人资料已保存')
        } finally {
            setSavingProfile(false)
        }
    }

    async function handlePasswordSubmit(values: ChangeCurrentUserPasswordRequest) {
        setSavingPassword(true)
        try {
            await changeCurrentUserPassword(values)
            passwordForm.resetFields()
            message.success('密码已修改')
        } finally {
            setSavingPassword(false)
        }
    }

    return (
        <>
            <PageToolbar
                description="维护当前账号的基础资料和登录密码。"
                title="个人设置"
            />
            <Row gutter={[16, 16]}>
                <Col lg={14} xs={24}>
                    <Card title={<Space><UserOutlined/>基础资料</Space>}>
                        <Form<UpdateCurrentUserProfileRequest>
                            form={profileForm}
                            layout="vertical"
                            onFinish={voidify(handleProfileSubmit)}
                            requiredMark={false}
                        >
                            <Form.Item label="用户名">
                                <Input disabled value={auth.user?.username}/>
                            </Form.Item>
                            <Form.Item label="昵称" name="nickname">
                                <Input maxLength={64} placeholder="用于界面展示"/>
                            </Form.Item>
                            <Form.Item label="邮箱" name="email" rules={[{type: 'email', message: '请输入正确的邮箱'}]}>
                                <Input maxLength={128} placeholder="name@example.com"/>
                            </Form.Item>
                            <Form.Item label="手机" name="mobile">
                                <Input maxLength={32} placeholder="手机号"/>
                            </Form.Item>
                            <Form.Item label="头像 URL" name="avatarUrl">
                                <Input maxLength={512} placeholder="https://example.com/avatar.png"/>
                            </Form.Item>
                            <Button icon={<SaveOutlined/>} htmlType="submit" loading={savingProfile} type="primary">
                                保存资料
                            </Button>
                        </Form>
                    </Card>
                </Col>
                <Col lg={10} xs={24}>
                    <Card title={<Space><KeyOutlined/>安全设置</Space>}>
                        <Typography.Paragraph type="secondary">
                            修改密码需要先输入当前密码，保存后当前会话保持登录。
                        </Typography.Paragraph>
                        <Form<ChangeCurrentUserPasswordRequest>
                            form={passwordForm}
                            layout="vertical"
                            onFinish={voidify(handlePasswordSubmit)}
                            requiredMark={false}
                        >
                            <Form.Item
                                label="当前密码"
                                name="currentPassword"
                                rules={[{required: true, message: '请输入当前密码'}]}
                            >
                                <Input.Password autoComplete="current-password"/>
                            </Form.Item>
                            <Form.Item
                                label="新密码"
                                name="newPassword"
                                rules={[
                                    {required: true, message: '请输入新密码'},
                                    {min: 8, message: '密码至少 8 位'},
                                ]}
                            >
                                <Input.Password autoComplete="new-password"/>
                            </Form.Item>
                            <Form.Item
                                dependencies={['newPassword']}
                                label="确认新密码"
                                name="confirmPassword"
                                rules={[
                                    {required: true, message: '请再次输入新密码'},
                                    ({getFieldValue}) => ({
                                        validator(_, value) {
                                            if (!value || getFieldValue('newPassword') === value) {
                                                return Promise.resolve()
                                            }
                                            return Promise.reject(new Error('两次输入的密码不一致'))
                                        },
                                    }),
                                ]}
                            >
                                <Input.Password autoComplete="new-password"/>
                            </Form.Item>
                            <Button icon={<KeyOutlined/>} htmlType="submit" loading={savingPassword}>
                                修改密码
                            </Button>
                        </Form>
                    </Card>
                </Col>
            </Row>
        </>
    )
}

export const Component = AccountSettingsPage
