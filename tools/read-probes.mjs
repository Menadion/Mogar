// Prints what each phone reported its brand's settings pages are really called.
//
//   node tools/read-probes.mjs
//
// The app sends this once per Mogar version, with consent (see Probe.kt). It is how a settings
// button that goes nowhere gets fixed for a phone that cannot be plugged in. Same admin key as
// tools/read-feedback.mjs.

import { createSign } from "node:crypto";
import { readdirSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

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

function plain(value) {
  if (value == null || "nullValue" in value) return null;
  if ("mapValue" in value) {
    return Object.fromEntries(Object.entries(value.mapValue.fields ?? {}).map(([k, v]) => [k, plain(v)]));
  }
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(plain);
  if ("integerValue" in value) return Number(value.integerValue);
  return Object.values(value)[0];
}

const res = await fetch(
  `https://firestore.googleapis.com/v1/projects/${key.project_id}/databases/(default)/documents/probes`,
  { headers: { Authorization: `Bearer ${await token()}` } },
);
if (!res.ok) throw new Error(`Reading probes failed: ${res.status} ${await res.text()}`);

const docs = (await res.json()).documents ?? [];
if (docs.length === 0) console.log("No probes yet. A phone sends one on its next launch after consenting.");

for (const doc of docs) {
  const p = plain({ mapValue: doc });
  console.log(`\n${"=".repeat(70)}`);
  console.log(`${p.brand} ${p.model} · Android ${p.android} · Mogar ${p.app}`);
  console.log(`ROM: ${p.rom ?? "?"}  (${p.romBuild ?? "?"})`);

  console.log(`\n  pages we try to open:`);
  for (const [component, state] of Object.entries(p.candidates ?? {})) {
    // "not exported" is the one that matters: the page is there but closed to other apps, so no
    // component name will ever reach it and written steps are the only possible answer.
    const mark = state === "ok" ? "OK     " : state === "not exported" ? "CLOSED " : "MISSING";
    console.log(`    ${mark} ${component}`);
  }

  const found = p.found ?? {};
  if (Object.keys(found).length === 0) {
    console.log(`\n  no manufacturer settings packages found on this phone`);
  }
  for (const [pkg, names] of Object.entries(found)) {
    console.log(`\n  ${pkg} (${names.length} matching):`);
    for (const n of names) console.log(`    ${n}`);
  }
}
