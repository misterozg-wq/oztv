package com.iptvplayer.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var grid: RecyclerView
    private lateinit var catGroup: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var search: EditText

    private val adapter = ItemAdapter { onItemClick(it) }
    private var current = Kind.LIVE
    private var categories: List<Category> = emptyList()
    private var allItems: List<Item> = emptyList()
    private var displayed: List<Item> = emptyList()
    private var selectedCat = ""

    private val adultCatIds = HashSet<String>()
    private var adultUnlocked = false

    private val isXtream by lazy { Prefs.type(this) == Prefs.TYPE_XTREAM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        catGroup = findViewById(R.id.catGroup)
        progress = findViewById(R.id.progress)
        tvEmpty = findViewById(R.id.tvEmpty)
        search = findViewById(R.id.etSearch)

        grid.layoutManager = GridLayoutManager(this, spanForScreen())
        grid.adapter = adapter

        val tabLive = findViewById<Button>(R.id.tabLive)
        val tabMovies = findViewById<Button>(R.id.tabMovies)
        val tabSeries = findViewById<Button>(R.id.tabSeries)

        if (!isXtream) {
            tabMovies.visibility = View.GONE
            tabSeries.visibility = View.GONE
        }

        tabLive.setOnClickListener { switchTab(Kind.LIVE) }
        tabMovies.setOnClickListener { switchTab(Kind.VOD) }
        tabSeries.setOnClickListener { switchTab(Kind.SERIES) }

        findViewById<ImageButton>(R.id.btnLogout).setOnClickListener {
            Prefs.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Recherche masquée par défaut : la loupe l'affiche/masque pour libérer de la place
        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            if (search.visibility == View.VISIBLE) {
                search.setText("")
                search.visibility = View.GONE
            } else {
                search.visibility = View.VISIBLE
                search.requestFocus()
            }
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener { changePinDialog() }

        switchTab(Kind.LIVE)
    }

    private fun isAdult(name: String): Boolean {
        val n = name.lowercase()
        return listOf("adult", "adulte", "xxx", "+18", "18+", "porn", "sexe", "sex ", "brazzers")
            .any { n.contains(it) }
    }

    /** Demande le code parental ; appelle [onOk] si correct. */
    private fun askPin(onOk: () -> Unit) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Code (défaut 0000)"
        }
        AlertDialog.Builder(this)
            .setTitle("Contenu adulte — code parental")
            .setView(input)
            .setPositiveButton("Valider") { _, _ ->
                if (input.text.toString() == Prefs.pin(this)) onOk()
                else Toast.makeText(this, "Code incorrect", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun changePinDialog() {
        val current = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Code actuel (défaut 0000)"
        }
        AlertDialog.Builder(this)
            .setTitle("Code parental")
            .setView(current)
            .setPositiveButton("Continuer") { _, _ ->
                if (current.text.toString() != Prefs.pin(this)) {
                    Toast.makeText(this, "Code actuel incorrect", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val neu = EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    hint = "Nouveau code (4 chiffres)"
                }
                AlertDialog.Builder(this)
                    .setTitle("Nouveau code parental")
                    .setView(neu)
                    .setPositiveButton("Enregistrer") { _, _ ->
                        val v = neu.text.toString()
                        if (v.length in 4..8) {
                            Prefs.setPin(this, v)
                            adultUnlocked = false
                            Toast.makeText(this, "Code enregistré", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(this, "4 à 8 chiffres", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /** Nombre de colonnes calculé d'après la largeur réelle (plus de chaînes en paysage). */
    private fun spanForScreen(): Int {
        val dp = resources.configuration.screenWidthDp
        return (dp / 105).coerceIn(3, 8)
    }

    private fun switchTab(kind: Kind) {
        current = kind
        selectedCat = ""
        search.setText("")
        loadCurrent()
    }

    private fun loadCurrent() {
        progress.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE
        adapter.submit(emptyList())
        lifecycleScope.launch {
            try {
                val (cats, items) = withContext(Dispatchers.IO) { fetch() }
                categories = cats
                allItems = items
                buildCategoryChips()
                applyFilter()
            } catch (e: Exception) {
                tvEmpty.text = "Erreur : ${e.message}"
                tvEmpty.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun fetch(): Pair<List<Category>, List<Item>> {
        if (!isXtream) {
            val r = M3uParser(this).load()
            return r.categories to r.items
        }
        val api = XtreamApi(this)
        return when (current) {
            Kind.LIVE -> api.liveCategories() to api.liveStreams("")
            Kind.VOD -> api.vodCategories() to api.vodStreams("")
            Kind.SERIES -> api.seriesCategories() to api.seriesList("")
        }
    }

    private fun buildCategoryChips() {
        catGroup.removeAllViews()
        adultCatIds.clear()
        categories.forEach { cat ->
            if (cat.id.isNotEmpty() && isAdult(cat.name)) adultCatIds.add(cat.id)
            val chip = Button(this).apply {
                text = if (cat.id in adultCatIds) "🔒 ${cat.name}" else cat.name
                isAllCaps = false
                textSize = 13f
                minHeight = 0
                minimumHeight = 0
                setBackgroundResource(R.drawable.chip_bg)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.text))
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 12
                layoutParams = lp
                setOnClickListener {
                    if (cat.id in adultCatIds && !adultUnlocked) {
                        askPin { adultUnlocked = true; selectedCat = cat.id; applyFilter() }
                    } else {
                        selectedCat = cat.id
                        applyFilter()
                    }
                }
            }
            catGroup.addView(chip)
        }
    }

    private fun applyFilter() {
        val q = search.text.toString().trim().lowercase()
        var list = allItems
        // Masque le contenu adulte tant que le code n'a pas été saisi
        if (!adultUnlocked) list = list.filter { it.categoryId !in adultCatIds }
        if (selectedCat.isNotEmpty()) list = list.filter { it.categoryId == selectedCat }
        if (q.isNotEmpty()) list = list.filter { it.name.lowercase().contains(q) }
        displayed = list
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty()) tvEmpty.text = "Aucun résultat"
    }

    private fun onItemClick(item: Item) {
        if (item.kind == Kind.SERIES) {
            openSeries(item)
        } else {
            // Zapping : toute la catégorie affichée devient la file de lecture
            val queue = displayed.filter { it.streamUrl != null }
            Playlist.items = queue
            Playlist.index = queue.indexOf(item).coerceAtLeast(0)
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }

    private fun openSeries(item: Item) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val eps = try {
                withContext(Dispatchers.IO) { XtreamApi(this@MainActivity).episodes(item.seriesId!!) }
            } catch (e: Exception) { emptyList() }
            progress.visibility = View.GONE
            if (eps.isEmpty()) {
                Toast.makeText(this@MainActivity, "Aucun épisode trouvé", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val labels = eps.map { "S${it.season} · ${it.title}" }.toTypedArray()
            AlertDialog.Builder(this@MainActivity)
                .setTitle(item.name)
                .setItems(labels) { _, which ->
                    val queue = eps.map {
                        Item(it.id, "${item.name} · S${it.season} ${it.title}", item.logo,
                            Kind.VOD, null, streamUrl = it.url)
                    }
                    Playlist.items = queue
                    Playlist.index = which
                    startActivity(Intent(this@MainActivity, PlayerActivity::class.java))
                }
                .show()
        }
    }
}
