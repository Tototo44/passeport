package com.passeport.app

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Les deux langues de l'appli. */
enum class Lang(val label: String) {
    FR("FR"), EN("EN");

    val other: Lang get() = if (this == FR) EN else FR
}

/** Attend le résultat d'une tâche Google (ML Kit) sans bloquer l'écran. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Mise en forme des nombres et des dates, à la française. */
object Fmt {
    private val locale = Locale.FRANCE

    fun money(value: Double): String {
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return nf.format(value).replace(' ', ' ').replace(' ', ' ')
    }

    /** Lit un montant tapé : accepte la virgule ou le point. */
    fun parse(text: String): Double? {
        val t = text.replace(" ", "").replace(',', '.')
        if (t.isEmpty() || t == "." || t.count { it == '.' } > 1) return null
        return t.toDoubleOrNull()
    }

    /** "2026-10-12" -> "12/10/2026" */
    fun isoDay(iso: String): String {
        val parts = iso.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
    }

    fun dayTime(millis: Long): String =
        SimpleDateFormat("dd/MM HH:mm", locale).format(Date(millis))

    fun today(): String =
        SimpleDateFormat("dd MMM yyyy", locale).format(Date()).uppercase(locale).replace(".", "")
}
