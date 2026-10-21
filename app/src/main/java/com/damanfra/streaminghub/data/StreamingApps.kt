package com.damanfra.streaminghub.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Mapa "serviço do TMDB" → app Android, e a lógica para abrir um título nele.
 *
 * Nenhum serviço publica deep links oficiais por ID para terceiros, então a estratégia é em camadas:
 * 1. app instalado + URL de busca conhecida → abre a busca do app já com o título;
 * 2. app instalado → abre o app e deixa o título copiado para colar na busca;
 * 3. app ausente → abre a busca no navegador ou a página da loja.
 */
object StreamingApps {

    data class App(
        val name: String,
        /** Pacotes possíveis: celular, Android TV e Fire TV costumam ter IDs diferentes. */
        val packages: List<String>,
        /** Trechos (minúsculos) do nome do provedor no TMDB que apontam para este app. */
        val match: List<String>,
        /** URL de busca com %s para o título; null quando não há uma conhecida. */
        val searchUrl: String? = null,
    )

    val all = listOf(
        App(
            name = "Netflix",
            packages = listOf("com.netflix.mediaclient", "com.netflix.ninja"),
            match = listOf("netflix"),
            searchUrl = "https://www.netflix.com/search?q=%s",
        ),
        App(
            name = "Prime Video",
            packages = listOf("com.amazon.avod.thirdpartyclient", "com.amazon.avod", "com.amazon.firebat"),
            match = listOf("prime video", "amazon video"),
            searchUrl = "https://www.primevideo.com/search/ref=atv_nb_sr?phrase=%s",
        ),
        App(
            name = "HBO Max",
            packages = listOf("com.wbd.stream", "com.hbo.max", "com.hbo.hbonow"),
            match = listOf("hbo", "max"),
        ),
        App(
            name = "Disney+",
            packages = listOf("com.disney.disneyplus"),
            match = listOf("disney"),
        ),
        App(
            name = "Paramount+",
            packages = listOf("com.cbs.app", "com.cbs.ca", "com.cbs.ott"),
            match = listOf("paramount"),
        ),
        App(
            name = "Apple TV",
            packages = listOf(
                "com.apple.atve.android.appletv",
                "com.apple.atve.androidtv.appletv",
                "com.apple.atve.amazon.appletv",
            ),
            match = listOf("apple tv"),
            searchUrl = "https://tv.apple.com/br/search?term=%s",
        ),
        App(
            name = "Globoplay",
            packages = listOf("com.globo.globotv"),
            match = listOf("globoplay"),
        ),
        App(
            name = "Crunchyroll",
            packages = listOf("com.crunchyroll.crunchyroid"),
            match = listOf("crunchyroll"),
        ),
    )

    /** Acha o app de um provedor do TMDB. Canais vendidos dentro de outra loja ("... Amazon Channel") ficam de fora. */
    fun forProvider(providerName: String): App? {
        val n = providerName.lowercase()
        if ("channel" in n) return null
        return all.firstOrNull { app -> app.match.any { n.contains(it) } }
    }

    fun installedPackage(context: Context, app: App): String? =
        app.packages.firstOrNull { isInstalled(context, it) }

    /** Abre o app sem título específico (tela Apps). Devolve uma mensagem curta para o usuário. */
    fun launch(context: Context, app: App): String {
        val pkg = installedPackage(context, app)
        if (pkg != null && startLauncher(context, pkg)) return "Abrindo ${app.name}…"
        openStore(context, app.packages.first())
        return "${app.name} não está instalado."
    }

    /**
     * Abre um título no serviço indicado. [directUrl] é o link da página do título no serviço (via JustWatch);
     * quando existe, o app abre direto nele. Devolve uma mensagem curta para o usuário.
     */
    fun openTitle(context: Context, providerName: String, title: String, directUrl: String? = null): String {
        val app = forProvider(providerName)
        val pkg = app?.let { installedPackage(context, it) }

        if (app != null && pkg != null && directUrl != null &&
            tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).setPackage(pkg))
        ) {
            return "Abrindo no ${app.name}…"
        }

        copyToClipboard(context, title)
        if (app == null) return "Não sei abrir \"$providerName\" ainda. Título copiado."
        val search = app.searchUrl?.let { Uri.parse(it.format(Uri.encode(title))) }

        if (pkg != null) {
            if (search != null && tryStart(context, Intent(Intent.ACTION_VIEW, search).setPackage(pkg))) {
                return "Abrindo a busca do ${app.name}. Título copiado, se precisar colar."
            }
            if (startLauncher(context, pkg)) {
                return "Abrindo ${app.name}. Título copiado: cole na busca."
            }
        }
        if (search != null && tryStart(context, Intent(Intent.ACTION_VIEW, search))) {
            return "${app.name} não está instalado; abrindo no navegador."
        }
        openStore(context, app.packages.first())
        return "${app.name} não está instalado."
    }

    /** Abre a página do título no app do IMDb, se instalado; senão, no navegador. */
    fun openImdb(context: Context, imdbId: String) {
        val uri = Uri.parse("https://www.imdb.com/title/$imdbId/")
        if (isInstalled(context, IMDB_PACKAGE) &&
            tryStart(context, Intent(Intent.ACTION_VIEW, uri).setPackage(IMDB_PACKAGE))
        ) return
        tryStart(context, Intent(Intent.ACTION_VIEW, uri))
    }

    private const val IMDB_PACKAGE = "com.imdb.mobile"

    fun openUrl(context: Context, url: String): Boolean =
        tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun startLauncher(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg)
        return intent != null && tryStart(context, intent)
    }

    private fun openStore(context: Context, pkg: String) {
        if (!tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))) {
            tryStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("título", text))
    }
}
