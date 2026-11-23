# Regenere la PKI mTLS (CA + 3 keystores + truststore) et les cles RSA JWT
$ErrorActionPreference = "Stop"
$pkiDir = "$PSScriptRoot\..\certs-pki"
$password = "changeit"

New-Item -ItemType Directory -Force -Path $pkiDir | Out-Null
Remove-Item "$pkiDir\*" -Force -ErrorAction SilentlyContinue

keytool -genkeypair -alias zerotrust-ca -keyalg RSA -keysize 2048 -validity 365 `
  -dname "CN=ZeroTrust-RootCA, OU=ENSIAS, O=ZeroTrustShare, C=MA" `
  -ext "BasicConstraints:critical=ca:true" -ext "KeyUsage:critical=keyCertSign,cRLSign" `
  -keystore "$pkiDir\ca.p12" -storetype PKCS12 -storepass $password -keypass $password

keytool -exportcert -alias zerotrust-ca -keystore "$pkiDir\ca.p12" -storepass $password -rfc -file "$pkiDir\ca.crt"
keytool -importcert -noprompt -alias zerotrust-ca -file "$pkiDir\ca.crt" -keystore "$pkiDir\truststore.p12" -storetype PKCS12 -storepass $password

$services = @("share-gateway-service", "policy-metadata-service", "storage-vault-service")
foreach ($svc in $services) {
    keytool -genkeypair -alias $svc -keyalg RSA -keysize 2048 -validity 365 `
      -dname "CN=$svc, OU=ENSIAS, O=ZeroTrustShare, C=MA" `
      -ext "SubjectAlternativeName=dns:localhost,dns:policy-metadata-service,dns:storage-vault-service,dns:share-gateway-service,ip:127.0.0.1" `
      -keystore "$pkiDir\$svc.p12" -storetype PKCS12 -storepass $password -keypass $password

    keytool -certreq -alias $svc -keystore "$pkiDir\$svc.p12" -storepass $password -file "$pkiDir\$svc.csr"
    keytool -gencert -alias zerotrust-ca -keystore "$pkiDir\ca.p12" -storepass $password `
      -infile "$pkiDir\$svc.csr" -outfile "$pkiDir\$svc.crt" `
      -ext "SubjectAlternativeName=dns:localhost,dns:policy-metadata-service,dns:storage-vault-service,dns:share-gateway-service,ip:127.0.0.1" -ext "ExtendedKeyUsage=serverAuth,clientAuth" -rfc -validity 365

    keytool -importcert -noprompt -alias zerotrust-ca -file "$pkiDir\ca.crt" -keystore "$pkiDir\$svc.p12" -storepass $password
    keytool -importcert -noprompt -alias $svc -file "$pkiDir\$svc.crt" -keystore "$pkiDir\$svc.p12" -storepass $password
}

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$pkiDir\jwt-private.pem"
openssl rsa -pubout -in "$pkiDir\jwt-private.pem" -out "$pkiDir\jwt-public.pem"

foreach ($svc in $services) {
    $dest = "$PSScriptRoot\..\$svc\src\main\resources\certs"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item "$pkiDir\$svc.p12" "$dest\$svc.p12" -Force
    Copy-Item "$pkiDir\truststore.p12" "$dest\truststore.p12" -Force
    Copy-Item "$pkiDir\jwt-public.pem" "$dest\jwt-public.pem" -Force
    if ($svc -eq "share-gateway-service") {
        Copy-Item "$pkiDir\jwt-private.pem" "$dest\jwt-private.pem" -Force
    }
}
Write-Host "PKI mTLS et cles JWT regenerees avec succes !" -ForegroundColor Green

