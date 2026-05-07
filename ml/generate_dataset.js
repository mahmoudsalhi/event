/*
 * Synthetic dataset for predicting kid attendance at outdoor English-learning events.
 *
 *   Target column : `attended` (1 = ATTENDED, 0 = NO_SHOW)
 *   Class balance : ~50/50 (balanced for ML training)
 *   Quality       : intentionally dirty (duplicates, missing markers, mixed-case,
 *                   outliers, type issues) so the notebook can show cleaning steps.
 *
 * 15 features chosen to be (a) genuinely predictive for outdoor kids' events
 * and (b) easy for a person to enter in a web form at inference time.
 *
 * Run:   node generate_dataset.js [numRows] [outputPath]
 */

const fs = require("fs");
const path = require("path");

const NUM_ROWS = parseInt(process.argv[2] || "10000", 10);
const OUT_PATH = process.argv[3] || path.join(__dirname, "dataset.csv");

let seed = 42;
function rand() { seed = (seed * 1664525 + 1013904223) % 4294967296; return seed / 4294967296; }
const randInt = (a, b) => Math.floor(rand() * (b - a + 1)) + a;
const randFloat = (a, b) => rand() * (b - a) + a;
const randChoice = (arr) => arr[Math.floor(rand() * arr.length)];
const randBool = (p) => (rand() < p ? 1 : 0);
const sigmoid = (x) => 1 / (1 + Math.exp(-x));
function gauss(m, s) {
  const u = Math.max(rand(), 1e-9), v = rand();
  return m + s * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}
const clamp = (x, a, b) => Math.max(a, Math.min(b, x));

const CATEGORIES = [
  "STORYTELLING_PARK","NATURE_VOCAB_WALK","TREASURE_HUNT","ROLE_PLAY_GAMES",
  "ENGLISH_PICNIC","SPORTS_AND_ENGLISH","OUTDOOR_THEATER","ARTS_AND_CRAFTS",
  "FIELD_TRIP","ENGLISH_CAMP_DAY",
];
const ENGLISH_LEVELS = ["A1_BEGINNER", "A2_ELEMENTARY", "B1_INTERMEDIATE"];
const TARGET_LEVELS = [...ENGLISH_LEVELS, "ALL_LEVELS"];

function generateRow() {
  // KID
  const kid_age = randInt(5, 13);
  const kid_english_level = kid_age <= 7
    ? randChoice(["A1_BEGINNER","A1_BEGINNER","A2_ELEMENTARY"])
    : kid_age <= 10
    ? randChoice(["A1_BEGINNER","A2_ELEMENTARY","A2_ELEMENTARY","B1_INTERMEDIATE"])
    : randChoice(["A2_ELEMENTARY","B1_INTERMEDIATE","B1_INTERMEDIATE"]);
  const previous_events_attended = randInt(0, 30);
  const previous_no_shows = previous_events_attended === 0
    ? 0 : Math.min(previous_events_attended, Math.round(randFloat(0, 0.6) * previous_events_attended));

  // EVENT
  const event_category = randChoice(CATEGORIES);
  const event_target_level = randChoice(TARGET_LEVELS);
  const event_duration_hours = randChoice([1, 1.5, 2, 2, 2.5, 3, 4]);
  const event_price_tnd = randBool(0.45) ? 0 : Math.round(randFloat(15, 80));
  const distance_km = clamp(Math.abs(gauss(8, 8)), 0, 50);
  const days_until_event = clamp(Math.round(Math.abs(gauss(8, 7))), 0, 30);
  const is_weekend = randBool(0.45);
  const event_is_featured = randBool(0.18);

  // WEATHER (outdoor matters!)
  const weather_rain_prob = clamp(gauss(0.25, 0.25), 0, 1);
  const weather_temp_c = clamp(gauss(22, 8), -2, 42);

  // ENGAGEMENT
  const sms_reminder_sent = randBool(0.65);

  // ENGINEERED HISTORY RATE for the target generation (not stored — model gets the raw counts)
  const past_rate = previous_events_attended === 0
    ? 0.5
    : (previous_events_attended - previous_no_shows) / previous_events_attended;
  const level_mismatch = event_target_level === "ALL_LEVELS"
    ? 0
    : event_target_level !== kid_english_level ? 1 : 0;

  // TARGET — strong, learnable signals
  let z = 0.5;
  z += (past_rate - 0.5) * 7.0;                        // past behavior is the dominant signal
  z += -0.12 * Math.min(previous_no_shows, 10);
  z += 1.0 * event_is_featured;
  z += 0.9 * sms_reminder_sent;
  z += -0.08 * days_until_event;
  z += -0.06 * distance_km;
  z += -2.5 * weather_rain_prob;                       // strong: outdoor!
  z += -1.0 * (weather_temp_c < 8 || weather_temp_c > 35 ? 1 : 0);
  z += 0.8 * (event_price_tnd > 0 ? 1 : 0);            // paid -> commitment
  z += -0.005 * event_price_tnd;
  z += 0.8 * is_weekend;
  z += -0.7 * level_mismatch;
  z += -0.1 * Math.max(0, kid_age - 11);
  z += -0.15 * Math.max(0, event_duration_hours - 2);
  z += gauss(0, 0.1);

  const p_attend = sigmoid(z);
  const attended = rand() < p_attend ? 1 : 0;

  return {
    kid_age, kid_english_level, previous_events_attended, previous_no_shows,
    event_category, event_target_level, event_duration_hours, event_price_tnd,
    distance_km: +distance_km.toFixed(2), days_until_event,
    is_weekend, event_is_featured,
    weather_rain_prob: +weather_rain_prob.toFixed(3),
    weather_temp_c: +weather_temp_c.toFixed(1),
    sms_reminder_sent, attended,
  };
}

