# Streaming Hub â€” contexto para novas sessÃµes

App Android pessoal (Kotlin + Jetpack Compose) que centraliza os serviÃ§os de streaming do dono: catÃ¡logo unificado, busca, "onde assistir" e botÃ£o que abre o tÃ­tulo direto no app certo. RepositÃ³rio: `github.com/damanfra/Android-Streaming-Hub`.

## Objetivo e fase atual

- **Meta final:** rodar na TV (Amazon Fire TV, navegaÃ§Ã£o por controle remoto, tela horizontal).
- **Fase atual:** testar no celular do dono e priorizar **funcionalidade**. Interface simples; refinamento visual fica para depois.
- O dono testa instalando o APK no celular e reporta por prints/texto. Responda em **portuguÃªs (BR)**.
- O Fire TV dele aceita APK (Fire OS/Android). Os Fire TV Sticks novos (4K Select 2025, HD 2026) rodam Vega OS e **nÃ£o** instalam APK â€” nÃ£o sÃ£o alvo.

## Como funciona

- **CatÃ¡logo e filtros:** API do TMDB (`/discover`, `/search/multi`, detalhes com `watch/providers`, `credits`, `external_ids`), regiÃ£o fixa `BR`, idioma `pt-BR`. O usuÃ¡rio cola a chave de API ou o Read Access Token (comeÃ§a com `eyJ`) em ConfiguraÃ§Ãµes; `Tmdb.kt` aceita os dois.
- **ServiÃ§os do usuÃ¡rio:** ele marca em ConfiguraÃ§Ãµes quais assina. O InÃ­cio filtra por eles (`with_watch_providers`).
- **Abrir no app:** `StreamingApps.openTitle` tenta, nesta ordem: (1) link direto da pÃ¡gina do tÃ­tulo (vem da JustWatch) com `setPackage` do app instalado; (2) busca do app, para quem tem `searchUrl`; (3) abre o app com o tÃ­tulo copiado; (4) navegador/loja.
- **JustWatch (`JustWatch.kt`):** usa a API GraphQL **nÃ£o oficial** (`apis.justwatch.com/graphql`) para pegar os links diretos por serviÃ§o e a nota do IMDb. Confere o tÃ­tulo pelo `tmdbId` para evitar homÃ´nimo. Qualquer falha cai no plano B sem quebrar a tela.
- **PersistÃªncia local (`Prefs.kt`, DataStore):** chave do TMDB, serviÃ§os assinados, Minha lista, Recentes, colunas por orientaÃ§Ã£o. Os **filtros do InÃ­cio ficam sÃ³ na memÃ³ria** (voltam ao padrÃ£o ao fechar o app).

## Mapa do cÃ³digo (`app/src/main/java/com/damanfra/streaminghub/`)

| Arquivo | Papel |
|---|---|
| `AppViewModel.kt` | estado global: chave, serviÃ§os, listas, recentes, filtros, colunas |
| `data/Tmdb.kt` | cliente TMDB (OkHttp + kotlinx.serialization) |
| `data/JustWatch.kt` | links diretos + nota IMDb |
| `data/StreamingApps.kt` | mapa serviÃ§oâ†’pacote Android, abertura de tÃ­tulos e do IMDb |
| `data/Filters.kt` | ordenaÃ§Ã£o, Ã©poca, ano "A partir de", nota mÃ­nima, gÃªneros |
| `data/Prefs.kt`, `data/Models.kt` | persistÃªncia e modelos |
| `ui/HomeScreen.kt`, `FilterSheet.kt` | catÃ¡logo em grade, chips de serviÃ§o, painel de filtros |
| `ui/DetailScreen.kt` | detalhes, onde assistir, elenco, IMDb |
| `ui/ListScreen.kt` | abas Recentes / Quero ver / Assistidos |
| `ui/SearchScreen.kt`, `AppsScreen.kt`, `SettingsScreen.kt`, `App.kt`, `Components.kt` | demais telas e navegaÃ§Ã£o |

## Funcionalidades prontas (atÃ© a v0.1.7)

InÃ­cio (Filmes/SÃ©ries, chip por serviÃ§o, **Filtros**), Busca, Detalhes, Minha lista (Recentes, Quero ver, Assistidos), Apps instalados, ConfiguraÃ§Ãµes (chave TMDB, serviÃ§os, **capas por linha** separadas para vertical e horizontal; na horizontal a barra de tÃ­tulo some para sobrar altura).

**Validado pelo dono no aparelho:** catÃ¡logo e filtro por serviÃ§o; detecÃ§Ã£o dos apps instalados; abertura direta no tÃ­tulo em **todos** os serviÃ§os que ele assina; 3 colunas na vertical.
**Ainda sem retorno de teste:** filtro "A partir de", elenco/direÃ§Ã£o, nota e botÃ£o do IMDb, aba Recentes.

## Armadilhas e decisÃµes

