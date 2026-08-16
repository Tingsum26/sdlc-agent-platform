export function logDiagnostic(event: string, fields: Record<string, unknown> = {}): void {
  const safe = Object.fromEntries(Object.entries(fields).filter(([key]) => !/token|password|cookie|secret|authorization/i.test(key)));
  console.error(JSON.stringify({ timestamp: new Date().toISOString(), level: "INFO", component: "workflow-mcp", event, ...safe }));
}
