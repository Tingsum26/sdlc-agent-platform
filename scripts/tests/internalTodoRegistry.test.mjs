import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { validateRegistry } from '../lib/internalTodoRegistry.mjs';

const registryEntry = (id, markerPaths = ['apps/sample/src/Example.java']) => ({
  id,
  component: 'sample',
  markerPaths,
  action: 'Complete the internal replacement.',
  evidence: 'Sanitized test result.',
  rollback: 'Restore the fake adapter.'
});

async function fixture({ entries, markdownIds, markers }) {
  const rootDirectory = await mkdtemp(join(tmpdir(), 'internal-todo-registry-'));
  await mkdir(join(rootDirectory, 'apps/sample/src'), { recursive: true });
  await mkdir(join(rootDirectory, 'docs/handoff'), { recursive: true });
  await writeFile(join(rootDirectory, 'apps/sample/src/Example.java'), markers.join('\n'));
  await writeFile(
    join(rootDirectory, 'docs/handoff/internal-todo-registry.json'),
    JSON.stringify({ entries }, null, 2)
  );
  await writeFile(
    join(rootDirectory, 'docs/handoff/INTERNAL_TODO.md'),
    ['| ID | Component |', '|---|---|', ...markdownIds.map((id) => `| ${id} | sample |`)].join('\n')
  );
  return rootDirectory;
}

async function withFixture(input, assertion) {
  const rootDirectory = await fixture(input);
  try {
    await assertion(rootDirectory);
  } finally {
    await rm(rootDirectory, { recursive: true, force: true });
  }
}

test('validates the checked-in registry against its canonical source markers', () => {
  const result = validateRegistry({ rootDirectory: process.cwd() });

  assert.equal(result.errors.length, 0, result.errors.join('\n'));
  assert.equal(result.registryCount, 10);
  assert.equal(result.markerCount, 19);
});

test('reports a canonical source marker that is not registered', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001')],
      markdownIds: ['INTERNAL-SAMPLE-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid', '// TODO(INTERNAL): INTERNAL-OTHER-001 missing']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.deepEqual(result.errors, [
        'Unregistered source marker: INTERNAL-OTHER-001 (apps/sample/src/Example.java)'
      ]);
    }
  );
});

test('reports a JSON entry with no canonical source marker', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001'), registryEntry('INTERNAL-MISSING-001')],
      markdownIds: ['INTERNAL-SAMPLE-001', 'INTERNAL-MISSING-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.ok(result.errors.includes('Registry entry has no source marker: INTERNAL-MISSING-001'));
    }
  );
});

test('reports a marker path mismatch', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001', ['apps/sample/src/Wrong.java'])],
      markdownIds: ['INTERNAL-SAMPLE-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.ok(result.errors.includes(
        'Marker paths do not match for INTERNAL-SAMPLE-001: expected [apps/sample/src/Wrong.java], found [apps/sample/src/Example.java]'
      ));
    }
  );
});

test('reports a Markdown and JSON ID mismatch', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001')],
      markdownIds: ['INTERNAL-DOCUMENTED-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.ok(result.errors.includes('Markdown is missing registry IDs: INTERNAL-SAMPLE-001'));
      assert.ok(result.errors.includes('Markdown has IDs absent from registry: INTERNAL-DOCUMENTED-001'));
    }
  );
});

test('reports duplicate IDs in the Markdown registry table', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001')],
      markdownIds: ['INTERNAL-SAMPLE-001', 'INTERNAL-SAMPLE-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.deepEqual(result.errors, ['Duplicate Markdown registry ID: INTERNAL-SAMPLE-001']);
    }
  );
});

test('does not mistake a template placeholder for a canonical source marker', async () => {
  await withFixture(
    {
      entries: [registryEntry('INTERNAL-SAMPLE-001')],
      markdownIds: ['INTERNAL-SAMPLE-001'],
      markers: ['// TODO(INTERNAL): INTERNAL-SAMPLE-001 valid', '// TODO(INTERNAL): INTERNAL-XXX placeholder']
    },
    async (rootDirectory) => {
      const result = validateRegistry({ rootDirectory });
      assert.deepEqual(result.errors, []);
      assert.equal(result.markerCount, 1);
    }
  );
});
