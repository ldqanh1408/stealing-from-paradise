#!/usr/bin/env python3
# ==============================================================================
# E2E Test Script: Payout Flow A-Z (Commission & Payout)
# ==============================================================================
# This script automates:
#   1. Depositing available USD balance on the seller's Stripe Connected Account
#   2. Creating an order & submitting checkout
#   3. Triggering payment_intent.succeeded webhook
#   4. Fulfilling the order (Ship & Deliver)
#   5. Setting the transfer eligibility to past timestamp in DB
#   6. Temporarily configuring payment-service for USD payouts
#   7. Verifying the payout scheduler processes it successfully (PAID_OUT)
#   8. Restoring original configurations
# ==============================================================================

import os
import sys
import io
import json
import time
import subprocess
import urllib.request
import urllib.error
import urllib.parse
import shutil

# Force UTF-8 stdout on Windows to avoid cp1252 encoding errors with Vietnamese text
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPTS_DIR, "../../.."))

# Parse .env to get configuration variables
env_vars = {}
env_path = os.path.join(PROJECT_ROOT, ".env")
if os.path.exists(env_path):
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                parts = line.split("=", 1)
                if len(parts) == 2:
                    env_vars[parts[0].strip()] = parts[1].strip()

GATEWAY = os.environ.get("GATEWAY") or "http://localhost:8080"
if "PORT_GATEWAY" in env_vars and not os.environ.get("GATEWAY"):
    GATEWAY = f"http://localhost:{env_vars['PORT_GATEWAY']}"
os.environ["GATEWAY"] = GATEWAY
os.environ["WEBHOOK_SECRET"] = os.environ.get("STRIPE_WEBHOOK_SECRET") or env_vars.get("STRIPE_WEBHOOK_SECRET") or ""

import argparse

parser = argparse.ArgumentParser(description="E2E Payout Flow Test Script")
parser.add_argument("--seller-account", default="acct_1Tjij4Cz262ctOlb", help="Seller Connect Account ID")
parser.add_argument("--admin-account", default="acct_1TitBaELrFVyqRnd", help="Platform Admin Account ID")
parser.add_argument("--stripe-key", default=None, help="Stripe Secret Key (sk_test_...)")
args, _ = parser.parse_known_args()

STRIPE_SECRET_KEY = args.stripe_key or os.environ.get("STRIPE_SECRET_KEY") or env_vars.get("STRIPE_SECRET_KEY") or "sk_test_placeholder"
CONNECTED_ACCOUNT = args.seller_account
PLATFORM_ACCOUNT = args.admin_account
VARIANT = "90000000-0000-4000-9001-000000000302" # FE-SKU-PHONE-BLUE (seller 900002)
CUST_ID = 6

# Dynamically resolve platform ID using Stripe key
platform_id = PLATFORM_ACCOUNT
try:
    platform_url = "https://api.stripe.com/v1/account"
    req = urllib.request.Request(platform_url)
    req.add_header("Authorization", f"Bearer {STRIPE_SECRET_KEY}")
    with urllib.request.urlopen(req, timeout=10) as r:
        platform_info = json.loads(r.read().decode("utf-8"))
        platform_id = platform_info.get("id", PLATFORM_ACCOUNT)
except Exception:
    pass

seller_link = f"https://dashboard.stripe.com/{platform_id}/test/connect/accounts/{CONNECTED_ACCOUNT}"
admin_link = f"https://dashboard.stripe.com/{platform_id}/test"

print("==============================================================")
print(" E2E PAYOUT FLOW TEST (A-Z)")
print("==============================================================")
print(f" Gateway:           {GATEWAY}")
print(f" Connected Account: {CONNECTED_ACCOUNT}")
print(f" Variant ID:        {VARIANT}")
print(f" Admin Account ID:  {platform_id}")
print(f" Admin Dashboard:   {admin_link}")
print(f" Seller Dashboard:  {seller_link}")
print("==============================================================")


def run_cmd(args, check=True):
    print(f"[CMD] {' '.join(args)}")
    res = subprocess.run(args, capture_output=True, text=True)
    if check and res.returncode != 0:
        print(f"[ERROR] Command failed with code {res.returncode}")
        print(f"stdout: {res.stdout}")
        print(f"stderr: {res.stderr}")
        raise RuntimeError(f"Command failed: {' '.join(args)}")
    return res

def api_call(method, path, body=None, token=None):
    url = f"{GATEWAY}{path}"
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
        
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode("utf-8")
            return r.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            return e.code, json.loads(raw) if raw else {}
        except:
            return e.code, raw
    except Exception as e:
        return -1, str(e)

