import { useState, type FormEvent } from "react";

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
    <label>Actor role<input value={actorRole} onChange={(e) => setActorRole(e.target.value)} /></label>
    <label>Execution time<input type="datetime-local" value={executedAt} onChange={(e) => setExecutedAt(e.target.value)} /></label>
    <label>Build fingerprint<input value={buildFingerprint} onChange={(e) => setBuildFingerprint(e.target.value)} /></label>
    <label>Actual result<textarea value={actualResult} onChange={(e) => setActualResult(e.target.value)} /></label>
    <label>Evidence or waiver<textarea value={evidenceOrWaiver} onChange={(e) => setEvidenceOrWaiver(e.target.value)} /></label>
    {error && <p role="alert">{error}</p>}
    <button type="submit">Save manual result</button>
  </form>;
}
