export interface ScreenItem {
  id: number;
  type: 's' | 'f' | 'l' | 'm' | 'd' | 'p' | 'c';
  title: string;
  elements: string[];
  category: 'cust' | 'drv' | 'adm';
  tableTarget?: string;
  apiEndpoint?: string;
  crudActions?: ('create' | 'read' | 'update' | 'delete')[];
}

export interface PopupItem {
  id: number;
  type: 'd' | 'b' | 'x' | 't'; // d: Dialog, b: Bottom sheet, x: Fullscreen, t: Toast
  title: string;
  bodyText: string;
  actions: string[];
  category: 'cust' | 'drv' | 'adm';
  priority?: string;
}

export const RAW_DATA = {
  cust: `s|Splash|Logo,Tagline
f|Language & permissions|Language list,Location,Notifications
f|Phone login|+91 number,Send OTP
f|OTP verify|6-box OTP,Resend 30s
f|Profile setup|Avatar,Name,Email
f|App lock|PIN,Biometric,Passkey
l|MFA settings|Authenticator,SMS,Backup codes
m|Home map|Where to?,Home / Work,Nearby rickshaws
l|Search destination|Search bar,Recents,Favorites
m|Pin drop|Centre pin,Address,Gate notes
l|Multi-stop route|Pickup,Stop 1,Add stop,Drop
l|Choose ride|Standard,Shared,Cargo,Metro Feeder
d|Fare breakup|Total ₹,Base+km+time,Promo code
f|Ride preferences|Quiet ride,AC temp,Luggage,Accessibility
l|Payment method|UPI,Card,Wallet,Cash
d|Confirm booking|Route,Fare,Book now
p|Out-of-zone alert|Restricted road,Change drop
m|Searching driver|Radar pulse,Offer timer,Cancel
d|Driver found|Photo + rating,Plate,Start OTP,ETA
m|Driver arriving|Live route,ETA,Call / Chat
l|Ride chat|Messages,Quick replies
p|Masked call|Driver name,Mute,End
m|Ride in progress|Live route,Share,SOS
f|Share trip|Contacts,Hide address,Expiry
p|SOS panic|Hold to alert,Contacts notified,Call police
l|Fare split|Co-passengers,Share ₹,Status
f|Cancel ride|Reasons,Fee notice,Confirm
d|Trip summary|Fare total,Distance / time,Receipt
l|Payment|UPI,Card,Wallet,Cash
f|Rate driver|Stars,Tag chips,Tip
f|Fare dispute|Ride,Amount,Reason
l|Refund status|Amount,Timeline,Gateway ref
l|Ride history|Filter tabs,Ride cards
d|Ride detail|Map,Fare,Invoice PDF
d|Wallet home|Balance ₹,Add money,Recent txns
f|Add money|Amount chips,Method,Auto top-up
l|Transactions|Credit / debit,Filters
l|Saved payments|UPI VPAs,Cards,Add new
l|Promo codes|Apply field,Offer cards
d|Refer & Earn|Code,Share,Rewards
m|Pool booking|Route,Seat select,Fare
m|Pool live trip|Stops order,Occupancy,Messages
l|Emergency contacts|Contacts,Notify toggles,Add
f|Report incident|Category,Severity,Evidence
f|Lost item|Item type,Description,Ride
l|Help centre|FAQs,Tickets,New ticket
l|Ticket chat|Agent thread,Attach,CSAT
l|Notifications|Offers,Ride alerts
d|Loyalty|Tier,Points,Streak
l|Profile & settings|Accessibility,Language,Logout`,

  drv: `f|Login OTP|Phone,OTP,Verify
f|Profile|Photo,Name,Language
f|Vehicle details|RC,Permit,PUC,Insurance
f|Document upload|Aadhaar,DL,RC
f|Selfie KYC|Camera frame,Liveness
f|Bank / UPI verify|Account,IFSC,UPI VPA
d|Approval tracker|Steps,Status,Support
l|Rejected docs|Reason,Re-upload
l|Training & terms|Videos,Accept
m|Driver home|Online toggle,Today ₹,Demand zones
m|Heatmap & surge|Heat zones,Multiplier
p|Ride request|20s ring,Fare,Pickup km,Accept / Reject
f|Auto-accept|Toggle,Max km,Min fare
m|Navigate to pickup|Route,ETA,Call customer
f|Arrived & OTP|4-digit OTP,Wait timer,Start
m|Trip in progress|Route,Stops,SOS
d|Drop & complete|Fare,Distance,Complete
d|Collect payment|Cash / UPI,Confirm,Tip
f|Rate customer|Stars,Tags
d|Earnings dashboard|Today,Week chart,Trips
d|Daily / weekly|Bar chart,Online hours
l|Trip history|Date filter,Trip cards
l|Ledger|Commission,Tips,Balance after
d|Wallet balance|Balance,Withdraw
f|Withdraw|Amount,Account,Fee
l|Payout status|Requested,Processing,Paid
l|Payout accounts|Bank,UPI,Primary
l|Tips|Trip,Amount
d|Statements|Month list,Download PDF
d|Daily target|Progress ring,Bonus
d|Weekly goals|Milestones,Bonus
l|Incentives|Programs,Progress,Expiry
d|Badge tier|Rating,Cancel rate,Tier
l|Leaderboard|Rank,City top 10
p|SOS|Hold,Contacts,Police
p|Fatigue alert|Hours online,Rest timer
l|Shift log|Start / end,Breaks,Earnings
f|Report incident|Category,Details,Evidence
f|Lost item|Item,Return plan
p|Fake-GPS warning|Risk score,Fix steps
m|Pool manager|Seats,Stop order,Occupancy
m|Pool navigation|Next stop,Pickup list
f|Pool broadcast|Quick messages,Send
l|Ride chat|Thread,Quick replies
l|Support tickets|List,New ticket
l|Notifications|Broadcasts,Alerts
l|Doc expiry|Expiring docs,Renew
l|Ratings|Feedback,Tags,Dispute
f|Settings|Language,Sound,Nav app
l|Profile & vehicle|Switch vehicle,Docs,Logout`,

  adm: `f|Login + MFA|Email,Password,OTP
d|Live dashboard|KPIs,Rides chart,Alerts
l|Global search|Rides,Users,Drivers
l|Audit logs|Actor,Action,Filters
m|Live map|Rides,Drivers,Zones
l|Dispatch monitor|Offers,Timeouts,Retries
l|Ride list|Filters,Status,Export
d|Ride detail|Timeline,Fare,Chat
l|Driver list|Status,Rating,Filters
l|Onboarding queue|Tickets,Assign,SLA
d|Document review|Preview,Approve,Reject
d|Driver profile|Docs,Wallet,Trips
l|Fatigue / spoof alerts|Risk score,Action
l|Customers|Search,Profile,Tier
d|Loyalty mgmt|Tiers,Points rules
l|Vehicle categories|Amenities,Capacity,Active
l|Vehicles|Plate,PUC,Fitness
f|Fare rules|Base,Per km,Commission
f|Surge rules|Zone,Multiplier,Schedule
m|Geofence editor|Polygon,Zone type
m|Road restrictions|Segment,Type,Active
l|Payments|Gateway,Status,Filters
l|Refunds & disputes|Amount,Resolve
l|Driver payouts|Requests,Approve
l|SOS console|Live alerts,Contacts,Notes
l|Incidents|Ticket,Severity,Evidence
l|Support tickets|SLA,Priority,Assign
f|Promos & referrals|Code,Limits,Validity
f|Broadcasts|Audience,Message,Schedule
d|Roles, analytics & latency|RBAC matrix,Route latency,Reports`
};

