/**
 * Deploy MCP server of ONE environment (DEPLOY_ENV = dev, staging or prod).
 *
 * It turns the environment's deployment powers into three tools:
 *   release   deploy a version (production: only with an authorized change ticket)
 *   status    wait for the observation window after the last release, then
 *             report the 5xx rate of every endpoint
 *   rollback  go back to the last good version; returns the version live again
 *
 * The agent never gets a shell, the container engine or the environment's
 * passwords: only an HTTP endpoint with a bearer token, and only these tools
 * with validated arguments. Every tool runs deploy/stack.sh with a fixed
 * argument list (no shell), the same commands a person would run.
 *
 * Transport: MCP Streamable HTTP, stateless (one server per request), on
 * POST /mcp. GET /healthz is the only unauthenticated path.
 */
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { execFile } from "node:child_process";
import { createHash, timingSafeEqual } from "node:crypto";
import { readFileSync } from "node:fs";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { z } from "zod";
import { checkProductionTicket } from "./gate.ts";

const ENVIRONMENTS = ["dev", "staging", "prod"] as const;
type Environment = (typeof ENVIRONMENTS)[number];

const environment = process.env.DEPLOY_ENV as Environment;
if (!ENVIRONMENTS.includes(environment)) fail(`DEPLOY_ENV must be one of ${ENVIRONMENTS.join(", ")}`);

// Fail closed: without a token the server does not start.
const tokenFile = process.env.DEPLOY_TOKEN_FILE ?? "";
const expectedToken = tokenFile ? readFileSync(tokenFile, "utf8").trim() : "";
if (expectedToken.length < 32) fail("DEPLOY_TOKEN_FILE must contain a token of at least 32 characters");

const stackScript = process.env.DEPLOY_STACK_SCRIPT ?? "/opt/cv/deploy/stack.sh";
const port = Number(process.env.PORT ?? 8080);
const actor = `agent via deploy-${environment}`;
const version = z.string().regex(/^[0-9a-f]{12}$/, "a version is the 12-character commit SHA");

function fail(message: string): never {
  console.error(`deploy-mcp: ${message}`);
  process.exit(1);
}

function log(event: string, details: Record<string, unknown> = {}): void {
  // One JSON line per event: the audit trail of what the agent asked for.
  console.log(JSON.stringify({ at: new Date().toISOString(), environment, event, ...details }));
}

interface Result {
  ok: boolean;
  stdout: string;
  stderr: string;
}

/** Runs deploy/stack.sh for this environment. Arguments are passed as a list, never through a shell. */
function stack(args: string[], timeoutMs: number): Promise<Result> {
  return new Promise((resolve) => {
    execFile(
      "bash",
      [stackScript, environment, ...args],
      { timeout: timeoutMs, maxBuffer: 4 * 1024 * 1024, env: { ...process.env, CV_DEPLOY_ACTOR: actor } },
      (error, stdout, stderr) => resolve({ ok: !error, stdout: stdout.trim(), stderr: stderr.trim() }),
    );
  });
}

/** Releases and rollbacks of this environment run one at a time. */
let queue: Promise<unknown> = Promise.resolve();
function serialized<T>(task: () => Promise<T>): Promise<T> {
  const next = queue.then(task, task);
  queue = next.catch(() => undefined);
  return next;
}

function reply(payload: Record<string, unknown>, isError = false) {
  return { content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }], isError };
}

function lastLines(text: string, n = 15): string {
  return text.split("\n").slice(-n).join("\n");
}

const windowSeconds = Number(process.env.CV_STATUS_WINDOW_SECONDS ?? 300);

