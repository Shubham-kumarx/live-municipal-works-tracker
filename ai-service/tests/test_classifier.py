import pytest

import services.classifier as classifier_module
from services.classifier import (
    ConfidencePolicy, ISSUE_PROMPTS, MunicipalIssueClassifier, SEVERITY_PROMPTS,
)


def test_confidence_threshold_boundaries():
    policy = ConfidencePolicy(0.45, 0.70)
    assert policy.level(0.44) == "LOW"
    assert policy.level(0.45) == "MEDIUM"
    assert policy.level(0.70) == "HIGH"


def test_invalid_confidence_configuration_is_rejected(monkeypatch):
    monkeypatch.setenv("AI_MEDIUM_CONFIDENCE_THRESHOLD", "0.8")
    monkeypatch.setenv("AI_HIGH_CONFIDENCE_THRESHOLD", "0.7")
    with pytest.raises(ValueError):
        ConfidencePolicy.from_environment()


def test_taxonomies_are_fixed_and_contain_no_demo_class():
    assert set(ISSUE_PROMPTS) == {
        "POTHOLE", "ROAD_CRACK", "GARBAGE_ACCUMULATION", "WATERLOGGING",
        "DAMAGED_STREETLIGHT", "OPEN_MANHOLE", "OTHER",
    }
    assert set(SEVERITY_PROMPTS) == {"LOW", "MEDIUM", "HIGH", "CRITICAL"}


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
    monkeypatch.setattr(classifier_module.CLIPProcessor, "from_pretrained",
                        lambda model_id, **options: calls.append(("processor", options)) or processor)
    monkeypatch.setattr(classifier_module.CLIPModel, "from_pretrained",
                        lambda model_id, **options: calls.append(("model", options)) or model)

    classifier = MunicipalIssueClassifier()
    classifier._load()

    assert [options["local_files_only"] for _, options in calls] == [True, True]
    assert classifier._processor is processor
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

    classifier = MunicipalIssueClassifier()
    classifier._load()

    assert calls == [("processor", True), ("processor", False), ("model", False)]
    assert classifier._processor is processor
    assert model.eval_called is True