// ---- balance to 50/50 ----
const pool = [];
const PER = Math.floor(NUM_ROWS / 2);
let nA = 0, nN = 0, safety = 0;
while ((nA < PER || nN < PER) && safety < NUM_ROWS * 6) {
  const r = generateRow();
  if (r.attended === 1 && nA < PER) { pool.push(r); nA++; }
  else if (r.attended === 0 && nN < PER) { pool.push(r); nN++; }
  safety++;
}
for (let i = pool.length - 1; i > 0; i--) {
  const j = Math.floor(rand() * (i + 1));
  [pool[i], pool[j]] = [pool[j], pool[i]];
}

// ---- dirtify ----
const MISSING = ["", "N/A", "null", "?", "NA", "unknown"];
function dirty(rows) {
  const dups = Math.floor(rows.length * 0.02);
  for (let i = 0; i < dups; i++) rows.push({ ...rows[randInt(0, rows.length - 1)] });

  const missable = [
    "kid_age","kid_english_level","event_category","event_target_level",
    "weather_temp_c","weather_rain_prob","distance_km",
  ];
  for (const r of rows) for (const c of missable) if (rand() < 0.03) r[c] = randChoice(MISSING);

  for (const r of rows) {
    if (typeof r.event_category === "string" && CATEGORIES.includes(r.event_category) && rand() < 0.1) {
      const ec = r.event_category;
      r.event_category = randChoice([ec.toLowerCase(), " " + ec + " ", ec[0] + ec.slice(1).toLowerCase()]);
    }
  }
  for (const r of rows) {
    if (rand() < 0.02) r.sms_reminder_sent = r.sms_reminder_sent === 1 ? "yes" : "no";
    if (rand() < 0.02) r.is_weekend = r.is_weekend === 1 ? "yes" : "no";
  }
  for (const r of rows) {
    if (rand() < 0.005) r.kid_age = randChoice([99, -2, 250]);
    if (rand() < 0.005) r.distance_km = randChoice([99999, -10]);
    if (rand() < 0.003) r.event_price_tnd = 999999;
  }
  return rows;
}
const dirtied = dirty(pool);

// ---- write ----
const headers = [
  "kid_age","kid_english_level","previous_events_attended","previous_no_shows",
  "event_category","event_target_level","event_duration_hours","event_price_tnd",
  "distance_km","days_until_event","is_weekend","event_is_featured",
  "weather_rain_prob","weather_temp_c","sms_reminder_sent","attended",
];
function esc(v) {
  if (v === null || v === undefined) return "";
  const s = String(v);
  return /[",\n]/.test(s) ? "\"" + s.replace(/"/g, "\"\"") + "\"" : s;
}
const lines = [headers.join(",")];
for (const r of dirtied) lines.push(headers.map((h) => esc(r[h])).join(","));
fs.writeFileSync(OUT_PATH, lines.join("\n"));

const a = dirtied.filter((r) => r.attended === 1).length;
console.log(`Wrote ${dirtied.length} rows to ${OUT_PATH}`);
console.log(`Class balance: attended=${a} (${((a / dirtied.length) * 100).toFixed(1)}%), ` +
  `no_show=${dirtied.length - a} (${(((dirtied.length - a) / dirtied.length) * 100).toFixed(1)}%)`);
console.log(`(15 features, balanced 50/50, intentionally dirty.)`);
