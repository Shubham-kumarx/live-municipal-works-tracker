# Phase 16 AI Model and Dataset Academic Verification

## Scope and Evidence Standard

This audit describes the AI implementation that is actually present in the repository. It separates functional software tests from model-quality evaluation and does not infer metrics that were never measured.

Primary evidence:

- OpenAI CLIP paper: <https://arxiv.org/abs/2103.00020>
- OpenAI CLIP implementation and model card: <https://github.com/openai/CLIP>
- Hugging Face checkpoint: <https://huggingface.co/openai/clip-vit-base-patch32>
- RDD2022 project considered during model selection: <https://github.com/sekilab/RoadDamageDetector>
- Repository implementation: `ai-service/services/classifier.py`

## T16.1 Dataset Source Verification

The project does not contain or use a municipal training dataset. It performs zero-shot inference with OpenAI's pretrained CLIP ViT-B/32 checkpoint distributed through the Hugging Face identifier `openai/clip-vit-base-patch32`.

The original CLIP model was pretrained by OpenAI on approximately 400 million image-text pairs assembled from publicly available internet sources. OpenAI describes sources that include YFCC100M and internet crawling, but does not release the full corpus. Consequently, the original pretraining set cannot be independently enumerated, redistributed, or reproduced by this project.

RDD2022 was reviewed because it contains road-damage images from six countries, including India. It covers longitudinal cracks, transverse cracks, alligator cracks, and potholes. It does not cover garbage accumulation, waterlogging, damaged streetlights, or open manholes. No RDD2022 image, annotation, split, or trained weight is used by this repository.

The single `artifacts/codex/Pothole.jpg` file is a demonstration image, not a training or evaluation dataset.

## T16.2 License and Usage Notes

The official OpenAI CLIP source-code repository is licensed under the MIT License. That license applies to the implementation and accompanying software; it must not be represented as a license for every image in the unreleased pretraining corpus.

The Hugging Face checkpoint page reproduces OpenAI's model card but does not declare a separate checkpoint license in its repository metadata. This project therefore cites the MIT implementation license while separately preserving the checkpoint's model-card usage conditions instead of asserting an unverified weight license.

The official model card describes CLIP as a research output. It says deployed uses require careful task-specific study, fixed-taxonomy in-domain testing, and evaluation of bias and performance. It also says the original model was not purposefully trained or evaluated for languages other than English. The current college project is an academic prototype with mandatory human confirmation; it is not evidence of production readiness.

RDD2022 licensing is relevant only if a future phase actually downloads or uses that dataset. It does not currently affect this repository because RDD2022 is neither bundled nor used.

## T16.3 Supported Classes

The underlying CLIP model is open-vocabulary, but this application exposes only the fixed English prompt taxonomy below. “Supported” means that the service compares an image against the listed prompt; it does not mean that the class has been validated on a representative dataset.

| API issue type | Category | Exact prompt |
|---|---|---|
| `POTHOLE` | `ROAD` | `a photo of a pothole in a road` |
| `ROAD_CRACK` | `ROAD` | `a photo of a cracked or broken road surface` |
| `GARBAGE_ACCUMULATION` | `SANITATION` | `a photo of accumulated garbage or an overflowing waste pile` |
| `WATERLOGGING` | `DRAINAGE` | `a photo of waterlogging or flooding on a street` |
| `DAMAGED_STREETLIGHT` | `LIGHTING` | `a photo of a damaged or non-working streetlight` |
| `OPEN_MANHOLE` | `SEWER` | `a photo of an open or dangerously uncovered manhole` |
| `OTHER` | `OTHER` | `a photo with no visible supported municipal infrastructure issue` |

Severity is ranked independently with four prompts describing `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`. The service returns one highest-scoring issue candidate and one highest-scoring severity suggestion. It does not perform object localization, segmentation, multi-label detection, damage measurement, or causal assessment.

## T16.4 Exact Preprocessing and Inference

1. The FastAPI layer accepts JPEG or PNG files up to 10 MiB, rejects empty/corrupt images, and rejects decompression-bomb warnings or errors.
2. Pillow fully decodes the image. The classifier converts it to three-channel RGB.
3. The checkpoint's slow `CLIPProcessor` implementation (`use_fast=False`) resizes the image to 224 pixels with bicubic resampling, performs a 224×224 center crop, converts pixels to tensors, and normalizes them with:
   - mean: `[0.48145466, 0.4578275, 0.40821073]`
   - standard deviation: `[0.26862954, 0.26130258, 0.27577711]`
4. Prompt text is lowercased and tokenized with the checkpoint's CLIP byte-pair tokenizer. The configured maximum sequence length is 77 tokens; all current prompts fit within it.
5. The processor batches one image with every prompt in the selected prompt group.
6. `CLIPModel` produces image-text similarity logits. The service applies softmax across only the prompts in that group and selects the maximum.
7. Issue and severity rankings are separate inference comparisons. Scores are therefore relative to their respective prompt sets.
8. PyTorch inference mode disables gradient calculation. CUDA is used when available; otherwise inference runs on CPU. The model is placed in evaluation mode.

The processor metadata is part of the checkpoint, while the RGB conversion and prompt sets are project code. Changing prompt wording, prompt membership, model revision, processor metadata, or dependency versions can change scores.

## T16.5 Model Architecture and Version

- Model family: OpenAI CLIP (Contrastive Language–Image Pretraining)
- Hugging Face identifier: `openai/clip-vit-base-patch32`
- Verified checkpoint revision: `3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268`
- Image encoder: Vision Transformer ViT-B/32
- Image size: 224×224
- Patch size: 32×32
- Vision Transformer: 12 layers, 12 attention heads, hidden size 768, intermediate size 3072
- Text encoder: masked self-attention Transformer, 12 layers, 8 attention heads, hidden size 512, intermediate size 2048
- Text vocabulary: 49,408 tokens
- Maximum text positions: 77
- Shared projection dimension: 512
- Activations: QuickGELU
- Objective used by the original pretrained model: contrastive image-text similarity
- Project inference libraries: PyTorch 2.8.0 and Transformers 4.56.2

