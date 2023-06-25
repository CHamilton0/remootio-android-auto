package com.example.remootioandroidws

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*

class RemootioScreen(carContext: CarContext?) : Screen(carContext!!) {
    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Garage Door").build())
            .addItem(Row.Builder().setTitle("Gate").build())
            .setOnSelectedListener { index: Int -> onSelected(index) }
            .build()
        templateBuilder.addSectionedList(
            SectionedItemList.create(radioList, "Remootio Devices")
        )
        return templateBuilder.setTitle("Remootio").build()
    }

    private fun onSelected(index: Int) {
        this.screenManager.push(RemootioDeviceScreen(carContext))
    }
}