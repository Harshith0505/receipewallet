package com.recipewallet.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PantryItem(val id: String, val name: String, val amount: String = "")

/** Local pantry entries for the page prototype. Cloud sync and recipe matching come next. */
internal class PantryStore(context: Context) {
    private val preferences = context.getSharedPreferences("pantry-items", Context.MODE_PRIVATE)
    fun read(): List<PantryItem> {
        val rows = JSONArray(preferences.getString("items", "[]"))
        return List(rows.length()) { index -> rows.getJSONObject(index).let {
            PantryItem(it.getString("id"), it.getString("name"), it.optString("amount"))
        } }
    }
    fun write(items: List<PantryItem>) {
        require(items.size <= 100) { "Keep up to 100 ingredients in this preview." }
        val rows = JSONArray()
        items.forEach {
            require(it.name.isNotBlank() && it.name.length <= 80 && it.amount.length <= 40) { "Add an ingredient name and a short amount." }
            rows.put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount))
        }
        check(preferences.edit().putString("items", rows.toString()).commit()) { "Couldn’t save your pantry. Please try again." }
    }
}
