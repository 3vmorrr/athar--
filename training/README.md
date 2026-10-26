# Synthetic recitation stress pilot

This pilot uses the existing MP3Quran bundle to create controlled **ayah-level** clean, omission, repetition, and substitution clips. It runs the same `@tilawa/core@0.4.0` correction engine and ONNX model used by the website.

The generated cases are a stress test, **not learner recordings, not phoneme-level mistakes, and not training data**. Results cannot establish real learner-error recall or support a 99% accuracy claim. The bundle contains an inference-only ONNX model; it does not include trainable Zipformer weights.

## Local run (Windows PowerShell)

Set the external training directory that already contains `pilot/` and `colab_bundle/`:

```powershell
$env:ATHAR_TRAINING_ROOT = "$env:LOCALAPPDATA\AtharTraining"
python -m pip install numpy scipy soundfile
Push-Location training
npm install
Pop-Location
python training/prepare_synthetic_pilot.py
node training/evaluate_synthetic.mjs `
  "$env:LOCALAPPDATA\AtharTraining\synthetic-pilot" `
  "$env:LOCALAPPDATA\AtharTraining\colab_bundle" `
  "$env:LOCALAPPDATA\AtharTraining\synthetic-pilot\report.json"
```

To compare the upstream frozen slip classifier, set `$env:ATHAR_SLIP_HEAD = "strict"` or `"high"` before running the evaluator. `strict` uses the package's conservative cutoff; `high` favors sensitivity. Keep these as measured candidates until clean-control results are reviewed.

The dependency install is local to `training/`; it does not change the website's dependency tree.

## Colab

Use the bundle already uploaded to Colab; do not upload it again. Unzip it to `/content/athar`, install `numpy scipy soundfile`, run `npm install --prefix training`, then run the Python generator and Node evaluator with `/content/athar` as the bundle directory and pilot root. Audio preparation is on CPU; the ONNX engine uses the CPU execution provider because this experiment evaluates the shipped inference path rather than fine-tuning weights.
