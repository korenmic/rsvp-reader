package com.rsvpreader

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

enum class RSVPMode {
    NAIVE, ORP
}

enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

data class RSVPWord(
    val text: String,
    val speed: Int,
    val isPaused: Boolean = false
)

data class WordContext(
    val targetWord: String,
    val contextTokens: List<String>
)

object RSVPEngine {
    private val _currentWord = MutableStateFlow<RSVPWord?>(null)
    val currentWord: StateFlow<RSVPWord?> = _currentWord.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0)
    val currentSpeed: StateFlow<Int> = _currentSpeed.asStateFlow()

    private var minWps = 3
    private var maxWps = 45
    private var mode = RSVPMode.NAIVE
    private var state = PlaybackState.STOPPED

    private val textBuffer = CopyOnWriteArrayList<String>()
    private var currentIndex = 0
    private var lastDisplayedIndex = -1
    private var lastWordContext: WordContext? = null

    private var readingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setSpeedRange(min: Int, max: Int) {
        minWps = min
        maxWps = max
    }

    fun setMode(newMode: RSVPMode) {
        mode = newMode
    }

    fun setSpeed(speed: Int) {
        _currentSpeed.value = speed
    }

    fun getState(): PlaybackState = state

    fun play() {
        if (state == PlaybackState.PLAYING) return
        state = PlaybackState.PLAYING
        startReading()
    }

    fun pause() {
        state = PlaybackState.PAUSED
        readingJob?.cancel()
        
        if (lastDisplayedIndex >= 0 && lastDisplayedIndex < textBuffer.size) {
            lastWordContext = createWordContext(lastDisplayedIndex)
            _currentWord.value = RSVPWord(
                text = formatWord(textBuffer[lastDisplayedIndex]),
                speed = _currentSpeed.value,
                isPaused = true
            )
        }
    }

    fun stop() {
        state = PlaybackState.STOPPED
        readingJob?.cancel()
        currentIndex = 0
        lastDisplayedIndex = -1
        lastWordContext = null
        _currentWord.value = null
    }

    fun updateTextBuffer(text: String) {
        val words = tokenizeText(text)
        
        if (state == PlaybackState.PAUSED && lastWordContext != null) {
            val resumeIndex = findResumeIndex(words, lastWordContext!!)
            if (resumeIndex >= 0) {
                textBuffer.clear()
                textBuffer.addAll(words)
                currentIndex = resumeIndex
                return
            }
        }

        textBuffer.clear()
        textBuffer.addAll(words)
        
        if (state == PlaybackState.STOPPED) {
            currentIndex = 0
        }
    }

    private fun tokenizeText(text: String): List<String> {
        return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun createWordContext(index: Int): WordContext? {
        if (index < 0 || index >= textBuffer.size) return null
        
        val contextSize = 3
        val contextTokens = mutableListOf<String>()
        
        val startIndex = (index - contextSize).coerceAtLeast(0)
        val endIndex = (index + contextSize).coerceAtMost(textBuffer.size - 1)
        
        for (i in startIndex..endIndex) {
            contextTokens.add(textBuffer[i])
        }
        
        return WordContext(
            targetWord = textBuffer[index],
            contextTokens = contextTokens
        )
    }

    private fun findResumeIndex(tokens: List<String>, context: WordContext): Int {
        if (tokens.size < context.contextTokens.size) return -1
        
        val targetIndices = mutableListOf<Int>()
        for (i in tokens.indices) {
            if (tokens[i].equals(context.targetWord, ignoreCase = true)) {
                targetIndices.add(i)
            }
        }
        
        for (targetIdx in targetIndices) {
            var matchCount = 0
            val contextTargetIdx = context.contextTokens.indexOfFirst { 
                it.equals(context.targetWord, ignoreCase = true) 
            }
            
            if (contextTargetIdx < 0) continue
            
            val startOffset = contextTargetIdx
            val checkStart = (targetIdx - startOffset).coerceAtLeast(0)
            val checkEnd = (checkStart + context.contextTokens.size - 1).coerceAtMost(tokens.size - 1)
            
            for (i in 0 until context.contextTokens.size) {
                val tokenIdx = checkStart + i
                if (tokenIdx >= tokens.size) break
                if (tokens[tokenIdx].equals(context.contextTokens[i], ignoreCase = true)) {
                    matchCount++
                }
            }
            
            if (matchCount >= context.contextTokens.size / 2) {
                return targetIdx
            }
        }
        
        return -1
    }

    private fun startReading() {
        readingJob?.cancel()
        readingJob = scope.launch {
            while (state == PlaybackState.PLAYING && isActive) {
                val speed = _currentSpeed.value
                
                if (speed == 0) {
                    delay(100)
                    continue
                }

                val word = getNextWord(speed)
                if (word != null) {
                    _currentWord.value = RSVPWord(formatWord(word), speed, false)
                    
                    val delayMs = (1000.0 / kotlin.math.abs(speed)).toLong()
                    delay(delayMs.coerceAtLeast(50))
                } else {
                    stop()
                    break
                }
            }
        }
    }

    private fun getNextWord(speed: Int): String? {
        if (textBuffer.isEmpty()) return null

        if (speed > 0) {
            if (currentIndex >= textBuffer.size) {
                return null
            }
            lastDisplayedIndex = currentIndex
            val word = textBuffer[currentIndex]
            currentIndex++
            return word
        } else {
            if (currentIndex <= 0) {
                return null
            }
            currentIndex--
            lastDisplayedIndex = currentIndex
            return textBuffer[currentIndex]
        }
    }

    private fun formatWord(word: String): String {
        return when (mode) {
            RSVPMode.NAIVE -> word
            RSVPMode.ORP -> formatWordORP(word)
        }
    }

    private fun formatWordORP(word: String): String {
        if (word.length <= 1) return word
        
        val orpIndex = when {
            word.length <= 5 -> 1
            word.length <= 9 -> 2
            word.length <= 13 -> 3
            else -> 4
        }
        
        val before = word.substring(0, orpIndex)
        val focus = word[orpIndex]
        val after = if (orpIndex < word.length - 1) word.substring(orpIndex + 1) else ""
        
        return "$before[$focus]$after"
    }

    fun handleTouchStart() {
        if (state == PlaybackState.STOPPED || state == PlaybackState.PAUSED) {
            play()
        }
    }

    fun handleTouchEnd() {
        if (state == PlaybackState.PLAYING) {
            pause()
        }
    }
}
