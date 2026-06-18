import {Sender} from '@ant-design/x'

export type ChatSenderProps = {
    input: string
    sending?: boolean
    placeholder?: string
    onInputChange: (value: string) => void
    onSend: (message: string) => void
    onStop?: () => void
}

export function ChatSender(props: ChatSenderProps) {
    const {
        input,
        sending = false,
        placeholder = 'Ask a question...',
        onInputChange,
        onSend,
        onStop,
    } = props

    return (
        <Sender
            autoSize={{minRows: 2, maxRows: 6}}
            className="chat-workspace-x-sender"
            loading={sending}
            placeholder={placeholder}
            submitType="enter"
            value={input}
            onCancel={onStop}
            onChange={onInputChange}
            onSubmit={(message) => {
                const nextMessage = message.trim()
                if (nextMessage) {
                    onSend(nextMessage)
                }
            }}
        />
    )
}
