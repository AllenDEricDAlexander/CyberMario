import {Button, Drawer} from 'antd'
import type {ReactNode} from 'react'
import {drawerWidth} from '../../theme/designTokens'

type FormDrawerSize = keyof typeof drawerWidth

type FormDrawerProps = {
    open: boolean
    title: ReactNode
    /** Sub-heading under the title, e.g. which record is being edited. */
    description?: ReactNode
    children: ReactNode
    onClose: () => void
    /** `id` of the `<Form>` inside — lets the footer button submit it. */
    formId?: string
    onSubmit?: () => void
    submitText?: string
    cancelText?: string
    loading?: boolean
    /** Disables the submit button, e.g. while the form is invalid. */
    submitDisabled?: boolean
    /** Muted text on the left of the footer — validation summaries or hints. */
    footerHint?: ReactNode
    /** Replaces the whole footer. Omit `false` to keep the default buttons. */
    footer?: ReactNode | false
    size?: FormDrawerSize
    extra?: ReactNode
    destroyOnHidden?: boolean
}

/**
 * The standard editor drawer.
 *
 * Editor drawers previously put their save button in the header `extra` slot at
 * four different widths. This keeps the action in a sticky footer where users
 * expect it, so long forms no longer need scrolling back up to submit.
 */
export function FormDrawer({
    open,
    title,
    description,
    children,
    onClose,
    formId,
    onSubmit,
    submitText = '保存',
    cancelText = '取消',
    loading,
    submitDisabled,
    footerHint,
    footer,
    size = 'md',
    extra,
    destroyOnHidden = true,
}: FormDrawerProps) {
    const defaultFooter = (
        <div className="form-drawer-footer">
            {footerHint && <span className="form-drawer-footer-hint">{footerHint}</span>}
            <Button disabled={loading} onClick={onClose}>{cancelText}</Button>
            <Button
                disabled={submitDisabled}
                form={formId}
                htmlType={formId ? 'submit' : 'button'}
                loading={loading}
                onClick={formId ? undefined : onSubmit}
                type="primary"
            >
                {submitText}
            </Button>
        </div>
    )

    return (
        <Drawer
            destroyOnHidden={destroyOnHidden}
            extra={extra}
            footer={footer === false ? undefined : (footer ?? defaultFooter)}
            onClose={onClose}
            open={open}
            title={
                description ? (
                    <div className="admin-user-meta">
                        <span>{title}</span>
                        <span className="admin-user-role">{description}</span>
                    </div>
                ) : title
            }
            size={drawerWidth[size]}
        >
            <div className="form-drawer-body">{children}</div>
        </Drawer>
    )
}
