package com.example.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager

enum class SocialShareTarget(
    val label: String,
    val packages: List<String>
) {
    INSTAGRAM("Instagram", listOf("com.instagram.android")),
    TIKTOK("TikTok", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    WHATSAPP("WhatsApp", listOf("com.whatsapp")),
    YOUTUBE("YouTube", listOf("com.google.android.youtube")),
    SYSTEM("Mais apps", emptyList())
}

object ReactionShareHelper {

    fun shareVideoToTarget(
        context: Context,
        videoUriString: String,
        target: SocialShareTarget,
        captionText: String
    ): String {
        val uri = Uri.parse(videoUriString)
        val matchedPackage = target.packages.firstOrNull { isPackageInstalled(context, it) }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (captionText.isNotBlank()) {
                putExtra(Intent.EXTRA_TEXT, captionText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (matchedPackage != null) {
                setPackage(matchedPackage)
            }
        }

        return runCatching {
            if (matchedPackage != null) {
                context.startActivity(sendIntent)
                "Abrindo ${target.label}..."
            } else {
                val chooser = Intent.createChooser(sendIntent, "Compartilhar reação")
                context.startActivity(chooser)
                if (target == SocialShareTarget.SYSTEM) {
                    "Abrindo opções de compartilhamento"
                } else {
                    "${target.label} não instalado · Abrindo compartilhamento"
                }
            }
        }.getOrElse {
            "Não foi possível abrir o compartilhamento"
        }
    }

    fun shareToCloudForDirectLink(
        context: Context,
        videoUriString: String,
        title: String
    ): String {
        val uri = Uri.parse(videoUriString)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isPackageInstalled(context, "com.google.android.apps.docs")) {
                setPackage("com.google.android.apps.docs")
            }
        }

        return runCatching {
            val chooser = Intent.createChooser(
                sendIntent,
                "Enviar para nuvem e gerar link direto"
            )
            context.startActivity(chooser)
            "Escolha Drive ou Fotos para gerar seu link compartilhável"
        }.getOrElse {
            "Não foi possível abrir o envio para nuvem"
        }
    }

    fun copyDirectLink(
        context: Context,
        link: String
    ): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("React Studio Link", link))
            true
        }.getOrDefault(false)
    }

    fun shareDirectTextLink(
        context: Context,
        link: String,
        title: String
    ) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "$title\n$link")
            }
            context.startActivity(Intent.createChooser(intent, "Enviar link direto"))
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}
