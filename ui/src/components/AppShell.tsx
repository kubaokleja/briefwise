import type { ReactNode } from 'react';

// Navigation grows one screen per milestone (ADR 0007). Links become router links in M1.
const NAV = [{ label: 'Status', current: true }];

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <aside className="w-56 shrink-0 border-r border-line bg-surface-1 px-4 py-5">
        <p className="text-lg font-semibold tracking-tight">
          <span className="text-accent">Brief</span>wise
        </p>
        <p className="text-xs text-muted">Relationship Manager copilot</p>
        <nav aria-label="Main" className="mt-8">
          <ul className="space-y-1 text-sm">
            {NAV.map((item) => (
              <li key={item.label}>
                <span
                  aria-current={item.current ? 'page' : undefined}
                  className="block rounded border-l-2 border-accent bg-surface-2 px-3 py-1.5 text-text"
                >
                  {item.label}
                </span>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <main className="flex-1 px-8 py-6">{children}</main>
    </div>
  );
}