The revision is taken from the locally verified Hugging Face cache reference. The repository does not store the approximately hundreds-of-megabytes checkpoint binary in Git.

## T16.6 Training Status

| Question | Verified answer |
|---|---|
| Is the model pretrained? | Yes. OpenAI pretrained the CLIP checkpoint. |
| Was it fine-tuned by this project? | No. |
| Was it trained from scratch by this project? | No. |
| Does the repository contain a training loop? | No. |
| Does the repository contain optimizer, loss, epoch, or checkpoint-writing code? | No. |
| Are the municipal prompts learned parameters? | No. They are hand-written inference inputs. |

The project contribution is application integration and a fixed zero-shot prompt policy around a third-party pretrained checkpoint. Configuring prompts and confidence thresholds does not constitute model training or fine-tuning.

## T16.7 Dataset Split

Training, validation, and test splits are **not applicable** to the current project implementation because it performs no training or fine-tuning and contains no labeled municipal evaluation dataset.

The AI unit-test images are generated fixtures used to verify request validation and response behavior. The single pothole demonstration image verifies that real inference can execute. Neither is a statistically meaningful evaluation split.

If a future phase collects a municipal dataset, its split must be documented before training and separated by collection location or session to prevent near-duplicate leakage. That future dataset is outside this audit.

## T16.8 Metrics

No project-specific model-quality metric exists. The repository contains no labeled evaluation set, ground-truth manifest, evaluation runner, prediction report, confusion matrix, or per-class metric output. Therefore this project makes no claim for:

- accuracy;
- precision;
- recall;
- F1 or macro-F1;
- mean average precision;
- calibration error;
- confusion matrix;
- municipal production performance.

The CLIP paper's benchmark results measure different tasks and taxonomies and cannot be transferred to these seven municipal prompts. The API's returned softmax value is relative to the current prompt alternatives and is not measured accuracy, calibrated confidence, or probability that the real-world diagnosis is correct.

Passing unit and integration tests demonstrates deterministic software behavior, validation, and schema conformance. It does not demonstrate classifier correctness on real municipal imagery.

## T16.9 Limitations and Unsupported Cases

- Any issue outside the six named issue classes can only compete for the broad `OTHER` prompt. The service does not identify the actual unsupported issue.
- A photograph containing multiple issues receives one issue candidate; concurrent issues can be missed.
- The model does not locate the issue within the image, count instances, measure dimensions, or estimate repair cost.
- The severity suggestion is inferred from visual/text similarity. It cannot assess hidden structural damage, traffic volume, affected population, legal standards, or on-site danger.
- Prompt wording and the set of competing prompts materially affect every relative score.
- Low light, glare, rain, blur, compression, occlusion, distant/small defects, unusual viewpoints, and clutter can reduce usefulness.
- CLIP was not trained specifically on this city's roads, sanitation infrastructure, drainage, streetlights, or manholes. Architecture, construction practices, climate, and camera conditions can cause regional domain shift.
- The prompts are English. The model card does not establish multilingual performance.
- The unreleased pretraining corpus limits provenance analysis and exact training-data reproduction.
- The model card reports sensitivity to class design and known demographic/geographic biases in broad pretraining data.
- No representative in-domain evaluation has established safe thresholds for this taxonomy.
- `LOW`, `MEDIUM`, and `HIGH` confidence levels are project thresholds over relative prompt scores, not statistical guarantees.
- A low-confidence result is withheld as a final issue type, but a medium/high score can still be wrong.
- Human confirmation is mandatory. The prediction must not autonomously determine enforcement, resource allocation, emergency response, or citizen eligibility.

Unsupported examples include water-pipe leaks, fallen trees, damaged footpaths, sewage overflow without a visible open manhole, unsafe buildings, traffic-signal failures, bridge damage, and any other municipal condition without a dedicated prompt.

## T16.10 Reproduction Instructions

The default code pins both processor and model downloads to:

```text
Model ID:       openai/clip-vit-base-patch32
Model revision: 3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268
Transformers:   4.56.2
PyTorch:        2.8.0
Pillow:         11.3.0
```

From `ai-service/` on Windows PowerShell:

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r requirements.txt
.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8000
```

Send one valid JPEG or PNG to `POST /predict`. Loading first checks `.model-cache` with network resolution disabled. If the pinned files are incomplete, Transformers downloads that immutable revision from the public Hugging Face repository and stores it under `.model-cache`. Later inference can run from the local cache.

No account or interactive license acceptance is currently required. Model binaries remain excluded through `.gitignore` and must not be committed. If `AI_MODEL_ID` is changed, `AI_MODEL_REVISION` must also be set to an immutable revision belonging to that repository, and the change invalidates direct comparison with results from the audited checkpoint.

## Verification Results

- AI functional suite: 14 tests passed.
- Cache-first behavior: tested without remote resolution.
- Incomplete-cache fallback: tested.
- Model and processor revision propagation: tested.
- Processor implementation: explicitly fixed to `use_fast=False` and tested.
- Real cached inference with `artifacts/codex/Pothole.jpg`: completed successfully using the audited model ID and revision.
- Demonstration result: `ROAD_CRACK` relative score `0.5274395942687988`; suggested severity `HIGH` relative score `0.6567380428314209`.

The demonstration result proves that real inference executes reproducibly with the cached checkpoint. It is one observation and is not an accuracy, precision, recall, F1, or calibration measurement.
