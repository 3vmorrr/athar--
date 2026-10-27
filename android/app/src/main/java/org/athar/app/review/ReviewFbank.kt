package org.athar.app.review

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Streaming Kaldi-compatible 80-bin fbank used by the pinned Tilawa Zipformer model.
 *
 * This is intentionally a small local implementation rather than a generic DSP dependency:
 * the model contract is fixed at 16 kHz, 25 ms frames, 10 ms shift and 80 mel bins.
 */
class ReviewKaldiFbank {
    private var buffer = FloatArray(0)
    private var stored = 0
    private var sampleOffset = 0
    private var framesProduced = 0
    private var trueLength = 0

    private val samples = DoubleArray(FRAME_LENGTH)
    private val fftRe = DoubleArray(FFT_SIZE)
    private val fftIm = DoubleArray(FFT_SIZE)

    val retainedSamples: Int get() = stored
    val producedFrames: Int get() = framesProduced

    fun acceptWaveform(input: FloatArray): List<FloatArray> {
        require(input.size <= MAX_ACCEPT_SAMPLES)
        append(input)
        trueLength = sampleOffset + stored

        val out = ArrayList<FloatArray>()
        while (frameEnd(framesProduced) <= sampleOffset + stored) {
            out += computeFrame(framesProduced, Int.MAX_VALUE)
            framesProduced++
        }
        trim()
        return out
    }

    fun inputFinished(): List<FloatArray> {
        val n = trueLength
        if (n == 0) return emptyList()
        val total = (n + FRAME_SHIFT / 2) / FRAME_SHIFT
        val out = ArrayList<FloatArray>()
        while (framesProduced < total) {
            out += computeFrame(framesProduced, n)
            framesProduced++
        }
        trim()
        return out
    }

    fun reset() {
        buffer = FloatArray(0)
        stored = 0
        sampleOffset = 0
        framesProduced = 0
        trueLength = 0
    }

    private fun append(input: FloatArray) {
        if (input.isEmpty()) return
        if (stored + input.size > buffer.size) {
            val capacity = maxOf(
                buffer.size * 2,
                stored + input.size,
                4096,
            )
            val next = FloatArray(capacity)
            buffer.copyInto(next, endIndex = stored)
            buffer = next
        }
        input.copyInto(buffer, destinationOffset = stored)
        stored += input.size
    }

    private fun trim() {
        val firstNeeded = max(0, frameStart(framesProduced))
        val extra = firstNeeded - sampleOffset
        if (extra <= TRIM_EXTRA) return
        require(extra in 1..stored)
        buffer.copyInto(
            destination = buffer,
            destinationOffset = 0,
            startIndex = extra,
            endIndex = stored,
        )
        stored -= extra
        sampleOffset += extra
    }

