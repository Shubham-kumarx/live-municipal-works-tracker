# Municipal Issue Model Decision

This document describes an AI-assisted issue-classification component. Its output is a
reviewable suggestion and does not autonomously make municipal or administrative
decisions.

## Selected model

Phase 3 uses OpenAI CLIP ViT-B/32 through the Hugging Face model identifier
`openai/clip-vit-base-patch32`. CLIP performs real vision-language inference by comparing
an image embedding with embeddings for a fixed list of text prompts. Model files are
downloaded to `ai-service/.model-cache/` and are not committed.

The academically verified checkpoint revision is
`3d74acf9a28c67741b2f4f2ea7635f0aaf6f0268`. Its vision encoder uses 224-by-224 input,
32-by-32 patches, 12 layers, 12 attention heads, and hidden size 768. Its 12-layer text
encoder uses 8 attention heads, hidden size 512, a 49,408-token vocabulary, and at most
77 positions. Both encoders project into a shared 512-dimensional space.

The service passes this immutable revision to both `CLIPProcessor.from_pretrained` and
`CLIPModel.from_pretrained`. `AI_MODEL_REVISION` may override it together with a compatible
`AI_MODEL_ID`; reproducibility requires recording both values.

### Four-class transfer-learning decision

The existing CLIP ViT-B/32 vision encoder is retained as the four-class model backbone.
Its 512-dimensional projected image embedding is technically suitable for a small
supervised classifier over the approved municipal taxonomy, so the first training stage
will freeze the pretrained encoder and learn only a 512-to-4 linear classification head.
This reuses the model already integrated with FastAPI and avoids training an 87-million-
parameter vision encoder from the comparatively small civic-issue dataset.

The four output indexes are fixed as `DOMESTIC_TRASH`, `ILLEGAL_PARKING`, `DAMAGED_SIGN`,
and `POTHOLE`, in that order. Severity remains a separate zero-shot CLIP suggestion because
the available dataset has issue labels but no verified severity ground truth. Unfreezing
any vision layers requires evidence of underfitting from the head-training curves and is
not the default training path.

The completed linear-probe run reached validation macro-F1 `0.9944538113` and validation
accuracy `0.9956896552`. Its best-epoch training/validation performance showed no material
generalization gap. Fine-tuning the CLIP backbone was therefore not justified: the maximum
remaining validation macro-F1 headroom was about 0.0055, while unfreezing approximately
87 million pretrained parameters would materially increase overfitting risk on 1,079
training representatives. The saved model retains the frozen pretrained backbone.

Primary sources:

- Paper: https://arxiv.org/abs/2103.00020
- Official implementation and MIT license: https://github.com/openai/CLIP
- Model card: https://huggingface.co/openai/clip-vit-base-patch32

## License and usage position

OpenAI's official CLIP implementation is released under the MIT License. This statement
applies to the source implementation and must not be treated as a license for every image
in the unreleased pretraining corpus. The Hugging Face checkpoint page reproduces the
OpenAI model card but does not declare a separate checkpoint license in its repository
metadata.

The model card describes CLIP as a research output and calls for task-specific, in-domain
testing before deployment. The service is therefore presented as an academic prototype
with mandatory human confirmation, not as a validated autonomous municipal decision
system. Its English prompts also do not establish performance in other languages.

## Supported taxonomy

The application offers only these issue types:

- `DOMESTIC_TRASH` (category `SANITATION`)
- `ILLEGAL_PARKING` (category `ROAD`)
- `DAMAGED_SIGN` (category `ROAD`)
- `POTHOLE` (category `ROAD`)

Older complaint rows may retain the former issue tokens for historical readability. Those
tokens are not accepted for new AI predictions or new complaint classifications.

Severity suggestions use the fixed values `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`.
Issue and severity scores come from separate calculations.

The trained 512-to-4 head selects one highest-scoring issue from the fixed class order.
The severity suggestion is independently ranked through the existing CLIP severity
prompts. The service does not localize objects, segment damage, return multiple
simultaneous issues, or measure physical damage. "Supported" describes the configured
application vocabulary; it does not claim production validation.

## Score interpretation and human review

The returned confidence is the trained classifier head's softmax score across the four
issue outputs. It is not a calibrated probability and is not a measured real-world
correctness probability. Low-score results remain tentative and do not populate a final
issue type. A citizen must confirm, edit, reject, or manually provide the final
classification before submission.

The accuracy, precision, recall, and F1 values reported below come only from the fixed
232-image held-out split. They are measured dataset results rather than guarantees for
new locations, cameras, weather, or unsupported issue types.

The CLIP paper's benchmark numbers apply to other datasets and taxonomies and are not
reported as this application's performance. API/unit-test pass counts measure software
behavior, not model quality. The service's per-image softmax score is distinct from the
held-out aggregate accuracy and is not a calibrated probability that a prediction is
correct.

## Training status

