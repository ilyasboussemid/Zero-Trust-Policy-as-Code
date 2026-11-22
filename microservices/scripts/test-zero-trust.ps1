$ErrorActionPreference = "Stop"

[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }

function Invoke-ZtRequest {
    param(
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [System.Security.Cryptography.X509Certificates.X509Certificate2]$ClientCert = $null
    )
    try {
        $params = @{
            Uri             = $Uri
            Method          = $Method
            Headers         = $Headers
            UseBasicParsing = $true
        }
        if ($Body) {
            $params["ContentType"] = "application/json"
            $params["Body"] = $Body
        }
        if ($ClientCert) {
            $params["Certificate"] = $ClientCert
        }
        $res = Invoke-WebRequest @params
        return @{ StatusCode = [int]$res.StatusCode; Body = $res.Content }
    } catch {
        if ($_.Exception.Response) {
            $resp = $_.Exception.Response
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $errBody = $reader.ReadToEnd()
            return @{ StatusCode = [int]$resp.StatusCode; Body = $errBody }
        }
        return @{ StatusCode = 0; Body = $_.Exception.Message }
    }
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  SUITE DE TESTS ZERO-TRUST (mTLS + JWT + PBAC/ABAC)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

foreach ($port in @(8441, 8442, 8443)) {
    $listening = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if (-not $listening) {
        Write-Host "[ATTENTION] Aucun service detecte sur le port $port ! Lance les 3 JARs avant le test." -ForegroundColor Red
    }
}

$p12Path = Resolve-Path "$PSScriptRoot\..\certs-pki\share-gateway-service.p12"
$keyFlags = [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::UserKeySet
$gatewayCert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($p12Path.Path, "changeit", $keyFlags)

# TEST 1 : Flux nominal PREMIUM
Write-Host "`n[TEST 1] Flux nominal PREMIUM (JWT -> Gateway :8441 -> mTLS+PBAC Metadata :8442 -> mTLS+PBAC Vault :8443)" -ForegroundColor Yellow
$authBody = @{ username = "ilyas@ensias.ma"; tier = "PREMIUM" } | ConvertTo-Json -Compress
$authRes = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8441/api/auth/demo-token" -Body $authBody
$token = ($authRes.Body | ConvertFrom-Json).accessToken

$shareBody = @{ folderName = "projet-ensias-2026.zip"; fileSizeBytes = 15728640; ttlHours = 24 } | ConvertTo-Json -Compress
$t1 = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8441/api/shares" -Headers @{ Authorization = "Bearer $token" } -Body $shareBody
Write-Host "HTTP $($t1.StatusCode) (200 attendu) :" -ForegroundColor Green
Write-Host $t1.Body

# TEST 2 : Appel direct vers storage-vault-service (:8443) SANS certificat mTLS
Write-Host "`n[TEST 2] Appel direct vers storage-vault-service (:8443) SANS certificat client mTLS" -ForegroundColor Yellow
$t2 = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8443/api/vault/presign" -Body '{"shareId":"test","objectKey":"uploads/test.zip"}'
Write-Host "HTTP $($t2.StatusCode) (0 / Handshake TLS rejete attendu) : $($t2.Body)" -ForegroundColor Green

# TEST 3 : Appel avec certificat mTLS valide MAIS SANS JWT -> 401
Write-Host "`n[TEST 3] Appel avec certificat mTLS valide MAIS SANS jeton JWT -> 401 Unauthorized attendu" -ForegroundColor Yellow
$t3 = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8443/api/vault/presign" -ClientCert $gatewayCert -Body '{"shareId":"test","objectKey":"uploads/test.zip"}'
Write-Host "HTTP $($t3.StatusCode) : $($t3.Body)" -ForegroundColor Green

# TEST 4 : Appel avec certificat mTLS valide + JWT falsifie -> 401
Write-Host "`n[TEST 4] Appel avec certificat mTLS valide + JWT falsifie -> 401 Unauthorized attendu" -ForegroundColor Yellow
$fakeJwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzaGFyZS1nYXRld2F5LXNlcnZpY2UifQ.signature_invalide"
$t4 = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8443/api/vault/presign" -ClientCert $gatewayCert -Headers @{ Authorization = "Bearer $fakeJwt" } -Body '{"shareId":"test","objectKey":"uploads/test.zip"}'
Write-Host "HTTP $($t4.StatusCode) : $($t4.Body)" -ForegroundColor Green

# TEST 5 : Violation politique ABAC/PBAC (STANDARD max 50 Mo tentant 120 Mo) -> 403
Write-Host "`n[TEST 5] Utilisateur STANDARD (plafond 50 Mo) tentant d'uploader 120 Mo -> 403 Forbidden (PBAC Deny) attendu" -ForegroundColor Yellow
$stdAuthBody = @{ username = "etudiant@ensias.ma"; tier = "STANDARD" } | ConvertTo-Json -Compress
$stdRes = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8441/api/auth/demo-token" -Body $stdAuthBody
$stdToken = ($stdRes.Body | ConvertFrom-Json).accessToken

$bigShareBody = @{ folderName = "gros-dossier.zip"; fileSizeBytes = 125829120; ttlHours = 24 } | ConvertTo-Json -Compress
$t5 = Invoke-ZtRequest -Method "POST" -Uri "https://localhost:8441/api/shares" -Headers @{ Authorization = "Bearer $stdToken" } -Body $bigShareBody
Write-Host "HTTP $($t5.StatusCode) : $($t5.Body)" -ForegroundColor Green