// Prints the phone facts every member's app reports, so a settings problem can be read from here.
//
//   node tools/read-phones.mjs
//
// Asks Firestore for ONLY the "name" and "diag" fields of every member document, through a
// select mask, so no location ever leaves the server. Same admin key as tools/read-feedback.mjs.

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
  `https://firestore.googleapis.com/v1/projects/${key.project_id}/databases/(default)/documents:runQuery`,
  {
    method: "POST",
    headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "members", allDescendants: true }],
        select: { fields: [{ fieldPath: "name" }, { fieldPath: "diag" }] },
      },
    }),
  },
);
if (!res.ok) throw new Error(`Reading members failed: ${res.status} ${await res.text()}`);

const rows = (await res.json()).filter((r) => r.document);
if (rows.length === 0) console.log("No members found.");

for (const row of rows) {
  const path = row.document.name.split("/documents/")[1];
  const family = path.split("/")[1];
  const m = plain({ mapValue: row.document });
  const d = m.diag ?? {};
  console.log(`\n${m.name ?? "?"}  (family ${family})`);
  if (!m.diag) {
    console.log("  no diagnostics reported yet (app older than 0.2, or never opened since)");
    continue;
  }
  console.log(`  phone      ${[d.brand, d.model].filter(Boolean).join(" ") || "?"}`);
  console.log(`  android    ${d.android ?? "?"}`);
  console.log(`  mogar      ${d.app ?? "?"}`);
  console.log(`  step 1 battery unrestricted   ${d.unrestricted}`);
  console.log(`  step 2 brand step ticked      ${d.brandStepDone}`);
  console.log(`  sharer alive when last opened ${d.sharerRunningAtOpen}`);
  if (d.lastCrash) console.log(`  last crash ${d.lastCrash}`);
}
