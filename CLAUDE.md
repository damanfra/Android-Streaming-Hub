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
- **Jellyfin (`Jellyfin.kt`, `JellyfinScreen.kt`, `PlayerActivity.kt`, 22/10/2026):** cliente da API REST oficial (rotas conferidas na especificação 12.3: tudo com `?userId=`, as antigas `/Users/{id}/Items` não existem mais). Login por usuário/senha ou Quick Connect; "Procurar na rede" usa a descoberta UDP 7359. Aparece no Início como serviço de ID fixo `Jellyfin.PROVIDER_ID = -100` (chip no celular, entrada no dial da TV, logo depois de "Todos"), com "Continuar assistindo" + biblioteca por data de adição. Um índice `tipo:tmdb:ID`/`tipo:imdb:ID` → item (`vm.jellyfinIndex`, carregado ao conectar/abrir) liga o TMDB ao servidor: botão "Assistir no Jellyfin" nos Detalhes e marca na Busca. Player Media3 numa Activity própria: toca o arquivo original (`/Videos/{id}/stream?static=true`, token no cabeçalho `Authorization`); legendas internas o ExoPlayer lê do arquivo, externas de texto vêm convertidas para SRT pelo servidor (`.../Subtitles/{i}/Stream.srt`). Se o aparelho não decodifica vídeo/áudio (ou dá erro), pede `PlaybackInfo` com DeviceProfile só de transcodificação e usa a `TranscodingUrl` (HLS fMP4) que o servidor devolve; a rota `master.m3u8` não aparece na especificação, por isso não é montada à mão. Progresso a cada 10 s em `/Sessions/Playing/*`.
- **Persistência local (`Prefs.kt`, DataStore):** chave do TMDB, Jellyfin, serviços assinados, Minha lista, Recentes, colunas por orientação e o **estado do Início** (`HomeState`: filtros, Filmes/Séries, serviço e último título aberto, salvo a cada mudança). Ao reabrir o app, o Início volta igual e rola até o último título (no Fire TV o sistema costuma fechar o app ao abrir um serviço).

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
| `data/Jellyfin.kt` | cliente do Jellyfin: login, Quick Connect, descoberta, biblioteca, índice TMDB, reprodução |
| `ui/JellyfinScreen.kt` | grade do Jellyfin no Início, tela do título (temporadas/episódios) e seção nas Configurações |
| `PlayerActivity.kt` | player embutido (Media3): legendas, conversão no servidor quando preciso, progresso |
| `ui/Tv.kt` | modo TV: detecção (`LocalIsTv`), moldura de foco, painel lateral com dial, roteamento de foco painel↔grade, campo de texto que só abre o teclado no OK |

## Funcionalidades prontas

Início (Filmes/Séries, chip por serviço, **Filtros**), Busca, Detalhes, Minha lista (Recentes, Quero ver, Assistidos), Apps instalados, Configurações (chave TMDB, serviços, **capas por linha** separadas para vertical e horizontal; na horizontal a barra de título some para sobrar altura).

**Modo TV (21/10/2026, só quando `isTvDevice`; o celular não muda):** protótipos aprovados no canvas https://claude.ai/artifact/WcvGEm8VZYjnnWmiVF6f2z.
- Painel lateral fixo (largura de 2 colunas de capas): atalhos Início/Buscar/Lista/Apps/Ajustes, Filmes/Séries, Filtros e, por último, o **dial de serviços** (▲▼ gira; catálogo recarrega após 350 ms parado). À direita, 5 capas por linha.
- Foco: moldura branca + leve zoom (`tvFocusFrame`), realce do Material reforçado na TV (`TvRipple`). ► do painel entra na última capa com foco; ◄ da 1ª coluna volta ao dial; ao voltar dos Detalhes o foco retorna à capa aberta.
- Voltar fora do painel leva o foco a ele; só fecha o app com o foco já no painel.
- Campos de texto só abrem o teclado com OK (`TvTextFieldBox`).
- Filtros num diálogo de 2 colunas: Ver resultados/Limpar e resumo fixos à esquerda, opções (chips grandes) à direita.
- Detalhes em 3 colunas sem rolagem: capa grande, informações, coluna "Assistir em" (um botão por serviço assinado, foco inicial no primeiro).

**Validado pelo dono no aparelho:** catálogo e filtro por serviço; detecção dos apps instalados; abertura direta no título em **todos** os serviços que ele assina; 3 colunas na vertical.
**Fire TV (Stick 3ª geração, Fire OS 7):** link direto validado em Netflix (precisa do extra `source=30`, já em `linkExtras`), HBO Max, Disney+ e Apple TV (os dois pedem perfil e depois seguem para o título) e Prime Video. Pacotes reais: `com.netflix.ninja`, `com.amazon.firebat`, `com.hbo.hbonow`, `com.disney.disneyplus`, `com.apple.atve.amazon.appletv`. Paramount+ e Globoplay não instalados lá.
**Ainda sem retorno de teste:** filtro "A partir de", aba Recentes.

## Armadilhas e decisões

