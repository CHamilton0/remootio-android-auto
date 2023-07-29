package chamilton0.remootioandroidws

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import java.net.URI

class RemootioDeviceScreen(carContext: CarContext?) : Screen(carContext!!) {
    private var title: String = ""
    private lateinit var client: RemootioClient

    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Activate").build())
            .setOnSelectedListener { index: Int -> onSelected(index) }
            .build()
        templateBuilder.addSectionedList(
            SectionedItemList.create(radioList, "Switch state")
        )
        return templateBuilder.setTitle(title).setHeaderAction(Action.BACK).build()
    }

    private fun onSelected(index: Int) {
        CarToast.makeText(carContext, "Changed selection to index: $index", CarToast.LENGTH_LONG)
            .show()
    }

    fun setDoor(door: String) {
        title = door
        val apiAuthKey = System.getenv("REMOOTIO_API_AUTH_KEY")
        val apiSecretKey = System.getenv("REMOOTIO_API_SECRET_KEY")

        client = RemootioClient(URI("ws://101.175.67.110:8080"), apiAuthKey, apiSecretKey)
        client.connect()
        println(client.connection.isOpen)
    }

    fun queryDoor() {
        client.sendQuery()
    }
}