    private fun computeFrame(frame: Int, n: Int): FloatArray {
        val start = frameStart(frame)
        for (i in 0 until FRAME_LENGTH) {
            var sample = start + i
            if (sample < 0 || sample >= n) sample = reflectIndex(sample, n)
            val local = sample - sampleOffset
            require(local in 0 until stored) {
                "fbank_sample_out_of_buffer"
            }
            samples[i] = buffer[local].toDouble()
        }

        var mean = 0.0
        for (value in samples) mean += value
        mean /= FRAME_LENGTH
        for (i in samples.indices) samples[i] -= mean

        for (i in FRAME_LENGTH - 1 downTo 1) {
            samples[i] -= PREEMPH * samples[i - 1]
        }
        samples[0] -= PREEMPH * samples[0]

        fftRe.fill(0.0)
        fftIm.fill(0.0)
        for (i in 0 until FRAME_LENGTH) {
            fftRe[i] = samples[i] * POVEY[i]
        }
        fft512(fftRe, fftIm)

        return FloatArray(FBANK_BINS) { band ->
            val filter = MEL[band]
            var energy = 0.0
            for (i in filter.weights.indices) {
                val bin = filter.firstBin + i
                val real = fftRe[bin]
                val imaginary = fftIm[bin]
                energy += (real * real + imaginary * imaginary) * filter.weights[i]
            }
            ln(max(energy, LOG_FLOOR)).toFloat()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FBANK_BINS = 80
        const val FRAME_LENGTH = 400
        const val FRAME_SHIFT = 160

        private const val FFT_SIZE = 512
        private const val PREEMPH = 0.97
        private const val LOW_FREQ = 20.0
        private const val HIGH_FREQ = 7600.0
        private const val LOG_FLOOR = 1.1920929e-7
        private const val TRIM_EXTRA = 1600
        private const val NYQUIST_BINS = FFT_SIZE / 2
        private const val MAX_ACCEPT_SAMPLES = 32_000

        private data class MelFilter(
            val firstBin: Int,
            val weights: DoubleArray,
        )

        private val POVEY = DoubleArray(FRAME_LENGTH) { i ->
            val hann = 0.5 - 0.5 * cos((2.0 * PI * i) / (FRAME_LENGTH - 1))
            hann.pow(0.85)
        }

        private fun hzToMel(value: Double): Double =
            1127.0 * ln(1.0 + value / 700.0)

        private val MEL: List<MelFilter> = run {
            val low = hzToMel(LOW_FREQ)
            val high = hzToMel(HIGH_FREQ)
            val points = DoubleArray(FBANK_BINS + 2) { i ->
                low + i * (high - low) / (FBANK_BINS + 1)
            }
            val binHz = SAMPLE_RATE.toDouble() / FFT_SIZE
            List(FBANK_BINS) { band ->
                val left = points[band]
                val center = points[band + 1]
                val right = points[band + 2]
                var first = -1
                val values = ArrayList<Double>()
                for (bin in 0 until NYQUIST_BINS) {
                    val mel = hzToMel(bin * binHz)
                    if (!(mel > left && mel < right)) continue
                    val weight = if (mel < center) {
                        (mel - left) / (center - left)
                    } else {
                        (right - mel) / (right - center)
                    }
                    if (first < 0) first = bin
                    values += weight
                }
                MelFilter(
                    firstBin = if (first < 0) 0 else first,
                    weights = values.toDoubleArray(),
                )
            }
        }

        private fun frameStart(frame: Int): Int =
            frame * FRAME_SHIFT - 120

        private fun frameEnd(frame: Int): Int =
            frame * FRAME_SHIFT + 280

        private fun reflectIndex(source: Int, length: Int): Int {
            require(length > 0)
            var value = source
            while (value < 0 || value >= length) {
                value = if (value < 0) {
                    -value - 1
                } else {
                    2 * length - 1 - value
                }
            }
            return value
        }

        private fun fft512(real: DoubleArray, imaginary: DoubleArray) {
            val n = FFT_SIZE
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while ((j and bit) != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val realTemp = real[i]
                    real[i] = real[j]
                    real[j] = realTemp
                    val imaginaryTemp = imaginary[i]
                    imaginary[i] = imaginary[j]
                    imaginary[j] = imaginaryTemp
                }
            }

            var length = 2
            while (length <= n) {
                val angle = -2.0 * PI / length
                val stepReal = cos(angle)
                val stepImaginary = kotlin.math.sin(angle)
                val half = length shr 1
                var offset = 0
                while (offset < n) {
                    var weightReal = 1.0
                    var weightImaginary = 0.0
                    for (inner in 0 until half) {
                        val left = offset + inner
                        val right = left + half
                        val leftReal = real[left]
                        val leftImaginary = imaginary[left]
                        val rightReal =
                            real[right] * weightReal -
                                imaginary[right] * weightImaginary
                        val rightImaginary =
                            real[right] * weightImaginary +
                                imaginary[right] * weightReal

                        real[left] = leftReal + rightReal
                        imaginary[left] = leftImaginary + rightImaginary
                        real[right] = leftReal - rightReal
                        imaginary[right] = leftImaginary - rightImaginary

                        val nextReal =
                            weightReal * stepReal -
                                weightImaginary * stepImaginary
                        weightImaginary =
                            weightReal * stepImaginary +
                                weightImaginary * stepReal
                        weightReal = nextReal
                    }
                    offset += length
                }
                length = length shl 1
            }
        }
    }
}
