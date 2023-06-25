package com.example.remootioandroidws

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*

class RemootioScreen(carContext: CarContext?) : Screen(carContext!!) {
    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Option 1").build())
            .addItem(Row.Builder().setTitle("Option 2").build())
            .addItem(Row.Builder().setTitle("Option 2").build())
            .addItem(Row.Builder().setTitle("Option 4").build())
            .setOnSelectedListener { index: Int -> onSelected(index) }
            .build()
        templateBuilder.addSectionedList(
            SectionedItemList.create(radioList, "Sample list")
        )
        return templateBuilder.setTitle("List Selection Demo").build()
    }

    private fun onSelected(index: Int) {
        CarToast.makeText(carContext, "Changed selection to index: $index", CarToast.LENGTH_LONG)
            .show()
    }
}