package com.risediary.app.di

import com.risediary.app.service.BootIdentityProvider
import com.risediary.app.service.SystemBootIdentityProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BootIdentityModule {
    @Binds @Singleton
    abstract fun bindBootIdentity(provider: SystemBootIdentityProvider): BootIdentityProvider
}