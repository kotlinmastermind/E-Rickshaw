import React, { useState } from 'react';


interface FareRuleItem {
  id: string;
  categoryCode: string;
  categoryName: string;
  version: number;
  baseFare: number;
  perKm: number;
  perMin: number;
  minFare: number;
  surge: number;
  isActive: boolean;
}

export const FareManagement: React.FC = () => {
  const [rules, setRules] = useState<FareRuleItem[]>([
    {
      id: "fr-001",
      categoryCode: "SEDAN",
      categoryName: "Executive Sedan",
      version: 1,
      baseFare: 500,
      perKm: 35,
      perMin: 5,
      minFare: 1000,
      surge: 1.0,
      isActive: true
    },
    {
      id: "fr-002",
      categoryCode: "LUXURY_SUV",
      categoryName: "Prestige SUV",
      version: 1,
      baseFare: 750,
      perKm: 50,
      perMin: 7.5,
      minFare: 1500,
      surge: 1.0,
      isActive: true
    },
    {
      id: "fr-003",
      categoryCode: "EXECUTIVE_LIMO",
      categoryName: "Royal Limousine",
      version: 1,
      baseFare: 1500,
      perKm: 90,
      perMin: 15,
      minFare: 3000,
      surge: 1.0,
      isActive: true
    }
  ]);

  const [editingRule, setEditingRule] = useState<FareRuleItem | null>(null);

  const handleSaveRule = () => {
    if (!editingRule) return;

    setRules(prev => prev.map(r => {
      if (r.categoryCode === editingRule.categoryCode) {
        return {
          ...editingRule,
          version: r.version + 1,
          id: `fr-v${r.version + 1}-${r.categoryCode}`
        };
      }
      return r;
    }));
    setEditingRule(null);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">Dynamic Tariff & Fare Governance</h1>
          <p className="page-description">Authoritative tariff versions, kilometer tariffs, minute charges, and surge multipliers.</p>
        </div>
      </div>

      <div className="stat-grid" style={{ gridTemplateColumns: 'repeat(3, 1fr)' }}>
        {rules.map(rule => (
          <div key={rule.id} className="stat-card" style={{ borderColor: 'var(--border-gold)' }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className="badge badge-gold">{rule.categoryName}</span>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Version {rule.version}.0</span>
              </div>
              <div className="stat-value gold" style={{ fontSize: '1.5rem', marginTop: 12 }}>
                ₹{rule.baseFare} <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Base Tariff</span>
              </div>
            </div>

            <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'flex', flexDirection: 'column', gap: 6, margin: '16px 0' }}>
              <div>Distance Rate: <strong>₹{rule.perKm}/km</strong></div>
              <div>Duration Rate: <strong>₹{rule.perMin}/min</strong></div>
              <div>Guaranteed Floor: <strong>₹{rule.minFare}</strong></div>
              <div>Active Surge: <strong>{rule.surge}x Multiplier</strong></div>
            </div>

            <button className="btn-gold" onClick={() => setEditingRule(rule)}>
              Adjust Tariff Schedule
            </button>
          </div>
        ))}
      </div>

      {editingRule && (
        <div className="modal-overlay" onClick={() => setEditingRule(null)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontFamily: 'var(--font-display)', color: 'var(--gold-primary)', marginBottom: 8 }}>
              Version Tariff: {editingRule.categoryName}
            </h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.88rem', marginBottom: 20 }}>
              Updating this schedule will create Version {editingRule.version + 1} and record an administrative audit entry.
            </p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14, marginBottom: 24 }}>
              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Base Fare (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.baseFare}
                  onChange={e => setEditingRule({ ...editingRule, baseFare: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Per-Kilometer Rate (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.perKm}
                  onChange={e => setEditingRule({ ...editingRule, perKm: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Minimum Floor Fare (₹)</label>
                <input
                  type="number"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.minFare}
                  onChange={e => setEditingRule({ ...editingRule, minFare: Number(e.target.value) })}
                />
              </div>

              <div>
                <label style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Surge Multiplier (1.0x - 3.0x)</label>
                <input
                  type="number"
                  step="0.1"
                  className="search-input"
                  style={{ width: '100%', marginTop: 4 }}
                  value={editingRule.surge}
                  onChange={e => setEditingRule({ ...editingRule, surge: Number(e.target.value) })}
                />
              </div>
            </div>

            <div style={{ display: 'flex', gap: 12 }}>
              <button className="btn-gold" style={{ flex: 1 }} onClick={handleSaveRule}>
                Publish Versioned Rule
              </button>
              <button className="btn-secondary" onClick={() => setEditingRule(null)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
