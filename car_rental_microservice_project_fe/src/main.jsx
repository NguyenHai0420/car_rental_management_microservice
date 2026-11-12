import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App as AntdApp, ConfigProvider } from 'antd'
import { AuthProvider } from './app/providers/AuthProvider'
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.min.css'
import './style/index.css'
import App from './app/App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ConfigProvider
      drawer={{ focusable: { trap: false } }}
      modal={{ focusable: { trap: false } }}
      theme={{
        token: {
          motion: !window.matchMedia('(prefers-reduced-motion: reduce)').matches,
          colorPrimary: '#ff6a1a',
          colorPrimaryHover: '#ff8a3d',
          colorPrimaryActive: '#e8480c',
          borderRadius: 8,
          colorLink: '#d4440c',
          colorLinkHover: '#ff6a1a',
          controlHeightLG: 44,
        },
      }}
    >
      <AntdApp>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </StrictMode>
)
