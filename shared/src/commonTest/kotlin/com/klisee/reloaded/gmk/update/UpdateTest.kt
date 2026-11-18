package com.klisee.reloaded.gmk.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun ordena_por_numero_y_no_por_texto() {
        // El bug del comparador viejo: "0.10.0" < "0.9.0" al comparar trozos como texto.
        assertTrue(AppVersion.isNewer("0.10.0", "0.9.0"))
        assertFalse(AppVersion.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun acepta_el_prefijo_v_de_github() {
        assertEquals(AppVersion(0, 6, 7), AppVersion.parse("v0.6.7"))
        assertEquals(AppVersion(0, 6, 7), AppVersion.parse("0.6.7"))
        assertNull(AppVersion.parse("versión nueva"))
        assertNull(AppVersion.parse(""))
    }

    @Test
    fun una_estable_es_posterior_a_su_beta() {
        assertTrue(AppVersion.isNewer("1.0.0", "1.0.0-beta1"))
        assertFalse(AppVersion.isNewer("1.0.0-beta1", "1.0.0"))
    }

    @Test
    fun ordena_betas_por_su_numero() {
        assertTrue(AppVersion.isNewer("1.0.0-beta10", "1.0.0-beta9"))
        assertTrue(AppVersion.isNewer("1.0.0-rc1", "1.0.0-beta9"))
    }
}

class PickUpdateTest {
    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        assets: List<GitHubAsset> = listOf(asset(UpdatePlatform.LinuxDeb.assetName(tag.removePrefix("v")))),
    ) = GitHubRelease(tagName = tag, draft = draft, prerelease = prerelease, assets = assets)

    private fun asset(name: String, digest: String = "sha256:" + "a".repeat(64)) =
        GitHubAsset(name = name, size = 100, digest = digest, downloadUrl = "https://example/$name")

    @Test
    fun elige_la_mas_nueva_que_tiene_paquete_para_esta_plataforma() {
        val releases = listOf(release("v0.7.2"), release("v0.8.0"), release("v0.7.9"))
        val picked = pickUpdate(releases, "0.7.0", UpdatePlatform.LinuxDeb)
        assertEquals("0.8.0", picked?.version)
    }

    @Test
    fun no_ofrece_nada_si_ya_estas_en_la_ultima() {
        assertNull(pickUpdate(listOf(release("v0.6.7")), "0.6.7", UpdatePlatform.LinuxDeb))
    }

    @Test
    fun el_canal_estable_ignora_las_betas() {
        val releases = listOf(release("v0.9.0-beta1", prerelease = true), release("v0.8.0"))
        assertEquals("0.8.0", pickUpdate(releases, "0.7.0", UpdatePlatform.LinuxDeb, UPDATE_CHANNEL_STABLE)?.version)
        assertEquals("0.9.0-beta1", pickUpdate(releases, "0.7.0", UpdatePlatform.LinuxDeb, UPDATE_CHANNEL_BETA)?.version)
    }

    @Test
    fun nunca_ofrece_un_borrador() {
        val drafts = listOf(release("v0.9.0", draft = true))
        assertNull(pickUpdate(drafts, "0.7.0", UpdatePlatform.LinuxDeb))
        assertTrue(releaseHistory(drafts).isEmpty())
    }

    @Test
    fun exige_que_exista_el_asset_de_esta_plataforma() {
        // Una release solo con MSI no sirve para Linux: mejor no ofrecer nada que un botón roto.
        val windowsOnly = release("v0.9.0", assets = listOf(asset("reloaded-opengmk-0.9.0-win-x64.msi")))
        assertNull(pickUpdate(listOf(windowsOnly), "0.7.0", UpdatePlatform.LinuxDeb))
    }

    @Test
    fun el_asset_tiene_el_nombre_que_publica_el_workflow() {
        assertEquals(
            "reloaded-opengmk-0.6.7-win-x64.msi",
            UpdatePlatform.Windows.assetName("0.6.7"),
        )
        assertEquals(
            "reloaded-opengmk_0.6.7_amd64.deb",
            UpdatePlatform.LinuxDeb.assetName("0.6.7"),
        )
        assertEquals(
            "reloaded-opengmk-0.6.7-linux-x86_64.AppImage",
            UpdatePlatform.LinuxAppImage.assetName("0.6.7"),
        )
        assertEquals(
            "reloaded-opengmk-0.6.7-android-arm64.apk",
            UpdatePlatform.AndroidArm64.assetName("0.6.7"),
        )
    }

    @Test
    fun un_digest_invalido_no_se_acepta() {
        assertNotNull(asset("x.deb").sha256)
        assertNull(asset("x.deb", digest = "md5:abc").sha256)
        assertNull(asset("x.deb", digest = "sha256:zz").sha256)
        // 63 caracteres: le falta uno.
        assertNull(asset("x.deb", digest = "sha256:" + "a".repeat(63)).sha256)
    }
}

