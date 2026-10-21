# Streaming Hub — contexto para novas sessões

App Android pessoal (Kotlin + Jetpack Compose) que centraliza os serviços de streaming do dono: catálogo unificado, busca, "onde assistir" e botão que abre o título direto no app certo. Repositório: `github.com/damanfra/Android-Streaming-Hub`.

## Objetivo e fase atual

- **Meta final:** rodar na TV (Amazon Fire TV, navegação por controle remoto, tela horizontal).
- **Fase atual:** testar no celular do dono e priorizar **funcionalidade**. Interface simples; refinamento visual fica para depois.
- O dono testa instalando o APK no celular e reporta por prints/texto. Responda em **português (BR)**.
- O Fire TV dele aceita APK (Fire OS/Android). Os Fire TV Sticks novos (4K Select 2025, HD 2026) rodam Vega OS e **não** instalam APK — não são alvo.

## Como funciona

- **Catálogo e filtros:** API do TMDB (`/discover`, `/search/multi`, detalhes com `watch/providers`, `credits`, `external_ids`), região fixa `BR`, idioma `pt-BR`. O usuário cola a chave de API ou o Read Access Token (começa com `eyJ`) em Configurações; `Tmdb.kt` aceita os dois.
- **Serviços do usuário:** ele marca em Configurações quais assina. O Início filtra por eles (`with_watch_providers`).
- **Abrir no app:** `StreamingApps.openTitle` tenta, nesta ordem: (1) link direto da página do título (vem da JustWatch) com `setPackage` do app instalado; (2) busca do app, para quem tem `searchUrl`; (3) abre o app com o título copiado; (4) navegador/loja.
- **JustWatch (`JustWatch.kt`):** usa a API GraphQL **não oficial** (`apis.justwatch.com/graphql`) para pegar os links diretos por serviço e a nota do IMDb. Confere o título pelo `tmdbId` para evitar homônimo. Qualquer falha cai no plano B sem quebrar a tela.
- **Persistência local (`Prefs.kt`, DataStore):** chave do TMDB, serviços assinados, Minha lista, Recentes, colunas por orientação. Os **filtros do Início ficam só na memória** (voltam ao padrão ao fechar o app).

## Mapa do código (`app/src/main/java/com/damanfra/streaminghub/`)

| Arquivo | Papel |
|---|---|
| `AppViewModel.kt` | estado global: chave, serviços, listas, recentes, filtros, colunas |
| `data/Tmdb.kt` | cliente TMDB (OkHttp + kotlinx.serialization) |
| `data/JustWatch.kt` | links diretos + nota IMDb |
| `data/StreamingApps.kt` | mapa serviço→pacote Android, abertura de títulos e do IMDb |
| `data/Filters.kt` | ordenação, época, ano "A partir de", nota mínima, gêneros |
| `data/Prefs.kt`, `data/Models.kt` | persistência e modelos |
| `ui/HomeScreen.kt`, `FilterSheet.kt` | catálogo em grade, chips de serviço, painel de filtros |
| `ui/DetailScreen.kt` | detalhes, onde assistir, elenco, IMDb |
| `ui/ListScreen.kt` | abas Recentes / Quero ver / Assistidos |
| `ui/SearchScreen.kt`, `AppsScreen.kt`, `SettingsScreen.kt`, `App.kt`, `Components.kt` | demais telas e navegação |

## Funcionalidades prontas (até a v0.1.7)

Início (Filmes/Séries, chip por serviço, **Filtros**), Busca, Detalhes, Minha lista (Recentes, Quero ver, Assistidos), Apps instalados, Configurações (chave TMDB, serviços, **capas por linha** separadas para vertical e horizontal; na horizontal a barra de título some para sobrar altura).

**Validado pelo dono no aparelho:** catálogo e filtro por serviço; detecção dos apps instalados; abertura direta no título em **todos** os serviços que ele assina; 3 colunas na vertical.
**Ainda sem retorno de teste:** filtro "A partir de", elenco/direção, nota e botão do IMDb, aba Recentes.

## Armadilhas e decisões