# Step 1: Prep Stripe Connected Account Balance
print("[Step 1] Checking Stripe Connected Account Balance...")
balance_url = "https://api.stripe.com/v1/balance"
req = urllib.request.Request(balance_url)
req.add_header("Authorization", f"Bearer {STRIPE_SECRET_KEY}")
req.add_header("Stripe-Account", CONNECTED_ACCOUNT)

try:
    with urllib.request.urlopen(req, timeout=15) as r:
        bal_info = json.loads(r.read().decode("utf-8"))
        avail_bal = bal_info.get("available", [{}])[0].get("amount", 0)
        print(f"[+] Current Available Balance: {avail_bal} cents (${avail_bal/100:.2f} USD)")
        
        if avail_bal < 150000:
            print("[+] Balance is low. Injecting $5,000 USD via tok_bypassPending...")
            charge_url = "https://api.stripe.com/v1/charges"
            charge_data = urllib.parse.urlencode({
                "amount": 500000,
                "currency": "usd",
                "source": "tok_bypassPending"
            }).encode("utf-8")
            
            req_charge = urllib.request.Request(charge_url, data=charge_data, method="POST")
            req_charge.add_header("Authorization", f"Bearer {STRIPE_SECRET_KEY}")
            req_charge.add_header("Stripe-Account", CONNECTED_ACCOUNT)
            
            with urllib.request.urlopen(req_charge, timeout=15) as cr:
                print("[+] Balance successfully updated!")
except Exception as e:
    print(f"[WARN] Stripe balance pre-check skipped/failed: {e}")

# Step 2: Authenticating Buyer & Submitting Checkout
print("[Step 2] Authenticating Buyer & Submitting Checkout...")
status, resp = api_call("POST", "/api/v1/auth/login", {"credential": "minhhoa", "password": "dev123"})
if status != 200:
    print(f"[FAIL] Login failed for user 'minhhoa': {resp}")
    sys.exit(1)
buyer_token = resp.get("data", {}).get("accessToken") or resp.get("accessToken")
if not buyer_token:
    print(f"[FAIL] No access token returned: {resp}")
    sys.exit(1)

print("[+] Clearing Cart...")
api_call("DELETE", "/api/v1/cart", token=buyer_token)

print("[+] Adding item to cart...")
status, resp = api_call("POST", "/api/v1/cart/items", {"variantId": VARIANT, "quantity": 1}, token=buyer_token)
if status != 200:
    print(f"[FAIL] Add item failed: {resp}")
    sys.exit(1)

print("[+] Creating checkout preview...")
status, resp = api_call("POST", "/api/v1/cart/checkout/preview", {"itemIds": [f"{CUST_ID}:{VARIANT}"]}, token=buyer_token)
if status != 200:
    print(f"[FAIL] Checkout preview failed: {resp}")
    sys.exit(1)
pt = resp.get("data", {}).get("previewToken")

print("[+] Getting shipping address...")
status, resp = api_call("GET", "/api/v1/users/me/addresses", token=buyer_token)
if status != 200 or not resp.get("data"):
    print(f"[FAIL] Address fetch failed: {resp}")
    sys.exit(1)
aid = resp.get("data", [{}])[0].get("address_id")

# Fetch pre-max parent order ID
status, resp = api_call("GET", "/api/v1/orders?page=0&size=100", token=buyer_token)
orders = resp.get("data", {}).get("content", []) if status == 200 else []
pre_max_order = max([o.get("parentOrderId", 0) for o in orders] + [0])

print("[+] Submitting checkout...")
status, resp = api_call("POST", "/api/v1/cart/checkout/submit", {"previewToken": pt, "addressId": aid}, token=buyer_token)
if status != 200:
    print(f"[FAIL] Submit checkout failed: {resp}")
    sys.exit(1)

pid = None
for _ in range(30):
    status, resp = api_call("GET", "/api/v1/orders?page=0&size=100", token=buyer_token)
    orders = resp.get("data", {}).get("content", []) if status == 200 else []
    new_orders = [o.get("parentOrderId") for o in orders if o.get("parentOrderId", 0) > pre_max_order]
    if new_orders:
        pid = new_orders[0]
        break
    time.sleep(1)

if not pid:
    print("[FAIL] Checkout submission did not generate parent order ID")
    sys.exit(1)
print(f"[+] Parent Order ID generated: {pid}")

# Step 3: Pay Order (Forge payment_intent.succeeded)
print("[Step 3] Forging Payment Webhook...")