class UpdateCheckDueTest {
    @Test
    fun comprueba_si_nunca_se_intento() {
        assertTrue(updateCheckIsDue(0, 1_000_000))
    }

    @Test
    fun respeta_la_hora_entre_intentos() {
        val now = 10_000_000L
        assertFalse(updateCheckIsDue(now - 30 * 60 * 1000, now))
        assertTrue(updateCheckIsDue(now - 61 * 60 * 1000, now))
    }

    @Test
    fun un_reloj_que_retrocede_no_bloquea_la_comprobacion() {
        // Si el reloj del sistema se atrasa, el último intento queda "en el futuro".
        assertTrue(updateCheckIsDue(20_000_000, 10_000_000))
    }
}

class ReleaseNotesTest {
    @Test
    fun separa_titulos_vinetas_y_parrafos() {
        val blocks = parseReleaseNotes(
            """
            ## Novedades

            Arreglado el audio en las VM.

            - Pantalla de carga
            - Autoactualizador
            """.trimIndent(),
        )
        assertTrue(blocks.any { it is NoteBlock.Heading && it.level == 2 && it.text == "Novedades" })
        assertTrue(blocks.any { it is NoteBlock.Paragraph })
        assertEquals(2, blocks.count { it is NoteBlock.Bullet })
    }

    @Test
    fun respeta_los_bloques_de_codigo() {
        val blocks = parseReleaseNotes("Antes\n```\n-- algo\n```\nDespués")
        val code = blocks.filterIsInstance<NoteBlock.Code>().single()
        assertEquals("-- algo", code.text)
    }

    @Test
    fun entiende_negritas_enlaces_y_codigo_en_linea() {
        val spans = spansOf("Hay **importante**, [enlace](https://x.test) y `codigo`.")
        assertTrue(spans.any { it is NoteSpan.Bold && it.value == "importante" })
        assertTrue(spans.any { it is NoteSpan.Link && it.url == "https://x.test" })
        assertTrue(spans.any { it is NoteSpan.Code && it.value == "codigo" })
    }

    @Test
    fun la_vista_previa_quita_el_markdown() {
        val preview = notesPlainPreview("## Título\n\n- Uno\n- Dos")
        assertFalse(preview.contains("#"))
        assertFalse(preview.contains("-"))
        assertTrue(preview.contains("Título"))
    }
}

class NotesPreviewRegressionTest {
    @Test
    fun conserva_los_guiones_dentro_de_las_palabras() {
        // Visto en la pantalla real: "SHA-256" se quedaba en "SHA 256".
        val preview = notesPlainPreview("Descarga verificada con `SHA-256` y fin.")
        assertTrue(preview.contains("SHA-256"), "no debe perder el guion interno: $preview")
    }

    @Test
    fun no_deja_espacio_antes_de_la_puntuacion() {
        // También se vio "autoactualizador ." al quitar la negrita.
        val preview = notesPlainPreview("Ya está aquí el **autoactualizador**.")
        assertTrue(preview.contains("autoactualizador."), "no debe quedar espacio antes del punto: $preview")
    }
}

