package com.wmods.wppenhacer.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity

class UiAdblockerManageActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private val rules = mutableListOf<RuleItem>()
    private lateinit var adapter: RulesAdapter

    data class RuleItem(val value: String, val isSelector: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ui_adblocker_manage)

        prefs = getSharedPreferences(BuildConfig.APPLICATION_ID + "_preferences", Context.MODE_PRIVATE)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Blocked Elements"

        loadRules()

        val recycler = findViewById<RecyclerView>(R.id.recycler_rules)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = RulesAdapter()
        recycler.adapter = adapter
    }

    private fun loadRules() {
        rules.clear()
        val ids = prefs.getString("hidden_ui_element_ids", "") ?: ""
        val selectors = prefs.getString("hidden_ui_element_selectors", "") ?: ""

        ids.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            rules.add(RuleItem(it, false))
        }
        selectors.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            rules.add(RuleItem(it, true))
        }
    }

    private fun saveRules() {
        val ids = rules.filter { !it.isSelector }.joinToString("\n") { it.value }
        val selectors = rules.filter { it.isSelector }.joinToString("\n") { it.value }

        prefs.edit()
            .putString("hidden_ui_element_ids", ids)
            .putString("hidden_ui_element_selectors", selectors)
            .apply()

        try {
            val prefFile = java.io.File(applicationInfo.dataDir, "shared_prefs/" + BuildConfig.APPLICATION_ID + "_preferences.xml")
            prefFile.setReadable(true, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    inner class RulesAdapter : RecyclerView.Adapter<RulesAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textValue: TextView = view.findViewById(R.id.text_rule_value)
            val textType: TextView = view.findViewById(R.id.text_rule_type)
            val btnDelete: ImageView = view.findViewById(R.id.button_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ui_adblocker_rule, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rule = rules[position]
            holder.textValue.text = rule.value
            holder.textType.text = if (rule.isSelector) "Selector Match" else "Exact ID Match"

            holder.btnDelete.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    rules.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    saveRules()
                }
            }
        }

        override fun getItemCount() = rules.size
    }
}
