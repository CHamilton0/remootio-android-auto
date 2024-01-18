package chamilton0.remootioandroidws.auto

import android.content.Context
import android.util.Base64
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
    var settingHelper = SavedData(getCarContext())

    init {
        lifecycle.addObserver(this)
        println(settingHelper.getSetting1())
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
        val sharedPreference =
            carContext.getSharedPreferences("remootio-preferences", Context.MODE_PRIVATE)
        println(sharedPreference.getString("garage_ip_alias", null))
        if (door == "Garage Door") {
            val ip = sharedPreference.getString("garage_ip_alias", null)
            val auth = sharedPreference.getString("garage_api_auth_key_alias", null)
            val secret = sharedPreference.getString("garage_api_secret_key_alias", null)
            val garageIp = keystore.decrypt("garage_ip_alias", Base64.decode(ip, Base64.DEFAULT))
            val garageAuth =
                keystore.decrypt("garage_api_auth_key_alias", Base64.decode(auth, Base64.DEFAULT))
            val garageSecret = keystore.decrypt(
                "garage_api_secret_key_alias", Base64.decode(secret, Base64.DEFAULT)
            )

            client = RemootioClient(
                URI(garageIp), garageAuth, garageSecret
            )
        } else {
            val ip = sharedPreference.getString("gate_ip_alias", "")
            val auth = sharedPreference.getString("gate_api_auth_key_alias", "")
            val secret = sharedPreference.getString("gate_api_secret_key_alias", "")
            val gateIp = keystore.decrypt("gate_ip_alias", Base64.decode(ip, Base64.DEFAULT))
            val gateAuth =
                keystore.decrypt("gate_api_auth_key_alias", Base64.decode(auth, Base64.DEFAULT))
            val gateSecret = keystore.decrypt(
                "gate_api_secret_key_alias", Base64.decode(secret, Base64.DEFAULT)
            )

            client = RemootioClient(
                URI(gateIp), gateAuth, gateSecret
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