# Municipal Issue Model Decision

## Selected model

Phase 3 uses OpenAI CLIP ViT-B/32 through the Hugging Face model identifier
`openai/clip-vit-base-patch32`. CLIP performs real vision-language inference by comparing
an image embedding with embeddings for a fixed list of text prompts. Model files are
downloaded to `ai-service/.model-cache/` and are not committed.

Primary sources:

- Paper: https://arxiv.org/abs/2103.00020
- Official implementation and MIT license: https://github.com/openai/CLIP
- Model card: https://huggingface.co/openai/clip-vit-base-patch32

## Supported taxonomy

The application offers only these issue types:

- `POTHOLE` (category `ROAD`)
- `ROAD_CRACK` (category `ROAD`)
- `GARBAGE_ACCUMULATION` (category `SANITATION`)
- `WATERLOGGING` (category `DRAINAGE`)
- `DAMAGED_STREETLIGHT` (category `LIGHTING`)
- `OPEN_MANHOLE` (category `SEWER`)
- `OTHER` (category `OTHER`)

Severity suggestions use the fixed values `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`.
Issue and severity scores come from separate CLIP prompt comparisons.

## Score interpretation and human review

The returned confidence is the softmax score relative to the configured prompts. It is
not a calibrated probability and is not a measured real-world accuracy value. Low-score
results remain tentative and do not populate a final issue type. A citizen must confirm,
edit, reject, or manually provide the final classification before submission.

No accuracy, precision, recall, or F1 metric is claimed in this phase. Such claims would
require a separate labeled, representative municipal evaluation set.

## Dataset decision

CLIP was trained on about 400 million web image-text pairs. The original training corpus
is not packaged with this project. Its broad pretraining makes a constrained zero-shot
prototype feasible, but its model card warns that performance varies by taxonomy and that
in-domain testing is required before deployment.

RDD2022 was also evaluated as a source. It is a published multi-country road-damage
dataset containing Indian data, but its four-class road-damage taxonomy does not cover
garbage, waterlogging, streetlights, or manholes. It is therefore not presented as the
training dataset for the full complaint classifier. It may support a later specialized
road-damage model.

- Official RDD2022 project: https://github.com/sekilab/RoadDamageDetector
- Dataset publication: https://doi.org/10.1111/gdj3.201

## Known limitations

- CLIP is a zero-shot research model and was not trained specifically for this city.
- The fixed prompt wording affects relative scores.
- A single image may contain several issues, while this API returns one candidate.
- Poor lighting, occlusion, unusual camera angles, and regional differences can reduce
  usefulness.
- Severity inferred from an image is advisory and cannot replace an on-site assessment.
- The service performs no face recognition, identity inference, or surveillance task.
