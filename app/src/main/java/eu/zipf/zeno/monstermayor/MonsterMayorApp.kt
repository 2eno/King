package eu.zipf.zeno.monstermayor

import android.app.Application
import eu.zipf.zeno.monstermayor.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MonsterMayorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MonsterMayorApp)
            modules(appModule)
        }
    }
}
