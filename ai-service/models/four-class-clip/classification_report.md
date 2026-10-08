# Four-Class Civic Issue Model Evaluation

This report evaluates the fixed validation-selected checkpoint on the derived
test partition. The test rows did not participate in training or checkpoint
selection, although this partition was previously inspected during Phase 3C.

- Checkpoint SHA-256: `09a74a5426b11643b4d7826a57616bca6e8f57f0b1e6285a2f845a30ed052511`
- Manifest SHA-256: `30b60f5d65e489aae154d4afd1b4a30da36028350e07bdcbf7c2d34bc0f21f3f`
- Test samples: 232
- Correct predictions: 231
- Accuracy: 0.9956896552
- Macro precision: 0.9964788732
- Macro recall: 0.9913793103
- Macro-F1: 0.9938409854

## Per-Class Metrics

| Class | Precision | Recall | F1 | Support |
|---|---:|---:|---:|---:|
| DOMESTIC_TRASH | 1.0000000000 | 1.0000000000 | 1.0000000000 | 37 |
| ILLEGAL_PARKING | 1.0000000000 | 1.0000000000 | 1.0000000000 | 96 |
| DAMAGED_SIGN | 0.9859154930 | 1.0000000000 | 0.9929078014 | 70 |
| POTHOLE | 1.0000000000 | 0.9655172414 | 0.9824561404 | 29 |

## Confusion Matrix

Rows are actual classes and columns are predicted classes.

| Actual / Predicted | DOMESTIC_TRASH | ILLEGAL_PARKING | DAMAGED_SIGN | POTHOLE |
|---|---:|---:|---:|---:|
| DOMESTIC_TRASH | 37 | 0 | 0 | 0 |
| ILLEGAL_PARKING | 0 | 96 | 0 | 0 |
| DAMAGED_SIGN | 0 | 0 | 70 | 0 |
| POTHOLE | 0 | 0 | 1 | 28 |

These measurements describe this audited dataset and split only. They do not
establish calibrated confidence or production performance in new locations.
