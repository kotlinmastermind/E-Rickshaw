import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backendTarget = process.env.BACKEND_URL || 'http://localhost:8000';
const wsTarget = backendTarget.replace(/^http/, 'ws');

export default defineConfig({
  base: './',
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api/v1/ws': {
        target: wsTarget,
        ws: true,
        changeOrigin: true
      },
      '/ws': {
        target: wsTarget,
        ws: true,
        changeOrigin: true
      },
      '/api/v1': {
        target: backendTarget,
        changeOrigin: true
      },
      '/health': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  }
});
