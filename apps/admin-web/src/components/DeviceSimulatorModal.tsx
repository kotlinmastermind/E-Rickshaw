import React, { useState, useEffect } from 'react';
import {
  X, ChevronLeft, ChevronRight, Phone, ShieldAlert,
  AlertTriangle
} from 'lucide-react';
import { ScreenItem } from '../data/screensData';

interface DeviceSimulatorModalProps {
  screens: ScreenItem[];
  initialScreenId: number;
  category: 'cust' | 'drv';
  onClose: () => void;
}

export const DeviceSimulatorModal: React.FC<DeviceSimulatorModalProps> = ({
  screens,
  initialScreenId,
  category,
  onClose
}) => {
  const [currentId, setCurrentId] = useState<number>(initialScreenId);
  const [activeTab, setActiveTab] = useState<string>('Home');
  const [sosActive, setSosActive] = useState<boolean>(false);
  const [offerTimer, setOfferTimer] = useState<number>(20);
  const [showExitConfirm, setShowExitConfirm] = useState<boolean>(false);
  const [fatigueActive, setFatigueActive] = useState<boolean>(false);

  const currentScreen = screens.find(s => s.id === currentId) || screens[0];

  // 20-second offer timer countdown for Driver Screen 12 (Ride Request)
  useEffect(() => {
    let interval: any;
    if (category === 'drv' && (currentId === 12 || currentScreen.title.toLowerCase().includes('ride request'))) {
      setOfferTimer(20);
      interval = setInterval(() => {
        setOfferTimer(prev => {
          if (prev <= 1) {
            clearInterval(interval);
            // Auto reject on timeout per rule
            setCurrentId(10); // Return to Driver Home
            return 20;
          }
          return prev - 1;
        });
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [currentId, category]);

  // Is Active Ride Mode for Customer?
  // Screens 18 (Searching) to 25 (Fare Split / SOS)
  const isCustActiveRide = category === 'cust' && currentId >= 18 && currentId <= 25;

  // Bottom Nav items
  const custTabs = ['Home', 'Rides', 'Wallet', 'Account'];
  const drvTabs = ['Home', 'Earnings', 'Goals', 'Profile'];
  const currentTabs = category === 'cust' ? custTabs : drvTabs;

  // Handle Tab Click
  const handleTabClick = (tab: string) => {
    setActiveTab(tab);
    if (category === 'cust') {
      if (tab === 'Home') setCurrentId(8);
      else if (tab === 'Rides') setCurrentId(33);
      else if (tab === 'Wallet') setCurrentId(35);
      else if (tab === 'Account') setCurrentId(50);
    } else {
      if (tab === 'Home') setCurrentId(10);
      else if (tab === 'Earnings') setCurrentId(20);
      else if (tab === 'Goals') setCurrentId(31);
      else if (tab === 'Profile') setCurrentId(50);
    }
  };

  // Handle Back Press with Navigation Rule Protection
  const handleBackPress = () => {
    if (isCustActiveRide) {
      // Rule: Back during ride shows confirm dialog and NEVER cancels the ongoing trip
      setShowExitConfirm(true);
      return;
    }
    if (currentId > 1) {
      setCurrentId(prev => prev - 1);
    }
  };

  // Next step in linear flow
  const handleNextFlow = () => {
    if (category === 'cust') {
      // Linear booking flow: Home(8) -> Search(9) -> Choose(12) -> Confirm(16) -> Searching(18) -> Found(19) -> Live(23) -> Summary(28) -> Pay(29) -> Rate(30)
      if (currentId === 8) setCurrentId(9);
      else if (currentId === 9) setCurrentId(12);
      else if (currentId === 12) setCurrentId(16);
      else if (currentId === 16) setCurrentId(18);
      else if (currentId === 18) setCurrentId(19);
      else if (currentId === 19) setCurrentId(23);
      else if (currentId === 23) setCurrentId(28);
      else if (currentId === 28) setCurrentId(29);
      else if (currentId === 29) setCurrentId(30);
      else if (currentId < 50) setCurrentId(prev => prev + 1);
    } else {
      // Driver flow: OFFER(12) -> ACCEPT(14) -> ARRIVED(15) -> OTP -> IN_PROGRESS(16) -> DROPPED(17) -> PAY(18) -> RATE(19)
      if (currentId === 10) setCurrentId(12);
      else if (currentId === 12) setCurrentId(14);
      else if (currentId === 14) setCurrentId(15);
      else if (currentId === 15) setCurrentId(16);
      else if (currentId === 16) setCurrentId(17);
      else if (currentId === 17) setCurrentId(18);
      else if (currentId === 18) setCurrentId(19);
      else if (currentId < 50) setCurrentId(prev => prev + 1);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(0,0,0,0.92)',
      backdropFilter: 'blur(10px)',
      zIndex: 110,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '12px'
    }}>
      <div style={{
        display: 'flex',
        flexDirection: 'row',
        gap: 24,
        alignItems: 'center',
        maxWidth: 960,
        width: '100%',
        maxHeight: '94vh',
        justifyContent: 'center'
      }}>
        {/* Smartphone Shell */}
        <div style={{
          width: 320,
          height: 640,
          background: '#000000',
          border: '3px solid #d4af37',
          borderRadius: 44,
          boxShadow: '0 0 35px rgba(212,175,55,0.3), 0 25px 60px rgba(0,0,0,0.95)',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          position: 'relative'
        }}>
          {/* Top Speaker & Dynamic Island */}
          <div style={{
            height: 28,
            background: '#000',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 20px',
            fontSize: 10,
            color: '#8d8672',
            borderBottom: '1px solid #1a1a1f'
          }}>
            <span>9:41</span>
            <div style={{
              width: 70,
              height: 12,
              background: '#16161a',
              borderRadius: 8,
              border: '1px solid #2a2a2e'
            }} />
            <span>5G 100%</span>
          </div>

          {/* Screen Title Bar */}
          <div style={{
            padding: '10px 14px 6px',
            borderBottom: '1px solid #222',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            background: '#111115'
          }}>
            <button
              onClick={handleBackPress}
              style={{
                background: 'none',
                border: 'none',
                color: '#d4af37',
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                padding: 0
              }}
            >
              <ChevronLeft size={16} />
            </button>
            <div style={{ fontSize: 12, fontWeight: 700, color: '#f2ead3' }}>
              {currentScreen.title}
            </div>
            <span style={{ fontSize: 10, color: '#d4af37', background: 'rgba(212,175,55,0.1)', padding: '1px 6px', borderRadius: 10 }}>
              {currentScreen.id}/50
            </span>
          </div>

          {/* Screen Content Body */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '12px',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            position: 'relative',
            background: '#0b0b0d'
          }}>
            {/* Map Simulation if screen type is map */}
            {(currentScreen.type === 'm' || currentScreen.title.toLowerCase().includes('map') || currentScreen.title.toLowerCase().includes('navigate') || currentScreen.title.toLowerCase().includes('trip in progress')) && (
              <div style={{
                height: 150,
                borderRadius: 12,
                margin: '-4px -4px 6px',
                background: 'repeating-linear-gradient(0deg,#15151a 0 18px,#1c1c22 18px 19px), repeating-linear-gradient(90deg,transparent 0 18px,#1c1c22 18px 19px)',
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: '1px solid #2a2a2e'
              }}>
                <div style={{
                  width: 18,
                  height: 18,
                  background: '#d4af37',
                  borderRadius: '50% 50% 50% 0',
                  transform: 'rotate(-45deg)',
                  boxShadow: '0 0 12px rgba(212,175,55,0.7)'
                }} />
                <div style={{
                  position: 'absolute',
                  bottom: 8,
                  left: 10,
                  background: 'rgba(11,11,13,0.85)',
                  padding: '3px 8px',
                  borderRadius: 6,
                  fontSize: 10,
                  color: '#d4af37',
                  border: '1px solid #333'
                }}>
                  Live GPS • 28.6139° N, 77.2090° E
                </div>
              </div>
            )}

            {/* Elements */}
            {currentScreen.elements.map((el, i) => (
              <div
                key={i}
                style={{
                  background: currentScreen.type === 'f' ? '#0f0f12' : '#16161a',
                  border: '1px solid #2a2a2e',
                  borderRadius: 10,
                  padding: '9px 12px',
                  fontSize: 12,
                  color: currentScreen.type === 'f' ? '#8d8672' : '#f2ead3',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center'
                }}
              >
                <span>{el}</span>
                {currentScreen.type === 'f' && (
                  <span style={{ fontSize: 10, color: '#d4af37' }}>tap to edit</span>
                )}
              </div>
            ))}

            {/* Driver 20s Incoming Ride Offer Overlay */}
            {category === 'drv' && (currentId === 12 || currentScreen.title.toLowerCase().includes('ride request')) && (
              <div style={{
                position: 'absolute',
                inset: 0,
                background: 'rgba(0,0,0,0.92)',
                backdropFilter: 'blur(4px)',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                padding: 16,
                zIndex: 30
              }}>
                <div style={{
                  width: 70,
                  height: 70,
                  borderRadius: '50%',
                  border: '3px solid #d4af37',
                  display: 'grid',
                  placeItems: 'center',
                  fontSize: 22,
                  fontWeight: 700,
                  color: '#d4af37',
                  marginBottom: 12,
                  boxShadow: '0 0 20px rgba(212,175,55,0.4)'
                }}>
                  {offerTimer}s
                </div>
                <div style={{ color: '#fff', fontSize: 16, fontWeight: 700 }}>NEW RIDE REQUEST</div>
                <div style={{ color: '#d4af37', fontSize: 24, fontWeight: 800, margin: '6px 0' }}>₹ 145.00</div>
                <div style={{ fontSize: 11, color: '#8d8672', marginBottom: 16, textAlign: 'center' }}>
                  Pickup: Rajiv Chowk Gate 2 (1.2 km away)<br />Drop: Connaught Place Outer Circle
                </div>
                <div style={{ display: 'flex', gap: 10, width: '100%' }}>
                  <button
                    onClick={() => setCurrentId(10)}
                    style={{
                      flex: 1,
                      background: 'rgba(239,68,68,0.2)',
                      border: '1px solid #ef4444',
                      color: '#ef4444',
                      padding: 10,
                      borderRadius: 8,
                      fontWeight: 700,
                      fontSize: 12,
                      cursor: 'pointer'
                    }}
                  >
                    Reject
                  </button>
                  <button
                    onClick={() => setCurrentId(14)}
                    style={{
                      flex: 1,
                      background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                      border: 'none',
                      color: '#000',
                      padding: 10,
                      borderRadius: 8,
                      fontWeight: 700,
                      fontSize: 12,
                      cursor: 'pointer'
                    }}
                  >
                    Accept Ride
                  </button>
                </div>
              </div>
            )}

            {/* Fatigue Rest Lock Overlay */}
            {fatigueActive && (
              <div style={{
                position: 'absolute',
                inset: 0,
                background: 'rgba(0,0,0,0.95)',
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                padding: 16,
                zIndex: 35
              }}>
                <AlertTriangle size={36} color="#f59e0b" style={{ marginBottom: 10 }} />
                <div style={{ color: '#f59e0b', fontSize: 16, fontWeight: 700 }}>FATIGUE REST LOCK</div>
                <div style={{ fontSize: 12, color: '#8d8672', textAlign: 'center', margin: '8px 0 16px' }}>
                  You have been online for over 8 hours. Mandatory 20-minute rest is enforced by regulation.
                </div>
                <div style={{ fontSize: 28, color: '#fff', fontWeight: 800 }}>19:45</div>
                <button
                  onClick={() => setFatigueActive(false)}
                  style={{
                    marginTop: 20,
                    background: '#222',
                    border: '1px solid #444',
                    color: '#fff',
                    padding: '8px 16px',
                    borderRadius: 6,
                    fontSize: 11,
                    cursor: 'pointer'
                  }}
                >
                  Dismiss Demo Lock
                </button>
              </div>
            )}

            {/* Floating SOS Button during Customer Active Ride */}
            {isCustActiveRide && (
              <button
                onClick={() => setSosActive(true)}
                style={{
                  position: 'absolute',
                  top: 10,
                  right: 10,
                  background: 'rgba(239,68,68,0.9)',
                  border: '1px solid #fff',
                  borderRadius: 20,
                  color: '#fff',
                  padding: '5px 12px',
                  fontSize: 11,
                  fontWeight: 800,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                  boxShadow: '0 4px 15px rgba(239,68,68,0.6)',
                  cursor: 'pointer',
                  zIndex: 25
                }}
              >
                <ShieldAlert size={14} /> SOS
              </button>
            )}

            {/* SOS Dialog */}
            {sosActive && (
              <div style={{
                position: 'absolute',
                inset: 0,
                background: 'rgba(0,0,0,0.85)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 16,
                zIndex: 40
              }}>
                <div style={{
                  background: '#16161a',
                  border: '2px solid #ef4444',
                  borderRadius: 16,
                  padding: 16,
                  textAlign: 'center',
                  width: '100%'
                }}>
                  <ShieldAlert size={32} color="#ef4444" style={{ margin: '0 auto 8px' }} />
                  <div style={{ color: '#fff', fontSize: 14, fontWeight: 700 }}>TRIGGER EMERGENCY SOS?</div>
                  <div style={{ fontSize: 11, color: '#8d8672', margin: '6px 0 14px' }}>
                    This will instantly dispatch your GPS coordinates to Emergency SOC, local Police (112), and your designated emergency contacts.
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <button
                      onClick={() => {
                        alert('SOS broadcasted to Police & SOC Command Center!');
                        setSosActive(false);
                      }}
                      style={{
                        background: '#ef4444',
                        color: '#fff',
                        border: 'none',
                        padding: 10,
                        borderRadius: 8,
                        fontWeight: 700,
                        fontSize: 12,
                        cursor: 'pointer'
                      }}
                    >
                      CONFIRM SOS BROADCAST
                    </button>
                    <button
                      onClick={() => setSosActive(false)}
                      style={{
                        background: '#222',
                        color: '#aaa',
                        border: '1px solid #333',
                        padding: 8,
                        borderRadius: 8,
                        fontSize: 11,
                        cursor: 'pointer'
                      }}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Exit Confirm Dialog during Ride */}
            {showExitConfirm && (
              <div style={{
                position: 'absolute',
                inset: 0,
                background: 'rgba(0,0,0,0.85)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 16,
                zIndex: 40
              }}>
                <div style={{
                  background: '#16161a',
                  border: '1px solid #d4af37',
                  borderRadius: 14,
                  padding: 16,
                  textAlign: 'center',
                  width: '100%'
                }}>
                  <div style={{ color: '#d4af37', fontSize: 13, fontWeight: 700 }}>End ride here?</div>
                  <div style={{ fontSize: 11, color: '#8d8672', margin: '6px 0 12px' }}>
                    Confirm early drop. The ongoing ride remains active on our dispatch servers.
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button
                      onClick={() => setShowExitConfirm(false)}
                      style={{
                        flex: 1,
                        background: '#222',
                        color: '#aaa',
                        border: '1px solid #333',
                        padding: 8,
                        borderRadius: 6,
                        fontSize: 11,
                        cursor: 'pointer'
                      }}
                    >
                      Stay in Ride
                    </button>
                    <button
                      onClick={() => {
                        setShowExitConfirm(false);
                        setCurrentId(8); // Return to home
                      }}
                      style={{
                        flex: 1,
                        background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                        color: '#000',
                        border: 'none',
                        padding: 8,
                        borderRadius: 6,
                        fontWeight: 700,
                        fontSize: 11,
                        cursor: 'pointer'
                      }}
                    >
                      Exit to Home
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* Step Action Button */}
            <button
              onClick={handleNextFlow}
              style={{
                marginTop: 'auto',
                background: 'linear-gradient(90deg, #b8962e, #d4af37)',
                color: '#0b0b0d',
                border: 'none',
                padding: '10px',
                borderRadius: 10,
                fontWeight: 700,
                fontSize: 12,
                cursor: 'pointer',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 6
              }}
            >
              <span>Continue / Next Step</span>
              <ChevronRight size={14} />
            </button>
          </div>

          {/* Persistent Bottom Navigation (Hidden during active ride per rule) */}
          {!isCustActiveRide && (
            <div style={{
              display: 'flex',
              justifyContent: 'space-around',
              alignItems: 'center',
              borderTop: '1px solid #222226',
              padding: '8px 0 12px',
              background: '#0b0b0d'
            }}>
              {currentTabs.map(tab => (
                <button
                  key={tab}
                  onClick={() => handleTabClick(tab)}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: activeTab === tab ? '#d4af37' : '#8d8672',
                    fontSize: 10,
                    fontWeight: activeTab === tab ? 700 : 500,
                    cursor: 'pointer',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 2
                  }}
                >
                  <div style={{
                    width: 5,
                    height: 5,
                    borderRadius: '50%',
                    background: activeTab === tab ? '#d4af37' : 'transparent',
                    marginBottom: 2
                  }} />
                  {tab}
                </button>
              ))}
            </div>
          )}

          {/* Persistent Driver ETA Bottom Card when in Active Ride */}
          {isCustActiveRide && (
            <div style={{
              background: '#16161a',
              borderTop: '1px solid #d4af37',
              padding: '10px 14px',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <div style={{ color: '#d4af37', fontSize: 12, fontWeight: 700 }}>DL-1ER-4821 • Ramesh K.</div>
                <div style={{ fontSize: 10, color: '#8d8672' }}>Arriving in 3 mins • Start OTP: <b>4821</b></div>
              </div>
              <button
                onClick={() => alert('Connecting via VoIP Masked Call...')}
                style={{
                  background: '#222',
                  border: '1px solid #444',
                  color: '#d4af37',
                  borderRadius: '50%',
                  width: 32,
                  height: 32,
                  display: 'grid',
                  placeItems: 'center',
                  cursor: 'pointer'
                }}
              >
                <Phone size={14} />
              </button>
            </div>
          )}
        </div>

        {/* Right Simulator Controls & Flow Guide */}
        <div style={{
          flex: 1,
          maxWidth: 420,
          background: '#16161a',
          border: '1px solid #2a2a2e',
          borderRadius: 16,
          padding: 20,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          maxHeight: 640,
          overflowY: 'auto'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h3 style={{ margin: 0, color: '#d4af37', fontSize: 16 }}>
                {category === 'cust' ? 'Customer App Simulator' : 'Driver Cockpit Simulator'}
              </h3>
              <p style={{ margin: 0, color: '#8d8672', fontSize: 11 }}>
                Live Rapido-style interactive flow engine
              </p>
            </div>
            <button
              onClick={onClose}
              style={{
                background: '#222',
                border: '1px solid #333',
                color: '#fff',
                borderRadius: '50%',
                width: 28,
                height: 28,
                display: 'grid',
                placeItems: 'center',
                cursor: 'pointer'
              }}
            >
              <X size={14} />
            </button>
          </div>

          {/* Jump to screen slider / dropdown */}
          <div style={{ background: '#0b0b0d', padding: 12, borderRadius: 10, border: '1px solid #2a2a2e' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, marginBottom: 6 }}>
              <span style={{ color: '#8d8672' }}>Direct Screen Selector:</span>
              <span style={{ color: '#d4af37', fontWeight: 700 }}>Screen #{currentId}</span>
            </div>
            <select
              value={currentId}
              onChange={e => setCurrentId(Number(e.target.value))}
              style={{
                width: '100%',
                padding: '8px 10px',
                background: '#16161a',
                border: '1px solid #333',
                borderRadius: 6,
                color: '#f2ead3',
                fontSize: 12
              }}
            >
              {screens.map(s => (
                <option key={s.id} value={s.id}>
                  {String(s.id).padStart(2, '0')}. {s.title} ({s.elements.slice(0, 2).join(', ')})
                </option>
              ))}
            </select>
          </div>

          {/* Flow Stages Shortcuts */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ fontSize: 11, color: '#8d8672', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Rapido Lifecycle Shortcuts:
            </div>
            {category === 'cust' ? (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                <button
                  onClick={() => setCurrentId(8)}
                  style={{ background: currentId === 8 ? '#d4af37' : '#222', color: currentId === 8 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  1. Home Map (08)
                </button>
                <button
                  onClick={() => setCurrentId(12)}
                  style={{ background: currentId === 12 ? '#d4af37' : '#222', color: currentId === 12 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  2. Choose Ride (12)
                </button>
                <button
                  onClick={() => setCurrentId(18)}
                  style={{ background: currentId === 18 ? '#d4af37' : '#222', color: currentId === 18 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  3. Searching Radar (18)
                </button>
                <button
                  onClick={() => setCurrentId(19)}
                  style={{ background: currentId === 19 ? '#d4af37' : '#222', color: currentId === 19 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  4. Driver Found (19)
                </button>
                <button
                  onClick={() => setCurrentId(23)}
                  style={{ background: currentId === 23 ? '#d4af37' : '#222', color: currentId === 23 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  5. Live Ride + SOS (23)
                </button>
                <button
                  onClick={() => setCurrentId(28)}
                  style={{ background: currentId === 28 ? '#d4af37' : '#222', color: currentId === 28 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  6. Trip Summary (28)
                </button>
              </div>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
                <button
                  onClick={() => setCurrentId(10)}
                  style={{ background: currentId === 10 ? '#d4af37' : '#222', color: currentId === 10 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  1. Cockpit Home (10)
                </button>
                <button
                  onClick={() => setCurrentId(12)}
                  style={{ background: currentId === 12 ? '#d4af37' : '#222', color: currentId === 12 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  2. 20s Ride Offer (12)
                </button>
                <button
                  onClick={() => setCurrentId(14)}
                  style={{ background: currentId === 14 ? '#d4af37' : '#222', color: currentId === 14 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  3. Navigate Pickup (14)
                </button>
                <button
                  onClick={() => setCurrentId(15)}
                  style={{ background: currentId === 15 ? '#d4af37' : '#222', color: currentId === 15 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  4. Arrived & OTP (15)
                </button>
                <button
                  onClick={() => setCurrentId(16)}
                  style={{ background: currentId === 16 ? '#d4af37' : '#222', color: currentId === 16 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  5. Trip In Progress (16)
                </button>
                <button
                  onClick={() => setCurrentId(18)}
                  style={{ background: currentId === 18 ? '#d4af37' : '#222', color: currentId === 18 ? '#000' : '#f2ead3', border: '1px solid #333', padding: '6px 8px', borderRadius: 6, fontSize: 11, cursor: 'pointer', textAlign: 'left' }}
                >
                  6. Collect Cash/UPI (18)
                </button>
              </div>
            )}
          </div>

          {/* Special Condition Simulation Buttons */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <div style={{ fontSize: 11, color: '#8d8672', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Trigger Safety & Priority Events:
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {category === 'drv' && (
                <>
                  <button
                    onClick={() => setFatigueActive(true)}
                    style={{
                      flex: 1,
                      background: 'rgba(245,158,11,0.2)',
                      border: '1px solid #f59e0b',
                      color: '#f59e0b',
                      padding: '8px 10px',
                      borderRadius: 6,
                      fontSize: 11,
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    Simulate Fatigue Lock
                  </button>
                  <button
                    onClick={() => alert('Fake-GPS Warning: Mock coordinates detected. Online mode locked.')}
                    style={{
                      flex: 1,
                      background: 'rgba(239,68,68,0.2)',
                      border: '1px solid #ef4444',
                      color: '#ef4444',
                      padding: '8px 10px',
                      borderRadius: 6,
                      fontSize: 11,
                      cursor: 'pointer',
                      fontWeight: 600
                    }}
                  >
                    Simulate Mock GPS
                  </button>
                </>
              )}
              {category === 'cust' && (
                <button
                  onClick={() => setSosActive(true)}
                  style={{
                    flex: 1,
                    background: 'rgba(239,68,68,0.2)',
                    border: '1px solid #ef4444',
                    color: '#ef4444',
                    padding: '8px 10px',
                    borderRadius: 6,
                    fontSize: 11,
                    cursor: 'pointer',
                    fontWeight: 600
                  }}
                >
                  Trigger Emergency SOS Dialog
                </button>
              )}
            </div>
          </div>

          {/* Rules info */}
          <div style={{
            background: '#0b0b0d',
            padding: 12,
            borderRadius: 8,
            border: '1px solid #2a2a2e',
            fontSize: 11,
            color: '#8d8672',
            lineHeight: 1.5
          }}>
            <strong style={{ color: '#d4af37' }}>Active Rule Verification:</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: 16 }}>
              <li>4 persistent bottom tabs with auto-hide during active rides.</li>
              <li>Back button protected by exit dialog during ongoing trips.</li>
              {category === 'drv' && <li>20-second authoritative dispatch countdown with auto-reject on timeout.</li>}
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
};
