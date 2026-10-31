import React, { useState } from 'react';
import { CheckCircle } from 'lucide-react';
import { PopupItem, parsePopups } from '../data/screensData';

export const InteractivePopupPlayground: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');

  // Active Live Popup Queue State
  const [activeQueue, setActiveQueue] = useState<PopupItem[]>([]);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const sections: { key: 'cust' | 'drv' | 'adm'; title: string }[] = [
    { key: 'cust', title: 'Customer popups (24)' },
    { key: 'drv', title: 'Driver popups (24)' },
    { key: 'adm', title: 'Admin popups (20)' }
  ];

  // Priority ranking per specification
  const getPriorityScore = (item: PopupItem): number => {
    const t = item.title.toLowerCase();
    if (t.includes('session') || t.includes('timeout')) return 100;
    if (t.includes('sos') || t.includes('panic')) return 90;
    if (t.includes('ride request')) return 80;
    if (t.includes('fatigue') || t.includes('fake-gps')) return 70;
    if (t.includes('payment failed') || t.includes('internet')) return 60;
    if (item.type === 't') return 10;
    return 50;
  };

  // Trigger popup with Priority Queue handling
  const triggerPopup = (popup: PopupItem) => {
    if (popup.type === 't') {
      // Toast auto dismisses in 3 seconds, non-blocking
      setToastMessage(`${popup.title}: ${popup.bodyText}`);
      setTimeout(() => setToastMessage(null), 3000);
      return;
    }

    // Insert into priority queue
    setActiveQueue(prev => {
      const newQueue = [...prev, popup];
      newQueue.sort((a, b) => getPriorityScore(b) - getPriorityScore(a));
      return newQueue;
    });
  };

  // Close top popup
  const dismissCurrentPopup = () => {
    setActiveQueue(prev => prev.slice(1));
  };

  const currentActive = activeQueue[0];

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {/* Legend Notice matching uploaded HTML */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, padding: '0 16px' }}>
        <p className="lg" style={{ padding: 0, margin: 0 }}>
          Dialog = centred, blocking · Sheet = bottom, swipe-down · Toast = 3s, non-blocking · Full = critical, no dismiss
        </p>

        <input
          type="text"
          placeholder="Filter popups by name..."
          value={searchTerm}
          onChange={e => setSearchTerm(e.target.value)}
          style={{
            padding: '6px 14px',
            background: '#16161a',
            border: '1px solid #2a2a2e',
            borderRadius: 20,
            color: '#f2ead3',
            fontSize: 12,
            minWidth: 200
          }}
        />
      </div>

      {/* Render all 3 categories stacked matching uploaded HTML */}
      {sections.map(sec => {
        const list = parsePopups(sec.key).filter(p => {
          if (!searchTerm) return true;
          const term = searchTerm.toLowerCase();
          return p.title.toLowerCase().includes(term) || p.bodyText.toLowerCase().includes(term);
        });

        if (list.length === 0) return null;

        return (
          <div key={sec.key} style={{ marginTop: 12 }}>
            <h2 className="ph2">{sec.title}</h2>
            <div className="grid">
              {list.map((p, idx) => {
                const isToast = p.type === 't';
                const isSheet = p.type === 'b';
                const isFull = p.type === 'x';

                return (
                  <div
                    key={p.id}
                    className={`ph ${sec.key === 'adm' ? 'ad' : ''}`}
                    style={{ cursor: 'pointer' }}
                    onClick={() => triggerPopup(p)}
                    title="Click to trigger this popup live in the simulator"
                  >
                    <div className="top">
                      <span>{p.title}</span>
                      <i>{idx + 1}</i>
                    </div>

                    {/* Dimmed background wireframe */}
                    <div className="body" style={{ opacity: 0.3 }}>
                      <div className="r">&nbsp;</div>
                      <div className="r">&nbsp;</div>
                      <div className="r">&nbsp;</div>
                    </div>

                    {/* Wireframe Popup Render matching uploaded HTML */}
                    {isToast ? (
                      <div className="tt">
                        {p.title}: {p.bodyText}
                      </div>
                    ) : (
                      <div className={`ov ${isSheet ? 'bt' : isFull ? 'full' : ''}`}>
                        <div className={`dl ${isSheet ? 'sh' : ''}`}>
                          <b>{p.title}</b>
                          <span>{p.bodyText}</span>
                          {p.actions.map((act, aIdx) => (
                            <div
                              key={aIdx}
                              className={aIdx === 0 ? 'b' : 'f'}
                              style={{ textAlign: 'center', marginTop: aIdx === 0 ? 2 : 0 }}
                            >
                              {act}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Quick Trigger Pill on Hover */}
                    <div className="card-overlay-actions">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          triggerPopup(p);
                        }}
                        style={{
                          background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                          border: 'none',
                          color: '#000',
                          fontWeight: 700,
                          padding: '8px 14px',
                          borderRadius: 8,
                          fontSize: 11,
                          cursor: 'pointer'
                        }}
                      >
                        Trigger Live Popup
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      {/* LIVE INTERACTIVE POPUP SIMULATION MODAL */}
      {currentActive && (
        <div
          onClick={() => {
            // Outside tap dismisses Sheets only (per Navigation Rule 4)
            if (currentActive.type === 'b') {
              dismissCurrentPopup();
            }
          }}
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,0.85)',
            backdropFilter: 'blur(6px)',
            zIndex: 150,
            display: 'flex',
            alignItems: currentActive.type === 'b' ? 'flex-end' : 'center',
            justifyContent: 'center',
            padding: currentActive.type === 'b' ? 0 : 20
          }}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{
              background: '#16161a',
              border: '2px solid #d4af37',
              borderRadius: currentActive.type === 'b' ? '24px 24px 0 0' : currentActive.type === 'x' ? 0 : 16,
              width: currentActive.type === 'x' ? '100vw' : '100%',
              maxWidth: currentActive.type === 'x' ? '100vw' : 400,
              height: currentActive.type === 'x' ? '100vh' : 'auto',
              padding: 24,
              boxShadow: '0 20px 60px rgba(0,0,0,0.95), 0 0 30px rgba(212,175,55,0.3)',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: currentActive.type === 'x' ? 'center' : 'flex-start',
              textAlign: 'center',
              position: 'relative'
            }}
          >
            {/* Queue Counter Header if multiple popups queued */}
            {activeQueue.length > 1 && (
              <div style={{
                position: 'absolute',
                top: 10,
                left: 14,
                fontSize: 10,
                background: '#d4af37',
                color: '#000',
                fontWeight: 700,
                padding: '2px 8px',
                borderRadius: 10
              }}>
                Priority Queue: {activeQueue.length} Active
              </div>
            )}

            <div style={{
              color: currentActive.type === 'x' ? '#ef4444' : '#d4af37',
              fontSize: 18,
              fontWeight: 700,
              marginBottom: 8
            }}>
              {currentActive.title}
            </div>

            <div style={{
              color: '#8d8672',
              fontSize: 13,
              marginBottom: 20,
              lineHeight: 1.5
            }}>
              {currentActive.bodyText}
            </div>

            {/* Actions */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {currentActive.actions.map((act, i) => (
                <button
                  key={i}
                  onClick={() => dismissCurrentPopup()}
                  style={{
                    background: i === 0 ? 'linear-gradient(90deg, #b8962e, #d4af37)' : '#222226',
                    color: i === 0 ? '#0b0b0d' : '#f2ead3',
                    border: i === 0 ? 'none' : '1px solid #333',
                    padding: 12,
                    borderRadius: 10,
                    fontWeight: 700,
                    fontSize: 13,
                    cursor: 'pointer'
                  }}
                >
                  {act}
                </button>
              ))}
            </div>

            {/* Outside tap hint for bottom sheet */}
            {currentActive.type === 'b' && (
              <div style={{ fontSize: 11, color: '#555', marginTop: 12 }}>
                Swipe down or tap outside to dismiss sheet
              </div>
            )}
          </div>
        </div>
      )}

      {/* Toast Alert Pill */}
      {toastMessage && (
        <div style={{
          position: 'fixed',
          bottom: 24,
          left: '50%',
          transform: 'translateX(-50%)',
          background: '#d4af37',
          color: '#0b0b0d',
          padding: '10px 20px',
          borderRadius: 30,
          fontWeight: 700,
          fontSize: 13,
          boxShadow: '0 10px 30px rgba(0,0,0,0.8)',
          zIndex: 200,
          display: 'flex',
          alignItems: 'center',
          gap: 8
        }}>
          <CheckCircle size={16} />
          {toastMessage}
        </div>
      )}
    </div>
  );
};
