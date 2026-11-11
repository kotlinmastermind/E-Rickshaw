# Keys कैसे निकालें — Twilio (SMS OTP) और Razorpay (live payments)

## 1. Twilio — असली SMS OTP
1. https://www.twilio.com/try-twilio पर account बनाएँ (email + phone verify)।
2. Console (https://console.twilio.com) के होम पेज पर **Account Info** में:
   - **Account SID** (`AC...` से शुरू) → `TWILIO_ACCOUNT_SID`
   - **Auth Token** (Show पर क्लिक करें) → `TWILIO_AUTH_TOKEN`
3. बाएँ menu: **Explore Products → Verify → Services → Create new**
   - Friendly name: `VaisentraMobility`, Channel में **SMS** चुनें → Create।
   - **Service SID** (`VA...`) → `TWILIO_VERIFY_SERVICE_SID`
4. **India में SMS के लिए ज़रूरी:** Console → Verify → Settings / Geo permissions में **India (+91)** चालू करें।
5. Trial account सिर्फ़ verified numbers पर SMS भेजता है। सबको SMS भेजने के लिए **Upgrade** (balance जोड़ें) करें।

## 2. Razorpay — live payments
1. https://dashboard.razorpay.com → KYC पूरा करें (live mode तभी खुलेगा)।
2. ऊपर toggle को **Live Mode** पर करें → **Account & Settings → API Keys → Generate Live Key**
   - Key Id (`rzp_live_...`) → `RAZORPAY_KEY_ID` (live slot)
   - Key Secret → `RAZORPAY_KEY_SECRET` (live slot) — सिर्फ़ एक बार दिखता है, सुरक्षित रखें।
3. **Account & Settings → Webhooks → Add New Webhook**
   - Webhook URL: `https://<आपका-backend-url>/api/webhooks/razorpay`
     (Render पर host करें तो: `https://<render-service>.onrender.com/api/webhooks/razorpay`)
   - Secret: कोई भी मज़बूत password खुद लिखें → वही `RAZORPAY_WEBHOOK_SECRET`
   - Active events: **payment.captured** और **payment.failed** → Create।

## 3. आख़िरी settings
- Production में `OTP_DEBUG=false` रखें (वरना test OTP चलता रहेगा)।
- Emergent पर: ये keys secure card में डालें। Render पर: Render Dashboard → Service → **Environment** में डालें।