- **Nome do repositório:** a URL canônica é `Android-Streaming-Hub`, mas a sessão original registrou `android-streaming-hub`. O `git remote` precisa bater com o nome esperado pelo `--teleport`.
- **Assinatura do APK:** `keystore/streaminghub-debug.jks` está **commitado de propósito** (senha `streaminghub`, uso pessoal). É isso que permite instalar uma versão por cima da outra, mantendo chave do TMDB e listas. Não trocar o keystore.
- **Versão:** `versionCode = GITHUB_RUN_NUMBER`; sem CI vale `1`. Só gera APK instalável por cima o build do GitHub Actions.
- **Release:** cada push na `main` (exceto os que só mudam `*.md`, `docs/` e `tools/`, ignorados por `paths-ignore`) dispara `.github/workflows/build.yml`, que compila `assembleRelease` e publica `StreamingHub-v0.1.N.apk` em Releases (pré-lançamento).
- **Jellyfin em http:** o manifesto liga `usesCleartextTraffic` porque o servidor de casa responde em `http://` na rede local. Sem isso o Android 9+ bloqueia.
- **Jellyfin, limites conhecidos:** legendas externas em imagem (.idx/.sub VobSub, .sup PGS) não entram; na versão convertida pelo servidor as legendas internas em imagem se perdem. Sem próximo episódio automático ainda. **Validado no Fire TV (22/10/2026):** descoberta na rede (servidor `DESKTOP-1660`, `http://192.168.15.36:8096`), login, biblioteca e um filme com legenda externa .srt. Controles do player na TV **aprovados pelo dono** (22/10/2026): ◄ ► pulam 10 s com os controles escondidos (segurando acelera), OK pausa e mostra os controles, Voltar esconde os controles antes de sair, botões Legendas/Áudio abaixo do ⏸; a barra de tempo com foco pulava 1/20 da duração (5 min), agora 10 s.
- **Android 11+:** todo app que precisa ser enxergado/aberto tem de estar em `<queries>` no `AndroidManifest.xml` (inclui `com.imdb.mobile`). Ao adicionar serviço novo, atualize o manifest **e** a lista em `StreamingApps.all`.
- **Provedores com nomes variantes:** o TMDB lista "Netflix" e "Netflix Standard with Ads" separados. A UI agrupa por app (`appKey`/`forProvider`) para ter um botão só. Canais vendidos dentro de outra loja (nome contém "channel") são ignorados de propósito.
- **Nota "Melhor avaliados":** exige mínimo de votos (300 filmes, 150 séries) para não listar títulos obscuros com nota 10.
- **"Mais recentes":** limita a data a hoje para não mostrar estreias futuras.
- **Pacotes por plataforma:** celular, Android TV e Fire TV usam IDs diferentes (ex.: Netflix `com.netflix.mediaclient` vs `com.netflix.ninja`). Os do Fire TV do dono já foram conferidos (ver acima).
- **Fire TV por ADB:** o IP é DHCP e muda após reiniciar (já foi `.5`, depois `.6`). Nesta máquina, iniciar o servidor com `Start-Process adb -ArgumentList 'nodaemon','server'` pelo PowerShell (sob o `timeout` do Git Bash ele morre/trava); no Git Bash, caminhos `/data/...` viram caminhos do Windows sem `MSYS_NO_PATHCONV=1`. Na primeira vez o Firewall do Windows pede para liberar o `adb.exe` (a janela fica escondida). Netflix e Apple TV bloqueiam `screencap` (sai vazio). Avisar o dono para não mexer no controle durante testes por ADB.
- **APK no Fire TV:** instalado pelo PC com `$env:GITHUB_RUN_NUMBER='900'` (versionCode 900), então as releases do GitHub (versionCode menor) não instalam por cima lá; atualizar o Fire TV sempre pelo build local.
- **Limite honesto do projeto:** os serviços não têm API pública. Não existe "continuar assistindo" nem histórico real; Recentes só registra o que foi aberto **por este app**.
- **Outra máquina:** o passo a passo para replicar este ambiente (JDK, SDK, rede, ADB do Fire TV) está em `docs/AMBIENTE-LOCAL.md`.
- **Compilação local (configurada em 21/10/2026):** JDK 17 (Temurin) e Android SDK (cmdline-tools, platform 35, build-tools 35.0.0) instalados na máquina do dono sem alterar `JAVA_HOME`/PATH do Windows (ele trabalha com .NET). Para compilar: na pasta do repositório, `. .\tools\env-android.ps1` (script versionado em `tools/`; define JDK, `ANDROID_HOME` e o truststore do Windows por causa do proxy com inspeção TLS) e depois `.gradlew.bat assembleRelease`. Sem o script, o `gradlew` falha com `PKIX path building failed`. O `gh` (GitHub CLI) está instalado em `C:Program FilesGitHub CLIgh.exe` e logado, para acompanhar o Actions. Fluxo atual: editar e compilar localmente, `git push` na `main`, GitHub só versiona e publica a release. Python não existe na máquina.

## Backlog

1. **TV (Fire TV):** a primeira rodada está pronta (ver "Modo TV"). Pendências observadas:
   - Telas Buscar, Lista e Apps ainda têm layout de celular dentro da área da direita; Apps poderia virar grade de ícones.
   - Lista ("Quero ver"/"Assistidos") fica só no aparelho: o que se marca no celular não aparece na TV.
   - Margens seguras para TVs com zoom/overscan ligado (a do dono tinha "zoom automático").
   - Digitar com controle: o teclado do Fire TV já oferece QR code para digitar pelo celular; busca por voz da Alexa ainda não testada.
   - "Remover" na Minha lista depende de toque longo; conferir se o OK mantido funciona no controle.
3. **Player:** ajustar estilo da legenda (cor, tamanho, opacidade, fundo), pedido do dono para um segundo momento. Media3 aceita via `SubtitleView.setStyle(CaptionStyleCompat)` e `setFractionalTextSize`.
4. Ideias: integração com Trakt (histórico/progresso), mais serviços (Globoplay, Crunchyroll já mapeados sem busca própria), links validados por app.

## Como o dono prefere trabalhar

- Entregas pequenas e frequentes, cada uma como release nova no GitHub.
- Funcionalidade antes de estética.
- Sugestões novas chegam a cada teste; junte pendências e entregue numa versão só quando ele pedir "anote para a próxima iteração".
