# File Upload System — Complete Deep Dive

> **Codebase:** React + MUI frontend  
> **Files covered:** `Input.jsx` → `FileUpload.jsx` → `ParseFile.js` → Parent components (`ConfigMap.jsx`, `AccessLevel.js`)  
> **Status:** Single-file upload works. Multi-file upload is architecturally ready but requires 3 targeted fixes.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Layer-by-Layer Code Walkthrough](#2-layer-by-layer-code-walkthrough)
   - 2.1 [Input.jsx — The HTML Rendering Layer](#21-inputjsx--the-html-rendering-layer)
   - 2.2 [ParseFile.js — The File Reading Layer](#22-parsefilejs--the-file-reading-layer)
   - 2.3 [FileUpload.jsx — The Form State Layer](#23-fileuploadjsx--the-form-state-layer)
   - 2.4 [ConfigMap.jsx — The Parent Consumer Layer](#24-configmapjsx--the-parent-consumer-layer)
3. [Complete Upload Flow Diagram](#3-complete-upload-flow-diagram)
4. [State Shape Reference](#4-state-shape-reference)
5. [Why Multi-File Doesn't Work Today](#5-why-multi-file-doesnt-work-today)
6. [What Needs to Change for Multi-File](#6-what-needs-to-change-for-multi-file)
7. [After the Fix — Multi-File Flow Diagram](#7-after-the-fix--multi-file-flow-diagram)
8. [Change Summary Table](#8-change-summary-table)

---

## 1. Architecture Overview

The file upload system is split into **4 distinct layers**, each with a single responsibility:

```
┌─────────────────────────────────────────────────────────────────┐
│                    PARENT COMPONENTS                            │
│          (ConfigMap.jsx, AccessLevel.js, etc.)                  │
│  • Decides which variant to use                                 │
│  • Reads final files array via inherits.getData()               │
│  • Sends data to API                                            │
└────────────────────────┬────────────────────────────────────────┘
                         │  props: varient, state, inherits
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    FileUpload.jsx                               │
│  • Manages files[] array in React state                         │
│  • Exposes inherits.getData(), validateForm(), getState()       │
│  • Decides: replace array (single) vs append (multi)            │
│  • Calls ParseFile per file event                               │
└────────────────────────┬────────────────────────────────────────┘
                         │  onChange event → ParseFile(e, callback)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ParseFile.js                                 │
│  • Reads ONE file from e.target.files[0]          ← BUG HERE   │
│  • Converts bytes → Base64 via FileReader API                   │
│  • Returns { event_name, name, content } to callback            │
└────────────────────────┬────────────────────────────────────────┘
                         │  type prop + varient prop
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Input.jsx                                    │
│  • Renders the raw <input type="file"> HTML                     │
│  • type="file"            → single picker, no multiple attr     │
│  • type="multi-file-upload" → multiple attr + drag-and-drop     │
│  • Renders file chips with remove (×) buttons                   │
└─────────────────────────────────────────────────────────────────┘
```

**Data flows upward** (files bubble up through callbacks).  
**Configuration flows downward** (variant, state, inherits pass down as props).

---

## 2. Layer-by-Layer Code Walkthrough

---

### 2.1 `Input.jsx` — The HTML Rendering Layer

This is the **bottom of the stack** — it renders HTML and fires browser events. It has no knowledge of parsing or state management.

#### How it decides what to render

`Input.jsx` is a giant switch-case on the `type` prop:

```jsx
// Inside Input.jsx — getFieldBasedOnType()
switch (type) {
  case "file":             // → single file picker
  case "multi-file-upload":// → multi file picker with drag-drop
  case "file-upload-name": // → compact name-only display
  case "file-support":     // → styled single + download template button
  // ...etc
}
```

`FileUpload.jsx` chooses which `type` to pass based on its own `varient` prop:

```jsx
// Inside FileUpload.jsx render:
<Input
  type={
    varient == "single"            ? "file" :
    varient == "show_filename_only"? "file-upload-name" :
                                     "multi-file-upload"  // default
  }
  ...
/>
```

So the chain is: **parent sets `varient`** → FileUpload maps it to `type` → Input renders accordingly.

---

#### The `"file"` case (single mode)

```jsx
case ("file"):
  const isServiceOverview = window?.location?.pathname?.includes('/service/');

  if (isServiceOverview) {
    // Renders a styled drag-and-drop zone BUT still single file
    return (
      <div className="file-upload">
        <div className="image-upload-wrap" style={{ ... }}>
          <input
            className="file-upload-input"
            type="file"
            name={name}
            onChange={onChangeHandler}
            // ⚠️ NO `multiple` attribute — browser enforces single file
            style={{ position: 'absolute', opacity: 0, ... }}
          />
          {/* File chip display */}
          {data && data[name] ? (
            <div>
              {data[name].name ?
                // Single file object with .name property
                <div className="image-upload-chip">{data[name].name}</div>
                :
                // Array of files
                (data[name]).map((element, i) => (
                  <div className="image-upload-chip" key={i}>
                    <span onClick={() => onFileSelect(i)}>{element.name}</span>
                    <button onClick={() => onFileRemove(i)}>×</button>
                  </div>
                ))
              }
            </div>
          ) : null}
        </div>
      </div>
    );
  }
  // Non-service-overview path — same logic, simpler styling
```

**Key observation:** The `"file"` case renders chips for both `.name` (single object) and arrays — so the display is already array-aware. But the `<input>` itself has no `multiple` attribute, meaning the browser only allows picking one file at a time.

---

#### The `"multi-file-upload"` case (multi mode)

```jsx
case ("multi-file-upload"):
  return (
    <div className="multi-file-upload">
      <div
        className="image-upload-wrap"
        style={{ minHeight: 'var(--space-86)', ... }}
        onClick={() => fileInputRef.current && fileInputRef.current.click()}
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          e.stopPropagation();
          const droppedFiles = e.dataTransfer.files;
          if (droppedFiles.length > 0) {
            // ✅ Iterates ALL dropped files
            Array.from(droppedFiles).forEach(file => {
              const syntheticEvent = {
                target: { files: [file], name: name }
              };
              onChangeHandler(syntheticEvent); // fires once per file
            });
          }
        }}
      >
        <input
          ref={fileInputRef}
          className="file-upload-input"
          type="file"
          name={name}
          onChange={onChangeHandler}
          multiple              // ✅ Browser allows multi-select
          style={{ display: 'none' }}
        />

        {/* Empty state prompt */}
        {!(data[name] && data[name].length > 0) && (
          <div>
            <i className="ri-upload-2-line" />
            <span>Drag and Drop file here or click here to choose file</span>
            <span>You can Upload Multiple Files</span>
          </div>
        )}

        {/* File chips */}
        {data[name] ? (
          <div>
            {(data[name]).map((element, i) => (
              <div className="image-upload-chip" key={i}>
                <span onClick={(e) => { e.stopPropagation(); onFileSelect(i); }}>
                  {element.name}
                </span>
                <button onClick={(e) => { e.stopPropagation(); onFileRemove(i); }}>
                  <i className="ri-close-line" />
                </button>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
```

**Key differences from `"file"` case:**

| Feature | `"file"` | `"multi-file-upload"` |
|---|---|---|
| `multiple` attribute on input | ❌ No | ✅ Yes |
| Drag-and-drop support | ❌ No | ✅ Yes (iterates all dropped files) |
| Drag-and-drop fires `onChangeHandler` | N/A | Once **per file** (synthetic event) |
| Hidden input + click trigger | No | Yes (ref-based) |
| Display chips for arrays | ✅ Yes | ✅ Yes |

---

#### What `onChangeHandler` receives

When a user picks a file, the browser fires a `change` event. The event object looks like:

```javascript
// e.target.files — a FileList (array-like, not real array)
e.target.files[0]  // first File object
e.target.files[1]  // second File object (only if multiple=true AND user picked 2)
e.target.name      // the name prop of the input ("files")

// A File object has:
file.name          // "my-config.yaml"
file.size          // bytes
file.type          // "application/x-yaml"
file.lastModified  // timestamp
```

This event travels up to `FileUpload.jsx`'s `onChangeHandler`, which calls `ParseFile`.

---

### 2.2 `ParseFile.js` — The File Reading Layer

This utility receives the browser event and returns parsed file data via a callback. It runs **asynchronously** because `FileReader` is async.

#### Full annotated code

```javascript
import Base64 from 'base-64';

const ParseFile = (e, onParsingComplete, onError) => {
  try {
    const event_name = e.target.name;          // "files" — the input's name prop
    const file_to_load = e.target.files[0];    // ⚠️ ONLY the first file, always

    const file_reader = new FileReader();

    file_reader.onload = function (fileLoadedEvent) {
      try {
        // Step 1: Get raw binary data as ArrayBuffer
        const arrayBuffer = fileLoadedEvent.target.result;

        // Step 2: Convert ArrayBuffer → Uint8Array → binary string
        const bytes = new Uint8Array(arrayBuffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
          binary += String.fromCharCode(bytes[i]);   // each byte → char
        }

        // Step 3: Base64 encode the binary string
        const content = Base64.encode(binary);

        // Step 4: Build the file_data object
        const file_data = {
          event_name,              // "files"
          name: file_to_load.name, // original filename e.g. "config.yaml"
          content: content,        // Base64-encoded file content
        };

        // Step 5: Fire the callback with the single parsed file
        onParsingComplete(file_data);

      } catch (error) {
        onError(error);
      }
    };

    // Trigger async read — result will be ArrayBuffer
    file_reader.readAsArrayBuffer(file_to_load);

  } catch (error) {
    onError(error);
  }

  // Reset input so same file can be re-uploaded
  e.target.value = null;
};

export default ParseFile;
```

#### Why ArrayBuffer instead of `readAsBinaryString`?

`readAsBinaryString` is deprecated in modern browsers. The current code:
1. Reads as `ArrayBuffer` (raw bytes, reliable)
2. Manually converts bytes to a binary string using `String.fromCharCode`
3. Passes that binary string to `Base64.encode()`

This works correctly for all file types including binary files (images, zips, etc.).

#### The callback contract

`ParseFile` calls `onParsingComplete` with exactly this shape:

```javascript
{
  event_name: "files",         // always the input's name attribute
  name: "my-config.yaml",      // original filename from OS
  content: "SGVsbG8gV29ybGQ=", // Base64-encoded file contents
}
```

#### The Critical Bug

```javascript
const file_to_load = e.target.files[0];  // ← only index 0, ever
```

Even if the user selects 3 files in the browser picker, only the first file is ever processed. `ParseFile` runs once, reads `files[0]`, and returns. Files at index 1, 2, etc. are silently discarded.

---

### 2.3 `FileUpload.jsx` — The Form State Layer

This is the **core state manager**. It wraps `Input.jsx`, calls `ParseFile`, maintains the files array, and exposes methods via the `inherits` pattern.

#### The `inherits` pattern explained

`inherits` is a plain object passed down from the parent as a prop. `FileUpload.jsx` attaches methods to it directly — this is how the parent gets access to `FileUpload`'s internal state without prop drilling:

```javascript
// Parent creates an empty object:
const file_upload_inherits = {};

// Passes it to FileUpload:
<FileUpload inherits={file_upload_inherits} ... />

// FileUpload attaches methods to it:
inherits.validateForm = () => { ... };
inherits.getData = () => { return state.form_data.data; };
inherits.getState = () => { return state; };

// Parent can now call:
const data = file_upload_inherits.getData();
// → { files: [{name, content, event_name}, ...], cmd_agrs: [] }
```

This is a React-specific pattern for exposing child state upward without lifting state.

---

#### State shape

```javascript
// getDefaultState() in FileUpload.jsx
{
  content: "",                    // currently previewed file's content (Base64)
  form_data: {
    data: {
      files: [],                  // ← THE ARRAY — all uploaded files live here
      cmd_agrs: []                // optional command arguments
    },
    error: {
      files: ""                   // validation error message
    },
    default_validations: {
      files: [VALIDATION_TYPE_REQUIRED]  // files field is required by default
    }
  }
}
```

Each entry in `files[]` looks like:
```javascript
{
  event_name: "files",
  name: "config.yaml",
  content: "SGVsbG8gV29ybGQ="   // Base64
}
```

---

#### The `onChangeHandler` → `ParseFile` → `onParseComplete` cycle

```javascript
// Step 1: Input fires onChange → FileUpload.onChangeHandler
function onChangeHandler(e) {
  ParseFile(e, onParseComplete, setErrorInCaseUploadFails);
  hitCallbackFn();  // optional side-effect callback
}

// Step 2: ParseFile reads the file async, then calls onParseComplete
const onParseComplete = (file_data) => {
  // ← file_data = { event_name, name, content }

  // Step 3: Decide whether to replace or append
  let files = varient == "single"
    ? []                           // REPLACE — clear existing files first
    : state.form_data.data.files;  // APPEND — keep existing files

  files.push(file_data);           // add the new file

  updateFilesInState(files);       // write to state
}
```

**This is the correct multi-file behavior already** — when `varient != "single"`, it appends. The problem is `ParseFile` only ever calls this callback once (for `files[0]`), so you can never get more than one file appended per pick event.

---

#### `updateFilesInState`

```javascript
function updateFilesInState(files) {
  setState(new_state => ({
    ...new_state,
    content: files.length > 0 ? new_state.content : "",  // clear preview if empty
    form_data: {
      ...new_state.form_data,
      data: {
        files: files,    // replace the whole array
      },
      error: {
        files: ""        // clear error on successful upload
      }
    }
  }));

  // Notify parent of new files array via optional callback
  if (inherits.onFileChange) {
    inherits.onFileChange(files);
  }
}
```

The `inherits.onFileChange` callback allows parent components to react to file changes without polling. Example usage in `ConfigMap.jsx`:

```javascript
state.child_inherits.fileUpload.onFileChange = (files) => {
  setState(new_state => ({
    ...new_state,
    _file_count: files.length  // tracks count to show/hide placeholder
  }))
}
```

---

#### `removeEnvManifest` — removing a file

```javascript
const removeEnvManifest = (i) => {
  let files = state.form_data.data.files;
  files.splice(i, 1);          // remove file at index i
  updateFilesInState(files);   // write back
}
```

This is passed to `Input.jsx` as `onFileRemove`, which wires it to the `×` button on each chip.

---

#### `normalizeState` — state initialization

When the parent passes pre-existing data (e.g., on edit mode), `normalizeState` wraps raw data into the full state shape:

```javascript
function normalizeState(s) {
  if (!s) return getDefaultState();          // no state → fresh defaults
  if (s.form_data) return s;                 // already full state → pass through
  // Raw data from getData() — wrap it
  const defaults = getDefaultState();
  return {
    ...defaults,
    form_data: {
      ...defaults.form_data,
      data: { ...defaults.form_data.data, ...s },
    },
  };
}
```

This is why parents can pass either a full state object or just the raw data object and both work.

---

#### Exposed methods summary

| Method | Returns | Used for |
|---|---|---|
| `inherits.getData()` | `{ files: [], cmd_agrs: [] }` | Getting data for API post |
| `inherits.getState()` | Full state object | Getting complete state for advanced use |
| `inherits.validateForm()` | `{ valid: bool, error: {} }` | Pre-submit validation |
| `inherits.onFileChange` | (set by parent) | Real-time file count tracking |

---

### 2.4 `ConfigMap.jsx` — The Parent Consumer Layer

`ConfigMap.jsx` is a complex parent that uses file upload inside its `AddMoreGuidedForm` sub-component. Here's how the wiring works end-to-end.

#### `AddMoreGuidedForm` — the file upload consumer

```javascript
const AddMoreGuidedForm = (props) => {
  const [state, setState] = useState(prev_state ? prev_state : getAddMoreDefaultState());
  
  // state.child_inherits.fileUpload is the inherits object
  // FileUpload will attach its methods to this
```

#### `getAddMoreDefaultState` — initial state with stub inherits

```javascript
function getAddMoreDefaultState() {
  return {
    data: { properties_files: "manual_value", key: "", value: "" },
    error: {},
    fileUpload: null,    // pre-loaded file data (null for new, populated on edit)
    gitRepo: null,
    child_inherits: {
      fileUpload: {
        // Stubs so parent won't crash before FileUpload mounts
        "validateForm": () => { return { valid: true } },
        "getState": () => { return {} },
        "getData": () => { return {} }
      },
      // ...
    }
  }
}
```

#### `getFileUploadState` — hydrating state for edit mode

When editing an existing ConfigMap, the API returns file paths as strings. This function converts them back to the shape FileUpload expects:

```javascript
function getFileUploadState() {
  var state_temp = FileUploadDefaultState();   // gets a fresh default state
  if (state.fileUpload) {
    state.fileUpload.files.forEach(file_name => {
      if (typeof(file_name) == "string") {
        // API returned a string path — wrap as object
        state_temp.form_data.data.files.push({ name: file_name });
      } else {
        // Already an object (just uploaded) — use as-is
        state_temp.form_data.data.files.push(file_name);
      }
    });
  }
  return state_temp;
}
```

#### Rendering FileUpload with `varient="single"`

```jsx
<FileUpload
  style={{ padding: 0 }}
  noLabel={true}
  inherits={state.child_inherits.fileUpload}  // ← the inherits object
  varient="single"                             // ← THIS LOCKS SINGLE-FILE MODE
  state={state.fileUpload?.files ? getFileUploadState() : null}
/>
```

#### Reading data back in `inherits.getData()`

```javascript
inherits.getData = () => {
  const data = {};
  var post_data = {};

  if (state.data.properties_files == "fileupload") {
    data.strategy = "UPLOADED_MANIFEST";
    data.manifest_file_paths = state.child_inherits.fileUpload.getData().files;
    //                         ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //                         Already expects an array! API-ready.
    post_data.manifest_meta_data = { ...data };
    post_data.key = state.data.key;
    post_data.value = null;
    post_data.is_key_value_pairs_in_file = false;
  }
  // ...similar for other strategies
  return post_data;
}
```

Then in `parseDataForPost` (called just before the API call):

```javascript
obj.manifest_meta_data = {
  strategy: "UPLOADED_MANIFEST",
  manifest_file_paths: data.guided_form_childs[key].fileUpload.files
  // ↑ Sends the full array to the API
};
```

**The API already expects an array.** The parent is already built for multi-file — it just never receives more than one file because of the bug upstream.

---

## 3. Complete Upload Flow Diagram

```
USER ACTION: Clicks file input or drags files onto drop zone
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     INPUT.JSX                               │
│                                                             │
│  type="file"              type="multi-file-upload"          │
│  ┌──────────────┐         ┌──────────────────────────────┐  │
│  │ <input       │         │ <input                       │  │
│  │   type="file"│         │   type="file"                │  │
│  │   name="files│         │   name="files"               │  │
│  │   onChange={}│         │   multiple              ✅   │  │
│  │   />         │         │   style={{display:'none'}}   │  │
│  │              │         │ />                           │  │
│  │ NO multiple  │         │                              │  │
│  │ attr  ⚠️    │         │ onDrop fires once per file   │  │
│  └──────┬───────┘         └─────────────┬────────────────┘  │
│         │                               │                   │
│         └──────────────┬────────────────┘                   │
│                        │                                    │
│              onChange event fires                           │
│              e.target.files = FileList                      │
│              e.target.name  = "files"                       │
└────────────────────────┬────────────────────────────────────┘
                         │ onChangeHandler(e)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   FILEUPLOAD.JSX                            │
│                                                             │
│  function onChangeHandler(e) {                              │
│    ParseFile(e, onParseComplete, setErrorInCaseUploadFails) │
│    hitCallbackFn()                                          │
│  }                                                          │
└────────────────────────┬────────────────────────────────────┘
                         │ ParseFile(e, callback, onError)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    PARSEFILE.JS                             │
│                                                             │
│  const file_to_load = e.target.files[0]  ← TAKES ONLY [0] │
│                                                             │
│  const file_reader = new FileReader()                       │
│  file_reader.onload = (fileLoadedEvent) => {               │
│    const arrayBuffer = fileLoadedEvent.target.result        │
│                                                             │
│    ┌──────────────────────────────────────┐                 │
│    │  CONVERSION PIPELINE                 │                 │
│    │  ArrayBuffer                         │                 │
│    │    → Uint8Array                      │                 │
│    │    → binary string (char by char)    │                 │
│    │    → Base64.encode()                 │                 │
│    └──────────────────────────────────────┘                 │
│                                                             │
│    onParsingComplete({                                      │
│      event_name: e.target.name,   // "files"               │
│      name: file_to_load.name,     // "config.yaml"         │
│      content: base64String,       // "SGVsbG8..."          │
│    })                                                       │
│  }                                                          │
│                                                             │
│  file_reader.readAsArrayBuffer(file_to_load)  // async!    │
│  e.target.value = null  // reset for re-upload             │
└────────────────────────┬────────────────────────────────────┘
                         │ callback fires with file_data
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   FILEUPLOAD.JSX                            │
│                                                             │
│  const onParseComplete = (file_data) => {                   │
│                                                             │
│    // Decide: replace or append?                            │
│    let files = varient == "single"                          │
│      ? []                        // REPLACE existing        │
│      : state.form_data.data.files // APPEND to existing     │
│                                                             │
│    files.push(file_data)   // add the newly parsed file     │
│                                                             │
│    updateFilesInState(files)                                │
│  }                                                          │
│                                                             │
│  function updateFilesInState(files) {                       │
│    setState(new_state => ({                                  │
│      ...new_state,                                          │
│      form_data: {                                           │
│        ...new_state.form_data,                              │
│        data: { files: files },                              │
│        error: { files: "" }                                 │
│      }                                                      │
│    }))                                                      │
│                                                             │
│    if (inherits.onFileChange) {                             │
│      inherits.onFileChange(files)  // notify parent         │
│    }                                                        │
│  }                                                          │
└────────────────────────┬────────────────────────────────────┘
                         │ React re-render
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    INPUT.JSX                                │
│                                                             │
│  // data[name] = state.form_data.data  (passed as prop)    │
│  // data["files"] = [{ name, content, event_name }]        │
│                                                             │
│  {(data["files"]).map((element, i) => (                     │
│    <div className="image-upload-chip" key={i}>              │
│      <span onClick={() => onFileSelect(i)}>                 │
│        {element.name}   // "config.yaml"                   │
│      </span>                                               │
│      <button onClick={() => onFileRemove(i)}>×</button>     │
│    </div>                                                   │
│  ))}                                                        │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              USER CLICKS REMOVE (×) ON A CHIP               │
│                                                             │
│  onFileRemove(i)   →  FileUpload.removeEnvManifest(i)      │
│                                                             │
│  const removeEnvManifest = (i) => {                         │
│    let files = state.form_data.data.files                   │
│    files.splice(i, 1)    // remove file at index i          │
│    updateFilesInState(files)                                │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘


USER CLICKS SUBMIT / SAVE
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                 PARENT COMPONENT                            │
│                                                             │
│  // 1. Validate                                             │
│  const result = inherits.validateForm()                     │
│                                                             │
│  // 2. Read files                                           │
│  const fileData = inherits.getData()                        │
│  // → { files: [{ name, content, event_name }] }           │
│                                                             │
│  // 3. Build API payload                                    │
│  manifest_meta_data = {                                     │
│    strategy: "UPLOADED_MANIFEST",                           │
│    manifest_file_paths: fileData.files  // the array        │
│  }                                                          │
│                                                             │
│  // 4. POST to API                                          │
│  PostData(url, payload, successFn, failFn)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. State Shape Reference

### FileUpload internal state

```javascript
{
  content: "",                          // Base64 of currently previewed file
  form_data: {
    data: {
      files: [                          // ← the main array
        {
          event_name: "files",          // always "files"
          name: "config.yaml",          // original filename
          content: "SGVsbG8gV29ybGQ="   // Base64-encoded file contents
        },
        // ...more files
      ],
      cmd_agrs: []                      // optional: ["--verbose", "--dry-run"]
    },
    error: {
      files: ""                         // "" or "This field is required"
    },
    default_validations: {
      files: ["required"]
    }
  }
}
```

### `inherits.getData()` return value

```javascript
{
  files: [
    { event_name: "files", name: "config.yaml", content: "SGVsbG8..." },
    { event_name: "files", name: "values.yaml", content: "a2V5OiB2..." }
  ],
  cmd_agrs: []
}
```

### API POST payload (from `parseDataForPost`)

```javascript
{
  key: "my-key",
  value: null,
  is_key_value_pairs_in_file: false,
  manifest_meta_data: {
    strategy: "UPLOADED_MANIFEST",
    manifest_file_paths: [
      { event_name: "files", name: "config.yaml", content: "SGVsbG8..." },
      { event_name: "files", name: "values.yaml", content: "a2V5OiB2..." }
    ]
  }
}
```

---

## 5. Why Multi-File Doesn't Work Today

There are **3 compounding issues** that each need to be resolved:

### Issue 1 — `ParseFile.js` only reads `files[0]` (Root Cause)

```javascript
// ParseFile.js line 5
const file_to_load = e.target.files[0];  // ← hardcoded to first file
```

Even in drag-and-drop mode where multiple files are dropped at once, this function is only called once and only reads the first file. The remaining files in `e.target.files` are never touched.

**Impact:** Even if you select 5 files in the multi-file picker, only 1 file lands in the state.

---

### Issue 2 — `varient="single"` in parent components (Behavior Lock)

```jsx
// In ConfigMap.jsx AddMoreGuidedForm and AccessLevel.js
<FileUpload ... varient="single" ... />
```

When `varient="single"`:
- `FileUpload.jsx` passes `type="file"` to `Input.jsx`
- `Input.jsx` renders `<input type="file">` **without** the `multiple` attribute
- The browser's file picker only allows selecting one file at a time
- Additionally, `onParseComplete` clears existing files before adding the new one (`files = []`)

So even if ParseFile were fixed, `varient="single"` would still enforce single-file behavior at two levels.

---

### Issue 3 — `Input.jsx` drag-and-drop fires `onChangeHandler` once per file (Not a bug, but important to understand)

```javascript
// In Input.jsx multi-file-upload case
Array.from(droppedFiles).forEach(file => {
  const syntheticEvent = { target: { files: [file], name: name } };
  onChangeHandler(syntheticEvent);  // called once per file
});
```

This is intentional and correct — it's actually a workaround for the fact that `ParseFile` takes a single event. Each dropped file is wrapped in its own synthetic event, so `ParseFile` is called once per file. **This part is already correct** and works with the proposed fix to `ParseFile`.

However, this only works in drag-and-drop mode. When using the file picker with `multiple`, the browser fires `onChange` once with all files in `e.target.files`. `ParseFile` then needs to loop internally to handle them all.

---

## 6. What Needs to Change for Multi-File

### Fix 1 — `ParseFile.js` (The Critical Fix)

Change from reading a single file to reading all files and calling the callback once per file:

```javascript
// BEFORE (current — broken for multi):
const ParseFile = (e, onParsingComplete, onError) => {
  try {
    const event_name = e.target.name;
    const file_to_load = e.target.files[0];  // ← only first file

    const file_reader = new FileReader();
    file_reader.onload = function (fileLoadedEvent) {
      // ... converts and calls onParsingComplete once
    };
    file_reader.readAsArrayBuffer(file_to_load);
  } catch (error) {
    onError(error);
  }
  e.target.value = null;
};
```

```javascript
// AFTER (fixed — supports any number of files):
const ParseFile = (e, onParsingComplete, onError) => {
  try {
    const event_name = e.target.name;
    const files = Array.from(e.target.files);   // ← ALL files

    // Process each file independently
    files.forEach(file_to_load => {
      const file_reader = new FileReader();

      file_reader.onload = function (fileLoadedEvent) {
        try {
          const arrayBuffer = fileLoadedEvent.target.result;
          const bytes = new Uint8Array(arrayBuffer);
          let binary = '';
          for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
          }
          const content = Base64.encode(binary);

          // Callback fires once per file — FileUpload appends each one
          onParsingComplete({
            event_name,
            name: file_to_load.name,
            content: content,
          });
        } catch (error) {
          onError(error);
        }
      };

      file_reader.readAsArrayBuffer(file_to_load);
    });

  } catch (error) {
    onError(error);
  }

  e.target.value = null;
};
```

**Why this works with `FileUpload.jsx` as-is:** `onParseComplete` in `FileUpload.jsx` appends each file to the array:

```javascript
const onParseComplete = (file_data) => {
  let files = varient == "single" ? [] : state.form_data.data.files;
  files.push(file_data);  // ← called once per file, appends each time
  updateFilesInState(files);
}
```

Since `ParseFile` now calls `onParsingComplete` N times (once per file), `onParseComplete` will run N times, appending N files to the array. No changes needed in `FileUpload.jsx`.

> **Note on async ordering:** FileReader is async, so files may complete in any order. If ordering matters, you could use `Promise.all` over the readers. For typical use cases (YAML/config files), order doesn't matter because the API receives a flat array.

---

### Fix 2 — Parent components: change `varient="single"` to `varient="multi"`

#### In `ConfigMap.jsx` — `AddMoreGuidedForm` render:

```jsx
// BEFORE:
<FileUpload
  style={{ padding: 0 }}
  noLabel={true}
  inherits={state.child_inherits.fileUpload}
  varient="single"                           // ← single mode
  state={state.fileUpload?.files ? getFileUploadState() : null}
/>

// AFTER:
<FileUpload
  style={{ padding: 0 }}
  noLabel={true}
  inherits={state.child_inherits.fileUpload}
  varient="multi"                            // ← multi mode
  state={state.fileUpload?.files ? getFileUploadState() : null}
/>
```

#### In `AccessLevel.js` — `FileUploadComponent` render (~line 524):

```jsx
// BEFORE:
<FileUpload inherits={state.child_inherits.fileUpload} state={...} varient="single" />

// AFTER:
<FileUpload inherits={state.child_inherits.fileUpload} state={...} varient="multi" />
```

#### In `AccessLevel.js` — `EnabledComponentServiceAcount` render (~line 741):

```jsx
// BEFORE:
<FileUpload inherits={state.child_inherits.fileUpload} state={...} varient="single" />

// AFTER:
<FileUpload inherits={state.child_inherits.fileUpload} state={...} varient="multi" />
```

**Effect of this change:**
- `FileUpload.jsx` passes `type="multi-file-upload"` to `Input.jsx`
- `Input.jsx` renders the multi-file UI with `multiple` attribute and drag-and-drop
- `onParseComplete` stops clearing the array before appending

---

### Fix 3 — No other changes needed

Everything else is already correct:

| Component | Multi-file ready? | Reason |
|---|---|---|
| `FileUpload.jsx` — state shape | ✅ Yes | `files` is always an array |
| `FileUpload.jsx` — `onParseComplete` append logic | ✅ Yes | Already appends when not single mode |
| `FileUpload.jsx` — `removeEnvManifest` | ✅ Yes | Works on any index |
| `FileUpload.jsx` — `inherits.getData()` | ✅ Yes | Returns full `files[]` array |
| `Input.jsx` — `multi-file-upload` chip display | ✅ Yes | Maps over array |
| `Input.jsx` — drag-and-drop per-file loop | ✅ Yes | Already iterates dropped files |
| `ConfigMap.jsx` — `parseDataForPost` | ✅ Yes | Sends `files[]` array to API |
| `ConfigMap.jsx` — `inherits.getData()` | ✅ Yes | Returns `fileUpload.getData().files` |
| API payload shape | ✅ Yes | `manifest_file_paths` already expects array |

---

## 7. After the Fix — Multi-File Flow Diagram

```
USER SELECTS 3 FILES (or drags 3 files)
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│  INPUT.JSX  (type="multi-file-upload")                         │
│                                                                │
│  CASE A: File picker (multiple attr)                           │
│    onChange fires ONCE with e.target.files = [f1, f2, f3]     │
│                                                                │
│  CASE B: Drag and drop                                         │
│    onDrop fires ONCE with e.dataTransfer.files = [f1, f2, f3] │
│    forEach loop fires onChangeHandler 3 TIMES                  │
│    Each call: e.target.files = [f1]  /  [f2]  /  [f3]         │
└────────────────────────────┬───────────────────────────────────┘
                             │
              ┌──────────────┼──────────────────────────┐
              │              │  (CASE A: called once)    │
              │              │  (CASE B: called 3 times) │
              ▼              ▼              ▼            │
┌─────────────────────────────────────────────────────┐ │
│   FILEUPLOAD.JSX — onChangeHandler                  │ │
│                                                     │ │
│   ParseFile(e, onParseComplete, onError)            │ │
└────────────────────┬────────────────────────────────┘ │
                     │                                   │
                     ▼                                   │
┌─────────────────────────────────────────────────────┐ │
│   PARSEFILE.JS (FIXED)                              │ │
│                                                     │ │
│   const files = Array.from(e.target.files)          │ │
│   // CASE A: [f1, f2, f3]                           │ │
│   // CASE B: [f1] or [f2] or [f3]                   │ │
│                                                     │ │
│   files.forEach(file => {                           │ │
│     new FileReader().readAsArrayBuffer(file)        │ │
│     // async: fires onParsingComplete per file      │ │
│   })                                                │ │
└────────────────────┬────────────────────────────────┘ │
                     │                                   │
        fires callback N times (N = number of files)     │
                     │                                   │
        ┌────────────┼──────────────────┐                │
        ▼            ▼                  ▼                │
┌──────────────────────────────────────────────────────┐ │
│   FILEUPLOAD.JSX — onParseComplete (runs 3x)         │ │
│                                                      │ │
│   Run 1:                                             │ │
│     files = state.form_data.data.files  // []        │ │
│     files.push(file_data_1)             // [f1]      │ │
│     updateFilesInState([f1])                         │ │
│                                                      │ │
│   Run 2:                                             │ │
│     files = state.form_data.data.files  // [f1]      │ │
│     files.push(file_data_2)             // [f1, f2]  │ │
│     updateFilesInState([f1, f2])                     │ │
│                                                      │ │
│   Run 3:                                             │ │
│     files = state.form_data.data.files  // [f1, f2]  │ │
│     files.push(file_data_3)         // [f1, f2, f3]  │ │
│     updateFilesInState([f1, f2, f3])                 │ │
└────────────────────┬─────────────────────────────────┘ │
                     │                                   │
                     ▼                                   │
┌──────────────────────────────────────────────────────┐ │
│   INPUT.JSX — renders 3 chips                        │ │
│                                                      │ │
│   [config.yaml ×]  [values.yaml ×]  [secrets.yaml ×]│ │
└──────────────────────────────────────────────────────┘ │
                                                         │
ON SUBMIT:                                               │
                     │                                   │
                     ▼                                   │
┌──────────────────────────────────────────────────────┐ │
│   PARENT — inherits.getData()                        │ │
│                                                      │ │
│   → { files: [                                       │ │
│       { name: "config.yaml",  content: "SGVs..." },  │ │
│       { name: "values.yaml",  content: "a2V5..." },  │ │
│       { name: "secrets.yaml", content: "c2Vj..." },  │ │
│     ]}                                               │ │
│                                                      │ │
│   manifest_file_paths = fileData.files   // array   │ │
│   PostData(url, payload)                            │ │
└──────────────────────────────────────────────────────┘
```

---

## 8. Change Summary Table

| # | File | Line(s) | Change | Impact |
|---|---|---|---|---|
| 1 | `ParseFile.js` | 5–30 | Loop over `Array.from(e.target.files)` instead of `files[0]` | **Core fix** — enables parsing N files per event |
| 2 | `ConfigMap.jsx` | ~line in `AddMoreGuidedForm` | `varient="single"` → `varient="multi"` on `<FileUpload>` | Enables multi-file picker UI and append behavior |
| 3 | `AccessLevel.js` | ~line 524 | `varient="single"` → `varient="multi"` | Same as above for FileUploadComponent |
| 4 | `AccessLevel.js` | ~line 741 | `varient="single"` → `varient="multi"` | Same as above for EnabledComponentServiceAcount |

**Files with zero changes needed:**

- `FileUpload.jsx` — state management, `onParseComplete`, `removeEnvManifest`, `inherits.*` are all multi-file-ready
- `Input.jsx` — `multi-file-upload` case with `multiple` attr, drag-drop loop, and chip rendering already work
- `ConfigMap.jsx` — `parseDataForPost` and API payload shape already handle arrays
- Any API layer — `manifest_file_paths` is already defined as an array in the schema

---

*Generated from code analysis of `Input.jsx`, `FileUpload.jsx`, `ParseFile.js`, `ConfigMap.jsx`, and `AccessLevel.js`*
