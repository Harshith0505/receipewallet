package com.recipewallet.app

import org.junit.Assert.*
import org.junit.Test

class PantryMergeTest {
    @Test fun rescanningKeepsConfirmedAmountsAndAddsOnlyNewNames() {
        val milk=PantryItem("saved", "Milk", "Half a carton")
        val result=mergePantry(listOf(milk),listOf(PantryItem("scan-1"," MILK ","1 gallon"),PantryItem("scan-2","Green   onions",""),PantryItem("scan-3","green onions","1 bunch")))
        assertEquals(listOf(milk,PantryItem("scan-2","Green   onions","")),result)
        assertEquals("Half a carton",result.first().amount)
    }
}
