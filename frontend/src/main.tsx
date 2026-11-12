import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { ModuleRegistry, ClientSideRowModelModule } from 'ag-grid-community';
import { App } from './app/App';
import './styles/design-tokens.css';
import './styles/globals.css';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';

ModuleRegistry.registerModules([ClientSideRowModelModule]);

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error('Root element #root not found.');
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
