# ESLint Design Tokens Plugin — Complete Guide

> **Audience:** Developers joining the BuildPiper frontend team who want to understand how the design token enforcement system works — from the ground up.

---

## Table of Contents

1. [The Problem This Solves](#1-the-problem-this-solves)
2. [The Big Picture — How Everything Connects](#2-the-big-picture--how-everything-connects)
3. [What is ESLint?](#3-what-is-eslint)
4. [What is an ESLint Plugin?](#4-what-is-an-eslint-plugin)
5. [How Our Plugin Works — index.js Deep Dive](#5-how-our-plugin-works--indexjs-deep-dive)
6. [How .eslintrc.js Connects Everything](#6-how-eslintrcjs-connects-everything)
7. [How the VS Code Extension Works](#7-how-the-vs-code-extension-works)
8. [The Full Journey — What Happens When You Type Code](#8-the-full-journey--what-happens-when-you-type-code)
9. [File Structure](#9-file-structure)
10. [Setup From Scratch](#10-setup-from-scratch)
11. [Rules Reference](#11-rules-reference)
12. [What is Allowed vs Blocked](#12-what-is-allowed-vs-blocked)
13. [How to Add a New Token](#13-how-to-add-a-new-token)
14. [Running ESLint from Terminal](#14-running-eslint-from-terminal)
15. [FAQ](#15-faq)

---

## 1. The Problem This Solves

Before design tokens, every developer on the team wrote colors however they felt like:

```jsx
// Developer A wrote:
<div style={{ color: '#124d9b' }} />

// Developer B wrote the same color differently:
<div style={{ color: '#0d4494' }} />

// Developer C used a slightly wrong shade:
<div style={{ color: '#1a52a3' }} />
```

All three look similar on screen but are actually three different colors. When the design team decides to change the primary blue, they now have to hunt through hundreds of files finding every variation of that blue. Some get missed. The UI becomes inconsistent.

**Design tokens solve this** by giving every color a single name:

```jsx
// Everyone writes this — one source of truth:
<div style={{ color: 'var(--color-primary-500)' }} />
```

Now if the design team changes `--color-primary-500` in one file (`token.css`), it updates everywhere automatically.

**But how do you stop developers from writing raw hex colors again?** You can't just tell them — people forget, new joiners don't know, copy-paste from Stack Overflow brings in raw values. You need the editor itself to stop them the moment they type it.

That is exactly what this ESLint plugin does.

---

## 2. The Big Picture — How Everything Connects

```
┌─────────────────────────────────────────────────────────────────┐
│                        token.css                                 │
│   :root {                                                        │
│     --color-primary-500: #124d9b;   ← single source of truth    │
│     --color-white: #ffffff;                                      │
│     ... 101 tokens total                                         │
│   }                                                              │
└──────────────────────┬──────────────────────────────────────────┘
                       │  token names are copied into
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│              eslint-plugin-design-tokens/index.js               │
│                                                                  │
│   const VALID_COLOR_TOKENS = new Set([                          │
│     '--color-primary-500',   ← same names from token.css        │
│     '--color-white',                                             │
│     ... all 101                                                  │
│   ]);                                                            │
│                                                                  │
│   Rule: if developer writes color: '#124d9b'  → ERROR           │
│   Rule: if developer writes color: 'var(--color-primary-500)'   │
│          and token exists in Set  → OK                          │
│          and token NOT in Set     → ERROR (unknown token)        │
└──────────────────────┬──────────────────────────────────────────┘
                       │  plugin registered via symlink +
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                        .eslintrc.js                              │
│                                                                  │
│   plugins: ['design-tokens'],                                    │
│   rules: {                                                       │
│     'design-tokens/no-raw-colors': 'error',                     │
│     'design-tokens/no-raw-spacing': 'warn',                     │
│   }                                                              │
└──────────────────────┬──────────────────────────────────────────┘
                       │  VS Code extension reads .eslintrc.js
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│              VS Code ESLint Extension (Microsoft)               │
│                                                                  │
│   • Watches every file you open                                  │
│   • Runs ESLint on it in the background as you type             │
│   • Shows red underlines for errors, yellow for warnings        │
│   • Shows the exact error message on hover                      │
└─────────────────────────────────────────────────────────────────┘
```

Everything flows in one direction: `token.css` → `index.js` → `.eslintrc.js` → VS Code.

---

## 3. What is ESLint?

ESLint is a **code analysis tool** for JavaScript. It reads your JS/JSX files and checks them against a set of rules. If a rule is violated, it reports an error or warning.

Think of it like a spell-checker, but instead of checking spelling it checks code quality and coding rules.

**ESLint does NOT run your code.** It reads it as text, converts it to a tree structure (called an AST — more on that later), and inspects that tree.

```
Your file:  const color = '#fff';

ESLint reads it → builds a tree:
  VariableDeclaration
    VariableDeclarator
      Identifier: color
      Literal: '#fff'   ← ESLint rule sees this and checks: "is this a raw hex color?"
```

ESLint ships with built-in rules (like `no-unused-vars`, `no-console`) and can be extended with plugins that add custom rules. Our plugin adds two custom rules specific to design tokens.

---

## 4. What is an ESLint Plugin?

An ESLint plugin is just a **Node.js module** (a JavaScript file) that exports an object with a `rules` property. Each rule tells ESLint what patterns to look for and what to report.

```js
// Simplest possible ESLint plugin
module.exports = {
  rules: {
    'my-rule': {
      create(context) {
        return {
          Literal(node) {             // "whenever you find a string value..."
            if (node.value === 'bad') {
              context.report({        // "...report this error"
                node,
                message: 'Do not use the word bad',
              });
            }
          }
        };
      }
    }
  }
};
```

ESLint plugins are normally published to npm (like `eslint-plugin-react`). But you can also have a **local plugin** — a folder in your project — which is exactly what we have. ESLint requires plugins to be inside `node_modules`, which is why we create a symlink:

```
node_modules/eslint-plugin-design-tokens  →  eslint-plugin-design-tokens/
(symlink in node_modules)                    (real folder in project root)
```

The symlink tricks ESLint into thinking the plugin is an installed npm package while the actual code lives in your project so you can edit it easily.

---

## 5. How Our Plugin Works — index.js Deep Dive

Let's walk through `index.js` section by section.

### 5.1 The Token Registry

```js
const VALID_COLOR_TOKENS = new Set([
  '--color-white',
  '--color-black',
  '--color-primary-500',
  // ... all 101 tokens
]);
```

This is a JavaScript `Set` — like an array but optimised for fast lookups (`has()` is instant even with 1000 items). It contains every token name from `token.css`.

**This is the single place you update when you add a new token.** If a developer writes `var(--color-primary-500)` and `--color-primary-500` is in this Set, it passes. If they invent `var(--color-brand-blue)` which is NOT in this Set, it fails.

### 5.2 Property Sets

```js
const COLOR_PROPS = new Set([
  'color', 'backgroundColor', 'background',
  'borderColor', 'fill', 'stroke',
  // ...
]);

const SPACING_PROPS = new Set([
  'margin', 'padding', 'gap', 'width', 'height',
  // ...
]);
```

These tell the rule **which JS style properties to check**. The rule only fires when the property name is in one of these sets. So `color: '#fff'` triggers the rule but `zIndex: 10` does not, because `zIndex` is not a color property.

### 5.3 Regular Expressions

```js
const HEX_RE = /^#([0-9a-fA-F]{3,8})$/;
```
Matches hex colors: `#fff`, `#ffffff`, `#ff0000`, `#ffffff80` (with alpha).

```js
const RGB_RE = /^rgba?\s*\(/;
```
Matches `rgb(...)` and `rgba(...)` — currently skipped (deferred for later pass).

```js
const VAR_RE = /^var\(\s*(--[\w-]+)\s*\)$/;
```
Matches `var(--any-token-name)` and captures the token name inside.

```js
const PX_RE = /^\d+(\.\d+)?(px|rem)$/;
```
Matches `16px`, `1.6rem`, `24px` — raw spacing values.

### 5.4 What is an AST?

This is the most important concept to understand. ESLint does not search your file as text with regex. It converts your file into an **Abstract Syntax Tree (AST)** — a structured representation of your code as objects.

```jsx
// Your code:
<Button style={{ color: '#ff0000', padding: '16px' }} />
```

ESLint sees this as a tree:
```
JSXOpeningElement
  name: Button
  attributes:
    JSXAttribute                    ← node.name.name === 'style'
      name: style
      value:
        JSXExpressionContainer
          expression:
            ObjectExpression        ← the {{ ... }} object
              properties:
                Property            ← color: '#ff0000'
                  key: Identifier   → name: 'color'
                  value: Literal    → value: '#ff0000'   ← THIS is what we check
                Property            ← padding: '16px'
                  key: Identifier   → name: 'padding'
                  value: Literal    → value: '16px'
```

Our rule registers **visitors** — functions that ESLint calls when it encounters specific node types in this tree:

```js
return {
  JSXAttribute(node) { ... },    // called for every JSX prop like style={...}
  ObjectExpression(node) { ... } // called for every { } object in code
};
```

### 5.5 The no-raw-colors Rule Logic

Here is the full decision flow for every color property it finds:

```
Developer writes:  color: 'some-value'
                          │
                          ▼
              Is it 'transparent', 'inherit',
              'currentColor', 'none'?
                    │              │
                   YES             NO
                    │              │
                  PASS ✅          ▼
                          Does it start with #  or  rgb(?
                                  │              │
                                 YES             NO
                                  │              │
                              ERROR ❌           ▼
                              (raw hex)   Does it start with var(--...)?
                                                 │              │
                                                YES             NO
                                                 │              │
                              Is --token-name    │          Is it a plain word
                              in VALID_COLOR     │          like 'blue', 'red'?
                              _TOKENS Set?       │              │
                              │         │        │             YES
                             YES        NO       │              │
                              │         │      PASS ✅        ERROR ❌
                            PASS ✅  ERROR ❌              (named color)
                                    (unknown
                                     token)
```

### 5.6 The checkProperty Function

```js
function checkProperty(propNameNode, valueNode) {
  // Step 1: Get the property name (e.g. "color", "backgroundColor")
  const propName = propNameNode.type === 'Identifier'
    ? propNameNode.name
    : getStringValue(propNameNode);

  // Step 2: Is it a color property we care about?
  if (!propName || !allColorProps.has(propName)) return;  // not a color prop — skip

  // Step 3: Get the value as a string (e.g. "#fff" or "var(--color-white)")
  const value = getStringValue(valueNode);
  if (value === null) return;  // dynamic value like color={myVar} — can't check

  // Step 4: Run through the decision tree above
  if (ALLOWED_KEYWORDS.has(value.toLowerCase())) return;  // transparent etc — OK
  if (isRawColor(value)) { context.report(...); return; } // #hex — ERROR
  const token = extractVarToken(value);
  if (token && !VALID_COLOR_TOKENS.has(token)) { context.report(...); } // bad token — ERROR
}
```

### 5.7 Two Visitors — JSXAttribute and ObjectExpression

The rule needs to catch colors written in two different ways:

**Way 1 — Inline JSX style:**
```jsx
<div style={{ color: '#fff' }} />
```
Caught by the `JSXAttribute` visitor — it looks specifically for `style={{ ... }}` attributes.

**Way 2 — makeStyles / separate style objects:**
```js
const styles = {
  root: {
    color: '#fff',    // ← caught here
  }
};
```
Caught by the `ObjectExpression` visitor — it looks at every `{ }` object in the file and checks if any keys match color property names.

### 5.8 The Plugin Export

```js
module.exports = {
  rules: {
    'no-raw-colors':  noRawColors,   // ← accessed as 'design-tokens/no-raw-colors'
    'no-raw-spacing': noRawSpacing,  // ← accessed as 'design-tokens/no-raw-spacing'
  },
  configs: {
    recommended: { ... }  // preset config if someone wants to use 'extends'
  }
};
```

The plugin name is `design-tokens` (from the symlink name `eslint-plugin-design-tokens`). ESLint automatically strips `eslint-plugin-` prefix. So rule `no-raw-colors` inside plugin `design-tokens` is referenced as `design-tokens/no-raw-colors`.

---

## 6. How .eslintrc.js Connects Everything

```js
module.exports = {
  extends: ['react-app', 'react-app/jest'],  // ← keeps all CRA rules
  plugins: ['design-tokens'],                // ← loads our plugin from node_modules
  rules: {
    'design-tokens/no-raw-colors':  'error', // ← 'error' = red, stops CI
    'design-tokens/no-raw-spacing': 'warn',  // ← 'warn'  = yellow, doesn't stop CI
  },
};
```

**`extends: ['react-app']`** — This loads all the existing rules that CRA (Create React App) uses. We keep this so we don't lose existing linting. Our rules are added on top.

**`plugins: ['design-tokens']`** — This tells ESLint to load `eslint-plugin-design-tokens` from `node_modules`. Without this line, ESLint doesn't know our rules exist. This is why the symlink is required — ESLint looks for plugins ONLY inside `node_modules`.

**Rule severity levels:**
| Level | Value | What happens |
|-------|-------|-------------|
| off | `0` | Rule disabled |
| warn | `1` or `'warn'` | Yellow underline in VS Code, printed in terminal, does NOT fail CI |
| error | `2` or `'error'` | Red underline in VS Code, printed in terminal, FAILS CI build |

We use `error` for colors (must fix) and `warn` for spacing (should fix, but won't block deploys during migration).

---

## 7. How the VS Code Extension Works

The **ESLint extension by Microsoft** (`dbaeumer.vscode-eslint`) is a bridge between VS Code and ESLint.

### Without the extension:
```
You type code → save file → manually run "npx eslint src/file.js" → read terminal output
```

### With the extension:
```
You type code → extension runs ESLint in background every few seconds → 
shows errors as red underlines directly in the file as you type
```

Here is exactly what happens internally:

```
1. You open a .js or .jsx file in VS Code

2. Extension reads .vscode/settings.json:
   "eslint.validate": ["javascript", "javascriptreact"]
   → Knows to lint this file type

3. Extension reads .eslintrc.js from project root:
   → Loads the plugins and rules configuration

4. Extension starts a background ESLint "language server" process:
   → This is a Node.js process running in the background
   → It has ESLint loaded with your config

5. Every time you pause typing (after ~300ms):
   → Extension sends the current file content to the language server
   → Language server runs ESLint on it
   → Returns list of errors/warnings with line numbers and messages

6. VS Code renders the results:
   → Red squiggly underline for errors
   → Yellow squiggly underline for warnings
   → Red dot in the gutter (left margin)
   → Error count in the status bar at bottom

7. When you hover over the underlined code:
   → Shows the full error message from our plugin's `messages` object
   → Shows the rule name (design-tokens/no-raw-colors)
```

### The .vscode/settings.json file

```json
{
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": "explicit"
  },
  "eslint.validate": [
    "javascript",
    "javascriptreact",
    "typescript",
    "typescriptreact"
  ],
  "eslint.workingDirectories": [
    { "directory": ".", "changeProcessCWD": true }
  ],
  "eslint.enable": true
}
```

**`eslint.validate`** — tells the extension which file types to run ESLint on. Without this, it might only lint `.js` files and skip `.jsx`.

**`eslint.workingDirectories`** — tells the extension where the project root is. This is how it finds `.eslintrc.js` and `node_modules/eslint-plugin-design-tokens`.

**`editor.codeActionsOnSave`** — when you save a file, auto-fix any ESLint errors that have a fix available. Our rules don't have auto-fix (you need to manually replace with the right token), but other ESLint rules like quote style do.

**`eslint.enable: true`** — explicitly enables the extension for this workspace.

> **Important:** This `.vscode/settings.json` file should be committed to git so every developer on the team automatically gets VS Code configured correctly when they clone the repo.

---

## 8. The Full Journey — What Happens When You Type Code

Let's trace exactly what happens when a developer writes a raw color:

```
Developer types:   backgroundColor: '#0086ff'
                                     ─────────
```

**Step 1 — VS Code detects the change**
The ESLint extension notices the file has been modified and queues a lint run.

**Step 2 — ESLint parses the file into an AST**
ESLint reads the entire file and converts it to a tree. Every node has a type, location (line/column), and children.

**Step 3 — ESLint traverses the tree**
ESLint walks through every node in the tree, calling registered visitor functions.

**Step 4 — Our rule's ObjectExpression visitor is called**
When ESLint reaches the `{ backgroundColor: '#0086ff' }` object:
```js
ObjectExpression(node) {
  for (const prop of node.properties) {
    checkProperty(prop.key, prop.value);
    //             ──────────  ─────────
    //             'backgroundColor'  '#0086ff'
  }
}
```

**Step 5 — checkProperty runs**
```js
// propName = 'backgroundColor'
// value    = '#0086ff'

allColorProps.has('backgroundColor')  // → true, continue checking
isRawColor('#0086ff')                 // → true! HEX_RE matches
```

**Step 6 — context.report() is called**
```js
context.report({
  node: valueNode,       // points to '#0086ff' in the AST
  messageId: 'rawColor',
  data: {
    value: '#0086ff',
    prop: 'backgroundColor'
  }
});
// ESLint resolves the message:
// "Raw color '#0086ff' found in 'backgroundColor'.
//  Use var(--color-*) token instead.
//  Run: node apply-color-tokens-v2.js --root src --backup"
```

**Step 7 — ESLint returns results to the extension**
The result includes: file path, line number, column number, severity (error), message.

**Step 8 — VS Code renders the error**
```jsx
backgroundColor: '#0086ff'
                  ─────────  ← red squiggly underline appears here
```

Red dot appears in the gutter. Error count updates in status bar. Developer hovers and sees the full message.

**Step 9 — Developer fixes it**
```jsx
// Before:
backgroundColor: '#0086ff'

// After:
backgroundColor: 'var(--color-tertiary-500)'
```

The underline disappears immediately once the value is a valid token.

---

## 9. File Structure

```
frontend-react/                         ← project root
│
├── eslint-plugin-design-tokens/        ← OUR LOCAL PLUGIN (commit this to git)
│   ├── index.js                        ← all rule logic lives here
│   ├── package.json                    ← tells Node this is a package named
│   │                                     'eslint-plugin-design-tokens'
│   ├── stylelint.config.js             ← for CSS/SCSS files (separate tool)
│   └── README.md                       ← this file
│
├── node_modules/
│   └── eslint-plugin-design-tokens     ← SYMLINK → ../eslint-plugin-design-tokens
│                                          (do NOT commit this, it's auto-created)
│
├── src/
│   └── assets/
│       └── token.css                   ← source of truth for all token names
│
├── .eslintrc.js                        ← ESLint config (commit to git)
├── .vscode/
│   └── settings.json                   ← VS Code config (commit to git)
└── package.json
```

### What to commit to git

| File/Folder | Commit? | Why |
|------------|---------|-----|
| `eslint-plugin-design-tokens/` | ✅ Yes | This is your source code |
| `node_modules/eslint-plugin-design-tokens` | ❌ No | Symlink, auto-created |
| `.eslintrc.js` | ✅ Yes | Config shared by whole team |
| `.vscode/settings.json` | ✅ Yes | VS Code config for whole team |

---

## 10. Setup From Scratch

Follow these steps when setting up on a new machine or for a new team member:

### Step 1 — Clone the repo
```bash
git clone <repo-url>
cd frontend-react
npm install
```

### Step 2 — Create the symlink
```bash
ln -s $(pwd)/eslint-plugin-design-tokens node_modules/eslint-plugin-design-tokens
```

Verify it worked:
```bash
node -e "const p = require('eslint-plugin-design-tokens'); console.log(Object.keys(p.rules))"
# Output: [ 'no-raw-colors', 'no-raw-spacing' ]
```

### Step 3 — Verify .eslintrc.js exists
```js
// .eslintrc.js at project root
module.exports = {
  extends: ['react-app', 'react-app/jest'],
  plugins: ['design-tokens'],
  rules: {
    'design-tokens/no-raw-colors':  'error',
    'design-tokens/no-raw-spacing': 'warn',
  },
};
```

### Step 4 — Test from terminal
```bash
npx eslint src/components/SomeComponent.jsx
```

### Step 5 — Install VS Code extension
Open VS Code → `Ctrl+Shift+X` → Search **ESLint** → Install **ESLint by Microsoft**

### Step 6 — Reload VS Code
`Ctrl+Shift+P` → "Developer: Reload Window"

### Step 7 — Verify it works
Open any `.jsx` file and write `color: '#ff0000'` — you should see a red underline immediately.

### Automate the symlink for the whole team
Add this to `package.json` so `npm install` always recreates the symlink:
```json
"scripts": {
  "postinstall": "ln -sf $(pwd)/eslint-plugin-design-tokens node_modules/eslint-plugin-design-tokens"
}
```

---

## 11. Rules Reference

### design-tokens/no-raw-colors — ERROR 🔴

Blocks raw color values in JavaScript style properties.

**What it checks:** Any property in `COLOR_PROPS`:
`color`, `backgroundColor`, `background`, `borderColor`, `borderTopColor`, `borderRightColor`, `borderBottomColor`, `borderLeftColor`, `outlineColor`, `fill`, `stroke`, `boxShadow`, `caretColor`, `textDecorationColor`, `columnRuleColor`

**Three types of errors it reports:**

| Error Type | Example | Message |
|------------|---------|---------|
| Raw hex | `color: '#fff'` | Raw color '#fff' found in 'color'. Use var(--color-*) token |
| Unknown token | `color: 'var(--color-fake)'` | Unknown color token '--color-fake' in 'color' |
| Named color | `color: 'blue'` | Raw color 'blue' in string value for 'color' |

---

### design-tokens/no-raw-spacing — WARNING 🟡

Warns on raw px/rem values in spacing properties.

**What it checks:** Any property in `SPACING_PROPS`:
`margin`, `marginTop`, `marginRight`, `marginBottom`, `marginLeft`, `padding`, `paddingTop`, `paddingRight`, `paddingBottom`, `paddingLeft`, `gap`, `rowGap`, `columnGap`, `top`, `right`, `bottom`, `left`, `borderRadius`, `borderWidth`, `width`, `height`, `minWidth`, `maxWidth`, `minHeight`, `maxHeight`

Set to `'warn'` now. Change to `'error'` once spacing token migration is complete.

---

## 12. What is Allowed vs Blocked

### Colors

| Value | Result | Why |
|-------|--------|-----|
| `'var(--color-primary-500)'` | ✅ Pass | Valid token in VALID_COLOR_TOKENS |
| `'var(--color-white)'` | ✅ Pass | Valid token |
| `'transparent'` | ✅ Pass | Allowed keyword |
| `'inherit'` | ✅ Pass | Allowed keyword |
| `'currentColor'` | ✅ Pass | Allowed keyword |
| `'none'` | ✅ Pass | Allowed keyword (for background: none) |
| `'#ffffff'` | ❌ Error | Raw hex |
| `'#fff'` | ❌ Error | Raw hex (shorthand) |
| `'rgb(255,255,255)'` | ⏭️ Skipped | rgb/rgba deferred for later pass |
| `'rgba(0,0,0,0.5)'` | ⏭️ Skipped | rgb/rgba deferred for later pass |
| `'blue'` | ❌ Error | Named CSS color |
| `'var(--color-fake)'` | ❌ Error | Token not in token.css |
| `{myColor}` (dynamic) | ⏭️ Skipped | Can't check dynamic values statically |

### Spacing

| Value | Result | Why |
|-------|--------|-----|
| `'var(--space-16)'` | ✅ Pass | Token reference |
| `0` | ✅ Pass | Zero is always allowed |
| `'auto'` | ✅ Pass | Keyword |
| `'calc(100% - 16px)'` | ✅ Pass | calc() skipped — too complex |
| `'16px'` | ⚠️ Warn | Raw px value |
| `'1.6rem'` | ⚠️ Warn | Raw rem value |
| `'8px 16px'` | ⚠️ Warn | Multi-value with raw px |

---

## 13. How to Add a New Token

When a new token is added to `token.css`, you must also add it to `index.js` — otherwise using it will trigger an "unknown token" error.

**Step 1 — Add to token.css**
```css
:root {
  --color-brand-teal: #00bfa5;   ← new token
}
```

**Step 2 — Add to VALID_COLOR_TOKENS in index.js**
```js
const VALID_COLOR_TOKENS = new Set([
  // ... existing tokens ...
  '--color-brand-teal',   ← add here
]);
```

**Step 3 — Recreate the symlink** (so node_modules picks up the change)
```bash
rm node_modules/eslint-plugin-design-tokens
ln -s $(pwd)/eslint-plugin-design-tokens node_modules/eslint-plugin-design-tokens
```

Or just reload VS Code window — the extension will pick up the updated file.

---

## 14. Running ESLint from Terminal

### Lint a single file
```bash
npx eslint src/components/MyComponent.jsx
```

### Lint an entire folder
```bash
npx eslint src/
```

### Lint and see all errors with line numbers
```bash
npx eslint src/ --format stylish
```

### Lint only for design token errors (ignore other rules)
```bash
npx eslint src/ --rule '{"design-tokens/no-raw-colors": "error"}' --no-eslintrc
```

### Count how many raw color violations exist in the whole codebase
```bash
npx eslint src/ --rule '{"design-tokens/no-raw-colors": "error"}' 2>&1 | grep "no-raw-colors" | wc -l
```

### Run before committing (add to CI pipeline)
```bash
npx eslint src/ --max-warnings 0
# --max-warnings 0 means even warnings fail the build
```

---

## 15. FAQ

**Q: I added a new token to token.css but VS Code still shows an error for it.**

A: You need to add the token name to `VALID_COLOR_TOKENS` in `index.js` too. The plugin has its own copy of the token list — they must be kept in sync. Then reload the VS Code window.

---

**Q: I'm using a dynamic color value like `color={isDark ? '#000' : '#fff'}` — will it error?**

A: No. The rule can only check static string values. If the value is a JavaScript expression (ternary, variable, function call), the rule skips it with "can't check dynamically". You should still use tokens in dynamic cases: `color={isDark ? 'var(--color-black)' : 'var(--color-white)'}`.

---

**Q: Why is spacing a warning and not an error?**

A: Because we're still in the middle of the spacing token migration. Changing it to `error` right now would break every developer's workflow since the whole codebase has raw px values. Once `apply-spacing-tokens.js` has been run and the codebase is clean, change it to `error`.

---

**Q: Can the ESLint rule auto-fix the values?**

A: No. Auto-fix is possible in ESLint but we didn't implement it because picking the right token requires judgment — `#0086ff` could be `--color-tertiary-500` or `--color-border-sky` depending on context. Use `apply-color-tokens-v2.js` for bulk replacement with human review. The ESLint rule is for prevention (new code), not bulk migration (old code).

---

**Q: Does this work for CSS/SCSS files?**

A: No, ESLint only works on JS/JSX/TS/TSX files. For CSS/SCSS files, use Stylelint with the `stylelint.config.js` included in this folder. Install it with:
```bash
npm install --save-dev stylelint stylelint-declaration-strict-value
npx stylelint "src/**/*.{css,scss}"
```

---

**Q: A colleague says they don't see any red underlines in VS Code.**

A: Check these things in order:
1. Is the ESLint extension installed? (`Ctrl+Shift+X` → search ESLint)
2. Is `.vscode/settings.json` present in the project?
3. Does the symlink exist? `ls -la node_modules/eslint-plugin-design-tokens`
4. Did they reload VS Code after setup? (`Ctrl+Shift+P` → Reload Window)
5. Check the ESLint output panel: `View → Output → select "ESLint"` from dropdown — it shows errors loading the plugin if any.

---

**Q: The symlink breaks after every `npm install`. Why?**

A: `npm install` sometimes cleans and recreates `node_modules`, breaking the symlink. Add this to `package.json` to auto-recreate it:
```json
"scripts": {
  "postinstall": "ln -sf $(pwd)/eslint-plugin-design-tokens node_modules/eslint-plugin-design-tokens"
}
```
The `postinstall` script runs automatically after every `npm install`.

---

*Last updated: March 2026 — BuildPiper Design System Team*
