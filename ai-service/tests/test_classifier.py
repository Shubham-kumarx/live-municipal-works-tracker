import pytest
from PIL import Image
import torch
from transformers import CLIPProcessor

from issue_types import (
    CLASS_LABELS,
    CLASS_TO_INDEX,
    DATASET_FOLDER_TO_ISSUE_TYPE,
    EXCLUDED_DATASET_FOLDERS,
    INDEX_TO_CLASS,
)
import services.classifier as classifier_module
from services.classifier import (
    ConfidencePolicy, MODEL_CACHE, MODEL_ID, MODEL_REVISION, MunicipalIssueClassifier,
    SEVERITY_PROMPTS,
    TRAINED_MODEL_DIR,
)
from training.model import load_model_artifact


def test_confidence_threshold_boundaries():
    policy = ConfidencePolicy(0.60, 0.80)
    assert policy.level(0.59) == "LOW"
    assert policy.level(0.60) == "MEDIUM"
    assert policy.level(0.79) == "MEDIUM"
    assert policy.level(0.80) == "HIGH"


def test_default_confidence_thresholds_match_production_policy(monkeypatch):
    monkeypatch.delenv("AI_MEDIUM_CONFIDENCE_THRESHOLD", raising=False)
    monkeypatch.delenv("AI_HIGH_CONFIDENCE_THRESHOLD", raising=False)

    policy = ConfidencePolicy.from_environment()

    assert policy == ConfidencePolicy(0.60, 0.80)


def test_invalid_confidence_configuration_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_MEDIUM_CONFIDENCE_THRESHOLD", "0.8")
    monkeypatch.setenv("AI_HIGH_CONFIDENCE_THRESHOLD", "0.7")
    with pytest.raises(ValueError):
        ConfidencePolicy.from_environment()


def test_taxonomies_are_fixed_and_contain_no_demo_class():
    assert CLASS_LABELS == (
        "DOMESTIC_TRASH", "ILLEGAL_PARKING", "DAMAGED_SIGN", "POTHOLE",
    )
    assert set(SEVERITY_PROMPTS) == {"LOW", "MEDIUM", "HIGH", "CRITICAL"}


def test_dataset_folders_map_to_exact_supported_vocabulary():
    assert DATASET_FOLDER_TO_ISSUE_TYPE == {
        "Domestic_trash": "DOMESTIC_TRASH",
        "Parking_Issues_Illegal_Parking": "ILLEGAL_PARKING",
        "Road_Issues_Damaged_Sign": "DAMAGED_SIGN",
        "Road_Issues_Pothole": "POTHOLE",
    }
    assert EXCLUDED_DATASET_FOLDERS == {
        "Infrastructure_Damage_Concrete",
        "Vandalism_Graffiti",
    }
    assert tuple(DATASET_FOLDER_TO_ISSUE_TYPE.values()) == CLASS_LABELS


def test_deployed_artifact_uses_the_authoritative_four_class_order():
    head, metadata = load_model_artifact(
        TRAINED_MODEL_DIR,
        expected_model_id=MODEL_ID,
        expected_model_revision=MODEL_REVISION,
    )

    assert tuple(metadata["classLabels"]) == CLASS_LABELS
    assert metadata["classToIndex"] == CLASS_TO_INDEX
    assert INDEX_TO_CLASS == {index: label for index, label in enumerate(CLASS_LABELS)}
    assert head.classifier.out_features == len(CLASS_LABELS) == 4
    assert not {
        "INFRASTRUCTURE_DAMAGE", "VANDALISM", "GRAFFITI", "VANDALISM_GRAFFITI",
    }.intersection(metadata["classLabels"])


def test_pinned_runtime_processor_matches_evaluation_preprocessing():
    processor = CLIPProcessor.from_pretrained(
        MODEL_ID,
        revision=MODEL_REVISION,
        cache_dir=MODEL_CACHE,
        local_files_only=True,
        use_fast=False,
    )
    pixel_values = processor(
        images=Image.new("RGB", (640, 480), "gray"),
        return_tensors="pt",
    )["pixel_values"]

    assert pixel_values.shape == (1, 3, 224, 224)
    assert pixel_values.dtype == torch.float32
    assert processor.image_processor.do_resize is True
    assert processor.image_processor.do_center_crop is True
    assert processor.image_processor.do_rescale is True
    assert processor.image_processor.do_normalize is True
    assert processor.image_processor.image_mean == [0.48145466, 0.4578275, 0.40821073]
    assert processor.image_processor.image_std == [0.26862954, 0.26130258, 0.27577711]


