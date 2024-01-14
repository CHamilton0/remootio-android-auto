package chamilton0.remootioandroidws.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import chamilton0.remootioandroidws.BuildConfig
import chamilton0.remootioandroidws.shared.RemootioClient
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

        // TODO: Get the keys from the keystore

        if (door == "Garage Door") {
            client = RemootioClient(
                URI(BuildConfig.GARAGE_REMOOTIO_URI),
                BuildConfig.GARAGE_API_AUTH_KEY,
                BuildConfig.GARAGE_API_SECRET_KEY
            )
        } else {
            client = RemootioClient(
                URI(BuildConfig.GATE_REMOOTIO_URI),
                BuildConfig.GATE_API_AUTH_KEY,
                BuildConfig.GATE_API_SECRET_KEY
            )
        }

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