function buildServer(): McpServer {
  const server = new McpServer({ name: `deploy-${environment}`, version: "1.0.0" });

  const releaseDescription =
    `Deploy a version to ${environment}. The version is the 12-character commit SHA whose images ` +
    "were built by the pipeline (deploy/build.sh). Returns the release ID once every service is healthy." +
    (environment === "prod"
      ? " Production: requires the number of the change ticket (GitHub issue) that the release manager " +
        "authorized for exactly this version; without it the release is refused."
      : "");
  const doRelease = (args: { version: string; ticket?: number }) =>
    serialized(async () => {
      log("release.requested", { version: args.version, ticket: args.ticket });
      const extra: string[] = [];
      if (environment === "prod") {
        // Server-side production gate: enforced here even if no hook runs on the client.
        const gate = await checkProductionTicket(args.ticket, args.version);
        if (!gate.authorized) {
          log("release.refused", { version: args.version, ticket: args.ticket, reason: gate.reason });
          return reply({ released: false, reason: gate.reason }, true);
        }
        extra.push("--ticket", String(args.ticket));
      }
      const result = await stack(["release", args.version, ...extra], 15 * 60_000);
      log(result.ok ? "release.done" : "release.failed", { version: args.version, releaseId: result.stdout });
      return result.ok
        ? reply({ released: true, releaseId: result.stdout, version: args.version })
        : reply({ released: false, version: args.version, log: lastLines(result.stderr) }, true);
    });

  const releaseTitle = `Release to ${environment}`;
  if (environment === "prod") {
    const ticket = z.number().int().positive().describe("Change ticket: GitHub issue number");
    server.registerTool(
      "release",
      { title: releaseTitle, description: releaseDescription, inputSchema: { version, ticket } },
      doRelease,
    );
  } else {
    server.registerTool(
      "release",
      { title: releaseTitle, description: releaseDescription, inputSchema: { version } },
      doRelease,
    );
  }

  server.registerTool(
    "status",
    {
      title: `Status of ${environment}`,
      description:
        `Waits until ${windowSeconds} seconds have passed since the last release or rollback in ${environment}, ` +
        "then returns the running version and the 5xx error rate of every endpoint since that release, " +
        "with 'breached' true if any endpoint is above the threshold (1%).",
      annotations: { readOnlyHint: true },
    },
    async () => {
      log("status.requested");
      const result = await stack(["status", "--wait", "--json"], (windowSeconds + 120) * 1000);
      if (!result.ok) return reply({ error: lastLines(result.stderr) }, true);
      log("status.done", { status: JSON.parse(result.stdout) });
      return { content: [{ type: "text" as const, text: result.stdout }] };
    },
  );

  server.registerTool(
    "rollback",
    {
      title: `Roll back ${environment}`,
      description:
        `Rolls ${environment} back to the last good version before the current one and returns the ` +
        "version that is live again. Always allowed, also in production.",
      annotations: { destructiveHint: false, idempotentHint: false },
    },
    async () =>
      serialized(async () => {
        log("rollback.requested");
        const result = await stack(["rollback"], 15 * 60_000);
        log(result.ok ? "rollback.done" : "rollback.failed", { version: result.stdout });
        return result.ok
          ? reply({ rolledBack: true, liveVersion: result.stdout })
          : reply({ rolledBack: false, log: lastLines(result.stderr) }, true);
      }),
  );

  return server;
}

// --- HTTP ------------------------------------------------------------------

const expectedDigest = createHash("sha256").update(`Bearer ${expectedToken}`).digest();

/** Constant-time comparison of the Authorization header. */
function authorized(req: IncomingMessage): boolean {
  const given = createHash("sha256").update(req.headers.authorization ?? "").digest();
  return timingSafeEqual(given, expectedDigest);
}

function send(res: ServerResponse, status: number, body: unknown): void {
  res.writeHead(status, { "content-type": "application/json" }).end(JSON.stringify(body));
}

async function readJson(req: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > 1024 * 1024) throw new Error("request too large");
    chunks.push(chunk as Buffer);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

createServer(async (req, res) => {
  const path = (req.url ?? "").split("?")[0];
  if (req.method === "GET" && path === "/healthz") return send(res, 200, { ok: true, environment });
  if (path !== "/mcp") return send(res, 404, { error: "not found" });
  if (!authorized(req)) {
    log("auth.rejected", { remote: req.socket.remoteAddress });
    return send(res, 401, { error: "missing or invalid bearer token" });
  }
  // Stateless server: no sessions, so no GET stream and no DELETE.
  if (req.method !== "POST") return send(res, 405, { error: "method not allowed" });

  try {
    const body = await readJson(req);
    const server = buildServer();
    const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
    res.on("close", () => {
      void transport.close();
      void server.close();
    });
    await server.connect(transport);
    await transport.handleRequest(req, res, body);
  } catch (error) {
    log("request.error", { error: String(error) });
    if (!res.headersSent) send(res, 400, { error: "invalid request" });
  }
}).listen(port, () => log("listening", { port }));
