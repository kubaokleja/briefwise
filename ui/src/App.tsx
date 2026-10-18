import { AppShell } from './components/AppShell';
import { StatusPage } from './pages/StatusPage';

// One screen in M0. A router comes with the second screen (M1).
export function App() {
  return (
    <AppShell>
      <StatusPage />
    </AppShell>
  );
}
