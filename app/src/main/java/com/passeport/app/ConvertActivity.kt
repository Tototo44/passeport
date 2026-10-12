package com.passeport.app

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Tiroir « Convertir » : euros ⇄ pesos philippins au taux du jour. */
class ConvertActivity : SheetActivity() {

    private lateinit var prefs: Prefs
    private lateinit var amount: EditText
    private lateinit var result: TextView
    private lateinit var stamp: TextView
    private lateinit var rateInfo: TextView
    private lateinit var rateMeta: TextView
    private lateinit var inCur: TextView
    private lateinit var outCur: TextView
    private lateinit var dirEur: TextView
    private lateinit var dirPhp: TextView
    private lateinit var keep: TextView

    private var eurToPhp = true
    private var fetching = false
    private var fetchFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sheet_convert)
        prefs = Prefs(this)

        amount = findViewById(R.id.amount)
        result = findViewById(R.id.result)
        stamp = findViewById(R.id.stamp)
        rateInfo = findViewById(R.id.rateInfo)
        rateMeta = findViewById(R.id.rateMeta)
        inCur = findViewById(R.id.inCur)
        outCur = findViewById(R.id.outCur)
        dirEur = findViewById(R.id.dirEur)
        dirPhp = findViewById(R.id.dirPhp)
        keep = findViewById(R.id.keep)

        setupSheet(findViewById(R.id.root), findViewById(R.id.dim), findViewById(R.id.sheet))

        amount.doAfterTextChanged { compute() }
        dirEur.setOnClickListener { setDirection(true) }
        dirPhp.setOnClickListener { setDirection(false) }
        keep.setOnClickListener { keepAmount() }
        findViewById<View>(R.id.openCarnet).setOnClickListener { openCarnet() }

        eurToPhp = prefs.eurToPhp
        renderDirection()
        renderRate()
        compute()
        showKeyboard(amount)
        refreshRateIfOld()
    }

    private fun refreshRateIfOld() {
        val r = prefs.rate
        if (r != null && System.currentTimeMillis() - r.fetchedAt < Rates.MAX_AGE_MS) return
        fetching = true
        renderRate()
        lifecycleScope.launch {
            try {
                prefs.rate = Rates.fetch()
                fetchFailed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fetchFailed = true
            }
            fetching = false
            renderRate()
            compute()
        }
    }

    private fun setDirection(fromEur: Boolean) {
        if (fromEur == eurToPhp) return
        eurToPhp = fromEur
        prefs.eurToPhp = fromEur
        renderDirection()
        renderRate()
        compute()
    }

    private fun renderDirection() {
        stamp.text = if (eurToPhp) "EUR" else "PHP"
        inCur.text = if (eurToPhp) "€" else "PHP"
        outCur.text = if (eurToPhp) "PHP" else "€"
        if (eurToPhp) paintSegments(dirEur, dirPhp) else paintSegments(dirPhp, dirEur)
    }

    private fun renderRate() {
        val r = prefs.rate
        val target = if (eurToPhp) "PHP" else "EUR"
        if (r == null) {
            rateInfo.text = "→ $target"
            rateMeta.text = when {
                fetching -> "Recherche du taux du jour…"
                else -> "Pas encore de taux : connecte-toi à Internet une fois."
            }
            return
        }
        rateInfo.text = "→ $target · 1 € = ${Fmt.money(r.phpPerEur)}"
        rateMeta.text = when {
            fetching -> "Mise à jour du taux…"
            fetchFailed -> "Hors ligne : taux du ${Fmt.isoDay(r.day)} gardé en mémoire"
            else -> "Taux du ${Fmt.isoDay(r.day)}"
        }
    }

    /** Renvoie (euros, pesos) pour le montant tapé, ou null. */
    private fun currentPair(): Pair<Double, Double>? {
        val value = Fmt.parse(amount.text.toString()) ?: return null
        val rate = prefs.rate?.phpPerEur ?: return null
        return if (eurToPhp) value to value * rate else value / rate to value
    }

    private fun compute() {
        val pair = currentPair()
        result.text = when {
            pair == null -> ""
            eurToPhp -> Fmt.money(pair.second)
            else -> Fmt.money(pair.first)
        }
        keep.text = "Garder ✓"
    }

    private fun keepAmount() {
        val pair = currentPair() ?: return
        prefs.addKept(Kept(System.currentTimeMillis(), pair.first, pair.second, eurToPhp))
        keep.text = "Gardé !"
        Toast.makeText(this, "Montant noté dans le Carnet", Toast.LENGTH_SHORT).show()
    }
}
