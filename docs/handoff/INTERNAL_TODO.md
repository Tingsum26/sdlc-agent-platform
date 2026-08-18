# INTERNAL TODO Registry

Every internal-network configuration point in public code carries a
`TODO(INTERNAL): INTERNAL-XXX` marker and MUST be listed here. M8 adds CI
enforcement so an unregistered marker fails the build.

| ID | Component | File | Internal agent action | Evidence required | Rollback |
|---|---|---|---|---|---|
| INTERNAL-IDN-001 | workflow-service | `api/InternalReadinessController.java` | Replace demo enrollment-code issuance with the corporate SSO/manual admin binding flow | Sanitized identity binding test results | Keep the endpoint behind the `fake` profile |
| INTERNAL-IDN-002 | workflow-service | `config/FakeRuntimeConfiguration.java` | Seed real admin-principal provisioning instead of the fictional `EMP-100` | Identity binding log entry | Re-seed the fictitious principal |
| INTERNAL-POD-001 | workflow-service | `api/InternalReadinessController.java` | Replace manual roster import with Teambook/HR sync when approved | Sanitized import report | Keep manual CSV/JSON import active |
| INTERNAL-EPIC-001 | workflow-service | `api/EpicController.java` | Sync Epic creation and Ticket status changes with the company Jira | Sanitized Jira projection log | Remove the sync call behind the `fake` profile |
| INTERNAL-AUD-001 | workflow-service | `config/*RuntimeConfiguration.java` | Persist M2 domain aggregates (epic/ticket/repo-task/dependency/change-request/skip/audit) to MongoDB | Sanitized Mongo mapping test report | Revert to in-memory beans |
| INTERNAL-JIRA-001 | workflow-service | `config/*RuntimeConfiguration.java`, `api/EpicController.java` | Route the Jira projection outbox to the real Jira comment API with credentials | Sanitized Jira comment publish log | Revert to the fake projection client |
| INTERNAL-CI-001 | workflow-service | `config/*RuntimeConfiguration.java`, `api/EpicController.java` | Route CI status to the real Jenkins adapter | Sanitized CI status log | Revert to the mock CI adapter |
| INTERNAL-SPLUNK-001 | workflow-service | `splunk/SplunkAuditPublisher.java` (Task 3) | Point the Splunk audit publisher at the real HEC endpoint | Sanitized HEC event log | Revert to the fake transport |
