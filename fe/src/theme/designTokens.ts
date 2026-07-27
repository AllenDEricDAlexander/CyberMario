/**
 * Single source of truth for the visual language.
 *
 * The same numbers are consumed twice: here by the Ant Design `ConfigProvider`
 * theme, and in `src/styles/tokens.css` by hand-written CSS. Keep the two in
 * sync — the CSS file documents which custom property mirrors which entry.
 */

export const palette = {
    /** Brand teal — primary actions, selected states, focus rings. */
    accent: '#0f766e',
    accentStrong: '#0b5f59',
    accentBright: '#2dd4bf',
    accentSoft: '#d9f5ed',
    /** Supporting hues used by tags, charts and status surfaces. */
    coral: '#e76f61',
    amber: '#d9822b',
    sky: '#2574d8',
    violet: '#7c5cd6',
    success: '#16a06f',
    warning: '#d9822b',
    error: '#de496c',
    info: '#2574d8',
} as const

export const lightTokens = {
    colorPrimary: palette.accent,
    colorInfo: palette.info,
    colorSuccess: palette.success,
    colorWarning: palette.warning,
    colorError: palette.error,
    colorLink: palette.accent,
    colorText: '#12292c',
    colorTextSecondary: '#4c6467',
    colorTextTertiary: '#6f8689',
    colorTextQuaternary: '#9bafb1',
    colorBorder: '#cfe2dc',
    colorBorderSecondary: '#e3efea',
    colorBgLayout: '#eef6f3',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorBgSpotlight: '#12292c',
    colorFillQuaternary: 'rgba(15, 118, 110, 0.04)',
    colorFillTertiary: 'rgba(15, 118, 110, 0.07)',
    colorFillSecondary: 'rgba(15, 118, 110, 0.1)',
} as const

export const darkTokens = {
    colorPrimary: '#2dd4bf',
    colorInfo: '#5aa2f5',
    colorSuccess: '#3ecf9a',
    colorWarning: '#f0a856',
    colorError: '#ff7396',
    colorLink: '#2dd4bf',
    colorText: '#e4f4f1',
    colorTextSecondary: '#a3bcbb',
    colorTextTertiary: '#7f9997',
    colorTextQuaternary: '#5f7877',
    colorBorder: '#24413f',
    colorBorderSecondary: '#1b3230',
    colorBgLayout: '#07171a',
    colorBgContainer: '#0d2124',
    colorBgElevated: '#12292c',
    colorBgSpotlight: '#1a3538',
    colorFillQuaternary: 'rgba(45, 212, 191, 0.05)',
    colorFillTertiary: 'rgba(45, 212, 191, 0.09)',
    colorFillSecondary: 'rgba(45, 212, 191, 0.13)',
} as const

/** 4px baseline grid. Every gap/padding in the app should land on one of these. */
export const spacing = {
    xxs: 4,
    xs: 8,
    sm: 12,
    md: 16,
    lg: 24,
    xl: 32,
    xxl: 48,
} as const

export const radius = {
    sm: 6,
    md: 10,
    lg: 14,
    xl: 20,
} as const

export const fontFamily =
    "'Inter var', Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif"

export const monoFontFamily =
    "'SFMono-Regular', ui-monospace, SFMono-Regular, Menlo, Consolas, 'Liberation Mono', monospace"

/** Widths for the standard drawer sizes so every editor drawer lines up. */
export const drawerWidth = {
    sm: 420,
    md: 560,
    lg: 720,
    xl: 900,
} as const

export type ThemeMode = 'light' | 'dark'
export type ThemeDensity = 'comfortable' | 'compact'
