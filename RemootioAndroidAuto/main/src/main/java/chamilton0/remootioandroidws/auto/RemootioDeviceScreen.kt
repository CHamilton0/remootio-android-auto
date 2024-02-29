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
import java.lang.Exception
import java.util.concurrent.TimeUnit

class RemootioDeviceScreen(carContext: CarContext?) : Screen(carContext!!),
    DefaultLifecycleObserver {
    private var title: String = ""
    private var client: RemootioClient? = null
    private var state: String = ""
    private var settingHelper = SavedData(getCarContext().applicationContext)

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val templateBuilder = ListTemplate.Builder()
        val radioList = ItemList.Builder().addItem(Row.Builder().setTitle("Activate").build())
            .setOnSelectedListener { onSelected() }.build()
        templateBuilder.addSectionedList(
            SectionedItemList.create(radioList, "Switch state")
        )

        return templateBuilder.setTitle("$title is $state").setHeaderAction(Action.BACK).build()
    }

    private fun onSelected() {
        CarToast.makeText(
            carContext,
            "Triggering $title",
            CarToast.LENGTH_LONG
        )
            .show()
        triggerDoor()
    }

    fun setDoor(door: String) {
        title = door
        var ip: String?
        var auth: String?
        var secret: String?

        if (door == "Garage Door") {
            ip = settingHelper.getGarageIp()
            auth = settingHelper.getGarageAuth()
            secret = settingHelper.getGarageSecret()
        } else {
            ip = settingHelper.getGateIp()
            auth = settingHelper.getGateAuth()
            secret = settingHelper.getGateSecret()
        }

        if (ip.isNullOrEmpty() || auth.isNullOrEmpty() || secret.isNullOrEmpty()) {
            CarToast.makeText(
                carContext,
                "$title is not set up correctly",
                CarToast.LENGTH_LONG
            )
                .show()
            throw Exception("$title is not set up correctly")
            return
        }

        try {
            client = RemootioClient(ip, auth, secret, true)
            client?.connectBlocking(10, TimeUnit.SECONDS)

            client?.addFrameStateChangeListener(object : RemootioClient.StateChangeListener {
                override fun onFrameStateChanged(newState: String) {
                    CarToast.makeText(
                        carContext,
                        "$title is now $newState",
                        CarToast.LENGTH_LONG
                    )
                        .show()
                    state = newState
                }
            })

            client?.addErrorListener(object : RemootioClient.ErrorListener {
                override fun onError(error: Error) {
                    CarToast.makeText(
                        carContext,
                        error.message.toString(),
                        CarToast.LENGTH_LONG
                    )
                        .show()
                }
            })

            queryDoor()
        } catch (e: Exception) {
            CarToast.makeText(
                carContext,
                e.toString(),
                CarToast.LENGTH_LONG
            )
                .show()
        }

        queryDoor()
    }

    private fun queryDoor() {
        client?.sendQuery()
    }

    private fun triggerDoor() {
        client?.sendTriggerAction()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        println("Stopping android auto device screen")
        client?.disconnect()
    }
}