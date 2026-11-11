# Mobility API को Render पर host करना (Hindi)

> ध्यान दें: बाहर (Render) host करना Emergent का officially supported flow नहीं है — setup आपको खुद संभालना होगा।

## Steps
1. **Code GitHub पर डालें:** Emergent chat में "Save to GitHub" → push करें।
2. Render (https://render.com) → **New → Blueprint** → अपना GitHub repo चुनें।
   Repo में मौजूद `render.yaml` अपने-आप service `vaisentra-mobility-api` बना देगा:
   - Root: `backend`, Build: `pip install -r requirements-render.txt`
   - Start: `uvicorn server:app --host 0.0.0.0 --port $PORT --workers 1`
   - Health check: `/health`
3. Render पूछेगा तो ये Environment values भरें:
   `SUPABASE_DB_URL`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `MAPBOX_SECRET_TOKEN`,
   `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`,
   `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_VERIFY_SERVICE_SID`
   (`JWT_SECRET` अपने-आप बनता है, `OTP_DEBUG=false` पहले से set है)। Keys कैसे निकालें: `KEYS_SETUP_HINDI.md`.
4. Deploy होने के बाद जाँचें: `https://<service>.onrender.com/health` → `{"status":"ok"}` जैसा जवाब।
5. **Mobile apps को नए backend से जोड़ें:** RiderGo / DriverGo / Admin में `EXPO_PUBLIC_BACKEND_URL=https://<service>.onrender.com` रखें, फिर app दोबारा publish करके नया build बनाएँ।
6. Razorpay webhook URL को Render URL पर बदलें: `https://<service>.onrender.com/api/webhooks/razorpay`.

## ज़रूरी सावधानियाँ
- **Free plan न लें** — 15 मिनट idle पर service सो जाती है, WebSocket live tracking और ride offers टूट जाएँगे। कम से कम *Starter* plan।
- **एक ही instance / `--workers 1` रखें** — WebSocket connections और rate-limit memory में हैं; कई instances पर driver/rider अलग servers पर पहुँचकर updates miss करेंगे। Scale करना हो तो पहले Redis pub/sub जोड़ना होगा।
- Supabase में **Session pooler / direct** connection string (port 5432) इस्तेमाल करें, और Render region को Supabase region के पास (Singapore) रखें।
- Database वही Supabase `mobility` schema है — Emergent और Render दोनों एक साथ चलें तो दोनों एक ही data पढ़ेंगे/लिखेंगे।

## RiderGo को Render से जोड़ना (code तैयार है)
- `frontend/src/config.ts`: Preview/Expo Go (`__DEV__`) हमेशा Emergent backend से चलता है। Published build में `EXPO_PUBLIC_PROD_API_URL` set हो तो सारे API calls, WebSocket, background location और share links उसी URL पर जाते हैं।
- करना बस इतना है: Render URL (हमारा Mobility API, `/health` पर `{"status":"ok"}`) मिलते ही `EXPO_PUBLIC_PROD_API_URL=https://<service>.onrender.com` set करें → Publish → नया build।
