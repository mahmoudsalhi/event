"""
Flask app that serves the trained no-show prediction model.

Usage:
    pip install flask joblib pandas scikit-learn xgboost
    python app.py
    # then open http://localhost:5000

Endpoints:
    GET  /          -> HTML form
    POST /predict   -> takes form fields (or JSON), returns prediction
    POST /api/predict -> JSON-only API for programmatic clients (Spring backend, etc.)

The model file (`model.joblib`) is produced by the notebook in section 11.
"""

from pathlib import Path

import joblib
import pandas as pd
from flask import Flask, jsonify, request

app = Flask(__name__)

MODEL_PATH = Path(__file__).parent / "model.joblib"
model = joblib.load(MODEL_PATH)
print(f"Loaded model from {MODEL_PATH}")

CATEGORIES = [
    "STORYTELLING_PARK", "NATURE_VOCAB_WALK", "TREASURE_HUNT", "ROLE_PLAY_GAMES",
    "ENGLISH_PICNIC", "SPORTS_AND_ENGLISH", "OUTDOOR_THEATER", "ARTS_AND_CRAFTS",
    "FIELD_TRIP", "ENGLISH_CAMP_DAY",
]
ENGLISH_LEVELS = ["A1_BEGINNER", "A2_ELEMENTARY", "B1_INTERMEDIATE"]
TARGET_LEVELS = ENGLISH_LEVELS + ["ALL_LEVELS"]


def build_features(payload: dict) -> pd.DataFrame:
    """Take raw form/JSON input, derive engineered features, return single-row DataFrame
    matching exactly what the trained pipeline expects."""
    row = {
        "kid_age": int(payload["kid_age"]),
        "kid_english_level": payload["kid_english_level"],
        "previous_events_attended": int(payload["previous_events_attended"]),
        "previous_no_shows": int(payload["previous_no_shows"]),
        "event_category": payload["event_category"],
        "event_target_level": payload["event_target_level"],
        "event_duration_hours": float(payload["event_duration_hours"]),
        "event_price_tnd": float(payload["event_price_tnd"]),
        "distance_km": float(payload["distance_km"]),
        "days_until_event": int(payload["days_until_event"]),
        "is_weekend": int(payload.get("is_weekend", 0)),
        "event_is_featured": int(payload.get("event_is_featured", 0)),
        "weather_rain_prob": float(payload["weather_rain_prob"]),
        "weather_temp_c": float(payload["weather_temp_c"]),
        "sms_reminder_sent": int(payload.get("sms_reminder_sent", 0)),
    }
    # engineered features (must match notebook section 3.7)
    if row["previous_events_attended"] > 0:
        row["past_attendance_rate"] = (
            (row["previous_events_attended"] - row["previous_no_shows"])
            / max(row["previous_events_attended"], 1)
        )
    else:
        row["past_attendance_rate"] = 0.5
    row["level_match"] = int(
        row["event_target_level"] == "ALL_LEVELS"
        or row["kid_english_level"] == row["event_target_level"]
    )
    return pd.DataFrame([row])


