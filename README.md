# Streaming Hub

Um hub pessoal para os serviços de streaming: um só lugar para descobrir, buscar e guardar filmes e séries, e abrir cada título direto no app certo (Netflix, HBO Max, Prime Video, Disney+, Paramount+, Apple TV, Globoplay...).

Fase atual: **funcionalidade primeiro**, testada no celular. Interface caprichada e versão para Fire TV vêm depois.

## O que já faz (v0.1)

- **Início:** o que está em alta nos serviços que você assina, no Brasil, com abas Filmes/Séries e filtro por serviço.
- **Buscar:** busca unificada de filmes e séries.
- **Detalhes:** sinopse, nota, gêneros e **onde assistir**, separado em "nos seus serviços", "em outros serviços" e "aluguel ou compra".
- **Abrir no app:** tenta abrir a busca do app já com o título. Se não der, abre o app e deixa o título copiado para colar na busca. Sem o app instalado, cai no navegador ou na loja.
- **Minha lista:** "Quero ver" e "Assistidos", guardados no aparelho.
- **Apps:** atalhos para os apps de streaming instalados.

## Primeiro uso

1. Crie uma conta grátis em [themoviedb.org](https://www.themoviedb.org/) e gere uma chave em *Configurações → API*. Serve a "Chave da API" ou o "Token de leitura".
2. No app: **Apps → ⚙ Configurações**, cole a chave e toque em Salvar.
3. Marque os serviços que você assina.

## Instalação

Cada push na `main` gera um APK pelo GitHub Actions e publica na aba **Releases**. Baixe o `.apk` mais recente no celular e instale. As versões novas instalam por cima das antigas, porque todos os builds usam a mesma chave de assinatura (`keystore/`, de uso pessoal, não é segredo).

No Fire TV (Fire OS), dá para instalar o mesmo APK com o app Downloader. O app já declara suporte a TV (sem touchscreen e com ícone no launcher), mas a navegação pelo controle remoto ainda vai ser refinada.

## Limitações conhecidas

- Os serviços não oferecem API pública. O catálogo e a disponibilidade vêm do **TMDB** (dados da JustWatch), então não há "continuar assistindo" nem histórico real dos apps.
- Não há deep link oficial por ID. Netflix, Prime Video e Apple TV abrem direto na busca. Nos outros serviços o app é aberto com o título copiado.
- Os pacotes dos apps variam entre celular, Android TV e Fire TV. A lista fica em `StreamingApps.kt`.

## Stack

Kotlin, Jetpack Compose (Material 3), OkHttp + kotlinx.serialization, DataStore, Coil. minSdk 26, targetSdk 35.

## Próximos passos

- Navegação por controle remoto (D-pad) e layout para TV.
- Integração opcional com Trakt para histórico e progresso.
- Mais serviços e deep links validados por app.
