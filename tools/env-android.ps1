# Ambiente Android só para esta sessão do PowerShell (não altera JAVA_HOME/PATH do Windows).
# Uso, a partir da pasta do repositório:  . .\tools\env-android.ps1
# Veja docs/AMBIENTE-LOCAL.md para instalar JDK 17 e Android SDK numa máquina nova.

$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $jdk) { Write-Warning 'JDK 17 (Temurin) não encontrado em C:\Program Files\Eclipse Adoptium.'; return }

$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# Só em rede com proxy de inspeção TLS (empresa): a JVM passa a confiar nos certificados do Windows.
# Em rede doméstica, rode antes de carregar o script:  $env:USE_WINDOWS_TRUSTSTORE = '0'
if ($env:USE_WINDOWS_TRUSTSTORE -ne '0') {
    $env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -Djavax.net.ssl.trustStore=NUL'
}

# O Gradle precisa do JDK 17 também no daemon; isso evita mexer no JAVA_HOME global.
$gp = "$env:USERPROFILE\.gradle\gradle.properties"
if (-not ((Test-Path $gp) -and (Select-String -Path $gp -Pattern 'org.gradle.java.home' -Quiet))) {
    New-Item -ItemType Directory -Force (Split-Path $gp) | Out-Null
    Add-Content $gp ('org.gradle.java.home=' + ($env:JAVA_HOME -replace '\\', '/' -replace ':', '\:')) -Encoding ascii
}

Write-Host "JDK: $env:JAVA_HOME"
Write-Host "SDK: $env:ANDROID_HOME"
