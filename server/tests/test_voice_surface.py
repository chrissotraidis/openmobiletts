"""Voice surfaces must not infer unsupported languages from speaker names."""

from src.sherpa_backend import _LANG_MAP, _VOICE_TO_SID


def test_sherpa_voice_surface_is_limited_to_accepted_english_ranges():
    assert set(_LANG_MAP) == {"a", "b"}
    assert len(_VOICE_TO_SID) == 28
    assert set(name[0] for name in _VOICE_TO_SID) == {"a", "b"}
    assert min(_VOICE_TO_SID.values()) == 0
    assert max(_VOICE_TO_SID.values()) == 27
