/**
 * Minimal MCP client, to call a deploy server by hand the same way the agent
 * does (useful to study the protocol and to test the servers).
 *
 *   node src/client.ts <env> tools
 *   node src/client.ts <env> <tool> ['{"version":"..."}']
 *
 * Reads the server's URL from DEPLOY_URL (default http://127.0.0.1:<port>/mcp,
 * the port published on the host by start.sh) and the token from
 * ~/.config/cv-generator-deploy/<env>/mcp-token, or DEPLOY_TOKEN.
 */
import { readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";

const ports: Record<string, number> = { dev: 9280, staging: 9380, prod: 9480 };
const [env, tool, rawArgs] = process.argv.slice(2);
if (!env || !(env in ports) || !tool) {
  console.error("usage: node src/client.ts <dev|staging|prod> <tools|release|status|rollback> [json arguments]");
  process.exit(2);
}

const stateDir = process.env.CV_DEPLOY_STATE_DIR ?? join(homedir(), ".config", "cv-generator-deploy");
const token = process.env.DEPLOY_TOKEN ?? readFileSync(join(stateDir, env, "mcp-token"), "utf8").trim();
const url = new URL(process.env.DEPLOY_URL ?? `http://127.0.0.1:${ports[env]}/mcp`);

const client = new Client({ name: "deploy-client", version: "1.0.0" });
await client.connect(
  new StreamableHTTPClientTransport(url, { requestInit: { headers: { Authorization: `Bearer ${token}` } } }),
);

if (tool === "tools") {
  const { tools } = await client.listTools();
  for (const t of tools) {
    console.log(`${t.name}: ${t.description}`);
    console.log(`  input: ${JSON.stringify(t.inputSchema.properties ?? {})}`);
  }
} else {
  const result = await client.callTool(
    { name: tool, arguments: rawArgs ? JSON.parse(rawArgs) : {} },
    undefined,
    { timeout: 30 * 60_000 },
  );
  for (const item of result.content as Array<{ type: string; text?: string }>) {
    if (item.type === "text") console.log(item.text);
  }
  if (result.isError) process.exitCode = 1;
}
await client.close();
