# Municipal Issue AI Service

This FastAPI service performs local computer-vision inference for the Spring Boot
application. Browser clients must use the Spring Boot complaint endpoints and must not
call this service directly.

## Local setup

From `ai-service/`:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

The service loads and validates the trained classifier during application startup. The
health endpoint is `GET /health`; a running application has therefore completed model
startup successfully. Base-model files are stored in `ai-service/.model-cache/` and are
excluded from Git.

At startup, the service checks the local cache without contacting the model
registry. If the cache is incomplete, it falls back to the standard model download.
After one successful download, later starts can load entirely from the local cache.
`AI_MODEL_ID`, when overridden, must identify a CLIP-compatible Transformers model.
The default checkpoint is pinned to Hugging Face revision
`3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268` through `AI_MODEL_REVISION`. If the model
identifier is overridden, set its matching immutable revision as well; otherwise the
default OpenAI revision will not exist in that repository.

To populate an empty cache reproducibly, install the pinned requirements and run one
inference while network access is available:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Submit a valid JPEG or PNG to `POST /predict`. The first startup downloads the pinned
processor and checkpoint into `.model-cache` when needed; later starts load locally. No
account or license-acceptance click is currently required for the public checkpoint. Do
not commit `.model-cache`.

Optional confidence settings:

```text
AI_MEDIUM_CONFIDENCE_THRESHOLD=0.60
AI_HIGH_CONFIDENCE_THRESHOLD=0.80
```

These values classify the trained issue head's softmax score; they are not accuracy or
calibration guarantees. They must satisfy `0 <= medium <= high <= 1`. A score below the
medium threshold is returned as a tentative candidate and requires manual classification.
The default policy treats scores below `0.60` as low confidence, scores from `0.60` up to
`0.80` as a possible prediction requiring verification, and scores of at least `0.80` as
high confidence. Environment overrides must preserve the same ordered range constraint.

## Supported issue types and dataset mapping

The inference API returns only `DOMESTIC_TRASH`, `ILLEGAL_PARKING`, `DAMAGED_SIGN`, or
`POTHOLE`. The canonical mapping is defined once in `issue_types.py`:

| Dataset folder | API issue type |
|---|---|
| `Domestic_trash` | `DOMESTIC_TRASH` |
| `Parking_Issues_Illegal_Parking` | `ILLEGAL_PARKING` |
| `Road_Issues_Damaged_Sign` | `DAMAGED_SIGN` |
| `Road_Issues_Pothole` | `POTHOLE` |

`Infrastructure_Damage_Concrete` and `Vandalism_Graffiti` are explicitly excluded.
The raw dataset folders are not renamed. The trained issue classifier uses the fixed
class order shown in this table. Severity remains a separate zero-shot suggestion because
the dataset does not contain verified severity labels.

## Prediction response

`POST /predict` returns this contract:

```json
{
  "issueType": "POTHOLE",
  "candidateIssueType": "POTHOLE",
  "confidence": 0.91,
  "confidenceLevel": "HIGH",
  "category": "ROAD",
  "suggestedSeverity": "HIGH",
  "requiresManualReview": false
}
```

`candidateIssueType` and `confidence` come from the trained four-class head. `issueType`
is null for a low-confidence result so that the candidate cannot be treated as confirmed.
`category` comes from the fixed application mapping in `issue_types.py`.
`suggestedSeverity` comes from a separate zero-shot CLIP prompt comparison; it was not
trained or evaluated as part of the four-class dataset and remains advisory.

## Leakage-safe dataset preparation

Place the extracted Kaggle dataset at repository-root `archive/`, then run from
`ai-service/`:

```powershell
.venv\Scripts\python.exe -m training.dataset_preparation `
  --dataset-root ../archive `
  --output-dir data
```

The command fully decodes retained images, records SHA-256 and perceptual fingerprints,
rejects cross-class conflicts, groups exact duplicates and source variants, selects one
deterministic representative per group, and creates 70/15/15 class-stratified splits with
seed `2026`. It writes:

- `data/four_class_inventory.csv` for complete raw-file traceability;
- `data/four_class_manifest.csv` for baseline training and evaluation;
- `data/four_class_dataset_report.json` for counts, digests, and readiness evidence.

The raw `archive/` directory is ignored by Git and is never renamed or modified. Dataset
preparation does not train or fine-tune the model.

## Reproduce four-class training

The training pipeline reuses the pinned CLIP ViT-B/32 image encoder, freezes it, and
learns a 512-to-4 linear classifier head. Run from `ai-service/` after preparing and
validating the manifest:

```powershell
.venv\Scripts\python.exe -m training.train `
  --manifest data/four_class_manifest.csv `
  --report data/four_class_dataset_report.json `
  --repository-root .. `
  --output-dir models/four-class-clip `
  --seed 2026 `
  --image-size 224 `
  --feature-batch-size 16 `
  --batch-size 64 `
  --epochs 50 `
  --learning-rate 0.001 `
  --patience 8
```

Training uses inverse-frequency class-weighted cross entropy and AdamW with weight decay
`0.01`. Checkpoint selection uses validation macro-F1, with lower validation loss breaking
a tie. The test split is not used for optimization, early stopping, or model selection.

After training, evaluate the selected checkpoint once on the held-out test split:

```powershell
.venv\Scripts\python.exe -m training.train `
  --manifest data/four_class_manifest.csv `
  --report data/four_class_dataset_report.json `
  --repository-root .. `
  --output-dir models/four-class-clip `
  --evaluate-only
```

The completed CPU run selected epoch 50. It achieved validation macro-F1 `0.99445` and
held-out test macro-F1 `0.99384` on 232 representatives. These results apply only to the
audited dataset and split; they are not evidence of production or city-wide performance.
Fine-tuning was skipped because the linear probe already had high validation performance
and a small train/validation gap.

Generated artifacts:

- `models/four-class-clip/classifier_head.pt` — trained 512-to-4 head;
- `models/four-class-clip/model_metadata.json` — class mapping, hashes, and configuration;
- `models/four-class-clip/training_history.json` — actual epoch history;
- `models/four-class-clip/evaluation.json` — held-out test results.
- `models/four-class-clip/test_predictions.csv` — deterministic per-image predictions;
- `models/four-class-clip/confusion_matrix.csv` — labeled actual/predicted counts;
- `models/four-class-clip/classification_report.md` — readable metric report;
- `models/four-class-clip/confidence_analysis.json` — descriptive score analysis;
- `models/four-class-clip/misclassifications.json` — reviewed errors.

See `../docs/ai-model-evaluation.md` for the evaluation method, confidence interpretation,
single observed misclassification, and limitations. The test split was isolated from
training and model selection, but it was already inspected during Phase 3C; it must not be
presented as a newly unseen test set after that phase.