HTML = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Kid Attendance Predictor</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 760px; margin: 30px auto; padding: 20px; color: #222; }
  h1 { margin-bottom: 4px; }
  .sub { color: #666; margin-bottom: 24px; }
  form { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; }
  label { display: flex; flex-direction: column; font-size: 14px; color: #333; }
  input, select { padding: 8px; font-size: 14px; border: 1px solid #ccc; border-radius: 6px; margin-top: 4px; }
  .checkboxes { grid-column: 1 / -1; display: flex; gap: 24px; padding: 8px 0; }
  .checkboxes label { flex-direction: row; align-items: center; gap: 6px; }
  button { grid-column: 1 / -1; padding: 12px; font-size: 16px; background: #2563eb; color: white;
           border: none; border-radius: 6px; cursor: pointer; margin-top: 12px; }
  button:hover { background: #1d4ed8; }
  .result { margin-top: 24px; padding: 18px; border-radius: 8px; }
  .result.attend  { background: #dcfce7; border: 1px solid #16a34a; color: #14532d; }
  .result.noshow  { background: #fee2e2; border: 1px solid #dc2626; color: #7f1d1d; }
  .prob { font-size: 13px; margin-top: 6px; }
</style>
</head>
<body>
  <h1>Kid Attendance Predictor</h1>
  <p class="sub">Outdoor English-learning events for kids. Fill in the registration details to see whether the model predicts the kid will attend.</p>

  <form method="post" action="/predict">
    <label>Kid age
      <input type="number" name="kid_age" min="4" max="16" value="{{kid_age}}" required>
    </label>
    <label>Kid English level
      <select name="kid_english_level">
        {% for lvl in english_levels %}<option value="{{lvl}}" {% if lvl == kid_english_level %}selected{% endif %}>{{lvl}}</option>{% endfor %}
      </select>
    </label>

    <label>Previous events attended
      <input type="number" name="previous_events_attended" min="0" value="{{previous_events_attended}}" required>
    </label>
    <label>Previous no-shows
      <input type="number" name="previous_no_shows" min="0" value="{{previous_no_shows}}" required>
    </label>

    <label>Event category
      <select name="event_category">
        {% for c in categories %}<option value="{{c}}" {% if c == event_category %}selected{% endif %}>{{c}}</option>{% endfor %}
      </select>
    </label>
    <label>Event target level
      <select name="event_target_level">
        {% for lvl in target_levels %}<option value="{{lvl}}" {% if lvl == event_target_level %}selected{% endif %}>{{lvl}}</option>{% endfor %}
      </select>
    </label>

    <label>Event duration (hours)
      <input type="number" step="0.5" name="event_duration_hours" min="0.5" max="12" value="{{event_duration_hours}}" required>
    </label>
    <label>Event price (TND, 0 = free)
      <input type="number" step="1" name="event_price_tnd" min="0" max="500" value="{{event_price_tnd}}" required>
    </label>

    <label>Distance to event (km)
      <input type="number" step="0.1" name="distance_km" min="0" max="100" value="{{distance_km}}" required>
    </label>
    <label>Days until event
      <input type="number" name="days_until_event" min="0" max="60" value="{{days_until_event}}" required>
    </label>

    <label>Rain probability (0 to 1)
      <input type="number" step="0.01" name="weather_rain_prob" min="0" max="1" value="{{weather_rain_prob}}" required>
    </label>
    <label>Temperature (°C)
      <input type="number" step="0.5" name="weather_temp_c" min="-10" max="50" value="{{weather_temp_c}}" required>
    </label>

    <div class="checkboxes">
      <label><input type="checkbox" name="is_weekend" value="1" {% if is_weekend %}checked{% endif %}> Weekend</label>
      <label><input type="checkbox" name="event_is_featured" value="1" {% if event_is_featured %}checked{% endif %}> Featured event</label>
      <label><input type="checkbox" name="sms_reminder_sent" value="1" {% if sms_reminder_sent %}checked{% endif %}> SMS reminder sent</label>
    </div>

    <button type="submit">Predict attendance</button>
  </form>

  {% if prediction is not none %}
    <div class="result {% if prediction == 1 %}attend{% else %}noshow{% endif %}">
      <strong>Prediction: {% if prediction == 1 %}WILL ATTEND ✓{% else %}LIKELY NO-SHOW ✗{% endif %}</strong>
      <div class="prob">P(attend) = {{ "%.1f"|format(p_attend * 100) }}%   ·   P(no-show) = {{ "%.1f"|format((1 - p_attend) * 100) }}%</div>
    </div>
  {% endif %}
</body>
</html>
"""

DEFAULTS = {
    "kid_age": 8,
    "kid_english_level": "A2_ELEMENTARY",
    "previous_events_attended": 5,
    "previous_no_shows": 1,
    "event_category": "TREASURE_HUNT",
    "event_target_level": "A2_ELEMENTARY",
    "event_duration_hours": 2,
    "event_price_tnd": 30,
    "distance_km": 5,
    "days_until_event": 4,
    "is_weekend": 1,
    "event_is_featured": 1,
    "weather_rain_prob": 0.1,
    "weather_temp_c": 24,
    "sms_reminder_sent": 1,
}


def render(prediction=None, p_attend=None, form=None):
    from flask import render_template_string
    ctx = {**DEFAULTS, **(form or {})}
    return render_template_string(
        HTML,
        categories=CATEGORIES,
        english_levels=ENGLISH_LEVELS,
        target_levels=TARGET_LEVELS,
        prediction=prediction,
        p_attend=p_attend,
        **ctx,
    )


@app.route("/", methods=["GET"])
def home():
    return render()


@app.route("/predict", methods=["POST"])
def predict():
    form = request.form.to_dict()
    # checkboxes don't appear in form data when unchecked
    for box in ("is_weekend", "event_is_featured", "sms_reminder_sent"):
        form.setdefault(box, 0)
    try:
        X = build_features(form)
        p_attend = float(model.predict_proba(X)[0, 1])
        prediction = int(p_attend >= 0.5)
    except (KeyError, ValueError) as e:
        return f"Bad input: {e}", 400
    return render(prediction=prediction, p_attend=p_attend, form=form)


@app.route("/api/predict", methods=["POST"])
def api_predict():
    """JSON API for programmatic clients (e.g. your Spring backend)."""
    payload = request.get_json(force=True)
    try:
        X = build_features(payload)
        p_attend = float(model.predict_proba(X)[0, 1])
    except (KeyError, ValueError) as e:
        return jsonify({"error": f"Bad input: {e}"}), 400
    return jsonify({
        "p_attend":  round(p_attend, 4),
        "p_no_show": round(1 - p_attend, 4),
        "prediction": "ATTEND" if p_attend >= 0.5 else "NO_SHOW",
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