# Query seller_id from payment.transactions for this parent order
print("[+] Looking up seller_id from payment.transactions...")
res = run_cmd([
    "docker", "exec", "-i", "fs-postgres", "psql", "-U", "postgres", "-d", "flashsale_platform", "-t", "-A", "-c",
    f"SELECT seller_id FROM payment.transactions WHERE parent_order_id = {pid} LIMIT 1;"
])
tx_seller_id = res.stdout.strip()
if not tx_seller_id:
    print(f"[FAIL] No transaction found for parent order {pid} — checkout may not have created payment records yet")
    sys.exit(1)
print(f"[+] Found seller_id = {tx_seller_id}")

run_cmd([
    "python", os.path.join(SCRIPTS_DIR, "forge.py"), "pi", "payment_intent.succeeded", str(pid),
    f"--seller-id={tx_seller_id}", f"--user-id={CUST_ID}"
])

# Poll transaction status until SUCCESS
poll_success = False
for _ in range(30):
    status, resp = api_call("GET", f"/api/v1/payments/parent-order/{pid}", token=buyer_token)
    tx_status = resp.get("data", {}).get("status")
    if tx_status == "SUCCESS":
        print(f"  [OK] Transaction Status = {tx_status}")
        poll_success = True
        break
    time.sleep(2)

if not poll_success:
    print("[FAIL] Payment webhook not processed successfully")
    sys.exit(1)

# Step 4: Fulfillment (Seller Ships Order)
print("[Step 4] Fulfilling the Order (Fulfillment)...")
status, resp = api_call("GET", f"/api/v1/orders/parent/{pid}", token=buyer_token)
orders = resp.get("data", {}).get("orders", []) if status == 200 else []
if not orders:
    print(f"[FAIL] No sub-orders found for parent {pid}")
    sys.exit(1)
order_id = orders[0].get("orderId")
sid = orders[0].get("sellerId")

# Poll for PAID status (order-service transitions asynchronously via Kafka)
print(f"[+] Waiting for sub-order {order_id} to reach PAID status...")
poll_success = False
for _ in range(30):
    status, resp = api_call("GET", f"/api/v1/orders/{order_id}", token=buyer_token)
    o_status = resp.get("data", {}).get("status")
    if o_status == "PAID":
        print(f"  [OK] Sub-order Status = {o_status}")
        poll_success = True
        break
    time.sleep(2)

if not poll_success:
    print(f"[FAIL] Sub-order did not transition to PAID status (current: {o_status})")
    sys.exit(1)

# Login seller (fe_seller — owns seller_id 900002 / the product variant)
status, resp = api_call("POST", "/api/v1/auth/login", {"credential": "fe_seller", "password": "dev123"})
if status != 200:
    print(f"[FAIL] Seller login failed: {resp}")
    sys.exit(1)
seller_token = resp.get("data", {}).get("accessToken") or resp.get("accessToken")

print(f"[+] Seller updating tracking number (Shipping) for sub-order {order_id}...")
status, resp = api_call("PUT", f"/api/v1/orders/{order_id}/tracking", {"trackingNumber": f"E2E-PAYOUT-{order_id}"}, token=seller_token)
if status != 200:
    print(f"[FAIL] Tracking update failed: {resp}")
    sys.exit(1)

# Poll for SHIPPING status
poll_success = False
for _ in range(15):
    status, resp = api_call("GET", f"/api/v1/orders/{order_id}", token=buyer_token)
    o_status = resp.get("data", {}).get("status")
    if o_status == "SHIPPING":
         print(f"  [OK] Sub-order Status = {o_status}")
         poll_success = True
         break
    time.sleep(2)

if not poll_success:
    print("[FAIL] Sub-order did not transition to SHIPPING status")
    sys.exit(1)

# Step 5: Confirm Delivery
print("[Step 5] Confirming Order Delivery...")
status, resp = api_call("POST", f"/api/v1/orders/{order_id}/confirm-received", {}, token=buyer_token)
if status != 200:
    print(f"[FAIL] Confirm received failed: {resp}")
    sys.exit(1)

# Poll for DELIVERED status
poll_success = False
for _ in range(15):
    status, resp = api_call("GET", f"/api/v1/orders/{order_id}", token=buyer_token)
    o_status = resp.get("data", {}).get("status")
    if o_status == "DELIVERED":
         print(f"  [OK] Sub-order Status = {o_status}")
         poll_success = True
         break
    time.sleep(2)

if not poll_success:
    print("[FAIL] Sub-order did not transition to DELIVERED status")
    sys.exit(1)

# Step 6: Make Transfer Eligible in DB
print("[Step 6] Updating DB to make the Seller Transfer eligible for immediate payout...")
run_cmd([
    "docker", "exec", "-i", "fs-postgres", "psql", "-U", "postgres", "-d", "flashsale_platform", "-c",
    f"UPDATE payment.seller_transfers SET status = 'RETURN_WINDOW', payout_eligible_at = now() - interval '1 hour' WHERE parent_order_id = {pid};"
])
print("[+] DB update complete.")