- **Nome do repositÃ³rio:** a URL canÃ´nica Ã© `Android-Streaming-Hub`, mas a sessÃ£o original registrou `android-streaming-hub`. O `git remote` precisa bater com o nome esperado pelo `--teleport`.
- **Assinatura do APK:** `keystore/streaminghub-debug.jks` estÃ¡ **commitado de propÃ³sito** (senha `streaminghub`, uso pessoal). Ã‰ isso que permite instalar uma versÃ£o por cima da outra, mantendo chave do TMDB e listas. NÃ£o trocar o keystore.
- **VersÃ£o:** `versionCode = GITHUB_RUN_NUMBER`; sem CI vale `1`. SÃ³ gera APK instalÃ¡vel por cima o build do GitHub Actions.
- **Release:** cada push na `main` (exceto mudanças só em `*.md`, `docs/` e `tools/`, ignoradas pelo `paths-ignore`) dispara `.github/workflows/build.yml`, que compila `assembleRelease` e publica `StreamingHub-v0.1.N.apk` em Releases (prÃ©-lanÃ§amento).
- **Android 11+:** todo app que precisa ser enxergado/aberto tem de estar em `<queries>` no `AndroidManifest.xml` (inclui `com.imdb.mobile`). Ao adicionar serviÃ§o novo, atualize o manifest **e** a lista em `StreamingApps.all`.
- **Provedores com nomes variantes:** o TMDB lista "Netflix" e "Netflix Standard with Ads" separados. A UI agrupa por app (`appKey`/`forProvider`) para ter um botÃ£o sÃ³. Canais vendidos dentro de outra loja (nome contÃ©m "channel") sÃ£o ignorados de propÃ³sito.
- **Nota "Melhor avaliados":** exige mÃ­nimo de votos (300 filmes, 150 sÃ©ries) para nÃ£o listar tÃ­tulos obscuros com nota 10.
- **"Mais recentes":** limita a data a hoje para nÃ£o mostrar estreias futuras.
- **Pacotes por plataforma:** celular, Android TV e Fire TV usam IDs diferentes (ex.: Netflix `com.netflix.mediaclient` vs `com.netflix.ninja`). Para o Fire TV, **conferir os pacotes reais** do aparelho dele.
- **Limite honesto do projeto:** os serviÃ§os nÃ£o tÃªm API pÃºblica. NÃ£o existe "continuar assistindo" nem histÃ³rico real; Recentes sÃ³ registra o que foi aberto **por este app**.
- **Outra mÃ¡quina:** o passo a passo para replicar este ambiente (JDK, SDK, rede, ADB do Fire TV) estÃ¡ em `docs/AMBIENTE-LOCAL.md`.
- **CompilaÃ§Ã£o local (configurada em 21/10/2026):** JDK 17 (Temurin) e Android SDK (cmdline-tools, platform 35, build-tools 35.0.0) instalados na mÃ¡quina do dono sem alterar `JAVA_HOME`/PATH do Windows (ele trabalha com .NET). Para compilar: na pasta do repositÃ³rio, `. .\tools\env-android.ps1` (script versionado em `tools/`; define JDK, `ANDROID_HOME` e o truststore do Windows por causa do proxy com inspeÃ§Ã£o TLS) e depois `.gradlew.bat assembleRelease`. Sem o script, o `gradlew` falha com `PKIX path building failed`. O `gh` (GitHub CLI) estÃ¡ instalado em `C:Program FilesGitHub CLIgh.exe` e logado, para acompanhar o Actions. Fluxo atual: editar e compilar localmente, `git push` na `main`, GitHub sÃ³ versiona e publica a release. Python nÃ£o existe na mÃ¡quina.

## Backlog

1. **TV (Fire TV) â€” adiado atÃ© o dono ter o aparelho Ã  mÃ£o** (sÃ³ tem Fire TV em outro local). Decidido: **APK Ãºnico adaptÃ¡vel** (celular + TV), com layout de TV ao detectar `UiModeManager` TELEVISION ou leanback. Plano:
   - Foco por D-pad com destaque visÃ­vel (borda + leve zoom) em capas, botÃµes e chips; `FocusRequester` na primeira capa; foco restaurado ao voltar.
   - Trocar `NavigationBar` inferior por barra lateral (ou abas no topo) no modo TV.
   - `FilterSheet` (`ModalBottomSheet`) vira painel lateral/diÃ¡logo navegÃ¡vel; "Remover" jÃ¡ existe via toque longo, que no controle Ã© o botÃ£o central mantido.
   - Sempre horizontal, margens seguras (overscan), textos e capas maiores. O Fire TV Full HD renderiza a UI em 1920x1080 px mesmo em modelos 4K; a densidade Ã© outra que a do celular, entÃ£o conferir com `adb shell wm size` e `wm density`.
   - Digitar a chave do TMDB com controle Ã© ruim: oferecer alternativa (ex.: vir do celular). Busca pode usar voz da Alexa.
   - Conferir os **pacotes reais** do Fire TV (`adb shell pm list packages`) e ajustar `StreamingApps.all` e o `<queries>` do manifest.
   - Teste: ativar DepuraÃ§Ã£o ADB no Fire TV (OpÃ§Ãµes do desenvolvedor), `adb connect IP`, instalar o APK direto e tirar screenshots; mais prÃ¡tico que emulador de TV.
3. Opcional: persistir os filtros do InÃ­cio entre aberturas.
4. Ideias: integraÃ§Ã£o com Trakt (histÃ³rico/progresso), mais serviÃ§os (Globoplay, Crunchyroll jÃ¡ mapeados sem busca prÃ³pria), links validados por app.

## Como o dono prefere trabalhar

- Entregas pequenas e frequentes, cada uma como release nova no GitHub.
- Funcionalidade antes de estÃ©tica.
- SugestÃµes novas chegam a cada teste; junte pendÃªncias e entregue numa versÃ£o sÃ³ quando ele pedir "anote para a prÃ³xima iteraÃ§Ã£o".
