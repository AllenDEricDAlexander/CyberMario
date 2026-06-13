import {defineConfig} from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
    plugins: [react()],
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
})
