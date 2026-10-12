package com.passeport.app

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/** Traduction « Rapide » : tout se passe sur le téléphone, sans Internet. */
class OfflineTranslator {
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build()
    )
    private val frToEn = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.FRENCH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )
    private val enToFr = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.FRENCH)
            .build()
    )

    /** Télécharge les dictionnaires FR et EN la première fois (il faut Internet une fois). */
    suspend fun prepare() {
        val conditions = DownloadConditions.Builder().build()
        frToEn.downloadModelIfNeeded(conditions).await()
        enToFr.downloadModelIfNeeded(conditions).await()
    }

    /** Devine si le texte est en français ou en anglais. Null si on ne sait pas. */
    suspend fun detect(text: String): Lang? {
        if (text.any { it in FRENCH_LETTERS }) return Lang.FR
        if (text.count { it.isLetter() } < 3) return null
        var fr = 0f
        var en = 0f
        for (lang in identifier.identifyPossibleLanguages(text).await()) {
            when (lang.languageTag) {
                "fr" -> fr = lang.confidence
                "en" -> en = lang.confidence
            }
        }
        if (fr == 0f && en == 0f) return null
        return if (fr >= en) Lang.FR else Lang.EN
    }

    suspend fun translate(text: String, from: Lang): String =
        (if (from == Lang.FR) frToEn else enToFr).translate(text).await()

    fun close() {
        identifier.close()
        frToEn.close()
        enToFr.close()
    }

    private companion object {
        const val FRENCH_LETTERS = "éèêëàâçùûôîïœÉÈÊÀÇÔÎ"
    }
}
