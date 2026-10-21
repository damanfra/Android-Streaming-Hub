# Montar o ambiente de desenvolvimento em outra máquina (Windows)

Objetivo: deixar a máquina igual à principal, para editar, compilar e testar no Fire TV, com o GitHub só como versionamento e releases. Nada aqui altera `JAVA_HOME` nem o PATH do Windows.

## 1. Instalar (PowerShell)

```powershell
winget install --id Git.Git -e
winget install --id EclipseAdoptium.Temurin.17.JDK -e
winget install --id GitHub.cli -e
```

**Atenção:** o instalador do Temurin troca o `JAVA_HOME` e o PATH da máquina. Se a máquina tem outro Java em uso (ex.: Oracle/.NET com Java), desfaça: restaure o `JAVA_HOME` anterior e remova `...\Eclipse Adoptium\jdk-17...\bin` do PATH do sistema (Variáveis de ambiente). O `tools/env-android.ps1` já aponta o JDK 17 por sessão, então essas mudanças globais não são necessárias.

## 2. Android SDK (só command-line tools)

Baixe `commandlinetools-win-*_latest.zip` em https://developer.android.com/studio#command-line-tools-only (use `curl.exe -L -o clt.zip <url>`; o `Invoke-WebRequest` já travou em 0 bytes numa rede corporativa). Extraia assim:

```
%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
```

Depois, com `JAVA_HOME` apontando para o JDK 17 na sessão (`. .\tools\env-android.ps1`):

```powershell
$sm = "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat"
# aceitar licenças: pipe de "y" não funciona; use um arquivo com várias linhas "y" e redirecione com cmd
cmd /c "`"$sm`" --sdk_root=$env:ANDROID_HOME --licenses < yes.txt"
cmd /c "`"$sm`" --sdk_root=$env:ANDROID_HOME `"platform-tools`" `"platforms;android-35`" `"build-tools;35.0.0`" < yes.txt"
```

(`yes.txt` = 30 linhas contendo `y`.)

## 3. Clonar e configurar o projeto

```powershell
git clone https://github.com/damanfra/Android-Streaming-Hub
cd Android-Streaming-Hub
git config user.name "Daniel Manfrini"; git config user.email "daniel.manfrini@gmail.com"
gh auth login          # GitHub.com, HTTPS, navegador
. .\tools\env-android.ps1
Set-Content local.properties "sdk.dir=$($env:ANDROID_HOME -replace '\\','\\' -replace ':','\:')" -Encoding ascii
.\gradlew.bat assembleRelease --console=plain
```

`local.properties` já está no `.gitignore`. O primeiro build baixa alguns GB (Gradle, dependências) e leva uns 5 minutos.

## 4. Rede

- **Rede com proxy de inspeção TLS (empresa):** sem tratamento o `gradlew` falha com `PKIX path building failed`. O `env-android.ps1` resolve usando o truststore do Windows.
- **Rede doméstica / sem proxy:** nada a fazer. Se quiser desligar a opção, `$env:USE_WINDOWS_TRUSTSTORE='0'` antes de carregar o script.
- O download do Gradle e do SDK precisa alcançar `services.gradle.org`, `dl.google.com`, `maven.google.com`, `repo.maven.apache.org` e `plugins.gradle.org`.

## 5. Fire TV por ADB (item de backlog "TV")

Os dois aparelhos precisam estar **na mesma rede local**. Em redes que isolam clientes (Wi-Fi de convidados, hotel), o `adb connect` não alcança o Fire TV; use outra rede ou um roteador próprio.

No Fire TV: Configurações → Meu Fire TV → Opções do desenvolvedor → **Depuração ADB** e **Apps de fontes desconhecidas** (se não aparecer, Sobre → toque 7 vezes no nome do aparelho). O IP está em Sobre → Rede.

```powershell
. .\tools\env-android.ps1
adb connect IP_DO_FIRETV:5555      # aceitar o pedido de autorização na TV
adb devices
adb shell wm size                  # resolução da UI (esperado 1920x1080)
adb shell wm density
adb shell pm list packages | findstr /i "netflix amazon disney hbo wbd max apple globo crunchyroll paramount cbs"
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb exec-out screencap -p > tela.png
```

Para o `adb install -r` atualizar por cima de uma versão do GitHub, o `versionCode` do APK local precisa ser maior: `$env:GITHUB_RUN_NUMBER = 900` antes do build. O keystore é o mesmo (`keystore/streaminghub-debug.jks`), então a assinatura combina.

## 6. Fluxo de trabalho

Editar → compilar local → testar (celular ou Fire TV por `adb`) → `git push origin main` → o GitHub Actions publica a release. Acompanhar com `gh run list` e `gh release list`.

## Checklist rápido

- [ ] Git, JDK 17, GitHub CLI instalados
- [ ] SDK em `%LOCALAPPDATA%\Android\Sdk` (platform-tools, android-35, build-tools 35.0.0, licenças aceitas)
- [ ] `git clone` feito, identidade do git e `gh auth login`
- [ ] `local.properties` criado e `assembleRelease` passou
- [ ] Fire TV com Depuração ADB, na mesma rede, `adb devices` lista o aparelho