The underlying checkpoint was pretrained by OpenAI; this project does not train CLIP from
scratch. The project now trains a four-output linear classifier over frozen, normalized
CLIP image embeddings. The actual run used 1,079 training representatives plus one
deterministic augmented view per training image, 232 validation representatives for model
selection, and 232 held-out representatives for one final evaluation.

The run used seed `2026`, class-weighted cross entropy, AdamW with learning rate `0.001`
and weight decay `0.01`, maximum 50 epochs, and validation macro-F1 for checkpoint
selection. Lower validation loss broke equal-macro-F1 ties, which selected epoch 50. The
saved training history contains actual loss and accuracy values for every epoch.

The selected checkpoint produced the following held-out results:

- loss: `0.0737825036`;
- accuracy: `0.9956896552`;
- macro precision: `0.9964788732`;
- macro recall: `0.9913793103`;
- macro-F1: `0.9938409854`.

The test confusion matrix contains one error: one of 29 pothole representatives was
classified as `DAMAGED_SIGN`. All 37 domestic-trash, 96 illegal-parking, and 70
damaged-sign representatives were classified correctly. These measurements describe only
the audited dataset and fixed split; they must not be generalized into a production or
city-wide accuracy claim.

Phase 3D preserves all 232 per-image outputs, confidence summaries, the labeled confusion
matrix, and the reviewed misclassification in `docs/ai-model-evaluation.md`. The test set
remained isolated from training and checkpoint selection, but Phase 3C had already
inspected it. Any future model comparison needs a new untouched external test set.

## Preprocessing and inference

FastAPI accepts JPEG or PNG input up to 10 MiB and fully decodes it with Pillow. The
classifier converts the image to RGB. Checkpoint processor metadata then uses bicubic
resize, a 224-by-224 center crop, and CLIP normalization with mean
`[0.48145466, 0.4578275, 0.40821073]` and standard deviation
`[0.26862954, 0.26130258, 0.27577711]`.

The service explicitly selects the audited slow Transformers processor with
`use_fast=False`; this prevents a future library default from silently changing image
preprocessing behavior.

For issue classification, the model normalizes the projected image embedding, applies the
trained linear head, and computes softmax across the four fixed issue outputs. For severity,
the CLIP tokenizer lowercases the English prompts and the model computes image-text
similarity logits before applying softmax across the four severity choices. Inference uses
PyTorch inference mode and model evaluation mode on CUDA when available, otherwise CPU.

## Dataset decision

The extracted dataset under `archive/` is the Kaggle **MLArtists Civic Issues Dataset**,
version 1, published by Nithila Thawalampitiya under the Apache 2.0 license:
https://www.kaggle.com/datasets/nithila7/mlartists-civic-issues-dataset

The canonical mapping is defined in `ai-service/issue_types.py`. It retains
`Domestic_trash`, `Parking_Issues_Illegal_Parking`, `Road_Issues_Damaged_Sign`, and
`Road_Issues_Pothole`, and excludes `Infrastructure_Damage_Concrete` and
`Vandalism_Graffiti`. Raw folders are not renamed or modified.

Phase 3A found 6,075 retained images, but also found substantial exact and perceptual
cross-split duplication. The supplied train, validation, and test split is therefore not
used directly. Dataset preparation instead built a derived, group-aware manifest that
keeps related images in one split. The trained classifier uses only one representative per
group and was evaluated on the derived held-out test partition.

The dataset-preparation stage creates that derived manifest without modifying raw files.
It combines exact hashes, normalized Roboflow source identities, and strict dual
perceptual hashes into source groups, rejects cross-class conflicts, selects one
representative per group, and assigns groups deterministically with a 70/15/15 split and
seed `2026`. The generated inventory, manifest, and report are stored under
`ai-service/data/`. Only the later training command uses these representatives to learn
the classifier head and calculate the explicitly reported validation and test metrics.

## Known limitations

- The CLIP backbone began as a zero-shot research model and was not pretrained specifically
  for this city; the project-trained head does not remove that domain limitation.
- Fixed prompt wording affects the separate severity suggestion.
- A single image may contain several issues, while this API returns one candidate.
- Poor lighting, occlusion, unusual camera angles, and regional differences can reduce
  usefulness.
- Severity inferred from an image is advisory and cannot replace an on-site assessment.
- The service performs no face recognition, identity inference, or surveillance task.
- Small or distant defects, glare, rain, blur, compression, and clutter can reduce
  usefulness.
- The model does not locate issues, count instances, measure dimensions, estimate repair
  cost, or observe hidden structural damage, traffic, or affected population.
- English prompts and broad web pretraining do not establish local or multilingual
  performance.
- Unsupported conditions still receive one of the four classifier outputs. A low score
  triggers manual review, but medium or high scores can also be wrong.
- Medium or high classifier scores can still be wrong. Human confirmation remains
  mandatory, and predictions must not autonomously control enforcement, emergency
  response, eligibility, or resource allocation.
