#!/usr/bin/env node
/**
 * Claude Code PreToolUse file guard (cross-platform Node ESM, zero dependencies).
 * Blocks Read, Edit, Write, MultiEdit, Grep, Glob, and NotebookEdit targeting
 * `.env` or `.env.*` while explicitly allowing `.env.example`.
 */
import { pathToFileURL } from 'node:url';
import { isBlockedEnvPath, readStdinJson } from './lib.mjs';

const FILE_TOOLS = new Set([
  'Read',
  'Edit',
  'Write',
  'MultiEdit',
  'Grep',
  'Glob',
  'NotebookEdit',
]);

export function evaluateFileTool(payload) {
  const toolName = payload?.tool_name ?? '';
  if (toolName && !FILE_TOOLS.has(toolName)) {
    return { blocked: false };
  }

  const input = payload?.tool_input ?? payload ?? {};
  const candidates = [
    input.file_path,
    input.notebook_path,
    input.path,
    input.glob,
    input.pattern,
  ].filter((v) => typeof v === 'string' && v.trim().length > 0);

  if (Array.isArray(input.edits)) {
    for (const edit of input.edits) {
      if (typeof edit?.file_path === 'string') {
        candidates.push(edit.file_path);
      }
    }
  }

  for (const candidate of candidates) {
    if (isBlockedEnvPath(candidate)) {
      return {
        blocked: true,
        reason:
          `BLOCKED: Tool '${toolName || 'file'}' cannot access secret environment file '${candidate}'. ` +
          `Use '.env.example' for templates and ask the developer to edit '.env' locally.`,
      };
    }
  }

  return { blocked: false };
}

function main() {
  const payload = readStdinJson();
  const result = evaluateFileTool(payload);
  if (result.blocked) {
    process.stderr.write(`${result.reason}\n`);
    process.exit(2);
  }
  process.exit(0);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
