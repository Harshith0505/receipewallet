package com.recipewallet.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PersonalRecipe(
    val id: String,
    val name: String,
    val ingredients: List<String>,
    val prepMinutes: Int? = null
)

/** Personal recipes stay on this device and do not change the priced demo catalog. */
internal class PersonalRecipeStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("personal-recipes", Context.MODE_PRIVATE)

    fun read(): List<PersonalRecipe> = runCatching {
        val rows = JSONArray(preferences.getString("recipes", "[]"))
        List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val ingredients = row.getJSONArray("ingredients")
            PersonalRecipe(row.getString("id"), row.getString("name"),
                List(ingredients.length()) { ingredients.getString(it) },
                row.optInt("prepMinutes", 0).takeIf { it > 0 })
        }
    }.getOrDefault(emptyList())

    fun save(recipe: PersonalRecipe): List<PersonalRecipe> {
        require(recipe.name.isNotBlank() && recipe.name.length <= 80) { "Add a recipe name (up to 80 characters)." }
        require(recipe.ingredients.isNotEmpty() && recipe.ingredients.size <= 40) { "Add between 1 and 40 ingredients, one per line." }
        require(recipe.ingredients.all { it.isNotBlank() && it.length <= 200 }) { "Keep each ingredient under 200 characters." }
        require(recipe.prepMinutes == null || recipe.prepMinutes in 1..999) { "Enter a prep time from 1 to 999 minutes, or leave it blank." }
        val rows = read().toMutableList()
        val index = rows.indexOfFirst { it.id == recipe.id }
        if (index >= 0) rows[index] = recipe else rows.add(0, recipe)
        write(rows)
        return rows
    }

    fun remove(id: String) { write(read().filterNot { it.id == id }) }

    private fun write(recipes: List<PersonalRecipe>) {
        val rows = JSONArray()
        recipes.forEach { recipe -> rows.put(JSONObject().put("id", recipe.id).put("name", recipe.name)
            .put("ingredients", JSONArray(recipe.ingredients))
            .put("prepMinutes", recipe.prepMinutes ?: JSONObject.NULL)) }
        check(preferences.edit().putString("recipes", rows.toString()).commit()) {
            "Couldn’t save on this tablet. Please try again."
        }
    }
}
