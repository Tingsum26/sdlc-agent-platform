---
applyTo: "**/*.{ts,tsx,js,jsx,css,html}"
---

# Web instructions

- Use semantic HTML, native controls, document-order keyboard navigation, visible focus, non-color status labels, text alternatives for graphs, reduced motion, and responsive layouts.
- Target WCAG 2.2 AA evidence, including automated checks plus keyboard, zoom, screen-reader, stale/offline, long-content, and high-contrast manual cases.
- Preserve API compatibility and analytics/tagging contracts. Do not assume native clients release with Web/API.
- Escape model/user content and use a strict Content Security Policy in Webviews. Never inject unsanitized HTML or expose secrets to the browser.
- Test behavior through accessible names and roles rather than implementation details.
