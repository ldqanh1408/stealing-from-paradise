# ============================================================
# Stripe Webhook Listener — Docker Container Approach
#
# Runs Stripe CLI inside a Docker container for local development.
# No local installation needed.
#
# Prereq: payment-service must be running (via docker compose)
#
# Usage:
#   .\stripe-webhook.ps1 -Mode Start      Start the listener
#   .\stripe-webhook.ps1 -Mode Stop       Stop the listener
#   .\stripe-webhook.ps1 -Mode Status     Check status
#   .\stripe-webhook.ps1 -Mode Logs       View logs
#   .\stripe-webhook.ps1 -Mode Help       Show usage guide
#   .\stripe-webhook.ps1 -Mode ProdGuide  Show production webhook setup
#
# After starting, Stripe CLI prints the webhook signing secret:
#   Ready! Your webhook signing secret is whsec_xxx
# Copy this to .env: STRIPE_WEBHOOK_SECRET=whsec_xxx
# Then restart payment-service: docker restart fs-payment
#
# To trigger test events from the host:
#   stripe trigger payment_intent.succeeded
#
# ============================================================
#
# PRODUCTION WEBHOOK (for server deployment):
# ============================================================
# Stripe CLI is ONLY for local dev. For production:
#
#   1. Go to Stripe Dashboard > Developers > Webhooks
#   2. Click "Add endpoint"
#   3. Endpoint URL: https://your-domain.com/api/v1/stripe/webhooks
#   4. Select events:
#        - payment_intent.succeeded
#        - payment_intent.payment_failed
#        - charge.refunded
#        - account.updated
#   5. Copy the "Signing secret" (whsec_xxx)
#   6. Set it in your production .env:
#        STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx
#   7. In docker-compose.prod, pass it as:
#        STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET_PROD}
# ============================================================

param(
    [ValidateSet("Start", "Stop", "Status", "Logs", "Help", "ProdGuide")]
    [string]$Mode = "Start"
)

$containerName = "fs-stripe-listener"

function Show-Help {
    Write-Host @"

USAGE: .\stripe-webhook.ps1 -Mode <Start|Stop|Status|Logs|Help|ProdGuide>

  Start     Start the Stripe CLI listener container (dev only)
  Stop      Stop the listener
  Status    Check if the listener is running
  Logs      View recent container logs
  Help      Show this help message
  ProdGuide Show production webhook setup instructions

NOTES:
  - This script uses Docker to run Stripe CLI — no local install needed.
  - This is for LOCAL DEVELOPMENT only.
  - For production, use Stripe Dashboard to create a real webhook endpoint.

STRIPE CLI TEST EVENTS (run in a separate terminal):
  stripe trigger payment_intent.succeeded
  stripe trigger payment_intent.payment_failed
  stripe trigger charge.refunded
  stripe trigger account.updated

"@ -ForegroundColor Cyan
}

function Show-ProdGuide {
    Write-Host @"

============================================================
  STRIPE PRODUCTION WEBHOOK SETUP
============================================================

STEP 1: Go to Stripe Dashboard
  https://dashboard.stripe.com/settings/webhooks

STEP 2: Add endpoint
  - Click "Add endpoint"
  - Endpoint URL: https://your-domain.com/api/v1/stripe/webhooks
  - Select events to listen:
      payment_intent.succeeded
      payment_intent.payment_failed
      charge.refunded
      account.updated
  - Click "Add endpoint"

STEP 3: Copy the Signing Secret
  Stripe shows: "Signing secret: whsec_xxx"
  Copy this value.

STEP 4: Set in Production .env
  STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx

STEP 5: Deploy
  Set the env var when deploying:
    docker compose -f docker-compose.yml \\
      -f docker-compose-backend.yml \\
      -f backend/docker-compose.prod-pulled.yml \\
      env STRIPE_WEBHOOK_SECRET_PROD=whsec_xxx \\
      up -d

IMPORTANT:
  - Never commit production webhook secrets to git.
  - Use CI/CD secrets or a secrets manager.
  - Stripe sends events from known IPs only — verify in Dashboard.

"@ -ForegroundColor Cyan
}

