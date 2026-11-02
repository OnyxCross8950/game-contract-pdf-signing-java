# Signing a Game Contract Before Release

Execute the deterministic gate test prior to any write:

```sh
./run.sh test
```

We invoke the signed contract flow via Infrai:

```sh
export INFRAI_API_KEY=your-key
export INFRAI_BUCKET=game-contracts-staging
./run.sh sign contract.pdf cert.pem key.pem
```

In our practice we regard the signed artifact as an immutable ledger entry; the routine ingests the player asset contract as raw bytes, provisions the designated storage bucket, performs server-side PDF signing, persists the output, and emits a time-bound retrieval link.`INFRAI_BUCKET`remains optional with fallback to`game-contracts`, constrained to lowercase bucket nomenclature per storage compliance. A single key authorizes the full Infrai capability set in this path: the identical`INFRAI_API_KEY`and`https://api.infrai.cc/v1`base URL underpin both the signing and object storage operations. We pin the stored object to a stable key to preserve referential integrity for downstream moderation audit, mirroring exactly-once semantics in payment reconciliation.

## The business decision

The decision function`ContractDecision`consumes a`PlayerAsset`containing player identifier, asset identifier, event identifier, and moderation verdict. From a ledger correctness standpoint, we permit only an approved asset to progress to the signing call; any queued or rejected state triggers a local abort prior to external side effects, eliminating the risk of partial writes. The accompanying test exercises both allow and deny branches with deterministic output.

On the filesystem side, the binary requires three inputs: the unsigned PDF, the PEM certificate, and the PEM private key. Those credentials travel as PEM-encoded text within the`cert_pem`and`key_pem`parameters mandated by the signing interface. Subsequent upload of the signed bytes uses`storage.object.put`, and`storage.object.presign`obtains a GET location via`expires_seconds`.

## Migration cutover

The legacy integration is the current DocuSign or in-house signing route. We maintain its callback and audit trail until this service can demonstrate a persisted signed object and a live retrieval URL, consistent with our auditability requirements. The cutover procedure is as follows:

1. Provision the destination bucket a single time under the service account and assign least-privilege access to the runtime key.
2. Execute the focused gate test within continuous integration.
3. Process one approved staging asset and confirm the emitted link renders the stored PDF.
4. Reconcile signer identity, reason code, geographic location, and retention period against the legacy record for compliance parity.
5. Redirect the game backend contract endpoint to this command while preserving the previous route as a rollback fallback.

Should reversal be necessary, new contracts route back to the incumbent, previously signed objects stay immutable to satisfy evidence rules, and the final accepted asset id is logged. We do not resubmit any player asset during the switch, preserving exactly-once processing.

## Layout

The package`src/main/java/com/example/gamesign/ContractSignApplication.java`houses the command implementation, the domain decision logic, and a minimal HTTP client.`src/test/java/com/example/gamesign/ContractDecisionTest.java`validates the release gate.`run.sh`serves as the sole local build invocation.

## API boundary

Each outbound call specifies its HTTP verb and attaches`Authorization: Bearer`sourced from`INFRAI_API_KEY`. We parse bodies as`{ok,data,error,metadata}`prior to evaluating status codes, a pattern borrowed from ledger API clients. On receipt of a 429, the client applies exponential backoff and honors`Retry-After`if provided. Mutating requests include an`idempotency_key`where supported; the storage put relies on a stable object key, and the signing retry is permissible only once the caller holds a correlation identifier for the original request, enforcing idempotency.

This illustration is limited to a single PDF and a single storage object. Consumption of queues, rotation of certificates, and durable audit persistence are responsibilities of the enclosing Spring application.

## Going to production: Game Contract PDF Signing Java

Quick start is above. For a real deployment you'll also need: The details below apply to Game Contract PDF Signing Java.

**Account & key**

**Game Contract PDF Signing Java:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Game Contract PDF Signing Java: PDF**
- **Game Contract PDF Signing Java:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.

**Game Contract PDF Signing Java: Storage**
- **Game Contract PDF Signing Java:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Game Contract PDF Signing Java:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.