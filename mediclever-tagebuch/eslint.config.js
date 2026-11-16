import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['plugin/**/build/**', 'app/src/icons.gen.ts', 'node_modules/**', 'vendor/**', '.wp/**'] },
  ...tseslint.configs.recommended,
  {
    rules: {
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-console': ['error', { allow: ['warn', 'error'] }],
      eqeqeq: 'error',
    },
  },
  { files: ['scripts/**', 'tests/**'], rules: { 'no-console': 'off' } },
);
