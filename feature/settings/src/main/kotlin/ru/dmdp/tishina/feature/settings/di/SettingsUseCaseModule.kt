package ru.dmdp.tishina.feature.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import ru.dmdp.tishina.core.domain.repository.SettingsRepository
import ru.dmdp.tishina.core.domain.usecase.ObserveAppSettingsUseCase
import ru.dmdp.tishina.core.domain.usecase.ResetCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateAppLocaleUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateDynamicColorsUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateThemeModeUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateTimeWeightingUseCase

/**
 * Bridges pure-Kotlin Phase-4 settings use-cases into the Hilt graph at
 * [ViewModelComponent] scope.
 *
 * Use-cases are stateless wrappers around [SettingsRepository] — there is no
 * value in promoting them to `SingletonComponent`. Scoping them per ViewModel
 * keeps the singleton graph lean and lets future per-screen specializations
 * (e.g. a future Onboarding flow with its own snapshot) provide an alternate
 * implementation without touching app-wide bindings.
 *
 * `SettingsRepository` itself is bound in `:core:data/DataModule` and is
 * `@Singleton`, so injecting it into these provider methods still resolves to
 * the one shared instance.
 */
@Module
@InstallIn(ViewModelComponent::class)
internal object SettingsUseCaseModule {

    @Provides
    @ViewModelScoped
    fun provideObserveAppSettingsUseCase(repository: SettingsRepository): ObserveAppSettingsUseCase =
        ObserveAppSettingsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideUpdateCalibrationUseCase(repository: SettingsRepository): UpdateCalibrationUseCase =
        UpdateCalibrationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideResetCalibrationUseCase(repository: SettingsRepository): ResetCalibrationUseCase =
        ResetCalibrationUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideUpdateTimeWeightingUseCase(repository: SettingsRepository): UpdateTimeWeightingUseCase =
        UpdateTimeWeightingUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideUpdateThemeModeUseCase(repository: SettingsRepository): UpdateThemeModeUseCase =
        UpdateThemeModeUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideUpdateDynamicColorsUseCase(repository: SettingsRepository): UpdateDynamicColorsUseCase =
        UpdateDynamicColorsUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideUpdateAppLocaleUseCase(repository: SettingsRepository): UpdateAppLocaleUseCase =
        UpdateAppLocaleUseCase(repository)
}
