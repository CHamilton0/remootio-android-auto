package chamilton0.remootioandroidws.auto

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.util.Base64
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import chamilton0.remootioandroidws.main.DataService
import chamilton0.remootioandroidws.shared.Keystore
import chamilton0.remootioandroidws.shared.RemootioClient
import chamilton0.remootioandroidws.shared.SavedData
import java.net.URI

class RemootioDeviceScreen(carContext: CarContext?) : Screen(carContext!!),
    DefaultLifecycleObserver {
    private var title: String = ""
    private lateinit var client: RemootioClient
    private var state: String = ""
    private val keystore by lazy { Keystore() }
    var settingHelper = SavedData(getCarContext().applicationContext)

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
        if (door == "Garage Door") {
            val ip = settingHelper.getGarageIp()
            println(ip)
            val auth = settingHelper.getGarageAuth()
            println(auth)
            val secret = settingHelper.getGarageSecret()
            println(secret) // TODO: Why not let the helper do all decoding
            val garageIp = Base64.decode(ip, Base64.DEFAULT).toString()
            val garageAuth = Base64.decode(auth, Base64.DEFAULT).toString()
            val garageSecret = Base64.decode(secret, Base64.DEFAULT).toString()

            client = RemootioClient(
                ip.toString(), auth.toString(), secret.toString()
            )
        } else {
            val ip = settingHelper.getGateIp()
            val auth = settingHelper.getGateAuth()
            val secret = settingHelper.getGateSecret()
            val gateIp = keystore.decrypt("gate_ip_alias", Base64.decode(ip, Base64.DEFAULT))
            val gateAuth =
                keystore.decrypt("gate_api_auth_key_alias", Base64.decode(auth, Base64.DEFAULT))
            val gateSecret = keystore.decrypt(
                "gate_api_secret_key_alias", Base64.decode(secret, Base64.DEFAULT)
            )

            client = RemootioClient(
                gateIp, gateAuth, gateSecret
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