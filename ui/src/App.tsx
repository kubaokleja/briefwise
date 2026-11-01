import { Route, Routes } from 'react-router';
import { AppShell } from './components/AppShell';
import { Empty } from './components/States';
import { DashboardPage } from './pages/DashboardPage';
import { NewsPage } from './pages/NewsPage';
import { PortfolioPage } from './pages/PortfolioPage';
import { StatusPage } from './pages/StatusPage';

// Declarative routing (ADR 0007): the URL picks the screen, each screen loads its own data with useApi.
// The router itself (BrowserRouter) is set up in main.tsx, so tests can wrap App in a MemoryRouter instead.
export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/news" element={<NewsPage />} />
        <Route path="/portfolios/:portfolioId" element={<PortfolioPage />} />
        <Route path="/status" element={<StatusPage />} />
        <Route path="*" element={<Empty title="Page not found" hint="Use the navigation on the left." />} />
      </Routes>
    </AppShell>
  );
}
