import {theme as antdTheme} from 'antd'
import type {ThemeConfig} from 'antd'
import {
    darkTokens,
    fontFamily,
    lightTokens,
    palette,
    radius,
    spacing,
    type ThemeDensity,
    type ThemeMode,
} from './designTokens'

/**
 * Component-level tokens. These carry most of the visual identity so that pages
 * can stay free of `style={{...}}` patches — anything set here applies app-wide.
 */
function componentTokens(mode: ThemeMode): ThemeConfig['components'] {
    const dark = mode === 'dark'

    return {
        Layout: {
            bodyBg: 'transparent',
            headerBg: 'transparent',
            headerHeight: 60,
            headerPadding: `0 ${spacing.lg}px`,
            siderBg: 'transparent',
            triggerBg: 'transparent',
        },
        Menu: {
            itemBorderRadius: radius.sm,
            itemHeight: 38,
            itemMarginInline: 10,
            itemMarginBlock: 2,
            iconMarginInlineEnd: 10,
            subMenuItemBg: 'transparent',
            darkItemBg: 'transparent',
            darkSubMenuItemBg: 'transparent',
            darkPopupBg: '#0c2528',
            darkItemSelectedBg: 'transparent',
            collapsedWidth: 72,
        },
        Card: {
            borderRadiusLG: radius.lg,
            headerHeight: 52,
            headerHeightSM: 44,
            headerFontSize: 15,
            headerFontSizeSM: 14,
            bodyPadding: spacing.lg,
            bodyPaddingSM: spacing.md,
            paddingLG: spacing.lg,
        },
        Table: {
            borderRadiusLG: radius.lg,
            // Table surfaces stay opaque so fixed columns can paint over
            // horizontally scrolled cells. See `--table-surface` in tokens.css.
            colorBgContainer: dark ? '#0d2124' : '#ffffff',
            headerBg: dark ? '#132f32' : '#edf6f2',
            headerColor: dark ? '#c3ddd9' : '#234549',
            headerSplitColor: 'transparent',
            headerBorderRadius: 0,
            cellPaddingBlock: spacing.sm,
            cellPaddingBlockMD: 10,
            cellPaddingBlockSM: spacing.xs,
            rowHoverBg: dark ? '#143437' : '#eef8f4',
            rowSelectedBg: dark ? '#16393c' : '#e3f4ee',
            rowSelectedHoverBg: dark ? '#1a4043' : '#d9f0e8',
            footerBg: 'transparent',
            stickyScrollBarBg: dark ? 'rgba(45, 212, 191, 0.4)' : 'rgba(15, 118, 110, 0.32)',
        },
        Button: {
            borderRadius: radius.sm,
            borderRadiusLG: radius.md,
            borderRadiusSM: radius.sm,
            fontWeight: 600,
            primaryShadow: 'none',
            defaultShadow: 'none',
            dangerShadow: 'none',
            paddingInline: spacing.md,
            controlHeight: 36,
            controlHeightSM: 28,
            controlHeightLG: 42,
        },
        Input: {
            borderRadius: radius.sm,
            borderRadiusLG: radius.md,
            paddingBlock: 6,
            paddingInline: spacing.sm,
            activeShadow: `0 0 0 3px ${dark ? 'rgba(45, 212, 191, 0.2)' : 'rgba(45, 212, 191, 0.16)'}`,
            errorActiveShadow: `0 0 0 3px ${dark ? 'rgba(255, 115, 150, 0.2)' : 'rgba(222, 73, 108, 0.14)'}`,
            warningActiveShadow: `0 0 0 3px ${dark ? 'rgba(240, 168, 86, 0.2)' : 'rgba(217, 130, 43, 0.14)'}`,
        },
        InputNumber: {
            borderRadius: radius.sm,
            activeShadow: `0 0 0 3px ${dark ? 'rgba(45, 212, 191, 0.2)' : 'rgba(45, 212, 191, 0.16)'}`,
        },
        Select: {
            borderRadius: radius.sm,
            borderRadiusLG: radius.md,
            optionSelectedBg: dark ? 'rgba(45, 212, 191, 0.16)' : palette.accentSoft,
            optionSelectedFontWeight: 600,
            optionHeight: 32,
            optionPadding: '5px 12px',
        },
        DatePicker: {
            borderRadius: radius.sm,
            activeShadow: `0 0 0 3px ${dark ? 'rgba(45, 212, 191, 0.2)' : 'rgba(45, 212, 191, 0.16)'}`,
            cellActiveWithRangeBg: dark ? 'rgba(45, 212, 191, 0.14)' : palette.accentSoft,
        },
        Form: {
            labelColor: dark ? '#a3bcbb' : '#3f5a5d',
            labelFontSize: 13,
            labelRequiredMarkColor: palette.error,
            verticalLabelPadding: '0 0 6px',
            itemMarginBottom: spacing.md,
        },
        Drawer: {
            paddingLG: spacing.lg,
            footerPaddingBlock: spacing.sm,
            footerPaddingInline: spacing.lg,
        },
        Modal: {
            borderRadiusLG: radius.lg,
            headerBg: 'transparent',
            contentBg: dark ? darkTokens.colorBgElevated : '#ffffff',
            footerBg: 'transparent',
            titleFontSize: 16,
            padding: spacing.lg,
            paddingContentHorizontalLG: spacing.lg,
        },
        Tabs: {
            horizontalItemPadding: `10px 0`,
            horizontalItemGutter: spacing.lg,
            titleFontSize: 14,
            itemSelectedColor: dark ? palette.accentBright : palette.accent,
            itemHoverColor: dark ? palette.accentBright : palette.accentStrong,
            inkBarColor: dark ? palette.accentBright : palette.accent,
            cardBg: dark ? 'rgba(45, 212, 191, 0.06)' : 'rgba(238, 248, 243, 0.8)',
        },
        Segmented: {
            borderRadius: radius.sm,
            itemSelectedBg: dark ? 'rgba(45, 212, 191, 0.16)' : '#ffffff',
            itemSelectedColor: dark ? palette.accentBright : palette.accent,
            trackBg: dark ? 'rgba(45, 212, 191, 0.07)' : 'rgba(15, 118, 110, 0.07)',
            trackPadding: 3,
        },
        Tag: {
            borderRadiusSM: radius.sm,
            defaultBg: dark ? 'rgba(45, 212, 191, 0.1)' : 'rgba(238, 248, 243, 0.9)',
            defaultColor: dark ? '#a3bcbb' : '#3f5a5d',
        },
        Tooltip: {
            borderRadius: radius.sm,
            colorBgSpotlight: dark ? '#1d3d41' : '#14343a',
        },
        Popover: {
            borderRadiusLG: radius.md,
        },
        Dropdown: {
            borderRadiusLG: radius.md,
            controlItemBgHover: dark ? 'rgba(45, 212, 191, 0.1)' : 'rgba(217, 245, 237, 0.62)',
            controlItemBgActive: dark ? 'rgba(45, 212, 191, 0.16)' : palette.accentSoft,
        },
        Pagination: {
            borderRadius: radius.sm,
            itemActiveBg: dark ? 'rgba(45, 212, 191, 0.16)' : palette.accentSoft,
        },
        Descriptions: {
            labelBg: dark ? 'rgba(45, 212, 191, 0.06)' : 'rgba(238, 248, 243, 0.7)',
            titleMarginBottom: spacing.sm,
            itemPaddingBottom: spacing.sm,
        },
        Alert: {
            borderRadiusLG: radius.md,
            withDescriptionPadding: `${spacing.sm}px ${spacing.md}px`,
        },
        Message: {
            contentBg: dark ? darkTokens.colorBgElevated : '#ffffff',
            borderRadiusLG: radius.md,
        },
        Notification: {
            borderRadiusLG: radius.lg,
        },
        Statistic: {
            titleFontSize: 13,
            contentFontSize: 26,
        },
        Steps: {
            iconSize: 28,
            titleLineHeight: 28,
        },
        Empty: {
            colorTextDescription: dark ? '#7f9997' : '#6f8689',
        },
        Divider: {
            marginLG: spacing.md,
        },
        Avatar: {
            groupBorderColor: 'transparent',
        },
        Switch: {
            handleShadow: '0 2px 4px rgba(0, 0, 0, 0.18)',
        },
        Badge: {
            textFontSizeSM: 11,
        },
        Skeleton: {
            gradientFromColor: dark ? 'rgba(45, 212, 191, 0.06)' : 'rgba(15, 118, 110, 0.06)',
            gradientToColor: dark ? 'rgba(45, 212, 191, 0.14)' : 'rgba(15, 118, 110, 0.13)',
        },
        Result: {
            titleFontSize: 20,
            subtitleFontSize: 14,
        },
        Breadcrumb: {
            fontSize: 13,
            separatorMargin: 6,
            linkColor: dark ? '#7f9997' : '#6f8689',
            linkHoverColor: dark ? palette.accentBright : palette.accent,
            lastItemColor: dark ? '#e4f4f1' : '#12292c',
        },
        Upload: {
            borderRadiusLG: radius.md,
        },
        Collapse: {
            borderRadiusLG: radius.md,
            headerPadding: `10px ${spacing.md}px`,
            contentPadding: `${spacing.sm}px ${spacing.md}px`,
        },
        Tree: {
            nodeSelectedBg: dark ? 'rgba(45, 212, 191, 0.16)' : palette.accentSoft,
            nodeHoverBg: dark ? 'rgba(45, 212, 191, 0.08)' : 'rgba(217, 245, 237, 0.5)',
            titleHeight: 28,
        },
        List: {
            itemPadding: `${spacing.sm}px ${spacing.md}px`,
        },
        Slider: {
            handleSize: 12,
            handleSizeHover: 14,
        },
        Progress: {
            defaultColor: dark ? palette.accentBright : palette.accent,
        },
    }
}

