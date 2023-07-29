package chamilton0.remootioandroidws

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.net.URI

class RemootioDeviceScreen(carContext: CarContext?) : Screen(carContext!!),
    DefaultLifecycleObserver {
    private var title: String = ""
    private lateinit var client: RemootioClient
    private var state: String = ""

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Activate").build())
            .setOnSelectedListener { index: Int -> onSelected(index) }
            .build()
        templateBuilder.addSectionedList(
            SectionedItemList.create(radioList, "Switch state")
        )

        return templateBuilder.setTitle("$title is $state").setHeaderAction(Action.BACK).build()
    }

    private fun onSelected(index: Int) {
        CarToast.makeText(carContext, "Changed selection to index: $index", CarToast.LENGTH_LONG)
            .show()
        triggerDoor()
    }

    fun setDoor(door: String) {
        title = door

        client = RemootioClient(
            URI(BuildConfig.REMOOTIO_URI),
            BuildConfig.API_AUTH_KEY,
            BuildConfig.API_SECRET_KEY
        )
        client.connect()
        Thread.sleep(1_000)

        state = client.state
    }

    private fun queryDoor() {
        client.sendQuery()
        Thread.sleep(1_000)

        state = client.state
    }

    private fun triggerDoor() {
        client.sendTriggerAction()
        queryDoor()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        client.close()
    }
}