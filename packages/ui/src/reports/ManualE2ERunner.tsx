import { useEffect, useRef, useState, type FormEvent } from "react";

export type ManualResult = "PASS" | "FAIL" | "BLOCKED" | "NOT_RUN";
export interface ManualE2EResult {
  caseId: string; result: ManualResult; actorRole: string; executedAt: string;
  buildFingerprint: string; actualResult: string; evidenceOrWaiver: string;
}

export function ManualE2ERunner({ caseId, title, onSubmit }: {
  caseId: string; title: string; onSubmit: (result: ManualE2EResult) => void;
}) {
  const [result, setResult] = useState<ManualResult>("NOT_RUN");
  const [actorRole, setActorRole] = useState("");
  const [executedAt, setExecutedAt] = useState("");
  const [buildFingerprint, setBuildFingerprint] = useState("");
  const [actualResult, setActualResult] = useState("");
  const [evidenceOrWaiver, setEvidenceOrWaiver] = useState("");
  const [error, setError] = useState("");
  const errorRef = useRef<HTMLParagraphElement>(null);
  const evidenceRequired = result !== "NOT_RUN";

  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (result !== "NOT_RUN" && [actorRole, executedAt, buildFingerprint, actualResult, evidenceOrWaiver].some((v) => !v.trim())) {
      setError("A recorded result requires actor role, execution time, build fingerprint, actual result, and evidence or waiver.");
      return;
    }
    setError("");
    onSubmit({ caseId, result, actorRole, executedAt, buildFingerprint, actualResult, evidenceOrWaiver });
  };

  return <form className="sdlc-card sdlc-stack" onSubmit={submit}>
    <h2>{title}</h2>
    <label>Result<select value={result} onChange={(e) => setResult(e.target.value as ManualResult)}>
      <option value="NOT_RUN">Not run</option><option value="PASS">Pass</option>
      <option value="FAIL">Fail</option><option value="BLOCKED">Blocked</option>
    </select></label>
    <label>Actor role<input aria-required={evidenceRequired} aria-invalid={Boolean(error)} aria-describedby={error ? "manual-e2e-error" : undefined} value={actorRole} onChange={(e) => setActorRole(e.target.value)} /></label>
    <label>Execution time<input aria-required={evidenceRequired} aria-invalid={Boolean(error)} aria-describedby={error ? "manual-e2e-error" : undefined} type="datetime-local" value={executedAt} onChange={(e) => setExecutedAt(e.target.value)} /></label>
    <label>Build fingerprint<input aria-required={evidenceRequired} aria-invalid={Boolean(error)} aria-describedby={error ? "manual-e2e-error" : undefined} value={buildFingerprint} onChange={(e) => setBuildFingerprint(e.target.value)} /></label>
    <label>Actual result<textarea aria-required={evidenceRequired} aria-invalid={Boolean(error)} aria-describedby={error ? "manual-e2e-error" : undefined} value={actualResult} onChange={(e) => setActualResult(e.target.value)} /></label>
    <label>Evidence or waiver<textarea aria-required={evidenceRequired} aria-invalid={Boolean(error)} aria-describedby={error ? "manual-e2e-error" : undefined} value={evidenceOrWaiver} onChange={(e) => setEvidenceOrWaiver(e.target.value)} /></label>
    {error && <p id="manual-e2e-error" ref={errorRef} role="alert" tabIndex={-1}>{error}</p>}
    <button type="submit">Save manual result</button>
  </form>;
}
