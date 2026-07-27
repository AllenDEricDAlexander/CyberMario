import {ReloadOutlined, SearchOutlined} from '@ant-design/icons'
import {Button, Form} from 'antd'
import type {FormInstance} from 'antd'
import {useState, type CSSProperties, type ReactNode} from 'react'

type FilterBarProps<Values> = {
    /** One `<Form.Item>` per filter; the bar lays them out on a responsive grid. */
    children: ReactNode
    form?: FormInstance<Values>
    initialValues?: Partial<Values>
    onSearch?: (values: Values) => void
    onReset?: () => void
    loading?: boolean
    searchText?: string
    /** Extra controls rendered after the search/reset pair. */
    actions?: ReactNode
    /** Minimum column width before the grid wraps. Defaults to 210px. */
    minFieldWidth?: number
    /**
     * Applies filters as soon as a field changes and hides the search button.
     * Use for short select-only strips where an explicit 查询 click is friction;
     * keep it off when a text field would fire a request per keystroke.
     */
    instant?: boolean
}

/**
 * Consistent filter strip for list pages.
 *
 * Fields auto-size on a grid, so pages no longer need `style={{width: 220}}`
 * on every `Select`. Submitting the form (Enter or the search button) calls
 * `onSearch` with the current values.
 */
export function FilterBar<Values = Record<string, unknown>>({
    children,
    form,
    initialValues,
    onSearch,
    onReset,
    loading,
    searchText = '查询',
    actions,
    minFieldWidth,
    instant = false,
}: FilterBarProps<Values>) {
    const [internalForm] = Form.useForm<Values>()
    const activeForm = form ?? internalForm
    const [resetToken, setResetToken] = useState(0)

    function handleReset() {
        activeForm.resetFields()
        // `resetFields` clears the store but Ant Design v6 does not repaint the
        // already-mounted controls, so the old text stays visible. Remounting
        // the field container makes them re-read the (now empty) store.
        setResetToken((token) => token + 1)
        if (onReset) {
            onReset()
            return
        }
        onSearch?.(activeForm.getFieldsValue())
    }

    return (
        <Form<Values>
            className="filter-bar"
            form={activeForm}
            initialValues={initialValues}
            layout="vertical"
            onFinish={(values) => onSearch?.(values)}
            onValuesChange={instant ? (_, values) => onSearch?.(values) : undefined}
        >
            <div className="filter-bar-body">
                <div
                    className="filter-bar-fields"
                    key={resetToken}
                    style={minFieldWidth ? {'--filter-field-min': `${minFieldWidth}px`} as CSSProperties : undefined}
                >
                    {children}
                </div>
                <div className="filter-bar-actions">
                    {actions}
                    <Button icon={<ReloadOutlined/>} onClick={handleReset}>重置</Button>
                    {onSearch && !instant && (
                        <Button htmlType="submit" icon={<SearchOutlined/>} loading={loading} type="primary">
                            {searchText}
                        </Button>
                    )}
                </div>
            </div>
        </Form>
    )
}
