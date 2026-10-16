package com.sameer.dhvani

import com.sameer.dhvani.utils.LanguageDetector
import com.sameer.dhvani.utils.SpokenLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageRoutingTest {

    @Test
    fun testEnglishMathQueries() {
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("hi", "What is 24 * 4?")
        )
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("hi", "What is 24 into 4")
        )
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("hi", "Calculate 24 times 4")
        )
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("en", "What is 24 * 4?")
        )
    }

    @Test
    fun testPureHindiQueries() {
        assertEquals(
            SpokenLanguage.HINDI,
            LanguageDetector.detectFromWhisper("hi", "कल का मौसम कैसा है?")
        )
        assertEquals(
            SpokenLanguage.HINDI,
            LanguageDetector.detectFromWhisper("ur", "आज मौसम कैसा रहेगा?")
        )
        assertEquals(
            SpokenLanguage.HINDI,
            LanguageDetector.detectLanguage("कल का मौसम कैसा है?")
        )
    }

    @Test
    fun testHinglishQueries() {
        assertEquals(
            SpokenLanguage.HINGLISH,
            LanguageDetector.detectFromWhisper("hi", "Mujhe kal subah 7 baje ka alarm set karna hai.")
        )
        assertEquals(
            SpokenLanguage.HINGLISH,
            LanguageDetector.detectFromWhisper("en", "Aaj ka schedule kya hai, aur meri next meeting kab hai?")
        )
    }

    @Test
    fun testEnglishSentencesWithIndianAccentMisclassifiedByWhisper() {
        // Whisper often predicts "hi" for Indian English speakers
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("hi", "What is the weather forecast for Bangalore tomorrow?")
        )
        assertEquals(
            SpokenLanguage.ENGLISH,
            LanguageDetector.detectFromWhisper("hi", "Can you set a reminder for my meeting at 5 PM?")
        )
    }
}