export const RAW_POPUPS = {
  cust: `d|Location permission|Allow location for pickup|Allow/Not now
d|Notification permission|Get ride alerts|Allow/Skip
d|Wrong OTP|2 attempts left|Retry
b|Logout|Sign out of this device?|Logout/Stay
d|No internet|Check connection|Retry
d|GPS off|Turn on GPS|Open settings
b|Confirm pickup|Pickup point + gate|Confirm/Adjust
b|Add favourite|Label Home / Work|Save/Cancel
d|Out of zone|Service not available here|Change drop
d|Surge pricing|1.5x demand now|Book/Wait
b|Fare changed|New fare shown|Accept/Cancel
b|Promo applied|You saved ₹|Done
d|No driver found|Retry or raise fare|Retry/Cancel
x|Driver arrived|Share OTP 4821|Share OTP
d|Cancel ride?|Fee ₹20 applies|Keep ride/Cancel
d|End ride here?|Confirm early drop|Yes/No
b|Add tip|₹10 / 20 / 50|Pay tip/Skip
b|Payment failed|Try another method|Retry/Switch
b|Low wallet|Add money to continue|Add money
d|Send SOS?|Alerts contacts + police|Send/Cancel
t|Trip shared|Link copied|—
d|Rate driver|How was the ride?|Rate/Later
b|Dispute submitted|Ticket created|Track
x|Session expired|Please login again|Login`,

  drv: `d|Go online?|Docs and location OK|Go online/Later
d|Go offline?|Active ride pending|Stay/Offline
x|New ride request|20s timer + fare|Accept/Reject
t|Request missed|Auto-rejected|—
d|Reject reason|Too far / Traffic / Break|Submit/Back
b|Navigation app|Choose Maps|Open
d|Arrived|Customer notified|OK
d|Wrong OTP|Ask customer again|Retry
d|Start trip?|OTP verified|Start/Back
b|Collect cash|Collect ₹128|Confirmed
b|Customer cancelled|Cancel fee credited|Go online
d|Cancel trip?|Penalty warning|Keep/Cancel
x|Fatigue rest|Mandatory 20 min rest|Start rest
d|Fake GPS|Disable mock location|Fix now
d|Document expiring|DL expires in 7 days|Renew/Later
d|Document rejected|Reason shown|Re-upload
b|Confirm withdraw|Amount to bank|Confirm/Cancel
d|Low balance|Minimum ₹100|OK
b|Incentive unlocked|Bonus credited|Claim
d|Send SOS?|Alerts HQ + contacts|Send/Cancel
b|Surge zone|Move to hot area|Navigate
d|Low battery|Under 15%|OK
t|Poor network|Reconnecting…|—
d|End shift?|Logout and go offline|Logout/Stay`,

  adm: `d|Confirm refund|Amount + reason|Approve/Cancel
d|Ban user|Reason required|Ban/Cancel
d|Wallet adjust|Amount + note|Apply/Cancel
d|Approve driver|Docs verified|Approve/Reject
d|Reject documents|Rejection reason|Submit
b|Assign ticket|Pick agent|Assign
d|Delete geofence|Irreversible|Delete/Cancel
d|Publish surge|Zone + multiplier|Publish/Cancel
b|Send broadcast|Audience count|Send/Schedule
d|Force cancel ride|Reason required|Confirm/Cancel
d|Resolve SOS|Add notes|Resolve
b|Export data|CSV / PDF|Export
d|Role change|Permission diff|Save/Cancel
d|MFA required|Enter OTP|Verify
x|Session timeout|Login again|Login
t|Saved|Changes saved|—
t|Permission denied|Contact superadmin|—
b|Filters|Date, status, city|Apply/Reset
d|Disable promo|Active users affected|Disable/Cancel
b|Approve payouts|Batch total|Approve`
};