def test_classifier_converts_decoded_input_to_rgb_for_all_inference(monkeypatch):
    classifier = MunicipalIssueClassifier()
    observed_modes = []
    monkeypatch.setattr(classifier, "_load", lambda: None)
    monkeypatch.setattr(
        classifier,
        "_classify_issue",
        lambda image: observed_modes.append(image.mode) or ("POTHOLE", 0.9),
    )
    monkeypatch.setattr(
        classifier,
        "_rank",
        lambda image, _prompts: observed_modes.append(image.mode) or ("HIGH", 0.7),
    )

    classifier.classify(Image.new("L", (12, 9), 128))

    assert observed_modes == ["RGB", "RGB"]


class FakeModel:
    def __init__(self):
        self.device = None
        self.eval_called = False

    def to(self, device):
        self.device = device
        return self

    def eval(self):
        self.eval_called = True


def test_model_loading_uses_cache_without_remote_resolution(monkeypatch):
    calls = []
    processor = object()
    model = FakeModel()
    head = object()
    monkeypatch.setattr(classifier_module.CLIPProcessor, "from_pretrained",
                        lambda model_id, **options: calls.append(("processor", options)) or processor)
    monkeypatch.setattr(classifier_module.CLIPModel, "from_pretrained",
                        lambda model_id, **options: calls.append(("model", options)) or model)
    monkeypatch.setattr(
        classifier_module,
        "load_model_artifact",
        lambda *args, **kwargs: (head, {"classLabels": list(CLASS_LABELS)}),
    )

    classifier = MunicipalIssueClassifier()
    classifier._load()

    assert [options["local_files_only"] for _, options in calls] == [True, True]
    assert [options["revision"] for _, options in calls] == [MODEL_REVISION, MODEL_REVISION]
    assert calls[0][1]["use_fast"] is False
    assert "use_fast" not in calls[1][1]
    assert classifier._processor is processor
    assert classifier._issue_head is head
    assert model.eval_called is True


def test_model_loading_falls_back_when_cache_is_incomplete(monkeypatch):
    calls = []
    processor = object()
    model = FakeModel()

    def load_processor(model_id, **options):
        calls.append(("processor", options["local_files_only"]))
        if options["local_files_only"]:
            raise OSError("model is not cached")
        return processor

    def load_model(model_id, **options):
        calls.append(("model", options["local_files_only"]))
        return model

    monkeypatch.setattr(classifier_module.CLIPProcessor, "from_pretrained", load_processor)
    monkeypatch.setattr(classifier_module.CLIPModel, "from_pretrained", load_model)
    monkeypatch.setattr(
        classifier_module,
        "load_model_artifact",
        lambda *args, **kwargs: (object(), {"classLabels": list(CLASS_LABELS)}),
    )

    classifier = MunicipalIssueClassifier()
    classifier._load()

    assert calls == [("processor", True), ("processor", False), ("model", False)]
    assert classifier._processor is processor
    assert model.eval_called is True


def test_issue_prediction_uses_trained_head_index_mapping():
    class Processor:
        def __call__(self, **_kwargs):
            return {"pixel_values": torch.zeros((1, 3, 224, 224))}

    class Model:
        def get_image_features(self, **_kwargs):
            features = torch.zeros((1, 512))
            features[0, 0] = 1.0
            return features

    class Head:
        def __call__(self, _features):
            return torch.tensor([[0.0, 0.1, 4.0, -1.0]])

    classifier = MunicipalIssueClassifier()
    classifier._processor = Processor()
    classifier._model = Model()
    classifier._issue_head = Head()

    issue_type, score = classifier._classify_issue(object())

    assert issue_type == "DAMAGED_SIGN"
    assert 0.9 < score <= 1.0
