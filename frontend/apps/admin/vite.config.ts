import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@shared': path.resolve(__dirname, '../../shared'),
    },
    dedupe: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'zustand', 'axios', 'js-cookie'],
  },
  server: {
    port: 3002,
    host: true,
    fs: {
      allow: ['../..'],
    },
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
    watch: {
      usePolling: true,
      interval: 300,
    },
  },
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', '@tanstack/react-query', 'zustand', 'axios', 'js-cookie'],
  },
});