export const ADMIN_CRUD_MAPPING: Record<number, { table: string; endpoint: string; actions: ('create'|'read'|'update'|'delete')[]; description: string }> = {
  1: { table: 'users', endpoint: '/auth/login', actions: ['read', 'create'], description: 'Admin authentication and session verification' },
  2: { table: 'rides', endpoint: '/admin/stats/overview', actions: ['read'], description: 'Executive operational KPIs, active dispatch counters, live telemetry' },
  3: { table: 'users', endpoint: '/admin/search', actions: ['read'], description: 'Global unified index search across riders, drivers, vehicles & rides' },
  4: { table: 'audit_logs', endpoint: '/admin/audit-logs', actions: ['read'], description: 'Immutable administrative audit trail and DPDP consent logs' },
  5: { table: 'driver_locations', endpoint: '/location/live-map', actions: ['read', 'update'], description: 'Real-time telemetry map with active rickshaw coordinates' },
  6: { table: 'ride_assignments', endpoint: '/admin/dispatch/monitor', actions: ['read', 'update'], description: 'Live cascading dispatch monitor with 20s offer countdown' },
  7: { table: 'rides', endpoint: '/admin/rides', actions: ['read', 'update', 'delete'], description: 'All rides directory with status filters, force cancellation and CSV export' },
  8: { table: 'rides', endpoint: '/admin/rides/{id}', actions: ['read', 'update'], description: 'Comprehensive ride detail, state timeline, OTP proof and dispute logs' },
  9: { table: 'driver_profiles', endpoint: '/admin/drivers', actions: ['read', 'update'], description: 'Driver fleet directory with approval status, ratings and suspension controls' },
  10: { table: 'driver_onboarding_tickets', endpoint: '/admin/onboarding/tickets', actions: ['read', 'update'], description: 'New driver onboarding verification queue and SLA assignment' },
  11: { table: 'driver_documents', endpoint: '/admin/documents/review', actions: ['read', 'update'], description: 'High-res document inspection (Aadhaar, DL, RC) with approve/reject reasons' },
  12: { table: 'driver_profiles', endpoint: '/admin/drivers/{id}', actions: ['read', 'update'], description: 'Driver master 360 profile, vehicle assignment, shift logs and wallet' },
  13: { table: 'driver_fatigue_checks', endpoint: '/admin/safety/fatigue-alerts', actions: ['read', 'update'], description: 'Driver continuous online fatigue checks and GPS mock spoof signals' },
  14: { table: 'users', endpoint: '/admin/users', actions: ['read', 'update', 'delete'], description: 'Customer management directory, tier status, account deactivation & passkeys' },
  15: { table: 'customer_profiles', endpoint: '/admin/loyalty/tiers', actions: ['create', 'read', 'update'], description: 'Loyalty points rules, streak rewards and customer tier multipliers' },
  16: { table: 'vehicle_categories', endpoint: '/vehicles/categories', actions: ['create', 'read', 'update', 'delete'], description: 'E-Rickshaw categories (Standard, Shared, Cargo, Metro Feeder) rate definitions' },
  17: { table: 'vehicles', endpoint: '/admin/vehicles', actions: ['create', 'read', 'update', 'delete'], description: 'Vehicle fleet registry, license plates, PUC, RC and battery specs' },
  18: { table: 'fare_rules', endpoint: '/fares/rules', actions: ['create', 'read', 'update', 'delete'], description: 'Base fare, per km, per minute, waiting charges and platform commission' },
  19: { table: 'surge_rules', endpoint: '/fares/surge/rules', actions: ['create', 'read', 'update', 'delete'], description: 'Dynamic demand pricing zones, peak multipliers and time window schedules' },
  20: { table: 'geofence_zones', endpoint: '/location/geofences', actions: ['create', 'read', 'update', 'delete'], description: 'Service area boundary polygon editor, restricted operational perimeters' },
  21: { table: 'road_restrictions', endpoint: '/location/road-restrictions', actions: ['create', 'read', 'update', 'delete'], description: 'No-entry flyovers, highway restrictions and pedestrian-only corridors' },
  22: { table: 'payments', endpoint: '/admin/payments', actions: ['read'], description: 'Unified payments stream (UPI, Cards, Wallet, Cash settlements)' },
  23: { table: 'payment_refunds', endpoint: '/admin/refunds', actions: ['create', 'read', 'update'], description: 'Automated and audited customer refund processing with dispute logs' },
  24: { table: 'driver_payout_requests', endpoint: '/admin/payouts', actions: ['read', 'update'], description: 'Driver weekly earnings settlement queue, UPI penny drop and batch approval' },
  25: { table: 'emergency_alerts', endpoint: '/admin/safety/sos-active', actions: ['read', 'update'], description: 'Priority Emergency SOS Command Desk with 1-tap police broadcast' },
  26: { table: 'incident_reports', endpoint: '/admin/safety/incidents', actions: ['create', 'read', 'update'], description: 'Safety incident tickets, passenger complaints and photographic evidence' },
  27: { table: 'support_tickets', endpoint: '/admin/support/tickets', actions: ['create', 'read', 'update'], description: 'Omnichannel customer support desk, conversation thread and CSAT ratings' },
  28: { table: 'promo_codes', endpoint: '/promos', actions: ['create', 'read', 'update', 'delete'], description: 'Coupon codes, percentage / flat discount vouchers and referral incentives' },
  29: { table: 'broadcast_notifications', endpoint: '/notifications/broadcast', actions: ['create', 'read', 'delete'], description: 'Mass push broadcast composer with audience segment filters (Driver/Rider)' },
  30: { table: 'admin_roles', endpoint: '/admin/roles-permissions', actions: ['create', 'read', 'update', 'delete'], description: 'RBAC permission matrix, route ETA latency metrics and system health' }
};

