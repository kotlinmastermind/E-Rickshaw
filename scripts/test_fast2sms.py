"""
Fast2SMS Verification & Health Check Script.
Use this script to test your Fast2SMS API key, check your account wallet balance,
and send a real live test OTP to your mobile phone.

Usage:
  python scripts/test_fast2sms.py <YOUR_FAST2SMS_API_KEY> [TARGET_PHONE_NUMBER]
"""

import sys
import httpx

def check_fast2sms_wallet(api_key: str):
    print("==================================================")
    print(" 1. Verifying Fast2SMS API Key & Checking Balance")
    print("==================================================")
    url = "https://www.fast2sms.com/dev/wallet"
    headers = {"authorization": api_key}
    try:
        resp = httpx.get(url, headers=headers, timeout=10.0)
        data = resp.json()
        if resp.status_code == 200 and data.get("return") is True:
            wallet_balance = data.get("wallet", 0)
            print(f"[SUCCESS] API Key is Valid!")
            print(f"Current Wallet Balance: Rs {wallet_balance}")
            return True
        else:
            print(f"[FAIL] Fast2SMS authentication failed: {data}")
            return False
    except Exception as e:
        print(f"[ERROR] Could not connect to Fast2SMS: {e}")
        return False

def send_test_otp(api_key: str, phone_number: str, otp_code: str = "582910"):
    clean_phone = phone_number.replace("+91", "").replace("+", "").strip()
    print("\n==================================================")
    print(f" 2. Sending Real Test OTP to +91 {clean_phone}")
    print("==================================================")
    url = "https://www.fast2sms.com/dev/bulkV2"
    headers = {
        "authorization": api_key,
        "Content-Type": "application/json"
    }
    payload = {
        "route": "otp",
        "variables_values": otp_code,
        "numbers": clean_phone
    }
    try:
        resp = httpx.post(url, json=payload, headers=headers, timeout=10.0)
        data = resp.json()
        if resp.status_code == 200 and data.get("return") is True:
            print(f"[SUCCESS] Real OTP ({otp_code}) dispatched to +91 {clean_phone}!")
            print(f"Server Response: {data.get('message', ['Delivered'])[0]}")
            print(f"Request ID: {data.get('request_id')}")
            return True
        else:
            print(f"[FAIL] Fast2SMS rejected SMS dispatch: {data}")
            return False
    except Exception as e:
        print(f"[ERROR] Dispatch error: {e}")
        return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python scripts/test_fast2sms.py <FAST2SMS_API_KEY> [PHONE_NUMBER]")
        sys.exit(1)
    
    key = sys.argv[1].strip()
    target_phone = sys.argv[2].strip() if len(sys.argv) >= 3 else None

    is_valid = check_fast2sms_wallet(key)
    if is_valid and target_phone:
        send_test_otp(key, target_phone)
