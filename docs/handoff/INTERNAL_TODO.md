# INTERNAL TODO Registry

Every internal-network configuration point in public code carries a
`TODO(INTERNAL): INTERNAL-XXX` marker and MUST be listed here. M8 adds CI
enforcement so an unregistered marker fails the build.

| ID | Component | File | Internal agent action | Evidence required | Rollback |
|---|---|---|---|---|---|
| INTERNAL-IDN-001 | workflow-service | `api/InternalReadinessController.java` | Replace demo enrollment-code issuance with the corporate SSO/manual admin binding flow | Sanitized identity binding test results | Keep the endpoint behind the `fake` profile |
| INTERNAL-IDN-002 | workflow-service | `config/FakeRuntimeConfiguration.java` | Seed real admin-principal provisioning instead of the fictional `EMP-100` | Identity binding log entry | Re-seed the fictitious principal |
| INTERNAL-POD-001 | workflow-service | `api/InternalReadinessController.java` | Replace manual roster import with Teambook/HR sync when approved | Sanitized import report | Keep manual CSV/JSON import active |
