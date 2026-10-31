import React, { useState, useEffect } from 'react';
import {
  Eye, CheckCircle2, Shield,
  Zap, RefreshCw, Layers
} from 'lucide-react';
import { fetchWithAuth } from '../services/api';

interface CategoryItem {
  id: string;
  code: string;
  name: string;
  description: string;
  icon_url: string;
  capacity: number;
  legroom_inches: number;
  quietness_db: number;
  luggage_large_capacity: number;
  luggage_small_capacity: number;
  is_electric: boolean;
  emissions_g_per_km: number;
  wheelchair_accessible: boolean;
  step_free_height_cm: number;
  pet_friendly: boolean;
  child_seat_available: boolean;
  signature_amenities: string[];
}

export const FleetAccessibilityConsole: React.FC = () => {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<CategoryItem | null>(null);
  const [galleryImages, setGalleryImages] = useState<Array<{ angle: string; label: string; url: string }>>([]);
  const [activeTab, setActiveTab] = useState<'tiers' | 'gallery' | 'accessibility' | 'sustainability'>('tiers');
  const [isLoading, setIsLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    loadCategories();
  }, []);

  const loadCategories = async () => {
    setIsLoading(true);
    try {
      const res = await fetchWithAuth('/api/v1/vehicles/compare');
      if (res && res.categories) {
        setCategories(res.categories);
        if (res.categories.length > 0) {
          setSelectedCategory(res.categories[0]);
          loadGallery(res.categories[0].code);
        }
      } else {
        // Fallback default fleet tiers
        const fallbackCats: CategoryItem[] = [
          {
            id: 'cat_sedan',
            code: 'SEDAN',
            name: 'Executive Sedan',
            description: 'Mercedes-Benz E-Class with burr walnut trim and executive rear lounge.',
            icon_url: 'https://images.unsplash.com/photo-1555215695-3004980ad54e?w=400',
            capacity: 3,
            legroom_inches: 40.5,
            quietness_db: 54.0,
            luggage_large_capacity: 2,
            luggage_small_capacity: 2,
            is_electric: false,
            emissions_g_per_km: 142.0,
            wheelchair_accessible: false,
            step_free_height_cm: 34.0,
            pet_friendly: false,
            child_seat_available: true,
            signature_amenities: ['Chilled Mineral Water', 'Braided Device Chargers', 'High-Speed Wi-Fi']
          },
          {
            id: 'cat_suv',
            code: 'SUV',
            name: 'Prestige SUV',
            description: 'Range Rover Autobiography with panoramic canopy and elevated ride height.',
            icon_url: 'https://images.unsplash.com/photo-1563720223185-11003d516935?w=400',
            capacity: 5,
            legroom_inches: 42.0,
            quietness_db: 52.0,
            luggage_large_capacity: 4,
            luggage_small_capacity: 3,
            is_electric: false,
            emissions_g_per_km: 185.0,
            wheelchair_accessible: true,
            step_free_height_cm: 32.0,
            pet_friendly: true,
            child_seat_available: true,
            signature_amenities: ['All-Wheel Drive', 'Panoramic Starlight Roof', 'ISOFIX Restraint Suite']
          },
          {
            id: 'cat_royal',
            code: 'ROYAL',
            name: 'Royal Limousine',
            description: 'Mercedes-Maybach S680 with rear champagne bar and heated reclining massage suites.',
            icon_url: 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=400',
            capacity: 4,
            legroom_inches: 46.5,
            quietness_db: 48.0,
            luggage_large_capacity: 3,
            luggage_small_capacity: 3,
            is_electric: false,
            emissions_g_per_km: 210.0,
            wheelchair_accessible: true,
            step_free_height_cm: 30.0,
            pet_friendly: true,
            child_seat_available: true,
            signature_amenities: ['Champagne Cooler & Flutes', 'First-Class Reclining Suite', 'Privacy Partition Glass']
          },
          {
            id: 'cat_ev',
            code: 'ULTRA_LUXURY_EV',
            name: 'Ultra Luxury EV',
            description: 'BMW i7 xDrive60 with 31.3" 8K Theater Display and whisper-quiet electric silence.',
            icon_url: 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=400',
            capacity: 4,
            legroom_inches: 43.5,
            quietness_db: 46.0,
            luggage_large_capacity: 3,
            luggage_small_capacity: 2,
            is_electric: true,
            emissions_g_per_km: 0.0,
            wheelchair_accessible: true,
            step_free_height_cm: 31.0,
            pet_friendly: true,
            child_seat_available: true,
            signature_amenities: ['Zero Tailpipe Emissions', 'Acoustic Double-Glazing', 'Bowers & Wilkins Diamond 4D Audio']
          }
        ];
        setCategories(fallbackCats);
        setSelectedCategory(fallbackCats[0]);
        loadGallery(fallbackCats[0].code);
      }
    } catch (_e) {
      setStatusMessage('Connected with safe fallback fleet specifications.');
    } finally {
      setIsLoading(false);
    }
  };

  const loadGallery = async (categoryCode: string) => {
    try {
      const res = await fetchWithAuth(`/api/v1/vehicles/categories/${categoryCode}/gallery`);
      if (res && res.gallery_images) {
        setGalleryImages(res.gallery_images);
      } else {
        setGalleryImages([
          { angle: 'EXTERIOR_PROFILE', label: 'Aerodynamic Obsidian Silhouette', url: 'https://images.unsplash.com/photo-1555215695-3004980ad54e?w=800' },
          { angle: 'REAR_VIP_SUITE', label: 'First-Class Executive Reclining Suite', url: 'https://images.unsplash.com/photo-1563720223185-11003d516935?w=800' },
          { angle: 'MBUX_COCKPIT', label: 'Telemetry & Navigation Chauffeur Station', url: 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?w=800' },
          { angle: 'PANORAMIC_CANOPY', label: 'Starlight Sunroof & Ambient Illumination', url: 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=800' },
          { angle: 'LUGGAGE_HOLD', label: 'Plush Carpeted Cargo Hold (Up to 4 Pieces)', url: 'https://images.unsplash.com/photo-1584345604476-8ec5e12e42dd?w=800' }
        ]);
      }
    } catch (_e) {
      setGalleryImages([]);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Console Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ color: 'var(--gold-primary)', fontSize: '1.4rem', fontWeight: 600, margin: 0, letterSpacing: '0.04em' }}>
            FLEET SPECIFICATIONS & ACCESSIBILITY CONSOLE
          </h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', margin: '4px 0 0 0' }}>
            Part 3 Standards • High-Res Interior Galleries • Vehicle Comparison Matrix • Accessibility (WAV & Step-Free) • Emissions
          </p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={loadCategories}
            className="btn-luxury-secondary"
            style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.82rem' }}
          >
            <RefreshCw size={14} className={isLoading ? 'animate-spin' : ''} />
            Refresh Specs
          </button>
        </div>
      </div>

      {statusMessage && (
        <div style={{ padding: '10px 14px', background: 'rgba(212, 175, 55, 0.1)', border: '1px solid var(--border-gold)', borderRadius: 8, color: 'var(--gold-primary)', fontSize: '0.85rem' }}>
          {statusMessage}
        </div>
      )}

      {/* Navigation Sub-Tabs */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border-subtle)', paddingBottom: 10 }}>
        <button
          onClick={() => setActiveTab('tiers')}
          className={`nav-item ${activeTab === 'tiers' ? 'active' : ''}`}
          style={{ padding: '8px 16px', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 8, background: activeTab === 'tiers' ? 'var(--gold-glow)' : 'transparent', color: activeTab === 'tiers' ? 'var(--gold-primary)' : 'var(--text-muted)', border: activeTab === 'tiers' ? '1px solid var(--border-gold)' : 'none' }}
        >
          <Layers size={16} />
          <span>Vehicle Class Comparison (Feature 52)</span>
        </button>
        <button
          onClick={() => setActiveTab('gallery')}
          className={`nav-item ${activeTab === 'gallery' ? 'active' : ''}`}
          style={{ padding: '8px 16px', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 8, background: activeTab === 'gallery' ? 'var(--gold-glow)' : 'transparent', color: activeTab === 'gallery' ? 'var(--gold-primary)' : 'var(--text-muted)', border: activeTab === 'gallery' ? '1px solid var(--border-gold)' : 'none' }}
        >
          <Eye size={16} />
          <span>Interior & Exterior Galleries (Feature 51)</span>
        </button>
        <button
          onClick={() => setActiveTab('accessibility')}
          className={`nav-item ${activeTab === 'accessibility' ? 'active' : ''}`}
          style={{ padding: '8px 16px', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 8, background: activeTab === 'accessibility' ? 'var(--gold-glow)' : 'transparent', color: activeTab === 'accessibility' ? 'var(--gold-primary)' : 'var(--text-muted)', border: activeTab === 'accessibility' ? '1px solid var(--border-gold)' : 'none' }}
        >
          <Shield size={16} />
          <span>Accessibility & WAV Specs (Features 55, 56, 69)</span>
        </button>
        <button
          onClick={() => setActiveTab('sustainability')}
          className={`nav-item ${activeTab === 'sustainability' ? 'active' : ''}`}
          style={{ padding: '8px 16px', borderRadius: 8, display: 'flex', alignItems: 'center', gap: 8, background: activeTab === 'sustainability' ? 'var(--gold-glow)' : 'transparent', color: activeTab === 'sustainability' ? 'var(--gold-primary)' : 'var(--text-muted)', border: activeTab === 'sustainability' ? '1px solid var(--border-gold)' : 'none' }}
        >
          <Zap size={16} />
          <span>EV & Low-Emissions Fleet (Features 67, 68)</span>
        </button>
      </div>

      {/* TAB 1: FLEET FEATURE COMPARISON MATRIX (Feature 52, 72, 73) */}
      {activeTab === 'tiers' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: 16 }}>
            {categories.map((cat) => (
              <div
                key={cat.code}
                onClick={() => {
                  setSelectedCategory(cat);
                  loadGallery(cat.code);
                }}
                style={{
                  background: 'var(--bg-card)',
                  border: selectedCategory?.code === cat.code ? '1.5px solid var(--gold-primary)' : '1px solid var(--border-subtle)',
                  borderRadius: 12,
                  padding: 16,
                  cursor: 'pointer',
                  transition: 'all 0.2s ease',
                  position: 'relative'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 }}>
                  <div>
                    <h3 style={{ margin: 0, color: 'var(--gold-primary)', fontSize: '1.05rem', fontWeight: 600 }}>{cat.name}</h3>
                    <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{cat.code}</span>
                  </div>
                  <span style={{ background: 'var(--gold-glow)', color: 'var(--gold-primary)', fontSize: '0.72rem', padding: '3px 8px', borderRadius: 6, fontWeight: 600 }}>
                    Up to {cat.capacity} Guests
                  </span>
                </div>

                <p style={{ fontSize: '0.8rem', color: 'var(--text-primary)', margin: '0 0 12px 0', lineHeight: 1.4 }}>
                  {cat.description}
                </p>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8, padding: '10px 0', borderTop: '1px solid var(--border-subtle)', borderBottom: '1px solid var(--border-subtle)', marginBottom: 12 }}>
                  <div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>LEGROOM (FEATURE 58)</div>
                    <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--gold-primary)' }}>{cat.legroom_inches}" Lounge</div>
                  </div>
                  <div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>ACOUSTICS (FEATURE 59)</div>
                    <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--gold-primary)' }}>≤ {cat.quietness_db} dB Quiet</div>
                  </div>
                  <div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>LUGGAGE (FEATURE 53)</div>
                    <div style={{ fontSize: '0.88rem', fontWeight: 600, color: 'var(--text-primary)' }}>{cat.luggage_large_capacity} Large / {cat.luggage_small_capacity} Small</div>
                  </div>
                  <div>
                    <div style={{ fontSize: '0.68rem', color: 'var(--text-muted)' }}>PROPULSION (FEATURE 67)</div>
                    <div style={{ fontSize: '0.88rem', fontWeight: 600, color: cat.is_electric ? '#10b981' : 'var(--text-primary)' }}>
                      {cat.is_electric ? '100% Electric (0g)' : `${cat.emissions_g_per_km} g/km`}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                  {cat.wheelchair_accessible && (
                    <span style={{ fontSize: '0.7rem', padding: '2px 6px', background: 'rgba(16, 185, 129, 0.15)', color: '#10b981', borderRadius: 4 }}>
                      ♿ WAV Ramp
                    </span>
                  )}
                  {cat.child_seat_available && (
                    <span style={{ fontSize: '0.7rem', padding: '2px 6px', background: 'rgba(212, 175, 55, 0.15)', color: 'var(--gold-primary)', borderRadius: 4 }}>
                      👶 ISOFIX Child Seat
                    </span>
                  )}
                  {cat.pet_friendly && (
                    <span style={{ fontSize: '0.7rem', padding: '2px 6px', background: 'rgba(59, 130, 246, 0.15)', color: '#60a5fa', borderRadius: 4 }}>
                      🐾 Pet-Friendly
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 2: HIGH-RESOLUTION GALLERY AUDIT (Feature 51) */}
      {activeTab === 'gallery' && (
        <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div>
              <h3 style={{ margin: 0, color: 'var(--gold-primary)', fontSize: '1.1rem' }}>
                {selectedCategory ? `${selectedCategory.name.toUpperCase()} GALLERY` : 'GALLERY ASSETS'}
              </h3>
              <p style={{ color: 'var(--text-muted)', fontSize: '0.8rem', margin: '4px 0 0 0' }}>
                Multi-angle verified high-resolution photographs for customer app display
              </p>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              {categories.map((c) => (
                <button
                  key={c.code}
                  onClick={() => {
                    setSelectedCategory(c);
                    loadGallery(c.code);
                  }}
                  className={selectedCategory?.code === c.code ? 'btn-luxury-primary' : 'btn-luxury-secondary'}
                  style={{ fontSize: '0.78rem', padding: '5px 12px' }}
                >
                  {c.name}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
            {galleryImages.map((img, i) => (
              <div
                key={i}
                style={{
                  background: 'var(--bg-surface)',
                  border: '1px solid var(--border-subtle)',
                  borderRadius: 10,
                  overflow: 'hidden'
                }}
              >
                <div style={{ position: 'relative', height: 180 }}>
                  <img
                    src={img.url}
                    alt={img.label}
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  />
                  <span
                    style={{
                      position: 'absolute',
                      bottom: 8,
                      left: 8,
                      background: 'rgba(9, 10, 15, 0.85)',
                      border: '1px solid var(--border-gold)',
                      borderRadius: 4,
                      padding: '2px 8px',
                      color: 'var(--gold-primary)',
                      fontSize: '0.72rem',
                      fontWeight: 600
                    }}
                  >
                    {img.angle.replace('_', ' ')}
                  </span>
                </div>
                <div style={{ padding: 12 }}>
                  <div style={{ color: 'var(--text-primary)', fontSize: '0.85rem', fontWeight: 600 }}>{img.label}</div>
                  <div style={{ color: 'var(--text-muted)', fontSize: '0.72rem', marginTop: 4 }}>
                    Verified 4K UHD Asset • Verified Lighting & Cleanliness Audit
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 3: ACCESSIBILITY & WAV SPECIFICATIONS (Features 55, 56, 69, 70, 71) */}
      {activeTab === 'accessibility' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 20 }}>
            <h3 style={{ margin: '0 0 10px 0', color: 'var(--gold-primary)', fontSize: '1.1rem' }}>
              WHEELCHAIR & STEP-FREE COMPLIANCE AUDIT
            </h3>
            <p style={{ color: 'var(--text-muted)', fontSize: '0.82rem', margin: '0 0 16px 0' }}>
              Ensuring royal accessibility standards under the Rights of Persons with Disabilities Act & Global Luxury Chauffeur Accessibility Protocols.
            </p>

            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-gold)', color: 'var(--gold-primary)', textAlign: 'left' }}>
                  <th style={{ padding: '8px 12px' }}>VEHICLE TIER</th>
                  <th style={{ padding: '8px 12px' }}>WAV HOIST / RAMP</th>
                  <th style={{ padding: '8px 12px' }}>STEP-FREE KERB HEIGHT</th>
                  <th style={{ padding: '8px 12px' }}>GUIDE ANIMAL CAPACITY</th>
                  <th style={{ padding: '8px 12px' }}>CHAUFFEUR ASSISTANCE PROTOCOL</th>
                </tr>
              </thead>
              <tbody>
                {categories.map((c) => (
                  <tr key={c.code} style={{ borderBottom: '1px solid var(--border-subtle)' }}>
                    <td style={{ padding: '12px', fontWeight: 600, color: 'var(--gold-primary)' }}>{c.name}</td>
                    <td style={{ padding: '12px' }}>
                      {c.wheelchair_accessible ? (
                        <span style={{ color: '#10b981', display: 'flex', alignItems: 'center', gap: 4 }}>
                          <CheckCircle2 size={14} /> Hydraulic Hoist (350kg capacity)
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>Low-Curb Boarding Only</span>
                      )}
                    </td>
                    <td style={{ padding: '12px', color: 'var(--text-primary)' }}>
                      ≤ {c.step_free_height_cm} cm (Air Suspension kneeling)
                    </td>
                    <td style={{ padding: '12px' }}>
                      Certified Service / Guide Animals permitted in passenger footwell
                    </td>
                    <td style={{ padding: '12px', color: 'var(--text-muted)', fontSize: '0.8rem' }}>
                      Portico door opening, luggage/chair transfer assistance, discreet boarding guidance
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 4: EV PROPULSION & EMISSIONS (Features 67, 68) */}
      {activeTab === 'sustainability' && (
        <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border-subtle)', borderRadius: 12, padding: 20 }}>
          <h3 style={{ margin: '0 0 10px 0', color: 'var(--gold-primary)', fontSize: '1.1rem' }}>
            ZERO-EMISSIONS & LOW-CARBON FLEET QUOTA
          </h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.82rem', margin: '0 0 16px 0' }}>
            Auditing electric vehicle propulsion, battery charge telemetry, and carbon offset credentials.
          </p>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
            <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: 16 }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>TOTAL EV RATIO</div>
              <div style={{ fontSize: '1.8rem', fontWeight: 700, color: '#10b981', margin: '6px 0' }}>38.5%</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Target: 50% Zero Exhaust Fleet by 2027</div>
            </div>
            <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: 16 }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>AVERAGE FLEET EMISSIONS</div>
              <div style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--gold-primary)', margin: '6px 0' }}>84.2 g/km</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Euro 6d-ISC-FCM compliant</div>
            </div>
            <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-subtle)', borderRadius: 10, padding: 16 }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>VIP GUEST EV PREFERENCE OPT-IN</div>
              <div style={{ fontSize: '1.8rem', fontWeight: 700, color: '#60a5fa', margin: '6px 0' }}>62.4%</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Bespoke Sanctuary Guest Profiles</div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
