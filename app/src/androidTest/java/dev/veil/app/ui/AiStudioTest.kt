package dev.veil.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.veil.app.ai.*
import dev.veil.app.domain.VeilPreferences
import dev.veil.app.domain.VeilUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Callback-only tests: no API key is persisted and no provider/network call is made. */
@RunWith(AndroidJUnit4::class)
class AiStudioTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localTemplatesAreDefaultAndPreviewRequiresUserInput() {
        var preview: String? = null
        setContent(onPreview = { preview = it })
        compose.onNodeWithText("On-device templates").assertIsSelected()
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("A sample incoming message").performScrollTo().performTextInput("hey")
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("hey", preview) }
    }

    @Test fun openAIConsentIsExplicitAndChangesAreSavedAsConfiguration() {
        var saved: AiConfiguration? = null
        setContent(onSaveConfiguration = { saved = it })
        compose.onNodeWithText("OpenAI API").performClick()
        compose.onNodeWithContentDescription("Allow OpenAI cloud generation").performScrollTo().assertIsOff()
        compose.onNodeWithText("Save AI settings").performScrollTo().performClick()
        compose.runOnIdle { assertNotNull(saved); assertFalse(saved!!.cloudConsent) }
        compose.onNodeWithContentDescription("Allow OpenAI cloud generation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Include limited conversation context").performScrollTo().assertIsOff()
        compose.onNodeWithText("Save AI settings").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(AiProvider.OPENAI, saved!!.provider)
            assertTrue(saved!!.cloudConsent)
            assertFalse(saved!!.includeContext)
        }
    }

    @Test fun APIKeyIsBlankUntilEnteredAndClearsAfterSave() {
        var key: String? = null
        val state = VeilUiState(preferences = VeilPreferences(ai = AiConfiguration(provider = AiProvider.OPENAI)))
        setContent(state, onSaveApiKey = { key = it })
        compose.onNodeWithContentDescription("OpenAI API key").performScrollTo()
        compose.onNodeWithText("Save key").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("OpenAI API key").performScrollTo().performTextInput("synthetic-not-a-real-api-key")
        compose.onNodeWithText("Save key").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("synthetic-not-a-real-api-key", key) }
        compose.onNodeWithText("Save key").assertIsNotEnabled()
        val editable = compose.onNodeWithContentDescription("OpenAI API key").fetchSemanticsNode()
            .config[SemanticsProperties.EditableText].text
        assertEquals("", editable)
    }

    @Test fun previewCannotUseCloudWithoutSavedConsentAndKey() {
        val state = mutableStateOf(VeilUiState(preferences = VeilPreferences(ai = AiConfiguration(provider = AiProvider.OPENAI))))
        var previews = 0
        compose.setContent { VeilTheme {
            AiStudioContent(state.value, {}, {}, {}, {}, { previews++ }, {})
        } }
        compose.onNodeWithText("A sample incoming message").performScrollTo().performTextInput("hello")
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(hasApiKey = true) }
        compose.onNodeWithText("Preview reply").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, previews) }
    }

    @Test fun explicitExamplesAndLanguageArePassedToStyleSave() {
        var style: WritingStyle? = null
        setContent(onSaveStyle = { style = it })
        compose.onNodeWithText("Hindi").performScrollTo().performClick()
        compose.onNodeWithText("Preferred phrases").performScrollTo().performTextInput("Namaste")
        compose.onNodeWithText("Approved writing examples").performScrollTo().performTextInput("Hi dost\nKya haal hai")
        compose.onNodeWithText("Save writing style").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(ReplyLanguage.HINDI, style!!.language)
            assertEquals("Namaste", style!!.preferredPhrases)
            assertEquals(listOf("Hi dost", "Kya haal hai"), style!!.approvedExamples)
        }
        compose.onNodeWithText("Preview reply").performScrollTo().assertIsNotEnabled()
    }

    private fun setContent(
        state: VeilUiState = VeilUiState(),
        onSaveConfiguration: (AiConfiguration) -> Unit = {},
        onSaveStyle: (WritingStyle) -> Unit = {},
        onSaveApiKey: (String) -> Unit = {},
        onPreview: (String) -> Unit = {},
    ) {
        compose.setContent { VeilTheme {
            AiStudioContent(state, onSaveConfiguration, onSaveStyle, onSaveApiKey, {}, onPreview, {})
        } }
    }
}
