// Seeds four positionless member documents into the Emu family (BEVNTE), as the fixture for the
// "Setting up" build. Each one is exactly what Family.memberFields writes on a join -- name and
// sharing and nothing else -- plus the joinedAt the build adds. No lat, no lng, no updatedAt, no
// battery: a member who has never reported has none of them, and adding any would make the fixture
// unrepresentative of the case being fixed.
//
//   node seed.mjs                prints what it would write, writes nothing
//   node seed.mjs --go           writes the four seed documents
//   node seed.mjs --clean        deletes the four seed documents again
//   node seed.mjs --strip-me     makes the EMULATOR's own record positionless (see below)
//   node seed.mjs --restore-me   puts the emulator's own record back
//
// The four seed documents only ever use ids beginning "seed-" inside BEVNTE, so the real family
// (JRVPGQ) and the existing tut-* members cannot be reached by them.

import { createSign } from "node:crypto";
import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const FAMILY = "BEVNTE";
// The emulator's own member document in BEVNTE, and also the family's createdBy. Hardcoded so
// --strip-me cannot be aimed at anything else.
const ME = "walO0YnuFldPxjuGjcmboZmFpd02";
const go = process.argv.includes("--go");
const clean = process.argv.includes("--clean");
const strip = process.argv.includes("--strip-me");
const restore = process.argv.includes("--restore-me");

const KEYS = join(homedir(), ".keys");
const keyFile = readdirSync(KEYS).find((f) => /firebase-adminsdk.*\.json$/.test(f));
if (!keyFile) {
  console.error(`No Firebase admin key in ${KEYS}.`);
  process.exit(1);
}
const key = JSON.parse(readFileSync(join(KEYS, keyFile), "utf8"));

async function token() {
  const now = Math.floor(Date.now() / 1000);
  const part = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = part({ alg: "RS256", typ: "JWT" }) + "." + part({
    iss: key.client_email,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 600,
  });
  const signature = createSign("RSA-SHA256").update(unsigned).sign(key.private_key, "base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${signature}`,
    }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status} ${await res.text()}`);
  return (await res.json()).access_token;
}

const BASE = `https://firestore.googleapis.com/v1/projects/${key.project_id}/databases/(default)/documents`;

const now = Date.now();
const iso = (ms) => new Date(ms).toISOString();

// joinedAt is a Firestore timestamp, not a number: Family.listen reads its sibling updatedAt with
// getTimestamp(), so the build will read this one the same way. A number here would compile and
// then read back null.
const docs = [
  {
    id: "seed-waiting",
    why: 'should read "Setting up" and draw no dot -- the case the build adds',
    fields: { name: "Seed Waiting", sharing: true, joinedAt: iso(now) },
  },
  {
    id: "seed-aged",
    why: "joined 2 hours ago, past the 15-minute threshold: should read quiet, still no dot",
    fields: { name: "Seed Aged", sharing: true, joinedAt: iso(now - 2 * 60 * 60 * 1000) },
  },
  {
    id: "seed-paused",
    why: "sharing off: should read paused, which the existing branch already handles",
    fields: { name: "Seed Paused", sharing: false, joinedAt: iso(now) },
  },
  {
    id: "seed-nostamp",
    why: "no joinedAt at all (a pre-0.11 record): should read quiet, never stuck in setting-up",
    fields: { name: "Seed NoStamp", sharing: true },
  },
];

// Firestore's REST wants every value tagged with its type.
const typed = (v) =>
  typeof v === "boolean"
    ? { booleanValue: v }
    : typeof v === "number"
      ? { integerValue: String(v) }
      : /^\d{4}-\d{2}-\d{2}T/.test(v)
        ? { timestampValue: v }
        : { stringValue: v };