export function parseScreens(category: 'cust' | 'drv' | 'adm'): ScreenItem[] {
  const text = RAW_DATA[category];
  return text.split('\n').filter(Boolean).map((line, idx) => {
    const [type, title, elementsStr] = line.split('|');
    const elements = (elementsStr || '').split(',').map(s => s.trim());
    const id = idx + 1;
    const mapping = category === 'adm' ? ADMIN_CRUD_MAPPING[id] : undefined;
    return {
      id,
      type: type as any,
      title: title.trim(),
      elements,
      category,
      tableTarget: mapping?.table,
      apiEndpoint: mapping?.endpoint,
      crudActions: mapping?.actions || ['read']
    };
  });
}

export function parsePopups(category: 'cust' | 'drv' | 'adm'): PopupItem[] {
  const text = RAW_POPUPS[category];
  return text.split('\n').filter(Boolean).map((line, idx) => {
    const [type, title, bodyText, actionsStr] = line.split('|');
    const actions = (actionsStr || '').split('/').map(s => s.trim()).filter(s => s !== '—');
    return {
      id: idx + 1,
      type: type as any,
      title: title.trim(),
      bodyText: (bodyText || '').trim(),
      actions,
      category,
      priority: type === 'x' ? 'CRITICAL (No dismiss)' : type === 'b' ? 'BOTTOM SHEET' : type === 't' ? 'TOAST (3s)' : 'BLOCKING DIALOG'
    };
  });
}

