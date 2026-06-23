#!/usr/bin/env python3
"""FCM v1 — envoi push one-shot (test device, Phase 3 push natif).

Auth = service account (JWT RS256 -> token OAuth2), puis POST FCM HTTP v1.
⚠️ REQUIERT le service account FCM du projet **oneclick-129bb** (PAS oneclick-wallet) :
    Firebase Console → oneclick-129bb → Paramètres du projet → Comptes de service
    → "Générer une nouvelle clé privée" → fichier JSON. (= même clé que le secret
    Supabase FCM_SERVICE_ACCOUNT_JSON utilisé par les Edge Functions.)

Le token FCM du device est : (a) passé via --token, ou (b) lu dans la table
`device_tokens` du Spring local via --from-db ios|android (psql).

Deps : PyJWT + cryptography (HTTP via urllib stdlib — pas besoin de `requests`).

Exemples :
    python3 fcm_test_send.py --sa ~/oneclick-129bb-fcm.json --from-db ios
    python3 fcm_test_send.py --sa ~/oneclick-129bb-fcm.json --token <FCM_TOKEN> \
        --title "Bravo" --body "Le push natif marche 🎉"
"""
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.parse
import urllib.request

import jwt  # PyJWT

SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


def mint_access_token(sa: dict) -> str:
    """JWT RS256 signé par le service account → token OAuth2 (firebase.messaging)."""
    now = int(time.time())
    assertion = jwt.encode(
        {
            "iss": sa["client_email"],
            "scope": SCOPE,
            "aud": sa["token_uri"],
            "iat": now,
            "exp": now + 3600,
        },
        sa["private_key"],
        algorithm="RS256",
    )
    body = urllib.parse.urlencode(
        {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion}
    ).encode()
    req = urllib.request.Request(
        sa["token_uri"], data=body, headers={"Content-Type": "application/x-www-form-urlencoded"}
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.load(resp)["access_token"]


def latest_token_from_db(platform: str) -> str:
    """Dernier token `device_tokens` (Spring local oneclick_enterprise) pour ce platform."""
    query = (
        "select token from device_tokens "
        f"where platform = '{platform}' "
        "order by last_used_at desc nulls last, created_at desc limit 1;"
    )
    env = dict(os.environ, PGPASSWORD="OneclickLocal2026")
    out = subprocess.run(
        ["psql", "-h", "localhost", "-U", "oneclick_app", "-d", "oneclick_enterprise", "-tAc", query],
        env=env, capture_output=True, text=True,
    )
    if out.returncode != 0:
        sys.exit(f"psql a échoué : {out.stderr.strip()}")
    return out.stdout.strip()


def send(sa: dict, token: str, title: str, body: str):
    project = sa["project_id"]
    url = f"https://fcm.googleapis.com/v1/projects/{project}/messages:send"
    payload = {"message": {"token": token, "notification": {"title": title, "body": body}}}
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode(),
        headers={
            "Authorization": f"Bearer {mint_access_token(sa)}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main() -> None:
    ap = argparse.ArgumentParser(description="Envoi FCM one-shot (test device)")
    ap.add_argument("--sa", required=True, help="chemin du service account FCM (projet oneclick-129bb)")
    ap.add_argument("--token", help="FCM device token (sinon --from-db)")
    ap.add_argument("--from-db", choices=["ios", "android"], help="dernier token de ce platform dans device_tokens")
    ap.add_argument("--title", default="OneClick — test push")
    ap.add_argument("--body", default="Si tu vois ceci, le push natif fonctionne 🎉")
    args = ap.parse_args()

    sa = json.load(open(args.sa))
    if sa.get("project_id") != "oneclick-129bb":
        print(
            f"⚠️  project_id={sa.get('project_id')} ≠ oneclick-129bb — ce service account "
            "n'enverra PAS vers les tokens oneclick-129bb (403 PERMISSION_DENIED attendu).",
            file=sys.stderr,
        )

    token = args.token or (latest_token_from_db(args.from_db) if args.from_db else None)
    if not token:
        sys.exit("Aucun token : passe --token <FCM> ou --from-db ios|android")

    print(f"→ envoi à {token[:24]}… (projet {sa['project_id']})")
    status, resp = send(sa, token, args.title, args.body)
    print(f"HTTP {status}\n{resp}")
    sys.exit(0 if status == 200 else 1)


if __name__ == "__main__":
    main()
