# Customer KYC private storage setup

The Customer KYC API stores document metadata in PostgreSQL and document bytes only in a dedicated private Supabase Storage bucket. It intentionally does not use the legacy driver-document local-filesystem fallback.

## Required Supabase setup

1. In Supabase Dashboard, open **Storage** and create a bucket named `customer-kyc-documents`.
2. Set the bucket to **Private** (public access disabled).
3. Set the bucket file-size limit to **5 MB**.
4. Allow only these MIME types: `application/pdf`, `image/jpeg`, and `image/png`.
5. Do not add public read policies. The backend uses its server-side service-role credential after checking that the bucket is private.

## Required backend environment

Configure these variables in the backend deployment environment (for Render, add them under the web service's Environment settings):

- `SUPABASE_URL`: existing Supabase project URL.
- `SUPABASE_SERVICE_ROLE_KEY`: Supabase service-role secret. Keep it server-side only; never place it in either Android app, Admin JavaScript, a public environment variable, or Git.
- `CUSTOMER_KYC_STORAGE_BUCKET`: `customer-kyc-documents` (already declared in `render.yaml`).

The existing generic `SUPABASE_KEY` is deliberately not reused for customer identity documents. The dedicated service-role variable avoids accidentally using a publishable/anon key.

## Runtime safeguards

- Upload, signed-download, and delete operations verify that the configured bucket exists and reports `public=false`.
- Missing credentials, a missing bucket, a public bucket, or a storage outage fail closed with HTTP 503. There is no local-disk fallback.
- Admin document links are short-lived (60 seconds) and issued only after Admin authentication; issuance is audited.
- The current manual-review allowlist is PAN Card, Driving Licence, Passport, and Voter ID. Aadhaar is not accepted by this customer upload flow.
- Document approval is a manual document-review outcome only. It does not set `users.is_verified` or claim government-source verification.
- Do not enable customer document submission in production until the private bucket and backend service-role environment variable have been configured and verified.
- This first release performs manual document review only; it does not run OCR, government-source verification, or antivirus/malware scanning. Add malware scanning and approve a formal retention schedule before treating the workflow as production-complete.