export const NAVIGATION_RULES_CONTENT = `
# Platform Architecture & Navigation Rules

## 1. Customer App (50 Screens)
- **Bottom Navigation (4 Persistent Tabs):** \`Home\` | \`Rides\` | \`Wallet\` | \`Account\`
- **Linear Booking Lifecycle:**
  \`Home (08) → Search (09) → Choose Ride (12) → Confirm (16) → Searching (18) → Driver Found (19) → Live Ride (23) → Trip Summary (28) → Payment (29) → Rating (30)\`
- **Active Ride Isolation:** During an active ride, the bottom navigation bar is hidden. A persistent bottom card displays driver ETA, vehicle plate, and live location.
- **Safety Priority:** The floating **SOS Button** is omnipresent on every active ride view. Tapping it opens the priority confirmation dialog with 1-tap police and family broadcast.
- **Back Stack Protection:** Pressing Back during an active ride displays an exit confirmation dialog and **never cancels the ongoing trip**.
- **Cascading Retries:** If no driver accepts within 3 cascading cycles (60s total), the UI prompts the customer with options to raise the fare or retry dispatch.

---

## 2. Driver Cockpit App (50 Screens)
- **Bottom Navigation (4 Persistent Tabs):** \`Home\` | \`Earnings\` | \`Goals\` | \`Profile\`
- **Onboarding Gatekeeper:** Unapproved drivers only have access to the Approval Tracker (07) and Document Upload (04). The Home cockpit remains locked until KYC verification.
- **Authoritative 20-Second Offer Modal:** Incoming ride requests display a full-screen overlay with a 20-second circular countdown ring. Timeout automatically records \`is_accepted=false\` in \`ride_assignments\`.
- **Authoritative Ride State Machine (No skips permitted):**
  \`OFFERED (12) → ACCEPTED → NAVIGATE (14) → ARRIVED (15) → 4-DIGIT OTP VERIFIED → IN_PROGRESS (16) → DROPPED (17) → COLLECT PAYMENT (18) → RATE CUSTOMER (19)\`
- **Safety & Regulatory Compliance:**
  - **Fatigue Rest Lock:** Driving over 8 continuous hours forces an immovable 20-minute rest countdown.
  - **Mock GPS Detector:** Spoofed location signals immediately prevent going online until native GPS is restored.

---

## 3. Admin Operations Console (30 Screens)
- **Top Bar:** Universal Search (\`rides\`, \`users\`, \`drivers\`), live clock, emergency alerts ticker.
- **Left Sidebar:** Role-Based Access Control (RBAC). Modules are filtered dynamically based on \`admin_permissions\`.
- **Destructive Operation Safeguards:** Every deletion, user suspension, driver rejection, or wallet adjustment requires an explicit confirmation modal with a mandatory justification note, automatically appended to \`audit_logs\`.
- **Live SOS Priority:** Active emergency SOS alerts display an urgent flashing banner across all admin screens, providing instant 1-click access to the SOC Command Desk.

---

## 4. Modal & Popup Hierarchy (70 Popups)
1. **Dialog:** Centred, blocking modal with a maximum of 2 actionable buttons.
2. **Bottom Sheet:** Slides upward from bottom for contextual choices; dismissible by dragging downward.
3. **Toast:** Non-blocking notification pill auto-dismissing after 3 seconds.
4. **Full-Screen Overlay:** System-critical screen (Session expired, Fatigue rest, Incoming 20s dispatch); back button and outside taps are strictly ignored.
- **Priority Queue:**
  \`Session Timeout > Emergency SOS > 20s Driver Offer > Fatigue/Fake-GPS > Payment Errors > Informational Dialogs > Toasts\`
`;