- **Nome do repositório:** a URL canônica é `Android-Streaming-Hub`, mas a sessão original registrou `android-streaming-hub`. O `git remote` precisa bater com o nome esperado pelo `--teleport`.
- **Assinatura do APK:** `keystore/streaminghub-debug.jks` está **commitado de propósito** (senha `streaminghub`, uso pessoal). É isso que permite instalar uma versão por cima da outra, mantendo chave do TMDB e listas. Não trocar o keystore.
- **Versão:** `versionCode = GITHUB_RUN_NUMBER`; sem CI vale `1`. Só gera APK instalável por cima o build do GitHub Actions.
- **Release:** cada push na `main` (exceto os que só mudam `*.md`, `docs/` e `tools/`, ignorados por `paths-ignore`) dispara `.github/workflows/build.yml`, que compila `assembleRelease` e publica `StreamingHub-v0.1.N.apk` em Releases (pré-lançamento).
- **Android 11+:** todo app que precisa ser enxergado/aberto tem de estar em `<queries>` no `AndroidManifest.xml` (inclui `com.imdb.mobile`). Ao adicionar serviço novo, atualize o manifest **e** a lista em `StreamingApps.all`.
- **Provedores com nomes variantes:** o TMDB lista "Netflix" e "Netflix Standard with Ads" separados. A UI agrupa por app (`appKey`/`forProvider`) para ter um botão só. Canais vendidos dentro de outra loja (nome contém "channel") são ignorados de propósito.
- **Nota "Melhor avaliados":** exige mínimo de votos (300 filmes, 150 séries) para não listar títulos obscuros com nota 10.
- **"Mais recentes":** limita a data a hoje para não mostrar estreias futuras.
- **Pacotes por plataforma:** celular, Android TV e Fire TV usam IDs diferentes (ex.: Netflix `com.netflix.mediaclient` vs `com.netflix.ninja`). Para o Fire TV, **conferir os pacotes reais** do aparelho dele.
- **Limite honesto do projeto:** os serviços não têm API pública. Não existe "continuar assistindo" nem histórico real; Recentes só registra o que foi aberto **por este app**.
- **Outra máquina:** o passo a passo para replicar este ambiente (JDK, SDK, rede, ADB do Fire TV) está em `docs/AMBIENTE-LOCAL.md`.
- **Compilação local (configurada em 21/10/2026):** JDK 17 (Temurin) e Android SDK (cmdline-tools, platform 35, build-tools 35.0.0) instalados na máquina do dono sem alterar `JAVA_HOME`/PATH do Windows (ele trabalha com .NET). Para compilar: na pasta do repositório, `. .\tools\env-android.ps1` (script versionado em `tools/`; define JDK, `ANDROID_HOME` e o truststore do Windows por causa do proxy com inspeção TLS) e depois `.gradlew.bat assembleRelease`. Sem o script, o `gradlew` falha com `PKIX path building failed`. O `gh` (GitHub CLI) está instalado em `C:Program FilesGitHub CLIgh.exe` e logado, para acompanhar o Actions. Fluxo atual: editar e compilar localmente, `git push` na `main`, GitHub só versiona e publica a release. Python não existe na máquina.

## Backlog

1. **TV (Fire TV) — adiado até o dono ter o aparelho à mão** (só tem Fire TV em outro local). Decidido: **APK único adaptável** (celular + TV), com layout de TV ao detectar `UiModeManager` TELEVISION ou leanback. Plano:
   - Foco por D-pad com destaque visível (borda + leve zoom) em capas, botões e chips; `FocusRequester` na primeira capa; foco restaurado ao voltar.
   - Trocar `NavigationBar` inferior por barra lateral (ou abas no topo) no modo TV.
   - `FilterSheet` (`ModalBottomSheet`) vira painel lateral/diálogo navegável; "Remover" já existe via toque longo, que no controle é o botão central mantido.
   - Sempre horizontal, margens seguras (overscan), textos e capas maiores. O Fire TV Full HD renderiza a UI em 1920x1080 px mesmo em modelos 4K; a densidade é outra que a do celular, então conferir com `adb shell wm size` e `wm density`.
   - Digitar a chave do TMDB com controle é ruim: oferecer alternativa (ex.: vir do celular). Busca pode usar voz da Alexa.
   - Conferir os **pacotes reais** do Fire TV (`adb shell pm list packages`) e ajustar `StreamingApps.all` e o `<queries>` do manifest.
   - Teste: ativar Depuração ADB no Fire TV (Opções do desenvolvedor), `adb connect IP`, instalar o APK direto e tirar screenshots; mais prático que emulador de TV.
3. Opcional: persistir os filtros do Início entre aberturas.
4. Ideias: integração com Trakt (histórico/progresso), mais serviços (Globoplay, Crunchyroll já mapeados sem busca própria), links validados por app.

## Como o dono prefere trabalhar

- Entregas pequenas e frequentes, cada uma como release nova no GitHub.
- Funcionalidade antes de estética.
- Sugestões novas chegam a cada teste; junte pendências e entregue numa versão só quando ele pedir "anote para a próxima iteração".
