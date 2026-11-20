package io.qzz.xandll.morgo.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.qzz.xandll.morgo.data.datasource.HafasClient
import io.qzz.xandll.morgo.data.datasource.HomeAssistantClient
import io.qzz.xandll.morgo.data.datasource.WebUntisClient
import io.qzz.xandll.morgo.data.repository.LessonCache
import io.qzz.xandll.morgo.data.repository.MorgoRepository
import io.qzz.xandll.morgo.data.repository.SettingsRepository
import io.qzz.xandll.morgo.data.service.AlarmSchedulerService
import io.qzz.xandll.morgo.data.service.EveningCheckScheduler
import io.qzz.xandll.morgo.domain.usecase.CalculateAlarmTimeUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideWebUntisClient(): WebUntisClient {
        return WebUntisClient()
    }

    @Provides
    @Singleton
    fun provideHafasClient(): HafasClient {
        return HafasClient()
    }

    @Provides
    @Singleton
    fun provideHomeAssistantClient(): HomeAssistantClient {
        return HomeAssistantClient()
    }

    @Provides
    @Singleton
    fun provideMorgoRepository(
        webUntisClient: WebUntisClient,
        hafasClient: HafasClient,
        homeAssistantClient: HomeAssistantClient,
        @ApplicationContext context: Context
    ): MorgoRepository {
        val cache = LessonCache(context.getSharedPreferences("morgo_prefs", Context.MODE_PRIVATE))
        return MorgoRepository(webUntisClient, hafasClient, homeAssistantClient, cache)
    }

    @Provides
    @Singleton
    fun provideCalculateAlarmTimeUseCase(
        repository: MorgoRepository
    ): CalculateAlarmTimeUseCase {
        return CalculateAlarmTimeUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideAlarmSchedulerService(
        @ApplicationContext context: Context
    ): AlarmSchedulerService {
        return AlarmSchedulerService(context)
    }

    @Provides
    @Singleton
    fun provideEveningCheckScheduler(
        @ApplicationContext context: Context
    ): EveningCheckScheduler {
        return EveningCheckScheduler(context)
    }
}
