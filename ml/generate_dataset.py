"""
Synthetic dataset for predicting kid attendance at outdoor English-learning events.

Target        : `attended` (1 = ATTENDED, 0 = NO_SHOW)
Class balance : ~50/50
Quality       : intentionally dirty (duplicates, missing markers, mixed-case, outliers)
Features      : 15 — chosen to be predictive AND fillable in a web form at inference time.
"""

import csv
import math
import random
import sys
from pathlib import Path

NUM_ROWS = int(sys.argv[1]) if len(sys.argv) > 1 else 10000
OUT_PATH = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(__file__).parent / "dataset.csv"

random.seed(42)

CATEGORIES = [
    "STORYTELLING_PARK","NATURE_VOCAB_WALK","TREASURE_HUNT","ROLE_PLAY_GAMES",
    "ENGLISH_PICNIC","SPORTS_AND_ENGLISH","OUTDOOR_THEATER","ARTS_AND_CRAFTS",
    "FIELD_TRIP","ENGLISH_CAMP_DAY",
]
ENGLISH_LEVELS = ["A1_BEGINNER", "A2_ELEMENTARY", "B1_INTERMEDIATE"]
TARGET_LEVELS = ENGLISH_LEVELS + ["ALL_LEVELS"]
MISSING = ["", "N/A", "null", "?", "NA", "unknown"]


def clamp(x, lo, hi): return max(lo, min(hi, x))
def sigmoid(x): return 1 / (1 + math.exp(-x))
def gauss(m, s): return random.gauss(m, s)


def generate_row():
    kid_age = random.randint(5, 13)
    if kid_age <= 7:
        kid_english_level = random.choice(["A1_BEGINNER","A1_BEGINNER","A2_ELEMENTARY"])
    elif kid_age <= 10:
        kid_english_level = random.choice(["A1_BEGINNER","A2_ELEMENTARY","A2_ELEMENTARY","B1_INTERMEDIATE"])
    else:
        kid_english_level = random.choice(["A2_ELEMENTARY","B1_INTERMEDIATE","B1_INTERMEDIATE"])
    previous_events_attended = random.randint(0, 30)
    previous_no_shows = (
        0 if previous_events_attended == 0
        else min(previous_events_attended, round(random.uniform(0, 0.6) * previous_events_attended))
    )

    event_category = random.choice(CATEGORIES)
    event_target_level = random.choice(TARGET_LEVELS)
    event_duration_hours = random.choice([1, 1.5, 2, 2, 2.5, 3, 4])
    event_price_tnd = 0 if random.random() < 0.45 else round(random.uniform(15, 80))
    distance_km = clamp(abs(gauss(8, 8)), 0, 50)
    days_until_event = clamp(round(abs(gauss(8, 7))), 0, 30)
    is_weekend = 1 if random.random() < 0.45 else 0
    event_is_featured = 1 if random.random() < 0.18 else 0

    weather_rain_prob = clamp(gauss(0.25, 0.25), 0, 1)
    weather_temp_c = clamp(gauss(22, 8), -2, 42)
    sms_reminder_sent = 1 if random.random() < 0.65 else 0

    past_rate = 0.5 if previous_events_attended == 0 else (previous_events_attended - previous_no_shows) / previous_events_attended
    level_mismatch = 0 if event_target_level == "ALL_LEVELS" else (1 if event_target_level != kid_english_level else 0)

    z = 0.5
    z += (past_rate - 0.5) * 7.0
    z += -0.12 * min(previous_no_shows, 10)
    z += 1.0 * event_is_featured
    z += 0.9 * sms_reminder_sent
    z += -0.08 * days_until_event
    z += -0.06 * distance_km
    z += -2.5 * weather_rain_prob
    z += -1.0 * (1 if (weather_temp_c < 8 or weather_temp_c > 35) else 0)
    z += 0.8 * (1 if event_price_tnd > 0 else 0)
    z += -0.005 * event_price_tnd
    z += 0.8 * is_weekend
    z += -0.7 * level_mismatch
    z += -0.1 * max(0, kid_age - 11)
    z += -0.15 * max(0, event_duration_hours - 2)
    z += gauss(0, 0.1)

    p_attend = sigmoid(z)
    attended = 1 if random.random() < p_attend else 0

    return {
        "kid_age": kid_age,
        "kid_english_level": kid_english_level,
        "previous_events_attended": previous_events_attended,
        "previous_no_shows": previous_no_shows,
        "event_category": event_category,
        "event_target_level": event_target_level,
        "event_duration_hours": event_duration_hours,
        "event_price_tnd": event_price_tnd,
        "distance_km": round(distance_km, 2),
        "days_until_event": days_until_event,
        "is_weekend": is_weekend,
        "event_is_featured": event_is_featured,
        "weather_rain_prob": round(weather_rain_prob, 3),
        "weather_temp_c": round(weather_temp_c, 1),
        "sms_reminder_sent": sms_reminder_sent,
        "attended": attended,
    }


def dirtify(rows):
    rows.extend([dict(random.choice(rows)) for _ in range(int(len(rows) * 0.02))])

    missable = ["kid_age","kid_english_level","event_category","event_target_level",
                "weather_temp_c","weather_rain_prob","distance_km"]
    for r in rows:
        for c in missable:
            if random.random() < 0.03:
                r[c] = random.choice(MISSING)

    for r in rows:
        if isinstance(r.get("event_category"), str) and r["event_category"] in CATEGORIES and random.random() < 0.1:
            ec = r["event_category"]
            r["event_category"] = random.choice([ec.lower(), f" {ec} ", ec.capitalize()])

    for r in rows:
        if random.random() < 0.02:
            r["sms_reminder_sent"] = "yes" if r["sms_reminder_sent"] == 1 else "no"
        if random.random() < 0.02:
            r["is_weekend"] = "yes" if r["is_weekend"] == 1 else "no"

    for r in rows:
        if random.random() < 0.005: r["kid_age"] = random.choice([99, -2, 250])
        if random.random() < 0.005: r["distance_km"] = random.choice([99999, -10])
        if random.random() < 0.003: r["event_price_tnd"] = 999999
    return rows


def main():
    per = NUM_ROWS // 2
    pool, nA, nN, safety = [], 0, 0, 0
    while (nA < per or nN < per) and safety < NUM_ROWS * 6:
        r = generate_row()
        if r["attended"] == 1 and nA < per:
            pool.append(r); nA += 1
        elif r["attended"] == 0 and nN < per:
            pool.append(r); nN += 1
        safety += 1

    random.shuffle(pool)
    pool = dirtify(pool)
    headers = list(generate_row().keys())

    with open(OUT_PATH, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=headers)
        w.writeheader()
        w.writerows(pool)

    a = sum(1 for r in pool if r["attended"] == 1)
    print(f"Wrote {len(pool)} rows to {OUT_PATH}")
    print(f"Class balance: attended={a} ({a/len(pool):.1%}), no_show={len(pool)-a} ({(len(pool)-a)/len(pool):.1%})")
    print("(15 features, balanced 50/50, intentionally dirty.)")


if __name__ == "__main__":
    main()
