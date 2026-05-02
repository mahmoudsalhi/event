"""
Synthetic dataset generator for predicting event no-shows.

Domain: an English e-learning platform for kids. Events are outdoor activities
(storytelling in the park, treasure hunts, nature walks, etc.). The user
account IS the kid — there is no separate parent entity.

Target        : `attended` (1 = ATTENDED, 0 = NO_SHOW)
Class balance : ~50/50 (balanced for ML training)
Quality       : intentionally dirty so the notebook can demonstrate cleaning

All features are derivable from your Spring entities (Event, EventRegistration)
plus a small holidays calendar lookup. No external APIs are required.
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
    "STORYTELLING_PARK", "NATURE_VOCAB_WALK", "TREASURE_HUNT", "ROLE_PLAY_GAMES",
    "ENGLISH_PICNIC", "SPORTS_AND_ENGLISH", "OUTDOOR_THEATER", "ARTS_AND_CRAFTS",
    "FIELD_TRIP", "ENGLISH_CAMP_DAY",
]
ENGLISH_LEVELS = ["A1_BEGINNER", "A2_ELEMENTARY", "B1_INTERMEDIATE"]
TARGET_LEVELS = ENGLISH_LEVELS + ["ALL_LEVELS"]
SKILL_FOCUS = ["SPEAKING", "LISTENING", "VOCABULARY", "PHONICS", "STORYTELLING", "READING", "MIXED"]
GENDERS = ["M", "F"]
MISSING_MARKERS = ["", "N/A", "null", "?", "NA", "unknown"]


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def sigmoid(x):
    return 1 / (1 + math.exp(-x))


def gauss(m, s):
    return random.gauss(m, s)


def generate_row():
    user_id = random.randint(1, 5000)
    user_age = random.randint(5, 13)
    user_gender = random.choice(GENDERS)
    user_account_age_days = random.randint(1, 1200)
    if user_age <= 7:
        user_english_level = random.choice(["A1_BEGINNER", "A1_BEGINNER", "A2_ELEMENTARY"])
    elif user_age <= 10:
        user_english_level = random.choice(["A1_BEGINNER", "A2_ELEMENTARY", "A2_ELEMENTARY", "B1_INTERMEDIATE"])
    else:
        user_english_level = random.choice(["A2_ELEMENTARY", "B1_INTERMEDIATE", "B1_INTERMEDIATE"])
    user_total_registrations = min(random.randint(0, max(1, user_account_age_days // 25)), 60)
    user_past_attendance_rate = -1.0 if user_total_registrations == 0 else clamp(gauss(0.55, 0.25), 0, 1)
    user_past_no_shows = (
        0 if user_total_registrations == 0
        else round(user_total_registrations * (1 - max(user_past_attendance_rate, 0)) * random.uniform(0.5, 1))
    )
    user_past_cancellations = round(user_total_registrations * random.uniform(0, 0.25))
    user_avg_rating_given = -1.0 if user_total_registrations < 2 else clamp(gauss(4.0, 0.8), 1, 5)
    user_profile_completeness = clamp(gauss(0.7, 0.2), 0, 1)
    user_has_phone = 1 if random.random() < 0.85 else 0

    event_id = random.randint(1, 600)
    event_category = random.choice(CATEGORIES)
    event_target_level = random.choice(TARGET_LEVELS)
    event_skill_focus = random.choice(SKILL_FOCUS)
    event_max_attendees = random.choice([10, 15, 20, 25, 30, 40, 50])
    event_capacity_utilization = clamp(gauss(0.7, 0.22), 0.1, 1)
    event_current_attendees = round(event_max_attendees * event_capacity_utilization)
    event_is_featured = 1 if random.random() < 0.18 else 0
    event_is_public = 1 if random.random() < 0.85 else 0
    event_is_free = 1 if random.random() < 0.45 else 0
    event_price_tnd = 0 if event_is_free else round(random.uniform(15, 80))
    event_duration_hours = random.choice([1, 1.5, 2, 2, 2.5, 3, 4])
    event_day_of_week = random.randint(0, 6)
    event_hour_of_day = random.randint(8, 18)
    event_is_weekend = 1 if event_day_of_week >= 5 else 0
    event_host_past_avg_attendance_rate = clamp(gauss(0.6, 0.18), 0.2, 1)
    event_host_total_events = random.randint(1, 60)

    level_mismatch = 0 if event_target_level == "ALL_LEVELS" else (1 if event_target_level != user_english_level else 0)

    days_until_event = clamp(round(abs(gauss(8, 9))), 0, 60)
    registration_hour = random.randint(7, 23)
    sms_reminder_sent = 1 if (user_has_phone and random.random() < 0.7) else 0
    email_reminder_sent = 1 if random.random() < 0.85 else 0
    registered_via_waitlist = 1 if random.random() < 0.08 else 0
    is_holiday = 1 if random.random() < 0.05 else 0

    z = 0.1
    z += (user_past_attendance_rate - 0.5 if user_past_attendance_rate >= 0 else -0.05) * 4.0
    z += -0.06 * min(user_past_no_shows, 10)
    z += 0.6 * event_is_featured
    z += 0.5 * sms_reminder_sent
    z += 0.3 * email_reminder_sent
    z += -0.04 * days_until_event
    z += 0.4 * user_profile_completeness
    z += 0.5 * (0 if event_is_free else 1)
    z += -0.005 * event_price_tnd * (0 if event_is_free else 1)
    z += 0.5 * event_is_weekend
    z += -0.6 * registered_via_waitlist
    z += 0.8 * (event_host_past_avg_attendance_rate - 0.6)
    z += -0.4 * is_holiday
    z += -0.4 * level_mismatch
    z += -0.05 * max(0, user_age - 11)
    z += 0.0008 * user_account_age_days
    z += gauss(0, 0.5)

    p_attend = sigmoid(z)
    attended = 1 if random.random() < p_attend else 0

    return {
        "user_id": user_id, "user_age": user_age, "user_gender": user_gender,
        "user_account_age_days": user_account_age_days,
        "user_english_level": user_english_level,
        "user_total_registrations": user_total_registrations,
        "user_past_attendance_rate": round(user_past_attendance_rate, 3),
        "user_past_no_shows": user_past_no_shows,
        "user_past_cancellations": user_past_cancellations,
        "user_avg_rating_given": round(user_avg_rating_given, 2),
        "user_profile_completeness": round(user_profile_completeness, 3),
        "user_has_phone": user_has_phone,
        "event_id": event_id, "event_category": event_category,
        "event_target_level": event_target_level, "event_skill_focus": event_skill_focus,
        "event_max_attendees": event_max_attendees,
        "event_current_attendees": event_current_attendees,
        "event_capacity_utilization": round(event_capacity_utilization, 3),
        "event_is_featured": event_is_featured, "event_is_public": event_is_public,
        "event_is_free": event_is_free, "event_price_tnd": event_price_tnd,
        "event_duration_hours": event_duration_hours,
        "event_day_of_week": event_day_of_week, "event_hour_of_day": event_hour_of_day,
        "event_is_weekend": event_is_weekend,
        "event_host_total_events": event_host_total_events,
        "event_host_past_avg_attendance_rate": round(event_host_past_avg_attendance_rate, 3),
        "days_until_event": days_until_event, "registration_hour": registration_hour,
        "sms_reminder_sent": sms_reminder_sent, "email_reminder_sent": email_reminder_sent,
        "registered_via_waitlist": registered_via_waitlist,
        "is_holiday": is_holiday, "attended": attended,
    }


def dirtify(rows):
    rows.extend([dict(random.choice(rows)) for _ in range(int(len(rows) * 0.02))])

    missable = [
        "user_age", "user_gender", "user_english_level",
        "user_profile_completeness", "user_avg_rating_given",
        "event_category", "event_target_level",
    ]
    for r in rows:
        for col in missable:
            if random.random() < 0.06:
                r[col] = random.choice(MISSING_MARKERS)

    for r in rows:
        if isinstance(r.get("event_category"), str) and r["event_category"] in CATEGORIES and random.random() < 0.1:
            ec = r["event_category"]
            r["event_category"] = random.choice([ec.lower(), f" {ec} ", ec.capitalize()])

    for r in rows:
        if random.random() < 0.02:
            r["user_has_phone"] = "yes" if r["user_has_phone"] == 1 else "no"

    for r in rows:
        if random.random() < 0.005: r["user_age"] = random.choice([99, -2, 250])
        if random.random() < 0.003: r["event_price_tnd"] = 999999
    return rows


def main():
    target_per_class = NUM_ROWS // 2
    pool, attended, no_show, safety = [], 0, 0, 0
    while (attended < target_per_class or no_show < target_per_class) and safety < NUM_ROWS * 6:
        r = generate_row()
        if r["attended"] == 1 and attended < target_per_class:
            pool.append(r); attended += 1
        elif r["attended"] == 0 and no_show < target_per_class:
            pool.append(r); no_show += 1
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
    print("(Kids' English platform. Includes duplicates, missing markers, mixed-case, outliers.)")


if __name__ == "__main__":
    main()
