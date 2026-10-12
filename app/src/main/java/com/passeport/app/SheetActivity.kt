package com.passeport.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Base commune aux deux tiroirs : un panneau qui monte du bas de l'écran,
 * par-dessus ce qui est affiché, avec le clavier déjà sorti.
 */
abstract class SheetActivity : AppCompatActivity() {
    private lateinit var sheet: View
    private lateinit var dim: View
    private var basePaddingBottom = 0
    private var closing = false
    /** Mis à vrai quand on ouvre le Carnet, pour ne pas fermer pendant la transition. */
    protected var leavingOnPurpose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    protected fun setupSheet(root: View, dim: View, sheet: View) {
        this.dim = dim
        this.sheet = sheet
        basePaddingBottom = sheet.paddingBottom

        // Le panneau remonte au-dessus du clavier et de la barre de navigation.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            sheet.updatePadding(bottom = basePaddingBottom + maxOf(ime, bars))
            insets
        }

        dim.setOnClickListener { dismiss() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dismiss()
        })

        dim.alpha = 0f
        sheet.visibility = View.INVISIBLE
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.visibility = View.VISIBLE
            sheet.animate().translationY(0f).setDuration(240).setInterpolator(DecelerateInterpolator(2f)).start()
            dim.animate().alpha(1f).setDuration(200).start()
        }
    }

    protected fun showKeyboard(field: EditText) {
        field.requestFocus()
        field.postDelayed({
            WindowCompat.getInsetsController(window, field).show(WindowInsetsCompat.Type.ime())
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    /** Referme le tiroir en le faisant redescendre. */
    fun dismiss() {
        if (closing) return
        closing = true
        currentFocus?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(it.windowToken, 0)
        }
        dim.animate().alpha(0f).setDuration(180).start()
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(180).withEndAction {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.start()
    }

    /** Un tiroir quitté (bouton d'accueil, autre appli) se referme pour repartir propre. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing && !leavingOnPurpose) finish()
    }

    /** Colore un bouton à deux choix (Rapide/Précis, € → PHP / PHP → €). */
    protected fun paintSegments(on: TextView, off: TextView) {
        (on.parent as? View)?.clipToOutline = true
        on.setBackgroundColor(ContextCompat.getColor(this, R.color.navy))
        on.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        off.setBackgroundColor(0)
        off.setTextColor(ContextCompat.getColor(this, R.color.navy))
    }

    protected fun openCarnet() {
        leavingOnPurpose = true
        startActivity(
            android.content.Intent(this, CarnetActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
