import pytest

from services.classifier import ConfidencePolicy, ISSUE_PROMPTS, SEVERITY_PROMPTS


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
