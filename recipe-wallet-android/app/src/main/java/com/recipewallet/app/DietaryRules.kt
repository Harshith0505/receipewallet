package com.recipewallet.app

/** Ingredient-name screening only; unknown product labels and cross-contact need review. */
internal fun excludedIngredientNames(names:List<String>,avoid:String):List<String>{
 val aliases=mapOf("milk" to listOf("parmesan","cheese","milk"),"dairy" to listOf("parmesan","cheese","milk"),"wheat" to listOf("noodles","rigatoni","soy sauce"),"gluten" to listOf("noodles","rigatoni","soy sauce"),"soy" to listOf("soy sauce"),"sesame" to listOf("sesame"),"peanuts" to listOf("peanut","groundnut"),"tree nuts" to listOf("almond","cashew","walnut","pecan","pistachio","hazelnut","macadamia"),"eggs" to listOf("egg"))
 val terms=avoid.lowercase(java.util.Locale.ROOT).split(',').map{it.trim()}.filter{it.isNotEmpty()}
 return names.filter{name->terms.any{term->(aliases[term]?:listOf(term)).any{name.lowercase(java.util.Locale.ROOT).contains(it)}}}
}
