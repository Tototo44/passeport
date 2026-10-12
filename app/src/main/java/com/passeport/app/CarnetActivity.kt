package com.passeport.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Le Carnet : taux du jour, montants gardés et réglage du mode Précis. */
class CarnetActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rateBig: TextView
    private lateinit var rateDate: TextView
    private lateinit var history: LinearLayout
    private lateinit var historyEmpty: TextView
    private lateinit var historyClear: TextView
    private lateinit var deeplKey: EditText
    private lateinit var deeplStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_carnet)
        prefs = Prefs(this)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.updatePadding(top = bars.top, bottom = maxOf(bars.bottom, ime))
            insets
        }

        rateBig = findViewById(R.id.rateBig)
        rateDate = findViewById(R.id.rateDate)
        history = findViewById(R.id.history)
        historyEmpty = findViewById(R.id.historyEmpty)
        historyClear = findViewById(R.id.historyClear)
        deeplKey = findViewById(R.id.deeplKey)
        deeplStatus = findViewById(R.id.deeplStatus)

        findViewById<TextView>(R.id.today).text = Fmt.today()
        findViewById<TextView>(R.id.version).text = "Passeport · version ${versionName()}"

        findViewById<View>(R.id.goTranslate).setOnClickListener {
            startActivity(Intent(this, TranslateActivity::class.java))
        }
        findViewById<View>(R.id.goConvert).setOnClickListener {
            startActivity(Intent(this, ConvertActivity::class.java))
        }
        findViewById<View>(R.id.rateRefresh).setOnClickListener { refreshRate() }
        historyClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Effacer tous les montants gardés ?")
                .setPositiveButton("Effacer") { _, _ ->
                    prefs.clearHistory()
                    renderHistory()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }

        findViewById<View>(R.id.deeplCreate).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.deepl.com/pro-api")))
        }
        deeplKey.setText(prefs.deeplKey)
        deeplStatus.text = if (prefs.deeplKey.isBlank()) "Aucune clé pour l’instant." else "Clé enregistrée."
        findViewById<View>(R.id.deeplSave).setOnClickListener { saveKey() }

        if (prefs.rate == null) refreshRate()
    }

    override fun onResume() {
        super.onResume()
        renderRate()
        renderHistory()
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun renderRate(message: String? = null) {
        val r = prefs.rate
        if (r == null) {
            rateBig.text = "1 € = … PHP"
            rateDate.text = message ?: "Pas encore de taux : connecte-toi à Internet."
        } else {
            rateBig.text = "1 € = ${Fmt.money(r.phpPerEur)} PHP"
            val source = if (r.source == "BCE") " · Banque centrale européenne" else ""
            rateDate.text = message ?: "Taux du ${Fmt.isoDay(r.day)}$source"
        }
    }

    private fun refreshRate() {
        renderRate("Mise à jour…")
        lifecycleScope.launch {
            try {
                prefs.rate = Rates.fetch()
                renderRate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                renderRate()
                rateDate.append("\nImpossible de joindre Internet pour l’instant.")
            }
        }
    }

    private fun renderHistory() {
        history.removeAllViews()
        val items = prefs.history()
        historyEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        historyClear.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        val mono = ResourcesCompat.getFont(this, R.font.plex_mono_regular)
        val monoBold = ResourcesCompat.getFont(this, R.font.plex_mono_semibold)
        val soft = ContextCompat.getColor(this, R.color.soft)
        val ink = ContextCompat.getColor(this, R.color.ink)
        val pad = (8 * resources.displayMetrics.density).toInt()

        for (k in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, pad, 0, pad)
            }
            row.addView(TextView(this).apply {
                text = Fmt.dayTime(k.time)
                typeface = mono
                textSize = 12f
                setTextColor(soft)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(this).apply {
                text = if (k.fromEur) {
                    "${Fmt.money(k.eur)} € · ${Fmt.money(k.php)} PHP"
                } else {
                    "${Fmt.money(k.php)} PHP · ${Fmt.money(k.eur)} €"
                }
                typeface = monoBold
                textSize = 13f
                setTextColor(ink)
            })
            history.addView(row)
            history.addView(View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@CarnetActivity, R.color.line))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        }
    }

    private fun saveKey() {
        val key = deeplKey.text.toString().trim()
        prefs.deeplKey = key
        if (key.isEmpty()) {
            prefs.precise = false
            deeplStatus.text = "Clé retirée. Le mode Rapide reste disponible."
            return
        }
        deeplStatus.text = "Test de la clé…"
        lifecycleScope.launch {
            deeplStatus.text = try {
                val test = DeepL.translate(key, "Bonjour, comment ça va ?", Lang.FR)
                prefs.precise = true
                "Clé valide ✓ « Bonjour, comment ça va ? » → « $test ». Le mode Précis est activé."
            } catch (e: CancellationException) {
                throw e
            } catch (e: DeepL.Problem) {
                e.userMessage
            } catch (e: Exception) {
                "Clé enregistrée, mais pas d’Internet pour la tester."
            }
        }
    }
}
