import type {ButtonHTMLAttributes, ReactNode} from 'react'

type ButtonVariant = 'primary' | 'secondary' | 'default'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
    icon?: ReactNode
    variant?: ButtonVariant
}

export function Button({
                           children,
                           className = '',
                           icon,
                           type = 'button',
                           variant = 'default',
                           ...buttonProps
                       }: ButtonProps) {
    const buttonClassName = [
        variant === 'secondary' ? 'secondary-action' : 'icon-action',
        variant === 'primary' ? 'primary' : '',
        className,
    ]
        .filter(Boolean)
        .join(' ')

    return (
        <button className={buttonClassName} type={type} {...buttonProps}>
            {icon}
            {children}
        </button>
    )
}
