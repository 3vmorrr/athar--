package org.athar.app.review

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ReviewTensorSpec(
    val name: String,
    val dims: LongArray,
    val dtype: String,
) {
    val elementCount: Long
        get() {
            var total = 1L
            dims.forEach { dimension ->
                require(dimension in 1..1_000_000L)
                total = Math.multiplyExact(total, dimension)
            }
            return total
        }

    val byteCount: Long
        get() = Math.multiplyExact(
            elementCount,
            when (dtype) {
                "float32" -> 4L
                "int64" -> 8L
                else -> error("unsupported_tensor_dtype")
            },
        )

    fun validated(): ReviewTensorSpec {
        require(name.matches(Regex("[A-Za-z0-9_]{1,120}")))
        require(dims.isNotEmpty() && dims.size <= 6)
        require(dtype == "float32" || dtype == "int64")
        require(elementCount in 1..2_000_000L)
        require(byteCount <= 8L * 1024L * 1024L)
        return this
    }

    override fun equals(other: Any?): Boolean =
        other is ReviewTensorSpec &&
            name == other.name &&
            dims.contentEquals(other.dims) &&
            dtype == other.dtype

    override fun hashCode(): Int =
        31 * (31 * name.hashCode() + dims.contentHashCode()) + dtype.hashCode()
}

data class ReviewModelIoSpec(
    val timeFrames: Int,
    val hopFrames: Int,
    val featureDim: Int,
    val vocabSize: Int,
    val inputs: List<ReviewTensorSpec>,
    val outputs: List<ReviewTensorSpec>,
) {
    val inputBytes: Long get() = inputs.sumOf { it.byteCount }

    fun validated(): ReviewModelIoSpec {
        require(timeFrames == 61)
        require(hopFrames == 48)
        require(featureDim == 80)
        require(vocabSize == 251)
        require(inputs.size in 2..128)
        require(outputs.size in 1..128)
        require(inputs.map { it.name }.toSet().size == inputs.size)
        require(outputs.map { it.name }.toSet().size == outputs.size)
        inputs.forEach { it.validated() }
        outputs.forEach { it.validated() }
        require(inputBytes <= 16L * 1024L * 1024L)

        val x = inputs.single { it.name == "x" }
        require(x.dtype == "float32")
        require(x.dims.contentEquals(longArrayOf(1, timeFrames.toLong(), featureDim.toLong())))

        val processed = inputs.single { it.name == "processed_lens" }
        require(processed.dtype == "int64")
        require(processed.dims.contentEquals(longArrayOf(1)))

        val logProbabilities = outputs.single { it.name == "log_probs" }
        require(logProbabilities.dtype == "float32")
        require(
            logProbabilities.dims.contentEquals(
                longArrayOf(1, 12, vocabSize.toLong()),
            ),
        )
        return this
    }
}

object ReviewModelIoCodec {
    const val MAX_IO_JSON_BYTES = 256 * 1024

    fun decode(raw: String): ReviewModelIoSpec {
        require(raw.toByteArray(Charsets.UTF_8).size in 100..MAX_IO_JSON_BYTES)
        val root = JsonParser.parseString(raw).asJsonObject
        val inputs = root.getAsJsonArray("inputs").map(::tensor)
        val outputs = root.getAsJsonArray("outputs").map(::tensor)
        return ReviewModelIoSpec(
            timeFrames = root.requiredInt("T"),
            hopFrames = root.requiredInt("hop"),
            featureDim = root.requiredInt("featureDim"),
            vocabSize = root.requiredInt("vocabSize"),
            inputs = inputs,
            outputs = outputs,
        ).validated()
    }

    private fun tensor(value: com.google.gson.JsonElement): ReviewTensorSpec {
        require(value.isJsonObject)
        val objectValue = value.asJsonObject
        val dims = objectValue.getAsJsonArray("dims")
            .map { element ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber)
                element.asLong
            }
            .toLongArray()
        return ReviewTensorSpec(
            name = objectValue.requiredString("name"),
            dims = dims,
            dtype = objectValue.requiredString("dtype"),
        ).validated()
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asInt
    }
}
