/*
 * Mesh Rider Wave - Radio Module (Hilt DI)
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Dependency injection module for radio-related components.
 */

package com.doodlelabs.meshriderwave.core.radio

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing radio-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RadioModule {

    /**
     * Provide RadioApiClient singleton.
     */
    @Provides
    @Singleton
    fun provideRadioApiClient(): RadioApiClient {
        return RadioApiClient()
    }

    /**
     * Provide RadioDiscoveryService singleton.
     */
    @Provides
    @Singleton
    fun provideRadioDiscoveryService(): RadioDiscoveryService {
        return RadioDiscoveryService()
    }
}
