/*
 * Synthetic dataset generator for predicting event no-shows.
 *
 * Domain: an English e-learning platform for kids. Events are outdoor
 * activities (storytelling in the park, treasure hunts, nature walks, etc.).
 * The user account IS the kid — there is no separate parent entity.
 *
 *   Target column : `attended` (1 = ATTENDED, 0 = NO_SHOW)
 *   Class balance : ~50/50 (balanced for ML training)
 *   Quality       : intentionally dirty so the notebook can demonstrate cleaning
 *
 * All features are derivable from your Spring entities (`Event`,
 * `EventRegistration`) plus a small holidays calendar lookup. No external APIs
 * are required.
 *
 * Run:   node generate_dataset.js [numRows] [outputPath]
 */

const fs = require("fs");
const path = require("path");

const NUM_ROWS = parseInt(process.argv[2] || "10000", 10);
const OUT_PATH = process.argv[3] || path.join(__dirname, "dataset.csv");

let seed = 42;
function rand() {
  seed = (seed * 1664525 + 1013904223) % 4294967296;
  return seed / 4294967296;
}
const randInt = (min, max) => Math.floor(rand() * (max - min + 1)) + min;
const randFloat = (min, max) => rand() * (max - min) + min;
const randChoice = (arr) => arr[Math.floor(rand() * arr.length)];
const randBool = (p) => (rand() < p ? 1 : 0);
const sigmoid = (x) => 1 / (1 + Math.exp(-x));
function gauss(mean, std) {
  const u = Math.max(rand(), 1e-9);
  const v = rand();
  return mean + std * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

// Outdoor English-learning event categories for kids
const CATEGORIES = [
  "STORYTELLING_PARK","NATURE_VOCAB_WALK","TREASURE_HUNT","ROLE_PLAY_GAMES",
  "ENGLISH_PICNIC","SPORTS_AND_ENGLISH","OUTDOOR_THEATER","ARTS_AND_CRAFTS",
  "FIELD_TRIP","ENGLISH_CAMP_DAY",
];
const ENGLISH_LEVELS = ["A1_BEGINNER", "A2_ELEMENTARY", "B1_INTERMEDIATE"];
const TARGET_LEVELS = [...ENGLISH_LEVELS, "ALL_LEVELS"];
const SKILL_FOCUS = ["SPEAKING","LISTENING","VOCABULARY","PHONICS","STORYTELLING","READING","MIXED"];
const GENDERS = ["M", "F"];

function generateRow() {
  // ---- USER (the kid) ----
  const user_id = randInt(1, 5000);
  const user_age = randInt(5, 13);
  const user_gender = randChoice(GENDERS);
  const user_account_age_days = randInt(1, 1200);
  // English level skewed by age
  const user_english_level = user_age <= 7
    ? randChoice(["A1_BEGINNER", "A1_BEGINNER", "A2_ELEMENTARY"])
    : user_age <= 10
    ? randChoice(["A1_BEGINNER", "A2_ELEMENTARY", "A2_ELEMENTARY", "B1_INTERMEDIATE"])
    : randChoice(["A2_ELEMENTARY", "B1_INTERMEDIATE", "B1_INTERMEDIATE"]);
  const user_total_registrations = Math.min(
    randInt(0, Math.max(1, Math.floor(user_account_age_days / 25))), 60
  );
  const user_past_attendance_rate =
    user_total_registrations === 0 ? -1 : clamp(gauss(0.55, 0.25), 0, 1);
  const user_past_no_shows =
    user_total_registrations === 0
      ? 0
      : Math.round(user_total_registrations * (1 - Math.max(user_past_attendance_rate, 0)) * randFloat(0.5, 1));
  const user_past_cancellations = Math.round(user_total_registrations * randFloat(0, 0.25));
  const user_avg_rating_given = user_total_registrations < 2 ? -1 : clamp(gauss(4.0, 0.8), 1, 5);
  const user_profile_completeness = clamp(gauss(0.7, 0.2), 0, 1);
  const user_has_phone = randBool(0.85);

  // ---- EVENT ----
  const event_id = randInt(1, 600);
  const event_category = randChoice(CATEGORIES);
  const event_target_level = randChoice(TARGET_LEVELS);
  const event_skill_focus = randChoice(SKILL_FOCUS);
  const event_max_attendees = randChoice([10, 15, 20, 25, 30, 40, 50]);
  const event_capacity_utilization = clamp(gauss(0.7, 0.22), 0.1, 1);
  const event_current_attendees = Math.round(event_max_attendees * event_capacity_utilization);
  const event_is_featured = randBool(0.18);
  const event_is_public = randBool(0.85);
  const event_is_free = randBool(0.45);
  const event_price_tnd = event_is_free ? 0 : Math.round(randFloat(15, 80));
  const event_duration_hours = randChoice([1, 1.5, 2, 2, 2.5, 3, 4]);
  const event_day_of_week = randInt(0, 6);
  const event_hour_of_day = randInt(8, 18);
  const event_is_weekend = event_day_of_week >= 5 ? 1 : 0;
  const event_host_past_avg_attendance_rate = clamp(gauss(0.6, 0.18), 0.2, 1);
  const event_host_total_events = randInt(1, 60);

  const level_mismatch =
    event_target_level === "ALL_LEVELS"
      ? 0
      : event_target_level !== user_english_level
      ? 1
      : 0;

  // ---- REGISTRATION ----
  const days_until_event = clamp(Math.round(Math.abs(gauss(8, 9))), 0, 60);
  const registration_hour = randInt(7, 23);
  const sms_reminder_sent = user_has_phone ? randBool(0.7) : 0;
  const email_reminder_sent = randBool(0.85);
  const registered_via_waitlist = randBool(0.08);

  // ---- CONTEXT (calendar only, no APIs) ----
  const is_holiday = randBool(0.05);

  // ---- TARGET ----
  let z = 0.1;
  z += (user_past_attendance_rate >= 0 ? user_past_attendance_rate - 0.5 : -0.05) * 4.0;
  z += -0.06 * Math.min(user_past_no_shows, 10);
  z += 0.6 * (event_is_featured ? 1 : 0);
  z += 0.5 * sms_reminder_sent;
  z += 0.3 * email_reminder_sent;
  z += -0.04 * days_until_event;
  z += 0.4 * user_profile_completeness;
  z += 0.5 * (event_is_free ? 0 : 1);
  z += -0.005 * event_price_tnd * (event_is_free ? 0 : 1);
  z += 0.5 * (event_is_weekend ? 1 : 0);
  z += -0.6 * registered_via_waitlist;
  z += 0.8 * (event_host_past_avg_attendance_rate - 0.6);
  z += -0.4 * is_holiday;
  z += -0.4 * level_mismatch;
  z += -0.05 * Math.max(0, user_age - 11);
  z += 0.0008 * user_account_age_days;
  z += gauss(0, 0.5);

  const p_attend = sigmoid(z);
  const attended = rand() < p_attend ? 1 : 0;

  return {
    user_id, user_age, user_gender, user_account_age_days, user_english_level,
    user_total_registrations,
    user_past_attendance_rate: +user_past_attendance_rate.toFixed(3),
    user_past_no_shows, user_past_cancellations,
    user_avg_rating_given: +user_avg_rating_given.toFixed(2),
    user_profile_completeness: +user_profile_completeness.toFixed(3),
    user_has_phone,
    event_id, event_category, event_target_level, event_skill_focus,
    event_max_attendees, event_current_attendees,
    event_capacity_utilization: +event_capacity_utilization.toFixed(3),
    event_is_featured, event_is_public, event_is_free, event_price_tnd,
    event_duration_hours, event_day_of_week, event_hour_of_day, event_is_weekend,
    event_host_total_events,
    event_host_past_avg_attendance_rate: +event_host_past_avg_attendance_rate.toFixed(3),
    days_until_event, registration_hour, sms_reminder_sent, email_reminder_sent,
    registered_via_waitlist, is_holiday, attended,
  };
}

// ---- balance to 50/50 ----
const pool = [];
const TARGET_PER_CLASS = Math.floor(NUM_ROWS / 2);
let countAttended = 0, countNoShow = 0, safety = 0;
while ((countAttended < TARGET_PER_CLASS || countNoShow < TARGET_PER_CLASS) && safety < NUM_ROWS * 6) {
  const r = generateRow();
  if (r.attended === 1 && countAttended < TARGET_PER_CLASS) { pool.push(r); countAttended++; }
  else if (r.attended === 0 && countNoShow < TARGET_PER_CLASS) { pool.push(r); countNoShow++; }
  safety++;
}
for (let i = pool.length - 1; i > 0; i--) {
  const j = Math.floor(rand() * (i + 1));
  [pool[i], pool[j]] = [pool[j], pool[i]];
}

// ---- dirtify ----
const MISSING_MARKERS = ["", "N/A", "null", "?", "NA", "unknown"];
function maybeDirty(rows) {
  const dupCount = Math.floor(rows.length * 0.02);
  for (let i = 0; i < dupCount; i++) rows.push({ ...rows[randInt(0, rows.length - 1)] });

  const missableCols = [
    "user_age", "user_gender", "user_english_level",
    "user_profile_completeness", "user_avg_rating_given",
    "event_category", "event_target_level",
  ];
  for (const r of rows) {
    for (const col of missableCols) {
      if (rand() < 0.06) r[col] = randChoice(MISSING_MARKERS);
    }
  }

  for (const r of rows) {
    if (typeof r.event_category === "string" && CATEGORIES.includes(r.event_category) && rand() < 0.1) {
      const variants = [
        r.event_category.toLowerCase(),
        " " + r.event_category + " ",
        r.event_category.charAt(0) + r.event_category.slice(1).toLowerCase(),
      ];
      r.event_category = randChoice(variants);
    }
  }

  for (const r of rows) {
    if (rand() < 0.02) r.user_has_phone = r.user_has_phone === 1 ? "yes" : "no";
  }

  for (const r of rows) {
    if (rand() < 0.005) r.user_age = randChoice([99, -2, 250]);
    if (rand() < 0.003) r.event_price_tnd = 999999;
  }
  return rows;
}
const dirtied = maybeDirty(pool);

// ---- write ----
const headers = [
  "user_id","user_age","user_gender","user_account_age_days","user_english_level",
  "user_total_registrations","user_past_attendance_rate","user_past_no_shows",
  "user_past_cancellations","user_avg_rating_given","user_profile_completeness","user_has_phone",
  "event_id","event_category","event_target_level","event_skill_focus",
  "event_max_attendees","event_current_attendees","event_capacity_utilization",
  "event_is_featured","event_is_public","event_is_free","event_price_tnd",
  "event_duration_hours","event_day_of_week","event_hour_of_day","event_is_weekend",
  "event_host_total_events","event_host_past_avg_attendance_rate",
  "days_until_event","registration_hour","sms_reminder_sent","email_reminder_sent",
  "registered_via_waitlist","is_holiday","attended",
];
function escapeCsv(v) {
  if (v === null || v === undefined) return "";
  const s = String(v);
  if (s.includes(",") || s.includes("\"") || s.includes("\n")) {
    return "\"" + s.replace(/"/g, "\"\"") + "\"";
  }
  return s;
}
const lines = [headers.join(",")];
for (const r of dirtied) lines.push(headers.map((h) => escapeCsv(r[h])).join(","));
fs.writeFileSync(OUT_PATH, lines.join("\n"));

const a = dirtied.filter((r) => r.attended === 1).length;
console.log(`Wrote ${dirtied.length} rows to ${OUT_PATH}`);
console.log(`Class balance: attended=${a} (${((a / dirtied.length) * 100).toFixed(1)}%), ` +
  `no_show=${dirtied.length - a} (${(((dirtied.length - a) / dirtied.length) * 100).toFixed(1)}%)`);
console.log(`(Kids' English platform. Includes duplicates, missing markers, mixed-case, outliers.)`);
