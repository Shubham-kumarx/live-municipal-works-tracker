# Four-Class AI Model Evaluation

## Evaluation scope

This evaluation uses the fixed validation-selected classifier head with SHA-256
`09a74a5426b11643b4d7826a57616bca6e8f57f0b1e6285a2f845a30ed052511`. The checkpoint,
class mapping, preprocessing, and training configuration were not changed after viewing
test results.

The derived test partition contains 232 unique representatives:

| Class | Index | Support |
|---|---:|---:|
| `DOMESTIC_TRASH` | 0 | 37 |
| `ILLEGAL_PARKING` | 1 | 96 |
| `DAMAGED_SIGN` | 2 | 70 |
| `POTHOLE` | 3 | 29 |

`Infrastructure_Damage_Concrete` and `Vandalism_Graffiti` do not participate in these
metrics. The test rows did not participate in training, augmentation, early stopping, or
checkpoint selection. Phase 3C already evaluated this partition and used four test images
for runtime smoke checks, so Phase 3D is reproducible analysis rather than a new blind
evaluation.

## Measured results

| Metric | Value |
|---|---:|
| Test loss | 0.0737825036 |
| Correct / total | 231 / 232 |
| Accuracy | 0.9956896552 |
| Macro precision | 0.9964788732 |
| Macro recall | 0.9913793103 |
| Macro-F1 | 0.9938409854 |

| Class | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| `DOMESTIC_TRASH` | 1.0000000000 | 1.0000000000 | 1.0000000000 | 37 |
| `ILLEGAL_PARKING` | 1.0000000000 | 1.0000000000 | 1.0000000000 | 96 |
| `DAMAGED_SIGN` | 0.9859154930 | 1.0000000000 | 0.9929078014 | 70 |
| `POTHOLE` | 1.0000000000 | 0.9655172414 | 0.9824561404 | 29 |

The confusion matrix uses actual classes as rows and predicted classes as columns:

| Actual / Predicted | Domestic Trash | Illegal Parking | Damaged Sign | Pothole |
|---|---:|---:|---:|---:|
| Domestic Trash | 37 | 0 | 0 | 0 |
| Illegal Parking | 0 | 96 | 0 | 0 |
| Damaged Sign | 0 | 0 | 70 | 0 |
| Pothole | 0 | 0 | 1 | 28 |

## Confidence analysis

The classifier head's softmax values are relative scores across the four available
outputs. They are not calibrated probabilities of correctness.

- Overall mean confidence: `0.9409441775`
- Overall median confidence: `0.9677131474`
- Overall range: `0.3819949627` to `0.9939720035`
- Mean confidence for 231 correct predictions: `0.9433638710`
- Confidence for the single incorrect prediction: `0.3819949627`
- Incorrect-prediction top-two margin: `0.0160471201`

| Confidence range | Count | Correct | Observed accuracy |
|---|---:|---:|---:|
| `[0.00, 0.50)` | 2 | 1 | 0.5000000000 |
| `[0.50, 0.70)` | 4 | 4 | 1.0000000000 |
| `[0.70, 0.90)` | 27 | 27 | 1.0000000000 |
| `[0.90, 1.00]` | 199 | 199 | 1.0000000000 |

These bins describe only this small fixed test partition. They do not establish that a
score has the same correctness frequency for new images.

## Misclassification analysis

The only incorrect prediction was:

```text
Path: archive/train/Road_Issues_Pothole/images207_jpg.rf.793529af5b8f6013aed916e07a154d92.jpg
Actual: POTHOLE
Predicted: DAMAGED_SIGN
Predicted score: 0.3819949627
Runner-up: POTHOLE
Runner-up score: 0.3659478426
Margin: 0.0160471201
```

The image is a close-up of a vertical cracked surface with a narrow opening. No road scene
or road-surface depression is visible. This makes the retained `POTHOLE` ground truth
visually ambiguous and possibly mislabeled. The ground truth remains unchanged; this
observation is not proof of the dataset creator's intent.

## Limitations

- The metrics measure one fixed dataset and cannot establish city-wide or production
  performance.
- The test partition has already been inspected, so future model changes require a new
  untouched external test set for an unbiased comparison.
- Class support is unequal, ranging from 29 potholes to 96 illegal-parking images.
- Only four issue types are supported. An unsupported scene is still forced into one of
  these four outputs.
- The softmax scores are not calibrated probabilities.
- The source dataset may contain ambiguous or incorrectly assigned labels, as suggested by
  the single observed error.
- Dataset collection sources, image style, geography, and camera conditions may differ
  from future citizen submissions.
- Blur, glare, darkness, occlusion, distant issues, unusual viewpoints, and compression
  can reduce performance.
- The model returns one class and does not perform multi-label classification, detection,
  localization, segmentation, dimension measurement, or repair-cost estimation.
- Severity is not trained by this dataset. It remains a separate zero-shot CLIP suggestion.
- Human confirmation remains required before complaint submission and administrative use.

## Reproduction artifacts

- `ai-service/models/four-class-clip/evaluation.json`
- `ai-service/models/four-class-clip/test_predictions.csv`
- `ai-service/models/four-class-clip/confusion_matrix.csv`
- `ai-service/models/four-class-clip/classification_report.md`
- `ai-service/models/four-class-clip/confidence_analysis.json`
- `ai-service/models/four-class-clip/misclassifications.json`

All numerical statements above are generated from or checked against these artifacts.
