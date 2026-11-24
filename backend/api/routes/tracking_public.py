from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import HTMLResponse
from sqlalchemy.ext.asyncio import AsyncSession
from backend.infrastructure.database.session import get_db
from backend.application.services.tracking_advanced_service import TrackingAdvancedService

router = APIRouter(prefix="/track", tags=["Public Ride Tracking"])

@router.get("/share/{token}", response_class=HTMLResponse)
async def public_ride_tracking_view(
    token: str,
    db: AsyncSession = Depends(get_db)
):
    service = TrackingAdvancedService(db)
    # Check if link is valid
    try:
        data = await service.resolve_shared_trip(token)
    except HTTPException as e:
        error_title = "Link Expired or Revoked" if e.status_code == 410 else "Trip Not Found"
        return HTMLResponse(content=f"""
        <!DOCTYPE html>
        <html>
        <head><meta name="viewport" content="width=device-width,initial-scale=1"><title>{error_title}</title>
        <style>body{{font-family:sans-serif;background:#0d1117;color:#fff;text-align:center;padding:50px 20px;}}
        .card{{background:#161b22;padding:30px;border-radius:16px;max-width:400px;margin:auto;border:1px solid #30363d;}}
        h2{{color:#f85149;}}</style></head>
        <body><div class="card"><h2>⚠️ {error_title}</h2><p>{e.detail}</p>
        <p style="color:#8b949e;font-size:12px;">This live tracking session has concluded or was revoked by the passenger.</p></div></body></html>
        """, status_code=e.status_code)

    # Return full interactive HTML tracking app
    html_page = f"""
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Live Ride Telemetry | Vaisentra Mobility</title>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            * {{ margin: 0; padding: 0; box-sizing: border-box; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }}
            body {{ background: #0b0f19; color: #f3f4f6; height: 100vh; display: flex; flex-direction: column; overflow: hidden; }}
            #map {{ flex: 1; width: 100%; z-index: 1; }}
            .overlay {{ position: absolute; top: 16px; left: 16px; right: 16px; z-index: 10; display: flex; justify-content: space-between; pointer-events: none; }}
            .pill {{ background: rgba(17, 24, 39, 0.9); backdrop-filter: blur(8px); padding: 8px 14px; border-radius: 999px; border: 1px solid rgba(255,255,255,0.1); font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 8px; pointer-events: auto; }}
            .live-dot {{ width: 8px; height: 8px; background: #10b981; border-radius: 50%; box-shadow: 0 0 10px #10b981; animation: pulse 1.5s infinite; }}
            @keyframes pulse {{ 0%, 100% {{ opacity: 1; }} 50% {{ opacity: 0.4; }} }}
            .card {{ position: absolute; bottom: 0; left: 0; right: 0; z-index: 10; background: #111827; border-top: 1px solid rgba(255,255,255,0.1); border-radius: 24px 24px 0 0; padding: 20px 24px; box-shadow: 0 -10px 30px rgba(0,0,0,0.5); }}
            .status-badge {{ background: #064e3b; color: #34d399; padding: 4px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; display: inline-block; }}
            .vehicle-title {{ font-size: 18px; font-weight: 700; color: #fff; margin-top: 6px; }}
            .plate {{ font-family: monospace; background: #1f2937; padding: 3px 8px; border-radius: 4px; font-size: 12px; color: #9ca3af; margin-left: 8px; }}
            .route {{ margin-top: 14px; padding-top: 12px; border-top: 1px solid #1f2937; font-size: 13px; }}
            .loc-row {{ display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }}
            .dot {{ width: 10px; height: 10px; border-radius: 50%; }}
            .green {{ background: #10b981; }} .red {{ background: #ef4444; }}
        </style>
    </head>
    <body>
        <div class="overlay">
            <div class="pill"><div class="live-dot"></div> Live Ride Telemetry</div>
            <div class="pill" id="shield-pill">🛡️ Privacy Shield Active</div>
        </div>
        <div id="map"></div>
        <div class="card">
            <div>
                <span class="status-badge" id="ride-status">{data['status']}</span>
                <div class="vehicle-title">{data['vehicle_model']} <span class="plate">{data['vehicle_plate_number']}</span></div>
            </div>
            <div class="route">
                <div class="loc-row"><div class="dot green"></div> <span id="pickup-label">{data['pickup_label']}</span></div>
                <div class="loc-row"><div class="dot red"></div> <span id="dest-label">{data['destination_label']}</span></div>
            </div>
            <p style="color:#6b7280; font-size:11px; margin-top:8px;">Auto-refreshing every 10 seconds directly from satellite telemetry.</p>
        </div>

        <script>
            const token = "{token}";
            let map = L.map('map', {{ zoomControl: false }}).setView([{data['driver_latitude']}, {data['driver_longitude']}], 14);
            L.tileLayer('https://{{s}}.basemaps.cartocdn.com/rastertiles/voyager/{{z}}/{{x}}/{{y}}{{r}}.png', {{ maxZoom: 19 }}).addTo(map);

            let driverMarker = L.marker([{data['driver_latitude']}, {data['driver_longitude']}]).addTo(map).bindPopup("Driver Location");

            async function refreshTelemetry() {{
                try {{
                    const res = await fetch(`/api/v1/rides/share/${{token}}`);
                    if (res.status === 410 || res.status === 404) {{
                        location.reload();
                        return;
                    }}
                    const json = await res.json();
                    document.getElementById('ride-status').innerText = json.status;
                    const newLatLng = [json.driver_latitude, json.driver_longitude];
                    driverMarker.setLatLng(newLatLng);
                    map.panTo(newLatLng);
                }} catch (e) {{ console.error("Telemetry poll failed", e); }}
            }}
            setInterval(refreshTelemetry, 10000);
        </script>
    </body>
    </html>
    """
    return HTMLResponse(content=html_page)