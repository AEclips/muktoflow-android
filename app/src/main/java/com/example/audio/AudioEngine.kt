package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.data.model.AmbientSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class AudioEngine {
    private val sampleRate = 44100
    private var ambientTrack: AudioTrack? = null
    private var ambientJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var currentSoundType = AmbientSoundType.NONE
    @Volatile
    private var currentVolume = 0.5f

    private val tickSamples = (sampleRate * 0.018).toInt()
    private val precomputedTickBuffer = ShortArray(tickSamples).apply {
        for (i in 0 until tickSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t / 0.0035)
            val s = (sin(2.0 * PI * 1650.0 * t) * 0.7 + sin(2.0 * PI * 920.0 * t) * 0.3) * envelope
            this[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        try {
            ambientTrack?.setVolume(currentVolume)
        } catch (_: Throwable) {}
    }

    fun startAmbient(type: AmbientSoundType, volume: Float = currentVolume) {
        currentVolume = volume.coerceIn(0f, 1f)
        if (type == AmbientSoundType.NONE) {
            stopAmbient()
            return
        }
        if (currentSoundType == type && ambientJob?.isActive == true && ambientTrack != null) {
            setVolume(currentVolume)
            return
        }
        stopAmbient()

        currentSoundType = type
        scope.launch {
            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBufferSize <= 0) return@launch
                val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                        AudioTrack.MODE_STREAM
                    )
                }

                ambientTrack = track
                track.setVolume(currentVolume)
                track.play()

                ambientJob = scope.launch {
                    val shortBuffer = ShortArray(bufferSize / 2)
                    val random = Random()

                    var b0 = 0.0
                    var b1 = 0.0
                    var b2 = 0.0
                    var b3 = 0.0
                    var b4 = 0.0
                    var b5 = 0.0
                    var b6 = 0.0
                    var brown = 0.0
                    var phase1 = 0.0
                    var phase2 = 0.0
                    var phase3 = 0.0
                    var sampleCounter = 0L
                    val fadeInSamples = sampleRate / 2 // 0.5s smooth fade-in

                    while (isActive && currentSoundType == type) {
                        for (i in shortBuffer.indices) {
                            val fadeMultiplier = if (sampleCounter < fadeInSamples) {
                                sampleCounter.toDouble() / fadeInSamples.toDouble()
                            } else {
                                1.0
                            }
                            sampleCounter++

                            val rawSample: Double = when (type) {
                                AmbientSoundType.WHITE_NOISE -> {
                                    val white = (random.nextGaussian() * 0.14).coerceIn(-1.0, 1.0)
                                    b0 = 0.88 * b0 + 0.12 * white
                                    b0
                                }
                                AmbientSoundType.RAIN -> {
                                    val white = random.nextGaussian() * 0.18
                                    b0 = 0.99886 * b0 + white * 0.0555179
                                    b1 = 0.99332 * b1 + white * 0.0750759
                                    b2 = 0.96900 * b2 + white * 0.1538520
                                    b3 = 0.86650 * b3 + white * 0.3104856
                                    b4 = 0.55000 * b4 + white * 0.5329522
                                    b5 = -0.7616 * b5 - white * 0.0168980
                                    val pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.11
                                    b6 = white * 0.115926
                                    val drop = if (random.nextFloat() < 0.0006f) (random.nextFloat() - 0.5) * 0.45 else 0.0
                                    (pink + drop).coerceIn(-1.0, 1.0)
                                }
                                AmbientSoundType.STREAM -> {
                                    phase1 += 2.0 * PI * 1.4 / sampleRate
                                    phase2 += 2.0 * PI * 3.1 / sampleRate
                                    if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                                    if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                                    val mod = 0.5 + 0.3 * sin(phase1) + 0.2 * sin(phase2)
                                    val white = random.nextGaussian() * (0.10 + 0.08 * mod)
                                    b0 = 0.91 * b0 + 0.09 * white
                                    b0.coerceIn(-1.0, 1.0)
                                }
                                AmbientSoundType.OCEAN -> {
                                    // Slow 0.13 Hz coastal wave swell (~7.7 sec cycle)
                                    phase1 += 2.0 * PI * 0.13 / sampleRate
                                    if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                                    val waveEnvelope = 0.25 + 0.75 * ((sin(phase1) + 1.0) * 0.5)
                                    val white = random.nextGaussian() * 0.18 * waveEnvelope
                                    brown = (0.975 * brown + 0.025 * white).coerceIn(-1.0, 1.0)
                                    b0 = 0.85 * b0 + 0.15 * white
                                    (brown * 0.7 + b0 * 0.3 * waveEnvelope).coerceIn(-1.0, 1.0)
                                }
                                AmbientSoundType.CAFE -> {
                                    val white = random.nextGaussian() * 0.10
                                    brown = ((brown * 0.985) + (white * 0.12)).coerceIn(-1.0, 1.0)
                                    phase1 += 2.0 * PI * 145.0 / sampleRate
                                    phase2 += 2.0 * PI * 0.4 / sampleRate
                                    if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                                    if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                                    val warmRoom = sin(phase1) * (0.04 + 0.02 * sin(phase2))
                                    (brown * 0.65 + warmRoom).coerceIn(-1.0, 1.0)
                                }
                                AmbientSoundType.BINAURAL -> {
                                    // 200 Hz + 240 Hz (40Hz Gamma Focus beat) + 100 Hz warm sub-pad
                                    phase1 += 2.0 * PI * 200.0 / sampleRate
                                    phase2 += 2.0 * PI * 240.0 / sampleRate
                                    phase3 += 2.0 * PI * 100.0 / sampleRate
                                    if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI
                                    if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI
                                    if (phase3 > 2.0 * PI) phase3 -= 2.0 * PI
                                    val drone = (sin(phase1) * 0.14 + sin(phase2) * 0.12 + sin(phase3) * 0.10)
                                    drone.coerceIn(-1.0, 1.0)
                                }
                                AmbientSoundType.NONE -> 0.0
                            }
                            val finalSample = (rawSample * fadeMultiplier * Short.MAX_VALUE)
                                .toInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                            shortBuffer[i] = finalSample
                        }
                        try {
                            track.write(shortBuffer, 0, shortBuffer.size)
                        } catch (_: Throwable) {
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun stopAmbient() {
        ambientJob?.cancel()
        ambientJob = null
        currentSoundType = AmbientSoundType.NONE
        val trackToStop = ambientTrack
        ambientTrack = null
        try {
            trackToStop?.pause()
            trackToStop?.flush()
            trackToStop?.stop()
            trackToStop?.release()
        } catch (_: Throwable) {}
    }

    /**
     * Plays a resonant Tibetan Singing Bowl / Zen meditation bell tone (2.8 seconds)
     */
    fun playChime(volume: Float = 0.85f) {
        scope.launch {
            try {
                val chimeDurationSec = 2.8
                val totalSamples = (sampleRate * chimeDurationSec).toInt()
                val buffer = ShortArray(totalSamples)

                val baseFreq = 528.0 // Solfeggio frequency for peaceful focus
                val harmonic2 = baseFreq * 2.0
                val harmonic3 = baseFreq * 3.01

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = exp(-t / 0.75)
                    val sampleVal = (
                        sin(2.0 * PI * baseFreq * t) * 0.65 +
                        sin(2.0 * PI * harmonic2 * t) * 0.25 +
                        sin(2.0 * PI * harmonic3 * t) * 0.10
                    ) * envelope * volume

                    buffer[i] = (sampleVal.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                val chimeTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(totalSamples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_NOTIFICATION,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        totalSamples * 2,
                        AudioTrack.MODE_STATIC
                    )
                }

                chimeTrack.write(buffer, 0, buffer.size)
                chimeTrack.play()
                kotlinx.coroutines.delay((chimeDurationSec * 1000).toLong() + 200)
                chimeTrack.stop()
                chimeTrack.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Plays a crisp celebratory two-note ascending chime when a task is marked complete
     */
    fun playTaskCompleteSound(volume: Float = 0.6f) {
        scope.launch {
            try {
                val durationSec = 0.45
                val totalSamples = (sampleRate * durationSec).toInt()
                val buffer = ShortArray(totalSamples)
                val splitSample = (sampleRate * 0.14).toInt()

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if (i < splitSample) 587.33 else 880.0 // D5 -> A5
                    val localT = if (i < splitSample) t else (i - splitSample).toDouble() / sampleRate
                    val env = exp(-localT / 0.14)
                    val s = sin(2.0 * PI * freq * localT) * env * volume
                    buffer[i] = (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(totalSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
                kotlinx.coroutines.delay(500)
                track.stop()
                track.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Plays a gentle subtle mechanical clock tick using precomputed PCM samples
     */
    fun playTick(volume: Float = 0.22f) {
        scope.launch {
            try {
                val tickTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(precomputedTickBuffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                tickTrack.setVolume(volume.coerceIn(0f, 1f))
                tickTrack.write(precomputedTickBuffer, 0, precomputedTickBuffer.size)
                tickTrack.play()
                kotlinx.coroutines.delay(45)
                tickTrack.stop()
                tickTrack.release()
            } catch (_: Throwable) {}
        }
    }

    fun release() {
        stopAmbient()
    }
}
