import {SmileOutlined} from '@ant-design/icons'
import {Button, Popover, Typography} from 'antd'
import {useState} from 'react'

type EmojiCategory = {
    key: string
    label: string
    icon: string
    emojis: string[]
}

const emojiCategories: EmojiCategory[] = [
    {
        key: 'smileys',
        label: '表情',
        icon: '😀',
        emojis: [
            '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣',
            '😊', '😇', '🙂', '🙃', '😉', '😌', '😍', '🥰',
            '😘', '😋', '😜', '🤪', '🤨', '🧐', '🤓', '😎',
            '🤩', '🥳', '😏', '😒', '😔', '😢', '😭', '😤',
        ],
    },
    {
        key: 'gestures',
        label: '手势',
        icon: '👍',
        emojis: [
            '👍', '👎', '👌', '✌️', '🤞', '🤟', '🤘', '🤙',
            '👈', '👉', '👆', '👇', '☝️', '✋', '🤚', '🖐️',
            '🖖', '👋', '🤏', '💪', '🙏', '👏', '🙌', '🫶',
            '🤝', '✍️', '💅', '👀', '🧠', '🗣️', '👤', '👥',
        ],
    },
    {
        key: 'animals',
        label: '动物',
        icon: '🐶',
        emojis: [
            '🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼',
            '🐻‍❄️', '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵',
            '🐔', '🐧', '🐦', '🐤', '🦄', '🐝', '🦋', '🐌',
            '🐞', '🐢', '🐍', '🦎', '🐙', '🦀', '🐠', '🐳',
        ],
    },
    {
        key: 'food',
        label: '食物',
        icon: '🍎',
        emojis: [
            '🍎', '🍐', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓',
            '🫐', '🍒', '🍑', '🥭', '🍍', '🥝', '🍅', '🥑',
            '🍔', '🍟', '🍕', '🌭', '🥪', '🌮', '🍜', '🍣',
            '🍙', '🍰', '🎂', '🍫', '🍿', '☕', '🍵', '🥂',
        ],
    },
    {
        key: 'activities',
        label: '活动',
        icon: '⚽',
        emojis: [
            '⚽', '🏀', '🏈', '⚾', '🎾', '🏐', '🏓', '🏸',
            '🥅', '⛳', '🏹', '🎣', '🤿', '🎿', '🏂', '🏆',
            '🥇', '🎯', '🎮', '🎲', '🧩', '🎨', '🎭', '🎬',
            '🎤', '🎧', '🎸', '🎹', '🥁', '🎺', '🚲', '✈️',
        ],
    },
    {
        key: 'symbols',
        label: '符号',
        icon: '❤️',
        emojis: [
            '❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍',
            '🤎', '💔', '❣️', '💕', '💞', '💓', '💗', '💖',
            '💘', '💝', '💟', '✨', '⭐', '🌟', '💫', '🔥',
            '🎉', '🎊', '✅', '❌', '❓', '❗', '💯', '🔔',
        ],
    },
]

export type ImEmojiPickerProps = {
    disabled?: boolean
    onSelect: (emoji: string) => void
}

export function ImEmojiPicker(props: ImEmojiPickerProps) {
    const [open, setOpen] = useState(false)
    const [activeCategoryKey, setActiveCategoryKey] = useState(emojiCategories[0].key)
    const activeCategory = emojiCategories.find((category) => category.key === activeCategoryKey)
        ?? emojiCategories[0]

    const picker = (
        <div aria-label="Emoji 表情选择器" className="platform-im-emoji-picker" role="dialog">
            <Typography.Text strong>选择表情</Typography.Text>
            <div aria-label="表情分类" className="platform-im-emoji-categories" role="tablist">
                {emojiCategories.map((category) => (
                    <button
                        aria-label={category.label}
                        aria-selected={category.key === activeCategory.key}
                        className="platform-im-emoji-category"
                        key={category.key}
                        onClick={() => setActiveCategoryKey(category.key)}
                        role="tab"
                        tabIndex={category.key === activeCategory.key ? 0 : -1}
                        title={category.label}
                        type="button"
                    >
                        {category.icon}
                    </button>
                ))}
            </div>
            <div
                aria-label={`${activeCategory.label}表情`}
                className="platform-im-emoji-grid"
                role="tabpanel"
            >
                {activeCategory.emojis.map((emoji) => (
                    <button
                        aria-label={`插入表情 ${emoji}`}
                        className="platform-im-emoji-option"
                        key={emoji}
                        onClick={() => {
                            props.onSelect(emoji)
                            setOpen(false)
                        }}
                        title={emoji}
                        type="button"
                    >
                        {emoji}
                    </button>
                ))}
            </div>
        </div>
    )

    return (
        <Popover
            content={picker}
            open={!props.disabled && open}
            placement="topLeft"
            trigger="click"
            onOpenChange={setOpen}
        >
            <Button
                aria-label="选择 Emoji 表情"
                disabled={props.disabled}
                icon={<SmileOutlined/>}
                size="small"
                type="text"
            >
                表情
            </Button>
        </Popover>
    )
}
