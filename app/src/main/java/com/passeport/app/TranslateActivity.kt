package com.passeport.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tiroir « Traduire » : on tape, la traduction FR ⇄ EN suit en direct. */
class TranslateActivity : SheetActivity() {

    private enum class Models { PREPARING, READY, FAILED }

    private lateinit var prefs: Prefs
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var note: TextView
    private lateinit var stamp: TextView
    private lateinit var langInfo: TextView
    private lateinit var modeFast: TextView
    private lateinit var modePrecise: TextView

    private val engine = OfflineTranslator()
    private var models = Models.PREPARING
    private var job: Job? = null
    /** Sens choisi à la main en touchant le tampon (sinon détection automatique). */
    private var forced: Lang? = null
    private var lastSource = Lang.FR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_translate)
        prefs = Prefs(this)

        input = findViewById(R.id.input)
        output = findViewById(R.id.output)
        note = findViewById(R.id.note)
        stamp = findViewById(R.id.stamp)
        langInfo = findViewById(R.id.langInfo)
        modeFast = findViewById(R.id.modeFast)
        modePrecise = findViewById(R.id.modePrecise)
        output.movementMethod = ScrollingMovementMethod()

        setupSheet(findViewById(R.id.root), findViewById(R.id.dim), findViewById(R.id.sheet))

        input.doAfterTextChanged {
            if (it.isNullOrBlank()) forced = null
            schedule()
        }
        // Toucher le tampon inverse le sens, au cas où la détection se trompe.
        val flip = View.OnClickListener {
            if (input.text.isNotBlank()) {
                forced = lastSource.other
                schedule(0)
            }
        }
        stamp.setOnClickListener(flip)
        langInfo.setOnClickListener(flip)

        modeFast.setOnClickListener { setPrecise(false) }
        modePrecise.setOnClickListener { setPrecise(true) }
        findViewById<View>(R.id.clear).setOnClickListener { input.setText("") }
        findViewById<View>(R.id.copy).setOnClickListener { copy() }
        findViewById<View>(R.id.openCarnet).setOnClickListener { openCarnet() }

        // Texte partagé ou envoyé depuis une autre appli
        intent?.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { input.setText(it) }

        renderMode()
        renderIdle()
        showKeyboard(input)
        prepareModels()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.close()
    }

    private fun prepareModels() {
        models = Models.PREPARING
        lifecycleScope.launch {
            models = try {
                engine.prepare()
                Models.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Models.FAILED
            }
            schedule(0)
        }
    }

    private fun setPrecise(on: Boolean) {
        if (on && prefs.deeplKey.isBlank()) {
            showNote("Pour le mode Précis, ajoute ta clé DeepL gratuite dans le Carnet (touche « Carnet » en haut).")
            return
        }
        prefs.precise = on
        renderMode()
        schedule(0)
    }

    private fun renderMode() {
        val precise = prefs.precise && prefs.deeplKey.isNotBlank()
        if (precise) paintSegments(modePrecise, modeFast) else paintSegments(modeFast, modePrecise)
    }

    private fun schedule(wait: Long = if (prefs.precise) 600L else 250L) {
        job?.cancel()
        job = lifecycleScope.launch {
            delay(wait)
            run()
        }
    }

    private suspend fun run() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            renderIdle()
            return
        }

        val detected: Lang? = try {
            engine.detect(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val source = forced ?: detected ?: lastSource
        lastSource = source
        stamp.text = source.label
        langInfo.text = (if (forced != null) "Choisi" else "Détecté") + " → " + source.other.label

        var problem: String? = null
        if (prefs.precise && prefs.deeplKey.isNotBlank()) {
            try {
                show(DeepL.translate(prefs.deeplKey, text, source), null)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeepL.Problem) {
                problem = e.userMessage + " Traduction rapide affichée."
            } catch (e: Exception) {
                problem = "Pas d’Internet : traduction rapide affichée."
            }
        }

        when (models) {
            Models.READY -> try {
                show(engine.translate(text, source), problem)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("", "La traduction hors ligne a échoué. Réessaie.")
            }
            Models.PREPARING -> show(
                "",
                "Préparation de la traduction hors ligne… Cela n’arrive qu’une fois et demande Internet.",
            )
            Models.FAILED -> {
                show(
                    "",
                    "Connecte-toi à Internet une fois : l’appli doit télécharger les dictionnaires français et anglais (environ 60 Mo).",
                )
                prepareModels()
            }
        }
    }

    private fun show(translation: String, message: String?) {
        output.text = translation
        output.scrollTo(0, 0)
        if (message == null) note.visibility = View.GONE else showNote(message)
    }

    private fun showNote(message: String) {
        note.text = message
        note.visibility = View.VISIBLE
    }

    private fun renderIdle() {
        stamp.text = "AUTO"
        langInfo.text = "FR ⇄ EN"
        output.text = ""
        note.visibility = if (models == Models.FAILED) View.VISIBLE else View.GONE
    }

    private fun copy() {
        val text = output.text.toString()
        if (text.isBlank()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Traduction", text))
        Toast.makeText(this, "Traduction copiée", Toast.LENGTH_SHORT).show()
    }
}
