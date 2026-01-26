/*
 * Mesh Rider Wave - Hilt DI Module
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.doodlelabs.meshriderwave.core.audio.MulticastAudioManager
import com.doodlelabs.meshriderwave.core.audio.OpusCodecManager
import com.doodlelabs.meshriderwave.core.audio.RTPPacketManager
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.network.Connector
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.core.network.NetworkTypeDetector
import com.doodlelabs.meshriderwave.data.local.SettingsDataStore
import com.doodlelabs.meshriderwave.core.crypto.MLSGroupManager
import com.doodlelabs.meshriderwave.core.group.GroupCallManager
import com.doodlelabs.meshriderwave.core.ptt.PTTManager
import com.doodlelabs.meshriderwave.core.ptt.floor.FloorControlManager
import com.doodlelabs.meshriderwave.core.ptt.floor.FloorControlProtocol
import com.doodlelabs.meshriderwave.core.ptt.floor.FloorArbitrator
import com.doodlelabs.meshriderwave.data.repository.ContactRepositoryImpl
import com.doodlelabs.meshriderwave.data.repository.GroupRepositoryImpl
import com.doodlelabs.meshriderwave.data.repository.PTTChannelRepositoryImpl
import com.doodlelabs.meshriderwave.data.repository.SettingsRepositoryImpl
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import com.doodlelabs.meshriderwave.domain.repository.GroupRepository
import com.doodlelabs.meshriderwave.domain.repository.PTTChannelRepository
import com.doodlelabs.meshriderwave.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

// DataStore extension
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mesh_rider_settings")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideCryptoManager(): CryptoManager {
        return CryptoManager()
    }

    @Provides
    @Singleton
    fun provideConnector(
        networkTypeDetector: NetworkTypeDetector
    ): Connector {
        return Connector(networkTypeDetector)
    }

    @Provides
    @Singleton
    fun provideMeshNetworkManager(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager,
        connector: Connector,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): MeshNetworkManager {
        return MeshNetworkManager(context, cryptoManager, connector, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        cryptoManager: CryptoManager
    ): SettingsDataStore {
        return SettingsDataStore(context, dataStore, cryptoManager)
    }

    // Dispatchers
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    // Audio Components for PTT (P0 - Production Critical)
    @Provides
    @Singleton
    fun provideOpusCodecManager(): OpusCodecManager {
        return OpusCodecManager()
    }

    @Provides
    @Singleton
    fun provideRTPPacketManager(
        @ApplicationContext context: Context
    ): RTPPacketManager {
        return RTPPacketManager(context)
    }

    @Provides
    @Singleton
    fun provideMulticastAudioManager(
        @ApplicationContext context: Context,
        opusCodecManager: OpusCodecManager,
        rtpPacketManager: RTPPacketManager
    ): MulticastAudioManager {
        return MulticastAudioManager(context, opusCodecManager, rtpPacketManager)
    }

    // =========================================================================
    // FLOOR CONTROL (3GPP MCPTT Compliant) - January 2026
    // =========================================================================

    @Provides
    @Singleton
    fun provideFloorControlProtocol(
        cryptoManager: CryptoManager,
        meshNetworkManager: MeshNetworkManager
    ): FloorControlProtocol {
        return FloorControlProtocol(cryptoManager, meshNetworkManager)
    }

    @Provides
    @Singleton
    fun provideFloorControlManager(
        cryptoManager: CryptoManager,
        floorControlProtocol: FloorControlProtocol
    ): FloorControlManager {
        return FloorControlManager(cryptoManager, floorControlProtocol)
    }

    @Provides
    @Singleton
    fun provideFloorArbitrator(
        floorControlProtocol: FloorControlProtocol
    ): FloorArbitrator {
        return FloorArbitrator(floorControlProtocol)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: GroupRepositoryImpl): GroupRepository

    @Binds
    @Singleton
    abstract fun bindPTTChannelRepository(impl: PTTChannelRepositoryImpl): PTTChannelRepository
}
