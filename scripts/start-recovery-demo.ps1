param([ValidateSet('backend', 'agent', 'web')][string]$Service = 'backend')
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
# Run each service in its own terminal. This backend uses disposable H2 only.
switch ($Service) {
    'backend' {
        Push-Location (Join-Path $repositoryRoot 'vidyut-backend')
        try {
            $demoSecret = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([guid]::NewGuid().ToString() + [guid]::NewGuid().ToString()))
            $demoArguments = '--server.address=127.0.0.1 --server.port=8080 --vidyut.agent.base-url=http://127.0.0.1:8001 --spring.datasource.url=jdbc:h2:mem:vidyut_recovery_qa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa --spring.jpa.hibernate.ddl-auto=create-drop --spring.jpa.show-sql=false --spring.flyway.enabled=false --demo.seed.enabled=true --demo.account.password=VidyutDemo@2026 --jwt.secret=' + $demoSecret
            & mvn.cmd -q spring-boot:run '-Dspring-boot.run.useTestClasspath=true' "-Dspring-boot.run.arguments=$demoArguments"
        } finally { Pop-Location }
    }
    'agent' {
        Push-Location (Join-Path $repositoryRoot 'vidyut-ai/agent')
        try {
            $env:VIDYUT_BACKEND_BASE_URL = 'http://127.0.0.1:8080'
            & .\.venv\Scripts\python.exe -m uvicorn vidyut_agent.service:app --host 127.0.0.1 --port 8001
        } finally { Pop-Location }
    }
    'web' {
        Push-Location (Join-Path $repositoryRoot 'vidyut-web')
        try {
            $env:VITE_API_BASE_URL = '/api'
            $env:VIDYUT_BACKEND_PROXY = 'http://127.0.0.1:8080'
            & npm.cmd run dev -- --host 127.0.0.1 --port 4173
        } finally { Pop-Location }
    }
}