switch ($Mode) {
    "Help" {
        Show-Help
    }

    "ProdGuide" {
        Show-ProdGuide
    }

    "Start" {
        $running = docker ps --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($running) {
            Write-Host "Stripe listener container '$containerName' is already running." -ForegroundColor Green
            Write-Host "Logs:"
            docker logs $containerName --tail 5
            return
        }

        $existing = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($existing) {
            Write-Host "Starting existing container '$containerName'..." -ForegroundColor Cyan
            docker start $containerName
        } else {
            Write-Host "Creating and starting Stripe CLI container..." -ForegroundColor Cyan
            docker compose up -d stripe-listener
        }

        Start-Sleep -Seconds 5
        $logs = docker logs $containerName 2>&1
        $secret = $logs | Select-String "whsec_"
        if ($secret) {
            $secretLine = ($secret -split "`n" | Select-Object -First 1).Trim()
            $secretLine -match "whsec_[a-zA-Z0-9]+"
            $whsec = $matches[0]
            Write-Host ""
            Write-Host "========================================" -ForegroundColor Green
            Write-Host "STRIPE CLI IS RUNNING!" -ForegroundColor Green
            Write-Host "========================================" -ForegroundColor Green
            Write-Host ""
            Write-Host "Webhook signing secret:" -ForegroundColor Yellow
            Write-Host "  $whsec" -ForegroundColor White
            Write-Host ""
            Write-Host "Add this to your .env file:" -ForegroundColor Yellow
            Write-Host "  STRIPE_WEBHOOK_SECRET=$whsec" -ForegroundColor White
            Write-Host ""
            Write-Host "Then restart payment-service:" -ForegroundColor Yellow
            Write-Host "  docker restart fs-payment" -ForegroundColor White
            Write-Host ""
        } else {
            Write-Host ""
            Write-Host "Webhook secret not found yet. Check logs:" -ForegroundColor Yellow
            Write-Host "  .\stripe-webhook.ps1 -Mode Logs" -ForegroundColor White
            Write-Host ""
        }

        Write-Host "To trigger test events (from a new terminal):" -ForegroundColor Cyan
        Write-Host "  stripe trigger payment_intent.succeeded" -ForegroundColor White
        Write-Host ""
        Write-Host "For production webhook setup, run:" -ForegroundColor Cyan
        Write-Host "  .\stripe-webhook.ps1 -Mode ProdGuide" -ForegroundColor White
        Write-Host ""
    }

    "Stop" {
        $running = docker ps --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if ($running) {
            docker stop $containerName
            Write-Host "Stripe listener stopped." -ForegroundColor Green
        } else {
            Write-Host "Stripe listener is not running." -ForegroundColor Gray
        }
    }

    "Status" {
        $status = docker ps --format "{{.Names}}:{{.Status}}" | Select-String ([regex]::Escape($containerName) + ":")
        if ($status) {
            Write-Host "Stripe CLI container: $status" -ForegroundColor Green
            Write-Host "Recent logs:"
            docker logs $containerName --tail 10
        } else {
            $exists = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
            if ($exists) {
                Write-Host "Stripe CLI container exists but is stopped." -ForegroundColor Yellow
                Write-Host "Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor White
            } else {
                Write-Host "Stripe CLI container not found." -ForegroundColor Red
                Write-Host "Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor White
            }
        }
    }

    "Logs" {
        $exists = docker ps -a --format "{{.Names}}" | Select-String "^$([regex]::Escape($containerName))$"
        if (-not $exists) {
            Write-Host "Stripe CLI container not found. Run: .\stripe-webhook.ps1 -Mode Start" -ForegroundColor Red
            return
        }
        docker logs $containerName --tail 30
    }
}
