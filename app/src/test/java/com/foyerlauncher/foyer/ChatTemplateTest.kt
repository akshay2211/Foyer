package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.ai.engine.ChatTemplate
import org.junit.Assert.assertEquals
import org.junit.Test

/** fromUrl must recognize both model families the app ships suggestions for. */
class ChatTemplateTest {
    @Test fun qwen_url_selects_chatml() {
        assertEquals(ChatTemplate.CHATML, ChatTemplate.fromUrl("https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/model.task"))
    }

    @Test fun gemma_url_selects_gemma() {
        assertEquals(ChatTemplate.GEMMA, ChatTemplate.fromUrl("https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"))
    }

    @Test fun unrecognized_url_falls_back_to_gemma() {
        assertEquals(ChatTemplate.GEMMA, ChatTemplate.fromUrl("https://example.com/some-other-model.task"))
    }

    @Test fun match_is_case_insensitive() {
        assertEquals(ChatTemplate.CHATML, ChatTemplate.fromUrl("https://example.com/QWEN-mirror/model.task"))
    }
}
