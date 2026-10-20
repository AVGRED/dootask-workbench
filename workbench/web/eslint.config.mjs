import js from "@eslint/js";
import tseslint from "typescript-eslint";
import vue from "eslint-plugin-vue";

const browserGlobals = {
  window: "readonly",
  document: "readonly",
  localStorage: "readonly",
  sessionStorage: "readonly",
  navigator: "readonly",
  console: "readonly",
  fetch: "readonly",
  setTimeout: "readonly",
  clearTimeout: "readonly",
  setInterval: "readonly",
  clearInterval: "readonly",
  requestAnimationFrame: "readonly",
  matchMedia: "readonly",
  URL: "readonly",
  URLSearchParams: "readonly",
  File: "readonly",
  FileReader: "readonly",
  Blob: "readonly",
  FormData: "readonly",
  Event: "readonly",
  KeyboardEvent: "readonly",
  MouseEvent: "readonly",
  DragEvent: "readonly",
  PointerEvent: "readonly",
  HTMLElement: "readonly",
  HTMLInputElement: "readonly",
  HTMLCanvasElement: "readonly",
  Element: "readonly",
  Node: "readonly",
  crypto: "readonly",
  process: "readonly",
  Buffer: "readonly",
  __dirname: "readonly",
  importMeta: "readonly"
};

export default tseslint.config(
  {
    ignores: [
      "**/dist/**",
      "**/node_modules/**",
      "**/test-results/**",
      "**/playwright-report/**",
      "**/*.d.ts",
      "**/public/sw.js"
    ]
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs["flat/recommended"],
  {
    files: ["**/*.{ts,vue,mjs,js}"],
    languageOptions: {
      globals: browserGlobals,
      parserOptions: {
        ecmaVersion: "latest",
        sourceType: "module"
      }
    }
  },
  {
    files: ["**/*.vue"],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
        extraFileExtensions: [".vue"]
      }
    }
  },
  {
    files: ["apps/e2e/**/*.mjs"],
    rules: {
      "@typescript-eslint/no-unused-vars": "off"
    }
  },
  {
    rules: {
      "vue/multi-word-component-names": "off",
      "vue/require-default-prop": "off",
      "vue/no-required-prop-with-default": "off",
      "vue/multiline-html-element-content-newline": "off",
      "vue/attributes-order": "off",
      "vue/max-attributes-per-line": "off",
      "vue/singleline-html-element-content-newline": "off",
      "vue/html-self-closing": "off",
      "vue/html-indent": "off",
      "vue/first-attribute-linebreak": "off",
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": ["warn", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
      "no-unused-vars": "off"
    }
  }
);
