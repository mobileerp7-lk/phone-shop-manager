package app.phoneshop.manager

import android.app.Application
import app.phoneshop.manager.data.AppDatabase
import app.phoneshop.manager.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneShopApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.build(this))
        CoroutineScope(Dispatchers.IO).launch { repo.seedIfEmpty() }
    }
}