# Step 7: Configure USD Payout & Recreate payment-service
print("[Step 7] Temporarily configuring payment-service for USD Payouts...")
env_backup_path = os.path.join(PROJECT_ROOT, ".env.backup")
compose_backup_path = os.path.join(PROJECT_ROOT, "docker-compose.yml.backup")
compose_path = os.path.join(PROJECT_ROOT, "docker-compose.yml")

shutil.copyfile(env_path, env_backup_path)
shutil.copyfile(compose_path, compose_backup_path)

# Modify .env
with open(env_path, "a", encoding="utf-8") as f:
    f.write("\nPAYOUT_TRANSFER_CURRENCY=usd\nPAYOUT_TRANSFER_AMOUNT_SCALE=0.0037984\n")

# Modify docker-compose.yml
with open(compose_path, "r", encoding="utf-8") as f:
    compose_content = f.read()

target = "- JVM_OPTS=${JVM_OPTS_PAYMENT}"
replacement = "- JVM_OPTS=${JVM_OPTS_PAYMENT}\n      - PAYOUT_TRANSFER_CURRENCY=${PAYOUT_TRANSFER_CURRENCY}\n      - PAYOUT_TRANSFER_AMOUNT_SCALE=${PAYOUT_TRANSFER_AMOUNT_SCALE}"
new_compose_content = compose_content.replace(target, replacement)

with open(compose_path, "w", encoding="utf-8") as f:
    f.write(new_compose_content)

print("[+] Recreating payment-service container...")
run_cmd(["docker", "compose", "-f", compose_path, "up", "-d", "payment-service"])

print("[+] Waiting for payment-service container to be healthy...")
poll_success = False
for _ in range(40):
    res = run_cmd(["docker", "inspect", "--format={{json .State.Health.Status}}", "fs-payment"], check=False)
    health = res.stdout.strip()
    if health == '"healthy"':
        print("  [OK] payment-service is healthy!")
        poll_success = True
        break
    time.sleep(3)

if not poll_success:
    print("[FAIL] Recreated payment-service failed to start up healthily")
    # Restore configuration
    shutil.move(env_backup_path, env_path)
    shutil.move(compose_backup_path, compose_path)
    run_cmd(["docker", "compose", "-f", compose_path, "up", "-d", "payment-service"])
    sys.exit(1)

# Step 8: Verify Payout Success
print("[Step 8] Monitoring Payout Scheduler...")
payout_success = False
for _ in range(30):
    res = run_cmd([
        "docker", "exec", "-i", "fs-postgres", "psql", "-U", "postgres", "-d", "flashsale_platform", "-t", "-A", "-c",
        f"SELECT status, stripe_payout_id FROM payment.seller_transfers WHERE parent_order_id = {pid};"
    ])
    out = res.stdout.strip()
    if out:
        parts = out.split("|")
        st_status = parts[0]
        st_payout_id = parts[1] if len(parts) > 1 else ""
        if st_status == "PAID_OUT" and st_payout_id:
            print("==============================================================")
            print(" SUCCESS: Payout executed successfully!")
            print(f" Parent Order ID:  {pid}")
            print(f" Transfer Status:  {st_status}")
            print(f" Stripe Payout ID: {st_payout_id}")
            print("==============================================================")
            payout_success = True
            break
    time.sleep(2)

# Step 9: Restore Original Config
print("[Step 9] Restoring original configuration...")
if os.path.exists(env_backup_path):
    shutil.move(env_backup_path, env_path)
if os.path.exists(compose_backup_path):
    shutil.move(compose_backup_path, compose_path)

print("[+] Restoring original payment-service container...")
run_cmd(["docker", "compose", "-f", compose_path, "up", "-d", "payment-service"])

print("[+] Waiting for original container health...")
for _ in range(40):
    res = run_cmd(["docker", "inspect", "--format={{json .State.Health.Status}}", "fs-payment"], check=False)
    health = res.stdout.strip()
    if health == '"healthy"':
        print("  [OK] payment-service is healthy under original configuration!")
        break
    time.sleep(3)

if not payout_success:
    print("[FAIL] Payout Scheduler did not process the transfer to PAID_OUT")
    sys.exit(1)

print("==============================================================")
print(" E2E PAYOUT FLOW TEST COMPLETE & SUCCESSFUL")
print(f" Admin Dashboard:   {admin_link}")
print(f" Seller Dashboard:  {seller_link}")
print("==============================================================")
