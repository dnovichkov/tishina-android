package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("UpdateThemeModeUseCase — FR-17 theme selection")
class UpdateThemeModeUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateThemeModeUseCase(repository)

    @Test
    fun `invoke System forwards to repository`() = runTest {
        useCase(ThemeMode.System)
        coVerify(exactly = 1) { repository.updateThemeMode(ThemeMode.System) }
    }

    @Test
    fun `invoke Light forwards to repository`() = runTest {
        useCase(ThemeMode.Light)
        coVerify(exactly = 1) { repository.updateThemeMode(ThemeMode.Light) }
    }

    @Test
    fun `invoke Dark forwards to repository`() = runTest {
        useCase(ThemeMode.Dark)
        coVerify(exactly = 1) { repository.updateThemeMode(ThemeMode.Dark) }
    }
}
