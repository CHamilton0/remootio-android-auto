package chamilton0.remootioandroidws.main;

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

interface DataServiceCallback {
    fun onDataServiceConnected(dataService: DataService)
}

class DataService : Service() {

    private var userInput: String = ""

    inner class LocalBinder : Binder() {
        fun getService(): DataService = this@DataService
    }

    private val dataServiceCallback = ArrayList<DataServiceCallback>()

    override fun onBind(intent: Intent): IBinder? {
        return LocalBinder()
    }

    fun addDataServiceCallback(callback: DataServiceCallback) {
        dataServiceCallback.add(callback)
    }

    fun removeDataServiceCallback(callback: DataServiceCallback) {
        dataServiceCallback.remove(callback)
    }

    private fun notifyDataServiceConnected() {
        for (callback in dataServiceCallback) {
            callback.onDataServiceConnected(this)
        }
    }

    fun setUserInput(input: String) {
        userInput = input
    }

    fun getUserInput(): String {
        return userInput
    }

    // Add other methods or logic as needed...

    companion object {
        // Add any constants or companion object members here...
    }
}
