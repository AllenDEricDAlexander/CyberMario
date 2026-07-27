import {FileTextOutlined, HistoryOutlined, KeyOutlined, SaveOutlined, SettingOutlined, UserOutlined} from '@ant-design/icons'
import {Alert, App, Button, Card, Form, Input, List, Space, Switch} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {PageGrid, PageSection, PageStack} from '../../../components/PageSection'
import {PageToolbar} from '../../../components/PageToolbar'
import {resolveErrorMessage} from '../../../services/request'
import {ApiRequestError} from '../../../types/api'
import {voidify} from '../../../utils/async'
import {useAuth} from '../../auth/authStore'
import {
    changeCurrentUserPassword,
    getCurrentUserSoulMd,
    getCurrentUserSoulMdVersions,
    updateCurrentUserProfile,
    updateCurrentUserSoulMd,
} from '../accountService'
import type {
    AgentSoulMdResponse,
    AgentSoulMdUpdateRequest,
    AgentSoulMdVersionResponse,
    ChangeCurrentUserPasswordRequest,
    UpdateCurrentUserProfileRequest,
} from '../accountTypes'

type PasswordFieldError = {
    name: keyof ChangeCurrentUserPasswordRequest
    message: string
}

/** Server rejections that belong to a single field, keyed by the backend error code. */
const passwordFieldErrors: Record<string, PasswordFieldError> = {
    RBAC_CURRENT_PASSWORD_INVALID: {name: 'currentPassword', message: '当前密码不正确'},
    RBAC_PASSWORD_CONFIRM_MISMATCH: {name: 'confirmPassword', message: '两次输入的密码不一致'},
    RBAC_PASSWORD_UNCHANGED: {name: 'newPassword', message: '新密码不能与当前密码相同'},
}

function AccountSettingsPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [profileForm] = Form.useForm<UpdateCurrentUserProfileRequest>()
    const [passwordForm] = Form.useForm<ChangeCurrentUserPasswordRequest>()
    const [soulForm] = Form.useForm<AgentSoulMdUpdateRequest>()
    const [soulMd, setSoulMd] = useState<AgentSoulMdResponse>()
    const [soulVersions, setSoulVersions] = useState<AgentSoulMdVersionResponse[]>([])
    const [savingProfile, setSavingProfile] = useState(false)
    const [savingPassword, setSavingPassword] = useState(false)
    const [passwordError, setPasswordError] = useState('')
    const [loadingSoul, setLoadingSoul] = useState(false)
    const [loadingSoulVersions, setLoadingSoulVersions] = useState(false)
    const [savingSoul, setSavingSoul] = useState(false)

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

    /**
     * Password failures stay on the card instead of bubbling to the global error
     * toast: the ones the backend attributes to a specific field land on that
     * field, anything else renders as an alert above the form.
     */
    async function handlePasswordSubmit(values: ChangeCurrentUserPasswordRequest) {
        setSavingPassword(true)
        setPasswordError('')
        try {
            await changeCurrentUserPassword(values)
            passwordForm.resetFields()
            message.success('密码已修改')
        } catch (error) {
            const fieldError = error instanceof ApiRequestError ? passwordFieldErrors[error.code] : undefined
            if (fieldError) {
                passwordForm.setFields([{name: fieldError.name, errors: [fieldError.message]}])
                return
            }
            setPasswordError(resolveErrorMessage(error))
        } finally {
            setSavingPassword(false)
        }
    }

    const loadSoulVersions = useCallback(async () => {
        setLoadingSoulVersions(true)
        try {
            const versions = await getCurrentUserSoulMdVersions()
            setSoulVersions(versions)
        } finally {
            setLoadingSoulVersions(false)
        }
    }, [])

    const loadSoulMd = useCallback(async () => {
        setLoadingSoul(true)
        try {
            const current = await getCurrentUserSoulMd()
            setSoulMd(current)
            soulForm.setFieldsValue({
                contentMarkdown: current.contentMarkdown,
                enabled: current.enabled,
            })
            voidify(loadSoulVersions)()
        } finally {
            setLoadingSoul(false)
        }
    }, [loadSoulVersions, soulForm])

    useEffect(() => {
        voidify(loadSoulMd)()
    }, [loadSoulMd])

    async function handleSoulSubmit(values: AgentSoulMdUpdateRequest) {
        setSavingSoul(true)
        try {
            const saved = await updateCurrentUserSoulMd({
                contentMarkdown: values.contentMarkdown ?? '',
                enabled: values.enabled ?? false,
            })
            setSoulMd(saved)
            soulForm.setFieldsValue({
                contentMarkdown: saved.contentMarkdown,
                enabled: saved.enabled,
            })
            voidify(loadSoulVersions)()
            message.success('SoulMD 已保存')
        } finally {
            setSavingSoul(false)
        }
    }

    return (
        <>
            <PageToolbar
                description="维护当前账号的基础资料、登录密码和 Agent 长期记忆。"
                icon={<SettingOutlined/>}
                title="个人设置"
            />
            <PageSection
                description="昵称与头像用于站内展示，邮箱和手机用于通知触达与账号找回。"
                title="账号资料"
            >
                <PageGrid minWidth={360}>
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
                            <Form.Item
                                hasFeedback
                                label="邮箱"
                                name="email"
                                rules={[{type: 'email', message: '请输入正确的邮箱'}]}
                            >
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
                    <Card title={<Space><KeyOutlined/>安全设置</Space>}>
                        <PageStack>
                            {passwordError && (
                                <Alert
                                    className="page-alert"
                                    message={passwordError}
                                    showIcon
                                    type="error"
                                />
                            )}
                            <Form<ChangeCurrentUserPasswordRequest>
                                form={passwordForm}
                                layout="vertical"
                                onFinish={voidify(handlePasswordSubmit)}
                                onValuesChange={() => setPasswordError('')}
                                requiredMark={false}
                            >
                                <Form.Item
                                    extra="修改密码需要先验证当前密码，保存后当前会话保持登录。"
                                    hasFeedback
                                    label="当前密码"
                                    name="currentPassword"
                                    rules={[{required: true, message: '请输入当前密码'}]}
                                >
                                    <Input.Password autoComplete="current-password" placeholder="请输入当前登录密码"/>
                                </Form.Item>
                                <Form.Item
                                    dependencies={['currentPassword']}
                                    extra="至少 8 位，建议混合字母、数字与符号。"
                                    hasFeedback
                                    label="新密码"
                                    name="newPassword"
                                    rules={[
                                        {required: true, message: '请输入新密码'},
                                        {min: 8, message: '密码至少 8 位'},
                                        ({getFieldValue}) => ({
                                            validator(_, value) {
                                                if (!value || getFieldValue('currentPassword') !== value) {
                                                    return Promise.resolve()
                                                }
                                                return Promise.reject(new Error('新密码不能与当前密码相同'))
                                            },
                                        }),
                                    ]}
                                    validateFirst
                                >
                                    <Input.Password autoComplete="new-password" placeholder="至少 8 位"/>
                                </Form.Item>
                                <Form.Item
                                    dependencies={['newPassword']}
                                    hasFeedback
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
                                    validateFirst
                                >
                                    <Input.Password autoComplete="new-password" placeholder="再次输入新密码"/>
                                </Form.Item>
                                <Button icon={<KeyOutlined/>} htmlType="submit" loading={savingPassword}>
                                    修改密码
                                </Button>
                            </Form>
                        </PageStack>
                    </Card>
                </PageGrid>
            </PageSection>
            <PageSection
                description="SoulMD 会在对话开始时注入 Agent，用来描述你的长期偏好；每次保存都会留下版本快照。"
                title="Agent SoulMD"
            >
                <PageGrid minWidth={360}>
                    <Card loading={loadingSoul} title={<Space><FileTextOutlined/>SoulMD 内容</Space>}>
                        <Form<AgentSoulMdUpdateRequest>
                            form={soulForm}
                            initialValues={{contentMarkdown: '', enabled: false}}
                            layout="vertical"
                            onFinish={voidify(handleSoulSubmit)}
                            requiredMark={false}
                        >
                            <Form.Item label="启用注入" name="enabled" valuePropName="checked">
                                <Switch/>
                            </Form.Item>
                            <Form.Item
                                label={`Markdown ${soulMd ? `${soulMd.contentChars}/${soulMd.maxChars}` : ''}`}
                                name="contentMarkdown"
                                rules={[{max: 50000, message: 'SoulMD 最多 50000 字符'}]}
                            >
                                <Input.TextArea
                                    autoSize={{minRows: 16, maxRows: 28}}
                                    maxLength={50000}
                                    showCount
                                />
                            </Form.Item>
                            <Button
                                disabled={!soulMd || loadingSoul}
                                icon={<SaveOutlined/>}
                                htmlType="submit"
                                loading={savingSoul}
                                type="primary"
                            >
                                保存 SoulMD
                            </Button>
                        </Form>
                    </Card>
                    <Card loading={loadingSoulVersions} title={<Space><HistoryOutlined/>版本记录</Space>}>
                        <List
                            dataSource={soulVersions}
                            locale={{
                                emptyText: (
                                    <EmptyState
                                        description="保存一次 SoulMD 后，这里会记录每个版本的变更摘要。"
                                        inline
                                        title="暂无版本"
                                    />
                                ),
                            }}
                            renderItem={(item) => (
                                <List.Item>
                                    <List.Item.Meta
                                        description={item.changeSummary || item.createdAt}
                                        title={`v${item.versionNo} · ${item.changeType || '版本快照'}`}
                                    />
                                </List.Item>
                            )}
                        />
                    </Card>
                </PageGrid>
            </PageSection>
        </>
    )
}

export const Component = AccountSettingsPage
