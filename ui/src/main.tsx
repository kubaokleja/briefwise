import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

// Fonts are self-hosted (no request to Google; the demo works offline).
import '@fontsource/inter/400.css';
import '@fontsource/inter/500.css';
import '@fontsource/inter/600.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import './index.css';

import { App } from './App';

const root = document.getElementById('root');
if (!root) {
  throw new Error('Missing #root element in index.html');
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
