import React from 'react';
import ReactDOM from 'react-dom/client';
import { createQueryClient, QueryClientProvider } from '@shared/lib/queryClient';
import { BrowserRouter } from 'react-router-dom';
import ErrorBoundary from '@shared/components/ErrorBoundary';
import App from '@/App';
import './index.css';

const queryClient = createQueryClient();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
