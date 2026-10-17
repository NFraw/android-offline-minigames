// Batch-decode Ogg Vorbis files to raw float32 PCM.
// Usage: node decode_batch.mjs <jobs.json>
//
// jobs.json: [{ "in": "a.ogg", "out": "a.f32", "meta": "a.json" }, ...]
// The meta JSON records { channels, samples, sampleRate }.
import { readFileSync, writeFileSync } from 'node:fs';

// The published bundle is UMD; under Node's ESM loader it falls through to the
// "assign onto globalThis" branch. That branch still evaluates the WebWorker
// subclass at load time, so give it a placeholder Worker to extend.
globalThis.Worker ??= class Worker {};
await import('./vendor/ogg-vorbis-decoder.min.mjs');
const { OggVorbisDecoder } = globalThis['ogg-vorbis-decoder'];

const jobs = JSON.parse(readFileSync(process.argv[2], 'utf8'));
const results = [];

for (const job of jobs) {
  try {
    const decoder = new OggVorbisDecoder();
    await decoder.ready;
    const { channelData, samplesDecoded, sampleRate } = await decoder.decodeFile(
      new Uint8Array(readFileSync(job.in))
    );

    // Interleave channels into a single Float32Array.
    const ch = channelData.length;
    const out = new Float32Array(samplesDecoded * ch);
    for (let i = 0; i < samplesDecoded; i++) {
      for (let c = 0; c < ch; c++) out[i * ch + c] = channelData[c][i];
    }

    writeFileSync(job.out, Buffer.from(out.buffer, out.byteOffset, out.byteLength));
    writeFileSync(job.meta, JSON.stringify({ channels: ch, samples: samplesDecoded, sampleRate }));
    results.push({ in: job.in, ok: true, channels: ch, samples: samplesDecoded, sampleRate });
    await decoder.free();
  } catch (err) {
    results.push({ in: job.in, ok: false, error: String(err) });
  }
}

console.log(JSON.stringify(results, null, 1));