class AssetNameToleranceTest {
    private fun asset(name: String) =
        GitHubAsset(name = name, size = 100, digest = "sha256:" + "a".repeat(64), downloadUrl = "https://x/$name")

    private fun release(vararg names: String) =
        GitHubRelease(tagName = "v0.6.7", assets = names.map(::asset))

    @Test
    fun acepta_el_nombre_real_que_produce_el_empaquetador() {
        // Visto de verdad: jpackage generó "reloaded-opengmk-0.5.0-linux-amd64.deb" y el updater no
        // reconocía el archivo porque esperaba "reloaded-opengmk_0.6.7_amd64.deb".
        val release = release("reloaded-opengmk-0.5.0-linux-amd64.deb")
        assertNotNull(release.assetFor(UpdatePlatform.LinuxDeb))
    }

    @Test
    fun prefiere_el_nombre_exacto_cuando_existe() {
        val release = release(
            "otro.deb",
            "reloaded-opengmk_0.6.7_amd64.deb",
        )
        assertEquals("reloaded-opengmk_0.6.7_amd64.deb", release.assetFor(UpdatePlatform.LinuxDeb)?.name)
    }

    @Test
    fun no_confunde_paquetes_de_otra_plataforma() {
        val release = release("reloaded-opengmk-0.6.7-win-x64.msi")
        assertNull(release.assetFor(UpdatePlatform.LinuxDeb))
        assertNotNull(release.assetFor(UpdatePlatform.Windows))
    }

    @Test
    fun un_asset_sin_subir_no_cuenta() {
        val pending = GitHubAsset(
            name = "reloaded-opengmk_0.6.7_amd64.deb",
            state = "starter",
            size = 100,
            digest = "sha256:" + "a".repeat(64),
            downloadUrl = "https://x/y.deb",
        )
        assertNull(GitHubRelease(tagName = "v0.6.7", assets = listOf(pending)).assetFor(UpdatePlatform.LinuxDeb))
    }
}

class PlatformSeparationTest {
    private fun asset(name: String) =
        GitHubAsset(name = name, size = 100, digest = "sha256:" + "a".repeat(64), downloadUrl = "https://x/$name")

    @Test
    fun quien_instalo_deb_no_recibe_el_appimage() {
        // Ambos son de Linux, pero son paquetes distintos: instalar el otro rompería el flujo.
        val release = GitHubRelease(
            tagName = "v0.7.0",
            assets = listOf(asset("reloaded-opengmk-0.7.0-linux-x86_64.AppImage")),
        )
        assertNull(release.assetFor(UpdatePlatform.LinuxDeb))
        assertNotNull(release.assetFor(UpdatePlatform.LinuxAppImage))
    }

    @Test
    fun cada_plataforma_ve_solo_su_paquete() {
        val release = GitHubRelease(
            tagName = "v0.7.0",
            assets = listOf(
                asset("reloaded-opengmk-0.7.0-win-x64.msi"),
                asset("reloaded-opengmk_0.7.0_amd64.deb"),
                asset("reloaded-opengmk-0.7.0-android-arm64.apk"),
            ),
        )
        assertEquals(".msi", release.assetFor(UpdatePlatform.Windows)?.name?.takeLast(4))
        assertEquals(".deb", release.assetFor(UpdatePlatform.LinuxDeb)?.name?.takeLast(4))
        assertEquals(".apk", release.assetFor(UpdatePlatform.AndroidArm64)?.name?.takeLast(4))
    }

    @Test
    fun un_apk_no_se_ofrece_a_escritorio() {
        val release = GitHubRelease(tagName = "v0.7.0", assets = listOf(asset("app-0.7.0-android-arm64.apk")))
        assertNull(release.assetFor(UpdatePlatform.LinuxDeb))
        assertNull(release.assetFor(UpdatePlatform.Windows))
    }
}
