import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './styles.css';

/**
 * 애플리케이션 진입점. / Application entry point.
 *
 * req: CONST-TECH-L01, ADR-001
 */
const container = document.getElementById('root');
if (!container) {
  throw new Error('#root element is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
