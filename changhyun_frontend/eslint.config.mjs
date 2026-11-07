import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { FlatCompat } from '@eslint/eslintrc';

const compat = new FlatCompat({ baseDirectory: dirname(fileURLToPath(import.meta.url)) });

const eslintConfig = [
  {
    // schema.d.ts is generated from contracts/openapi/core-api.yaml and never hand-edited.
    ignores: ['.next/**', 'node_modules/**', 'coverage/**', 'next-env.d.ts', 'src/lib/api/schema.d.ts', 'mock-server/**'],
  },
  ...compat.extends('next/core-web-vitals', 'next/typescript'),
];

export default eslintConfig;
