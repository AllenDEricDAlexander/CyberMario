import {CheckCircleFilled} from '@ant-design/icons'
import {Card, Typography} from 'antd'
import type {ReactNode} from 'react'
import {VisualBackdrop} from '../../../components/VisualBackdrop'

type AuthShellProps = {
    /** Accessible name for the screen, e.g. "CyberMario 登录". */
    label: string
    /** Marketing copy under the product title. */
    intro: ReactNode
    /** Short capability bullets rendered as pills. */
    highlights?: string[]
    /** Uppercase kicker above the form heading, e.g. "Secure Access". */
    panelLabel: string
    title: string
    subtitle: ReactNode
    children: ReactNode
    /** Widens the card for multi-column forms such as registration. */
    wide?: boolean
}

/**
 * Shared frame for `/login`, `/register` and `/activate`.
 *
 * The three screens previously repeated the whole hero markup, which is why
 * they had drifted apart visually. Everything except the form now lives here.
 */
export function AuthShell({
    label,
    intro,
    highlights,
    panelLabel,
    title,
    subtitle,
    children,
    wide,
}: AuthShellProps) {
    return (
        <div className="auth-page">
            <VisualBackdrop particleCount={24} variant="auth"/>
            <section aria-label={label} className={wide ? 'auth-hero is-wide' : 'auth-hero'}>
                <div className="auth-copy">
                    <Typography.Text className="auth-brand">CyberMario</Typography.Text>
                    <Typography.Title level={1}>Agent Control Workspace</Typography.Title>
                    <Typography.Paragraph>{intro}</Typography.Paragraph>
                    {highlights && highlights.length > 0 && (
                        <ul className="auth-highlights">
                            {highlights.map((item) => (
                                <li className="auth-highlight" key={item}>
                                    <CheckCircleFilled/>
                                    {item}
                                </li>
                            ))}
                        </ul>
                    )}
                    <div aria-hidden="true" className="auth-orbit">
                        <span/>
                        <span/>
                        <span/>
                    </div>
                </div>

                <Card className="auth-card">
                    <Typography.Text className="auth-panel-label">{panelLabel}</Typography.Text>
                    <Typography.Title level={2}>{title}</Typography.Title>
                    <Typography.Paragraph type="secondary">{subtitle}</Typography.Paragraph>
                    {children}
                </Card>
            </section>
        </div>
    )
}
