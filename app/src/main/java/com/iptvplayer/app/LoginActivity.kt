package com.iptvplayer.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Prefs.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val spinner = findViewById<Spinner>(R.id.spType)
        val xtreamBox = findViewById<View>(R.id.boxXtream)
        val m3uBox = findViewById<View>(R.id.boxM3u)
        val host = findViewById<EditText>(R.id.etHost)
        val user = findViewById<EditText>(R.id.etUser)
        val pass = findViewById<EditText>(R.id.etPass)
        val m3u = findViewById<EditText>(R.id.etM3u)
        val btn = findViewById<Button>(R.id.btnConnect)
        val progress = findViewById<ProgressBar>(R.id.progress)
        val error = findViewById<TextView>(R.id.tvError)

        spinner.adapter = ArrayAdapter.createFromResource(
            this, R.array.conn_types, android.R.layout.simple_spinner_dropdown_item
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                xtreamBox.visibility = if (pos == 0) View.VISIBLE else View.GONE
                m3uBox.visibility = if (pos == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        btn.setOnClickListener {
            error.visibility = View.GONE
            val xtream = spinner.selectedItemPosition == 0

            if (xtream) {
                var h = host.text.toString().trim()
                if (h.isEmpty() || user.text.isEmpty() || pass.text.isEmpty()) {
                    showError(error, "Remplissez le serveur, l'utilisateur et le mot de passe.")
                    return@setOnClickListener
                }
                if (!h.startsWith("http", true)) h = "http://$h"
                Prefs.save(this, Prefs.TYPE_XTREAM, h, user.text.toString(),
                    pass.text.toString(), "")
                verifyAndEnter(progress, btn, error)
            } else {
                val link = m3u.text.toString().trim()
                if (!link.startsWith("http", true)) {
                    showError(error, "Entrez un lien M3U valide (commençant par http).")
                    return@setOnClickListener
                }
                Prefs.save(this, Prefs.TYPE_M3U, "", "", "", link)
                goMain()
            }
        }
    }

    private fun verifyAndEnter(progress: ProgressBar, btn: Button, error: TextView) {
        progress.visibility = View.VISIBLE
        btn.isEnabled = false
        lifecycleScope.launch {
            val err = withContext(Dispatchers.IO) { XtreamApi(this@LoginActivity).login() }
            progress.visibility = View.GONE
            btn.isEnabled = true
            if (err == null) goMain() else {
                Prefs.logout(this@LoginActivity)
                showError(error, err)
            }
        }
    }

    private fun showError(tv: TextView, msg: String) {
        tv.text = msg
        tv.visibility = View.VISIBLE
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
