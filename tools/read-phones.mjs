// Prints the phone facts every member's app reports, so a settings problem can be read from here.
//
//   node tools/read-phones.mjs
//
// Asks Firestore, through a select mask, for only four fields of every member document: "name",
// "diag", "sharing" and "updatedAt". No lat or lng is ever named, so no location leaves the
// server. The two beyond diag are what tell a working phone from a dead one: the app can report
// perfect settings and still have stopped sending, which is what "sharing on, last seen 2 days
// ago" looks like. Same admin key as tools/read-feedback.mjs.

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

// "4 minutes ago". The number on its own is unreadable, and the gap is the whole point: a phone
// that is sharing but last spoke yesterday is being killed, and that only shows as elapsed time.
// A field an older Mogar never sent comes back undefined, which reads far too much like "false"
// in a column of true/false. Say so instead.
const said = (v) => (v === undefined || v === null ? "not reported (older Mogar)" : v);

function ago(iso) {
  if (!iso) return "never";
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms)) return String(iso);
  const mins = Math.round(ms / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} minute${mins === 1 ? "" : "s"} ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"} ago`;
}

const res = await fetch(
  `https://firestore.googleapis.com/v1/projects/${key.project_id}/databases/(default)/documents:runQuery`,
  {
    method: "POST",
    headers: { Authorization: `Bearer ${await token()}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "members", allDescendants: true }],
        select: {
          fields: [
            { fieldPath: "name" },
            { fieldPath: "diag" },
            { fieldPath: "sharing" },
            { fieldPath: "updatedAt" },
          ],
        },
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
  console.log(`  sharing switch                ${m.sharing}`);
  console.log(`  last location                 ${ago(m.updatedAt)}`);
  console.log(`  step 1 battery unrestricted   ${said(d.unrestricted)}`);
  console.log(`  step 2 brand step ticked      ${said(d.brandStepDone)}`);
  // Whether this phone had ANY brand page to open. A tick with this false was made off the
  // written card rather than a button, which is legitimate but is also what a blind tick
  // looks like, so the two have to be told apart.
  console.log(`  brand page existed            ${said(d.brandPageFound)}`);
  console.log(`  step 3 never auto-paused      ${said(d.neverPaused)}`);
  console.log(`  they said the button failed   ${said(d.brandPageFailed)}`);
  // Read at the last cold open and then re-sent with every location, so it goes stale: a phone
  // sending right now can still report false here. "last location" above is the live signal.
  console.log(`  sharer alive at last open     ${said(d.sharerRunningAtOpen)}`);
  if (d.lastCrash) console.log(`  last crash ${d.lastCrash}`);
}
