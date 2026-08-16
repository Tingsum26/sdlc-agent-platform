import { useState } from "react";

export interface ApprovalReference { artifactId: string; artifactVersion: number; taskVersion: number }

export function ApprovalPanel(props: ApprovalReference & { onApprove: (value: ApprovalReference) => void }) {
  const [confirmed, setConfirmed] = useState(false);
  const label = `I reviewed artifact ${props.artifactId} version ${props.artifactVersion}`;
  return <section className="sdlc-card sdlc-stack" aria-labelledby="approval-heading">
    <h2 id="approval-heading">Human approval</h2>
    <p>Approval applies only to task version {props.taskVersion} and artifact version {props.artifactVersion}.</p>
    <label><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /> {label}</label>
    <button type="button" disabled={!confirmed} onClick={() => props.onApprove({
      artifactId: props.artifactId, artifactVersion: props.artifactVersion, taskVersion: props.taskVersion,
    })}>Approve version {props.artifactVersion}</button>
  </section>;
}
