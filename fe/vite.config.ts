import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'
import {visualizer} from 'rollup-plugin-visualizer'

// https://vite.dev/config/
export default defineConfig(({mode}) => ({
    plugins: [
        react(),
        mode === 'analyze'
            ? visualizer({
                filename: 'dist/stats.html',
                gzipSize: true,
                brotliSize: true,
            })
            : undefined,
    ],
    build: {
        chunkSizeWarningLimit: 1200,
    },
    server: {
        port: 5173,
        proxy: {
            '/demo': {
                target:
                    process.env.VITE_BACKEND_TARGET ||
                    process.env.VITE_API_BASE_URL ||
                    `http://localhost:${process.env.VITE_BACKEND_PORT || process.env.BACKEND_PORT || '28080'}`,
                changeOrigin: true,
            },
            '/api': {
                target:
                    process.env.VITE_BACKEND_TARGET ||
                    process.env.VITE_API_BASE_URL ||
                    `http://localhost:${process.env.VITE_BACKEND_PORT || process.env.BACKEND_PORT || '28080'}`,
                changeOrigin: true,
            },
        },
    },
}))
