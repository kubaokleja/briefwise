import type { Health, Portfolio } from '../api/types';
import { useApi } from '../api/useApi';
import { Percent } from '../components/Num';
import { Empty, ErrorPanel, Loading } from '../components/States';

// M0 smoke screen: proves the proxy, the API client, the generated types and the three UI states end to end.
export function StatusPage() {
  return (
    <div className="max-w-4xl space-y-8">
      <h1 className="text-xl font-semibold">System status</h1>
      <HealthSection />
      <PortfoliosSection />
    </div>
  );
}

function HealthSection() {
  const health = useApi<Health>('/actuator/health');
  return (
    <section aria-labelledby="health-heading" className="rounded border border-line bg-surface-1 p-4">
      <h2 id="health-heading" className="mb-3 text-sm font-medium text-muted">
        API
      </h2>
      {health.status === 'loading' && <Loading label="Checking the API" />}
      {health.status === 'error' && <ErrorPanel error={health.error} onRetry={health.reload} />}
      {health.status === 'success' && <HealthBadge status={health.data.status} />}
    </section>
  );
}

function HealthBadge({ status }: { status: Health['status'] }) {
  const up = status === 'UP';
  return (
    <p className={`text-sm font-medium ${up ? 'text-up' : 'text-warning'}`}>
      <span aria-hidden="true">{up ? '●' : '▲'} </span>
      {up ? 'Operational' : `Degraded (${status})`}
    </p>
  );
}

function PortfoliosSection() {
  const portfolios = useApi<Portfolio[]>('/api/portfolios');
  return (
    <section aria-labelledby="portfolios-heading" className="rounded border border-line bg-surface-1 p-4">
      <h2 id="portfolios-heading" className="mb-3 text-sm font-medium text-muted">
        Portfolios
      </h2>
      {portfolios.status === 'loading' && <Loading label="Loading portfolios" />}
      {portfolios.status === 'error' && <ErrorPanel error={portfolios.error} onRetry={portfolios.reload} />}
      {portfolios.status === 'success' &&
        (portfolios.data.length === 0 ? (
          <Empty title="No portfolios yet" hint="Synthetic portfolios are seeded by Flyway (V5)." />
        ) : (
          <PortfolioTable portfolios={portfolios.data} />
        ))}
    </section>
  );
}

function PortfolioTable({ portfolios }: { portfolios: Portfolio[] }) {
  return (
    <table className="w-full text-sm">
      <thead className="text-left text-xs text-muted">
        <tr className="border-b border-line">
          <th className="py-2 font-medium">Portfolio</th>
          <th className="py-2 font-medium">Risk profile</th>
          <th className="py-2 text-right font-medium">Positions</th>
          <th className="py-2 text-right font-medium">Largest position</th>
        </tr>
      </thead>
      <tbody>
        {portfolios.map((portfolio) => {
          const largest = portfolio.positions.reduce<Portfolio['positions'][number] | undefined>(
            (max, position) => (max === undefined || position.weight > max.weight ? position : max),
            undefined,
          );
          return (
            <tr key={portfolio.id} className="border-b border-line/60 last:border-0">
              <td className="py-2">{portfolio.name}</td>
              <td className="py-2 text-muted">{titleCase(portfolio.riskProfile)}</td>
              <td className="py-2 text-right font-mono tabular-nums">{portfolio.positions.length}</td>
              <td className="py-2 text-right">
                {largest ? (
                  <>
                    <span className="font-mono">{largest.ticker}</span> <Percent points={largest.weight * 100} />
                  </>
                ) : (
                  <span className="text-muted">–</span>
                )}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function titleCase(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}
