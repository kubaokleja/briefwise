import type { ReactNode } from 'react';
import { NavLink } from 'react-router';

// Navigation grows one screen per milestone (ADR 0007). A portfolio page is reached from the Dashboard tiles.
const NAV = [
  { label: 'Dashboard', to: '/', end: true },
  { label: 'News', to: '/news', end: false },
  { label: 'Status', to: '/status', end: false },
];

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
              <li key={item.to}>
                {/* NavLink sets aria-current="page" on the active link by itself. */}
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `block rounded border-l-2 px-3 py-1.5 ${
                      isActive
                        ? 'border-accent bg-surface-2 text-text'
                        : 'border-transparent text-muted hover:bg-surface-2 hover:text-text'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <main className="min-w-0 flex-1 px-8 py-6">{children}</main>
    </div>
  );
}
