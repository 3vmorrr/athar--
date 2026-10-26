import fs from 'node:fs';
import path from 'node:path';
import * as ort from 'onnxruntime-node';
import { ZipformerSession } from '@tilawa/core';

const [dataDirArg, bundleDirArg, outputArg] = process.argv.slice(2);
if (!dataDirArg || !bundleDirArg || !outputArg) {
  console.error('Usage: node evaluate_synthetic.mjs <synthetic-pilot-dir> <bundle-dir> <output.json>');
  process.exit(2);
}

const dataDir = path.resolve(dataDirArg);
const bundleDir = path.resolve(bundleDirArg);
const outputFile = path.resolve(outputArg);
const slipHead = process.env.ATHAR_SLIP_HEAD || false;
const casesDoc = JSON.parse(fs.readFileSync(path.join(dataDir, 'cases.json'), 'utf8'));
const manifest = JSON.parse(fs.readFileSync(path.join(bundleDir, 'pilot', 'manifest.json'), 'utf8'));
const modelDir = path.join(bundleDir, 'model');
const model = new Uint8Array(fs.readFileSync(path.join(modelDir, 'zipformer_a0w_ep1_a05.int8.onnx')));
const io = JSON.parse(fs.readFileSync(path.join(modelDir, 'zipformer_a0w_ep1_a05.io.json'), 'utf8'));
const corpus = JSON.parse(fs.readFileSync(path.join(modelDir, 'zipformer_quran.json'), 'utf8'));
const quran = [...new Map(manifest.samples.map((row) => [`${row.surah}:${row.ayah}`, {
  surah: row.surah, ayah: row.ayah, text_uthmani: row.text_uthmani, surah_name: `Surah ${row.surah}`,
}])).values()];

let currentEvents = [];
let session;
session = await ZipformerSession.create({
  ort, model, io, corpus, quran, executionProviders: ['cpu'],
  minWordFraction: 0.72,
  structural: { timing: 'pause' },
  slipHead,
  tailSeconds: 1.5,
  onEvent: (event) => currentEvents.push(event),
});

const results = [];
for (let index = 0; index < casesDoc.cases.length; index += 1) {
  const item = casesDoc.cases[index];
  currentEvents = [];
  session.reset();
  session.setExpected({ surah: item.surah, ayah: item.ayah_start, ayahEnd: item.ayah_end });
  session.setMode('correction');
  const file = fs.readFileSync(path.join(dataDir, item.pcm_file));
  const pcm = new Float32Array(file.buffer.slice(file.byteOffset, file.byteOffset + file.byteLength));
  for (let offset = 0; offset < pcm.length; offset += 3200) {
    await session.feed(pcm.subarray(offset, Math.min(offset + 3200, pcm.length)));
  }
  await session.stop();
  const verdicts = (session.verdicts() || []).map((v) => ({
    surah: v.surah, ayah: v.ayah, word: v.word, state: v.state,
    distance: v.distance, heardRatio: v.heardRatio, margin: v.margin,
  }));
  const issues = currentEvents
    .filter((event) => event.type === 'correction' && event.state?.issue)
    .map((event) => ({ phase: event.state.phase, ...event.state.issue }));
  const notes = currentEvents
    .filter((event) => event.type === 'correction_note' && event.issue)
    .map((event) => event.issue);
  const counts = Object.fromEntries(['ok', 'unsure', 'wrong', 'skipped', 'pending']
    .map((state) => [state, verdicts.filter((v) => v.state === state).length]));
  results.push({
    ...item,
    detected: issues.length > 0,
    soft_note_kinds: [...new Set(notes.map((note) => note.kind))],
    detected_issue_kinds: [...new Set(issues.map((issue) => issue.kind))],
    issues,
    verdict_counts: counts,
  });
  console.log(`${index + 1}/${casesDoc.cases.length} ${item.case_id}: detected=${issues.length > 0} ${issues.map((x) => x.kind).join(',')}`);
}

const kinds = [...new Set(results.map((item) => item.kind))];
const summary = Object.fromEntries(kinds.map((kind) => {
  const group = results.filter((item) => item.kind === kind);
  const hits = group.filter((item) => item.detected).length;
  const noted = group.filter((item) => item.soft_note_kinds.length > 0).length;
  return [kind, {
    cases: group.length,
    alerts: hits,
    alert_rate: group.length ? +(hits / group.length).toFixed(4) : null,
    cases_with_soft_notes: noted,
  }];
}));
const report = {
  model: 'zipformer_a0w_ep1_a05.int8.onnx',
  engine: '@tilawa/core@0.4.0',
  slip_head: slipHead || 'off',
  purpose: 'Synthetic ayah-level stress test; not real learner speech and not a model-accuracy or word-error recall claim.',
  chunk_ms: 200,
  total_cases: results.length,
  summary,
  results,
};
fs.mkdirSync(path.dirname(outputFile), { recursive: true });
fs.writeFileSync(outputFile, JSON.stringify(report, null, 2), 'utf8');
console.log(JSON.stringify({ output: outputFile, summary }, null, 2));