if (!go && !clean && !strip && !restore) {
  console.log(`Would write ${docs.length} documents to families/${FAMILY}/members, and nothing else.\n`);
  for (const d of docs) {
    console.log(`  ${d.id}`);
    console.log(`    ${d.why}`);
    console.log(`    ${JSON.stringify(d.fields)}`);
    console.log("");
  }
  console.log("Nothing was written. Re-run with --go to write, or --clean to remove them later.");
  process.exit(0);
}

const auth = { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" };

if (clean) {
  for (const d of docs) {
    const res = await fetch(`${BASE}/families/${FAMILY}/members/${d.id}`, { method: "DELETE", headers: auth });
    console.log(`${res.ok ? "deleted" : `FAILED ${res.status}`}  ${d.id}`);
  }
  process.exit(0);
}

// --strip-me / --restore-me exercise MainActivity's mine() and meNow against the emulator's OWN
// record, which is the one changed path no seed document can reach: a seed is somebody else, and
// `me` is found by uid. The alternative -- having the emulator leave and rejoin -- is NOT safe
// here, because the emulator is BEVNTE's createdBy, so leaving hands the family to whichever other
// member has the newest updatedAt (tut-jen, a staged document no phone owns).
//
// The write names its fields in an updateMask, so only lat, lng and joinedAt are ever touched. A
// full-document PATCH like the seeds above would wipe this document's diag, photo, phone and
// battery, which is why this path does not reuse it.
if (strip || restore) {
  const SIDECAR = new URL("me-before-strip.json", import.meta.url);
  const url = `${BASE}/families/${FAMILY}/members/${ME}`;
  const mask = ["lat", "lng", "joinedAt"].map((f) => `updateMask.fieldPaths=${f}`).join("&");

  if (strip) {
    const before = await fetch(url, { headers: auth });
    if (!before.ok) throw new Error(`Could not read the emulator's record: ${before.status}`);
    const had = (await before.json()).fields ?? {};
    writeFileSync(SIDECAR, JSON.stringify({ lat: had.lat, lng: had.lng, joinedAt: had.joinedAt }, null, 2), "utf8");
    console.log(`saved its current lat/lng/joinedAt to ${SIDECAR.pathname}`);
    // Naming lat and lng in the mask while leaving them out of fields is how Firestore deletes them.
    const res = await fetch(`${url}?${mask}`, {
      method: "PATCH",
      headers: auth,
      body: JSON.stringify({ fields: { joinedAt: { timestampValue: iso(now) } } }),
    });
    if (!res.ok) {
      console.error(`FAILED ${res.status} ${await res.text()}`);
      process.exit(1);
    }
    console.log("stripped lat/lng and set a fresh joinedAt: it is now a never-reported member.");
    console.log("Turn the emulator's sharing OFF first, or its next location write undoes this.");
    process.exit(0);
  }

  const saved = JSON.parse(readFileSync(SIDECAR, "utf8"));
  const fields = Object.fromEntries(Object.entries(saved).filter(([, v]) => v !== undefined && v !== null));
  const res = await fetch(`${url}?${mask}`, { method: "PATCH", headers: auth, body: JSON.stringify({ fields }) });
  if (!res.ok) {
    console.error(`FAILED ${res.status} ${await res.text()}`);
    process.exit(1);
  }
  console.log(`restored ${Object.keys(fields).join(", ") || "nothing: none of the three were set before"}`);
  process.exit(0);
}

for (const d of docs) {
  // PATCH with no updateMask replaces the whole document, so a re-run cannot leave a stale field
  // behind from an earlier shape of the fixture.
  const res = await fetch(`${BASE}/families/${FAMILY}/members/${d.id}`, {
    method: "PATCH",
    headers: auth,
    body: JSON.stringify({ fields: Object.fromEntries(Object.entries(d.fields).map(([k, v]) => [k, typed(v)])) }),
  });
  if (!res.ok) {
    console.error(`FAILED ${d.id}: ${res.status} ${await res.text()}`);
    continue;
  }
  console.log(`wrote  ${d.id.padEnd(14)} ${JSON.stringify(d.fields)}`);
}
