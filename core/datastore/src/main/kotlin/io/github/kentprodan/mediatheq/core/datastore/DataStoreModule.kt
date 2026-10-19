package io.github.kentprodan.mediatheq.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface DataStoreModule {
    @Binds
    fun tokenCipher(cipher: AndroidKeystoreTokenCipher): TokenCipher

    companion object {
        /** One instance per file: two DataStores on the same file throw at runtime. */
        @Provides
        @Singleton
        fun accountDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("account") }
    }
}
