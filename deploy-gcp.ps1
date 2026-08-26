# Google Cloud Run Automated Deployment Script for Vidyut EV Platform
# Project: vidyut-autopilot
# Target Region: us-central1 (or asia-south1)

param (
    [string]$ProjectId = "vidyut-autopilot",
    [string]$Region = "us-central1",
    [string]$SecretPrefix = "vidyut"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 Vidyut Cloud Run Deployment - All Things Agentic     " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Set Active GCP Project
Write-Host "`n[1/4] Setting active GCP project to $ProjectId..." -ForegroundColor Yellow
gcloud config set project $ProjectId

# 2. Enable Required APIs
Write-Host "`n[2/4] Enabling required Google Cloud APIs..." -ForegroundColor Yellow
gcloud services enable run.googleapis.com `
                       artifactregistry.googleapis.com `
                       aiplatform.googleapis.com `
                       firestore.googleapis.com `
                       pubsub.googleapis.com `
                       routes.googleapis.com `
                       secretmanager.googleapis.com

# 3. Deploy Python ADK + Gemini AI Agent to Cloud Run
Write-Host "`n[3/4] Building & Deploying vidyut-agent to Cloud Run..." -ForegroundColor Yellow
Push-Location vidyut-ai\agent
try {
    gcloud run deploy vidyut-agent `
        --source . `
        --region $Region `
        --platform managed `
        --allow-unauthenticated `
        --set-env-vars="VIDYUT_AGENT_MODEL=gemini-3.5-flash,GOOGLE_GENAI_USE_VERTEXAI=1,GOOGLE_CLOUD_PROJECT=$ProjectId,GOOGLE_CLOUD_LOCATION=$Region"
} finally {
    Pop-Location
}

$AGENT_URL = (gcloud run services describe vidyut-agent --region $Region --format="value(status.url)")
Write-Host "✅ vidyut-agent deployed at: $AGENT_URL" -ForegroundColor Green

# 4. Check for Secret Manager Secrets for Backend
Write-Host "`n[4/4] Building & Deploying vidyut-backend to Cloud Run..." -ForegroundColor Yellow

$SECRETS_PARAM = @()
$hasRoutesSecret = (gcloud secrets list --filter="name:google-routes-api-key OR name:GOOGLE_ROUTES_API_KEY" --format="value(name)" 2>$null)
if ($hasRoutesSecret) {
    Write-Host "🔒 Attaching Secret Manager secret for Google Routes API Key..." -ForegroundColor Green
    $SECRETS_PARAM += "GOOGLE_ROUTES_API_KEY=google-routes-api-key:latest"
}

$hasJwtSecret = (gcloud secrets list --filter="name:jwt-secret OR name:JWT_SECRET" --format="value(name)" 2>$null)
if ($hasJwtSecret) {
    Write-Host "🔒 Attaching Secret Manager secret for JWT..." -ForegroundColor Green
    $SECRETS_PARAM += "JWT_SECRET=jwt-secret:latest"
}

$backendDeployCmd = "gcloud run deploy vidyut-api --source . --region $Region --platform managed --allow-unauthenticated --set-env-vars=""SPRING_PROFILES_ACTIVE=prod,VIDYUT_AGENT_BASE_URL=$AGENT_URL,GOOGLE_CLOUD_PROJECT=$ProjectId,GOOGLE_CLOUD_LOCATION=$Region,VIDYUT_GOOGLE_ROUTES_ENABLED=true"""

if ($SECRETS_PARAM.Count -gt 0) {
    $secretsJoined = $SECRETS_PARAM -join ","
    $backendDeployCmd += " --set-secrets=""$secretsJoined"""
}

Push-Location vidyut-backend
try {
    Invoke-Expression $backendDeployCmd
} finally {
    Pop-Location
}

$API_URL = (gcloud run services describe vidyut-api --region $Region --format="value(status.url)")
Write-Host "✅ vidyut-api deployed at: $API_URL" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " 🎉 Full Deployment Complete!                             " -ForegroundColor Cyan
Write-Host " 🤖 AI Agent URL: $AGENT_URL                              " -ForegroundColor Green
Write-Host " ⚡ API Core URL: $API_URL                                " -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
