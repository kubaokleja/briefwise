import { defineConfig, globalIgnores } from 'eslint/config';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default defineConfig(
  globalIgnores(['dist', 'src/api/schema.d.ts']),
  tseslint.configs.recommended,
  reactHooks.configs.flat.recommended,
  {
    rules: {
      // LLM output is untrusted: always render it as text, never as HTML (ADR 0007, DESIGN 15).
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'Never render HTML from data: AI and API text is untrusted. Render it as text.',
        },
      ],
    },
  },
);
