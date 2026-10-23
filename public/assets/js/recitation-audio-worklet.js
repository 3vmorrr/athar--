class AtharReviewCaptureProcessor extends AudioWorkletProcessor {
    constructor(options) {
        super();
        const chunkSeconds = Number(options.processorOptions?.chunkSeconds) || 0.28;
        this.chunkSize = Math.max(2048, Math.round(sampleRate * chunkSeconds));
        this.buffer = new Float32Array(this.chunkSize);
        this.offset = 0;
        this.sumSquares = 0;
    }

    process(inputs, outputs) {
        const input = inputs[0]?.[0];
        for (const output of outputs[0] || []) output.fill(0);
        if (!input) return true;

        let inputOffset = 0;
        while (inputOffset < input.length) {
            const count = Math.min(input.length - inputOffset, this.chunkSize - this.offset);
            const slice = input.subarray(inputOffset, inputOffset + count);
            this.buffer.set(slice, this.offset);
            for (let index = 0; index < count; index++) this.sumSquares += slice[index] * slice[index];
            inputOffset += count;
            this.offset += count;
            if (this.offset === this.chunkSize) {
                const completed = this.buffer;
                const rms = Math.sqrt(this.sumSquares / this.chunkSize);
                this.port.postMessage({ samples: completed.buffer, rms }, [completed.buffer]);
                this.buffer = new Float32Array(this.chunkSize);
                this.offset = 0;
                this.sumSquares = 0;
            }
        }
        return true;
    }
}

registerProcessor('athar-review-capture', AtharReviewCaptureProcessor);
