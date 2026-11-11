import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const backendTarget = process.env.BACKEND_URL || 'https://e-rickshaw-z32z.onrender.com';
const wsTarget = backendTarget.replace(/^http/, 'ws');

export default defineConfig({
  base: './',
  plugins: [react()],
  build: {
    target: 'esnext',
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (id.includes('node_modules/react/') || id.includes('node_modules/react-dom/')) {
            return 'vendor-react';
          }
          if (id.includes('node_modules/mapbox-gl/') || id.includes('node_modules/react-map-gl/')) {
            return 'vendor-mapbox';
          }
          if (id.includes('node_modules/leaflet/')) {
            return 'vendor-leaflet';
          }
          if (id.includes('node_modules/lucide-react/')) {
            return 'vendor-icons';
          }
        }
      }
    }
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api/v1/ws': {
        target: wsTarget,
        ws: true,
        changeOrigin: true
      },
      '/api/v1': {
        target: backendTarget,
        changeOrigin: true,
        ws: true
      },
      '/health': {
        target: backendTarget,
        changeOrigin: true
      }
    }
  }
});