export function buildAntdTheme(mode: ThemeMode, density: ThemeDensity): ThemeConfig {
    const base = mode === 'dark' ? darkTokens : lightTokens
    const algorithm =
        mode === 'dark'
            ? density === 'compact'
                ? [antdTheme.darkAlgorithm, antdTheme.compactAlgorithm]
                : [antdTheme.darkAlgorithm]
            : density === 'compact'
                ? [antdTheme.compactAlgorithm]
                : [antdTheme.defaultAlgorithm]

    return {
        algorithm,
        token: {
            ...base,
            borderRadius: radius.sm,
            borderRadiusLG: radius.md,
            borderRadiusSM: radius.sm,
            borderRadiusXS: 4,
            controlHeight: density === 'compact' ? 32 : 36,
            fontFamily,
            fontSize: 14,
            lineHeight: 1.5715,
            sizeStep: 4,
            sizeUnit: 4,
            wireframe: false,
            motionEaseInOut: 'cubic-bezier(0.16, 1, 0.3, 1)',
            motionDurationMid: '0.2s',
            boxShadow:
                mode === 'dark'
                    ? '0 10px 28px rgba(0, 0, 0, 0.44)'
                    : '0 10px 28px rgba(24, 72, 67, 0.1)',
            boxShadowSecondary:
                mode === 'dark'
                    ? '0 16px 42px rgba(0, 0, 0, 0.5)'
                    : '0 16px 42px rgba(24, 72, 67, 0.12)',
        },
        components: componentTokens(mode),
    }
}
