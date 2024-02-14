package chamilton0.remootioandroidws.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import chamilton0.remootioandroidws.shared.RemootioClient
import chamilton0.remootioandroidws.shared.SavedData

class RemootioDeviceScreen(carContext: CarContext?) : Screen(carContext!!),
    DefaultLifecycleObserver {
    private var title: String = ""
    private lateinit var client: RemootioClient
    private var state: String = ""
    private var settingHelper = SavedData(getCarContext().applicationContext)

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder().addItem(Row.Builder().setTitle("Activate").build())
            .setOnSelectedListener { index: Int -> onSelected(index) }.build()
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
        var ip = ""
        var auth = ""
        var secret = ""

        if (door == "Garage Door") {
            ip = settingHelper.getGarageIp().toString()
            auth = settingHelper.getGarageAuth().toString()
            secret = settingHelper.getGarageSecret().toString()
        } else {
            ip = settingHelper.getGateIp().toString()
            auth = settingHelper.getGateAuth().toString()
            secret = settingHelper.getGateSecret().toString()
        }

        client.addFrameStateChangeListener(object : RemootioClient.StateChangeListener {
            override fun onFrameStateChanged(newState: String) {
                state = newState
            }
        })

        client = RemootioClient(ip, auth, secret, true, 10000)
        client.connectBlocking()

        queryDoor()
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
        println("Stopping android auto device screen")
        client.disconnect()
    }
}