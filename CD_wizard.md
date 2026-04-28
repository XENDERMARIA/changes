# CdInfo & StepWizard — Complete Code Documentation

---

## Table of Contents

1. [Overview](#overview)
2. [CdInfo Component](#cdinfo-component)
   - [Purpose](#purpose)
   - [Imports & Dependencies](#imports--dependencies)
   - [URL Parameters & Location State](#url-parameters--location-state)
   - [State Structure](#state-structure)
   - [Panel List Configuration](#panel-list-configuration)
   - [Lifecycle & Data Fetching Flow](#lifecycle--data-fetching-flow)
   - [Core Functions](#core-functions)
   - [Render Logic](#render-logic)
3. [Helper Functions (CdInfo)](#helper-functions-cdinfo)
   - [panelPostDataParser](#panelpostdataparser)
   - [panelFetchDataParser](#panelfetchdataparser)
   - [Supporting Utilities](#supporting-utilities)
4. [StepWizard Component](#stepwizard-component)
   - [Purpose](#purpose-1)
   - [Props API](#props-api)
   - [Internal State](#internal-state)
   - [Core Functions](#core-functions-1)
   - [Render Logic](#render-logic-1)
   - [DefaultFooter Sub-Component](#defaultfooter-sub-component)
5. [Data Flow Between Components](#data-flow-between-components)
6. [Panel-by-Panel Breakdown](#panel-by-panel-breakdown)
7. [Clone vs Edit vs Create Modes](#clone-vs-edit-vs-create-modes)
8. [Key Design Patterns](#key-design-patterns)

---

## Overview

These two files together implement a **multi-step deployment configuration wizard** for a Kubernetes/CI-CD platform. The flow is:

```
CdInfo (orchestrator)
  └── StepWizard (step navigation engine)
        ├── Panel 1: Access Level
        ├── Panel 2: Resource Quota
        ├── Panel 3: Configuration & Secrets
        ├── Panel 4: Mount Details
        ├── Panel 5: Env Variables
        ├── Panel 6: Node And Service Affinity
        ├── Panel 7: Security Context Details
        ├── Panel 8: Tolerations And Priorities
        ├── Panel 9: Liveness/Readiness
        ├── Panel 10: Labels/Annotations
        ├── Panel 11: Additional Manifest
        ├── Panel 12: Hooks
        └── Panel 13: Versioning Details (conditional)
```

`CdInfo` is responsible for **fetching, parsing, and submitting** deployment configuration data. `StepWizard` is a **generic, reusable step navigation engine** that renders panels one at a time with Next/Back/Submit controls.

---

## CdInfo Component

### Purpose

`CdInfo` is the top-level page component for creating, editing, or cloning a **CD (Continuous Deployment) configuration** for a microservice environment. It:

- Fetches service details, environment list, and system settings from the backend API.
- Builds the initial state for each wizard panel from the fetched data.
- Passes everything into `StepWizard`.
- On submit, collects panel state, serialises it into a POST payload, and saves it to the backend.

---

### Imports & Dependencies

| Import | Role |
|---|---|
| `React`, `useState`, `useEffect` | Component lifecycle and state management |
| `makeStyles` (`@mui/styles`) | CSS-in-JS scoped styling |
| `AccessLevel`, `ResourceQuota`, `ConfigSecret`, etc. | Individual panel components (steps in the wizard) |
| `StepWizard` | The wizard shell that renders panels and navigation |
| `InvokeApi`, `PostData` | Generic API utility functions (GET and POST wrappers) |
| `GenerateURL` | Builds endpoint URLs from a route-name map (`properties.api.*`) |
| `properties` | Global config object holding API route names, environment labels, etc. |
| `useParams`, `useNavigate`, `useLocation` | React Router v6 hooks for routing |
| `queryString` | Parses `?selectedTabOrder=N` from the URL query string |
| `useCustomSnackbar` | App-level toast/notification context |
| `AlertStrip` | Inline error banner component |

---

### URL Parameters & Location State

```js
const { application_id, component_id, component_env_id, cd_id } = useParams();
```

| Parameter | Meaning |
|---|---|
| `application_id` | The parent application's ID |
| `component_id` | The service/component ID |
| `component_env_id` | The specific environment binding for this component |
| `cd_id` | If present, means we are **editing** an existing CD config |

From `location.state` (passed via React Router navigation):

| Key | Meaning |
|---|---|
| `clone_env_id` | If set, data is being **cloned from another environment** |
| `clone_deploy_id` | If set, data is being **cloned from a specific deployment** |
| `available_deployments` | List of existing deployments passed for hook configuration |

From `queryString.parse(location.search)`:

| Key | Meaning |
|---|---|
| `selectedTabOrder` | Opens the wizard directly on a specific tab number |

---

### State Structure

```js
const [state, setState] = useState({
  selectedTabOrder: Number,       // Which wizard tab is active
  available_settings: [],         // All system settings from the backend
  cd_settings: [],                // Filtered: VERSIONING_CD_ENABLE setting only
  data_loading: Boolean,          // Controls the loading spinner
  error_in_save_cd: null | String // Error message from save failure
});
```

After data loads, the state is expanded with:

```js
{
  service_name: String,
  build_strategy: String,          // "EVERY" or "PROMOTE"
  data: { selectedServiceEnv, envData },
  extraProps: { ... },             // Passed as props to every wizard panel
  panels: { "1": {...}, "2": {...}, ... }  // Pre-filled panel states
}
```

**`extraProps`** — this object is threaded through every panel so they have access to contextual data:

```js
extraProps = {
  namespace_name: String,
  istio_enabled: Boolean,
  cluster_id: Number,
  project_env_name: String,
  project_env_id: Number,
  environment_master_name: String,  // e.g. "production"
  service_name: String,
  hookType: 'deploy',
  component_env_id: Number,
  service_id: Number,
  application_id: Number,
  available_deployments: Array,
  multi_namespace: Boolean,
}
```

---

### Panel List Configuration

Two panel lists are defined:

#### `panel_list` (13 panels — with Versioning)
Used when system setting `VERSIONING_CD_ENABLE = "true"`.

#### `panel_list_2` (12 panels — without Versioning)
Used otherwise. Identical to `panel_list` but omits **Panel 13: Versioning Details**.

Each entry is:
```js
{
  order: Number,       // Tab number (1-based)
  mainText: String,    // Label shown in the stepper header
  body: Component,     // React component to render for that step
}
```

---

### Lifecycle & Data Fetching Flow

```
useEffect (mount)
  └─► getComponentDetails()           [GET /service/{id}/basic_details]
        └─► handleResponse()
              ├── stores service_name, build_strategy
              └─► fetchEnvList()      [GET /service/{id}/envs]
                    └─► onEnvListFetchSuccess()
                          ├── builds extraProps from selected env
                          └─► fetchSystemSettingsData()  [GET /system_settings]
                                └─► fetchSystemSettingsDataSuccess()
                                      ├── extracts VERSIONING_CD_ENABLE setting
                                      └─► fetchCdData()  [GET /cd or /cd/{id}]
                                            └─► onEditFetchSuccess()
                                                  └── calls panelFetchDataParser()
                                                        └── populates state.panels
```

**Why this sequential chain?** Each step needs data from the previous one. `extraProps` requires the environment details, and `fetchCdData` needs the versioning setting to decide whether to strip record IDs (versioning creates new records every time rather than updating existing ones).

---

### Core Functions

#### `getComponentDetails()`
Fetches the basic service info (name, build strategy). Triggers the entire data fetch chain on success.

#### `fetchEnvList()`
Fetches all environment bindings for the service. Finds the current environment (`getSelectedEnv`) and builds `extraProps`.

#### `fetchSystemSettingsData()`
Fetches global platform settings. Filters for `VERSIONING_CD_ENABLE` to decide which panel list to show and whether to null out IDs before editing.

#### `fetchCdData(clone_env_id, clone_deploy_id, cd_settings)`
Fetches existing CD configuration(s).
- If `clone_env_id` + `clone_deploy_id` are provided: fetches from the source environment/deployment.
- Otherwise: fetches from the current environment.
- Calls `panelFetchDataParser()` to convert raw API response into wizard panel state.

#### `onSubmit(panel_object)`
Called by `StepWizard` when the user clicks Submit on the last panel. It:
1. Iterates over all panels and calls `panelPostDataParser()` for each.
2. Merges results into a single `post_data_final` object.
3. Decides the API endpoint:
   - **Versioning enabled + existing record** → `save_versioning` endpoint
   - **Existing record (edit)** → `edit_cd` endpoint
   - **New record** → `save_cd` endpoint
4. Sets `data_loading: true` during the request.

#### `onSaveSuccess(response)`
Shows a success snackbar, sets `canRedirect = true` (triggers `<Navigate>` redirect back to the service detail page).

#### `onSaveFail(error)`
Shows an error snackbar, sets `error_in_save_cd` to display an `<AlertStrip>`.

---

### Render Logic

```jsx
<div className={classes.root}>
  <Button variant="back" onClick={...}>Back</Button>

  {/* Header: service name, build strategy, env info */}
  <div className="service-form-heading-section">
    <SquareAvatar />
    <span>Deploy Details for {state.service_name}</span>
    <span>Build Strategy: ...</span>
    <span>Env Name: ...</span>
  </div>

  {/* Inline error banner */}
  {state.error_in_save_cd && <AlertStrip variant="error" />}

  <div className="card">
    {state.data_loading
      ? <Loading />
      : state.extraProps
        ? <StepWizard
            dataJson={panel_list or panel_list_2}
            prev_state={state.panels}
            onSubmit={onSubmit}
            extraProps={state.extraProps}
          />
        : null
    }
    {canRedirect && <Navigate to="...service detail..." />}
  </div>
</div>
```

Key rendering decisions:
- **Loading spinner** shown while any async fetch is in progress.
- **`StepWizard` only mounts when `extraProps` is ready** — this prevents panels from rendering without contextual data.
- **`canRedirect`** uses React Router's `<Navigate>` for a declarative redirect after a successful save.

---

## Helper Functions (CdInfo)

### `panelPostDataParser`

**Signature:**
```js
function panelPostDataParser(panel_no, panel_data, env_cd_configmap, env_cd_secret, env_cd_pvc_variable, env_cd_empty_dir, env_cd_host_path, env_cd_configmap_keys, env_cd_secret_keys, env_cd_container_security_context, env_cd_pod_security_context, cd_settings)
```

This is a large `switch` statement that converts the **internal wizard panel state** into a **flat API POST payload**. It is called once per panel during `onSubmit`.

The accumulator arrays (`env_cd_configmap`, `env_cd_secret`, etc.) are passed between panel parsers because some data from Panel 3 (ConfigSecret) needs to be merged with data from Panel 5 (EnvVar) — for example, a configmap used as a volume mount vs. as an env var injection.

| Case | Panel | What it builds |
|---|---|---|
| `"0"` | (internal) | Spread `panel_data.data` directly |
| `"1"` | Access Level | `service_name`, `image_name`, `deployment_name`, `desired_replication`, `env_cd_access_detail` (port/ingress list), `custom_ingress_manifest` (file/git upload), `service_account`, `env_cd_service_account` |
| `"2"` | Resource Quota | `requests_memory_quota`, `requests_cpu_quota`, `requests_gpu_quota`, `limits_*` variants |
| `"3"` | Config & Secrets | `env_cd_configmap` (file mounts), `env_cd_configmap_keys` (sub-path mounts), `env_cd_secret`, `env_cd_secret_keys` |
| `"4"` | Mount Details | `env_cd_empty_dir`, `env_cd_host_path`, `env_cd_pvc_variable`, `entrypoint` |
| `"5"` | Env Variables | `env_cd_deploy_variable` (raw key/val, secret ref, configmap ref), `env_cd_field_ref_variable` |
| `"6"` | Node/Service Affinity | `env_cd_node_affinity` (REQUIRED + PREFERRED), `env_cd_pod_affinity` |
| `"7"` | Security Context | `env_cd_container_security_context`, `env_cd_pod_security_context` |
| `"8"` | Tolerations/Priority | `env_cd_tolerations`, `priority_class` |
| `"9"` | Liveness/Readiness | `env_cd_deployment_strategy` (surge, unavailable, canary), `env_cd_liveness_probe`, `env_cd_readiness_probe`, `deployment_rollback_validation`, `server_side_apply` |
| `"10"` | Labels/Annotations | `labels`, `annotations`, `label_selectors` (via `keyValueParser`) |
| `"11"` | Additional Manifest | `env_cd_other_manifest` with strategy `UPLOADED_MANIFEST`, `GIT_MANIFEST`, or `YAML_MANIFEST` |
| `"12"` | Hooks | `env_cd_hook` (pre + post), `queue_name`, `host_mapping` |
| `"13"` | Versioning | `versioning_repo_id`, `draft_branch`, `main_branch`, `deployed_branch`, `versioning_path`, `auto_refresh` |

---

### `panelFetchDataParser`

**Signature:**
```js
function panelFetchDataParser(data, clone_env_id, extraProps, clone_deploy_id, cd_settings)
```

The inverse of `panelPostDataParser`. Converts the **raw API GET response** into the shape expected by each panel component's internal state. Returns a `panels` object with keys `"1"` through `"13"`.

Each panel entry is constructed by spreading the panel's own `getXxxDefaultState()` factory (which provides the full empty shape) and then overwriting fields with live data. This ensures no undefined keys cause panel rendering errors.

Notable logic:

- **Clone mode** (`clone_env_id || clone_deploy_id`): Fields like `deployment_name`, `service_name`, `image_name` are reconstructed from `service_name + env_master + project_env_name` instead of using the raw value. IDs are nulled out so the save creates a new record.
- **Versioning mode** (`data_post_for_versioing = true`): All child record IDs (access details, liveness probes, deployment strategy) are nulled out, forcing the versioning endpoint to create fresh records.
- **Service Account**: Uses a `getNonEmptyString` helper to search multiple possible paths in the API response for the account name, because different API versions may store it differently.
- **Env Var categorisation**: `getEnvDeployVarsWithCategories()` splits the flat `env_cd_deploy_variable` array into three buckets: `keyVal`, `secrets` (by `value_from_secret`), and `configMaps` (by `value_from_configmap`).

---

### Supporting Utilities

#### `getNodeAffinity(data)`
Iterates a multi-row object (keyed by numeric strings), skipping meta keys (`data`, `count`, etc.), and returns an array of node affinity rule objects.

#### `getPodAffinity(affinity, anti_affinity)`
Merges service affinity and anti-affinity into a single `env_cd_pod_affinity` array with `affinity_choice` (REQUIRED/PREFERRED) and `affinity_type` (true = affinity, false = anti-affinity) flags.

#### `getNodeReqdAff(nodes)` / `getNodePreAff(nodes)`
Converts flat node affinity arrays from the API into the nested multi-row format the `NodeAffinityRequired`/`NodeAffinityPreferred` components expect.

#### `getAffChild(node)`
Builds numeric-keyed child objects from a key-value-with-operator array.

#### `getListForClone(data)`
Strips volatile fields (`id`, `updated_at`, `created_at`) from access detail entries when cloning, so the clone creates new DB records.

#### `getIngressListAndDeleteIds(data)`
Same as above but used in versioning mode for the current environment.

#### `getEnvCdSecretsList` / `getEnvCdCongigMapsList`
Convert flat arrays of secret/configmap mounts into the multi-row child format, filtering out entries where `mount_path === null` (those are env var references, handled in Panel 5).

#### `getEnvCdConfigMapKeyList` / `getEnvCdSecretKeyList`
Handle sub-path mounts (mounting individual keys from a configmap/secret to a specific file path).

#### `getEnvCdEmptyDirList` / `getEnvCdHostPathList` / `getEnvCdPvcsMapsList`
Similar converters for EmptyDir volumes, HostPath volumes, and Persistent Volume Claims.

#### `getSecretEnvVars` / `getConfigMapEnvVars` / `getKeyValEnvVars` / `getFieldsRefEnvVars`
Build multi-row state for the EnvVar panel (Panel 5), one function per env var source type.

#### `getHostMappingList(hostMappingData)`
Converts host mapping data (IP → hostname aliases) into the numbered-child format for the Hooks panel.

#### `getPodSecurityKeyValue` / `getContainerSecurityKeyValue`
Build multi-row key/value state for the Security Context panel.

#### `getReadinessData` / `getLivelinessData`
Extract probe configuration fields from the API response into a flat object for the Liveness/Readiness panel.

#### `getSelectedEnv(id, data)`
Scans the environment list and returns the environment object whose `id` matches `component_env_id`.

#### `getDefaultPortDetails(data)`
Constructs a default port entry `{ port, target_port, name, protocol: "TCP" }`.

#### `getManifestFilePaths(fileUploadedData)`
Extracts file path information from either a fresh file upload state or an existing `uploaded_file` field.

#### `getEnvCdAccessDetail(fileUploadedData)`
Builds a single-element access detail array from file-upload ingress data.

---

## StepWizard Component

### Purpose

`StepWizard` is a **generic, stateful, reusable wizard shell**. It does not know anything about CD configuration — it only knows how to:

1. Render a stepper/tab header.
2. Render the currently active panel component.
3. Collect panel state via an `inherits` ref pattern.
4. Navigate forward (with optional validation) and backward.
5. Call `onSubmit` with the complete collected state when the user finishes.

---

### Props API

| Prop | Type | Description |
|---|---|---|
| `dataJson` | `Array` | List of `{ order, mainText, body }` panel descriptors |
| `currentTab` | `Number` | Which tab to start on (default: 1) |
| `prev_state` | `Object` | Pre-filled state (for edit mode) — entire wizard state including `panels` |
| `onSubmit` | `Function` | Called with the complete panel object on final Submit |
| `extraProps` | `Object` | Passed as a prop to every panel component |
| `horizontalTab` | `Component` | Override the default `HorizontalTab` stepper header |
| `footer` | `Component` | Override the default `DefaultFooter` |
| `variant` | `String` | Rendering style: `"service-config"`, `"policy-template-wizard"`, `"new_ui"`, or default |
| `stepper_variant` | `String` | `"new_ui"` triggers the `HorizontalTabNewUI` header |
| `isListing` | `Boolean` | Read-only mode — hides the footer |
| `cancelButtonUrl` | `Function` | Called when Cancel is clicked |
| `parentPath` | `String` | Passed through to footer |
| `sendTabChangeEvent` | `Function` | Callback fired after tab change with new tab order |
| `currentTabData` | `Function` | Callback fired with current panel's state after moving forward |
| `type`, `child_build`, `child_build_data`, `application_id`, `shallow_cloning`, `depth`, `is_add` | Various | Domain-specific props threaded through to panel components |

---

### Internal State

```js
const [state, setState] = useState({
  panels: {},              // { [order]: panelState } — accumulated panel states
  selectedTabOrder: 1,     // Currently active tab
});
```

The `childInherits` object is a **plain object reference** (not state) used as a bridge between `StepWizard` and the active panel:

```js
const childInherits = {};
// Each panel component receives this as `inherits` and populates it with:
// childInherits.getState = () => currentPanelState
// childInherits.validateForm = () => { valid: Boolean, ... }
// childInherits.getAPIObject = () => { ... }
```

This is a **callback/imperative handle pattern** — instead of React refs, the panel writes functions onto `childInherits` that the wizard can call when it needs to collect or validate state.

---

### Core Functions

#### `getActivePanel()`
Looks up `indexedDataJson[state.selectedTabOrder].body` (the panel component) and renders it with:
- `extraProps` — contextual data
- `inherits={childInherits}` — the shared callback bridge
- `prev_state={state.panels[state.selectedTabOrder]}` — previously saved state for this tab (so returning to a tab restores your input)
- `total_state={state.panels}` — all panels' state (some panels need cross-panel context)

#### `moveNext()`
1. Calls `childInherits.validateForm()` — if the panel has a validation function, run it.
2. If valid (or no validator), calls `childInherits.getState()` to snapshot the current panel state.
3. If not the last panel: increments `selectedTabOrder` and saves the snapshot into `state.panels[currentOrder]`.
4. If the last panel: saves the snapshot but **returns `true`** (signals `onSubmit` that validation passed).

#### `moveBack()`
Snapshots the current panel state into `state.panels`, then decrements `selectedTabOrder`. Does nothing if already on tab 1.

#### `onSubmit(additional_data)`
1. Calls `moveNext()` — this validates the last panel and returns `true` if valid.
2. If valid, assembles the complete panel object: `{ ...state.panels, [lastTab]: childInherits.getState() }`.
3. Calls `props.onSubmit(complete_panel_object, additional_data)`.

#### `onClickHandler(order)`
Allows clicking directly on a stepper tab to jump to that step. Note: **no validation is run** on the current panel when jumping — this is intentional for edit flows where the user may want to jump around freely.

#### `arrayToIndexedObject(wizardJson)`
Converts the `panel_list` array into a keyed object `{ [order]: panelDescriptor }` for O(1) lookup.

---

### Render Logic

```jsx
// 1. Stepper header — variant determines which component and wrapper to use
{variant == "service-config" || stepper_variant == "new_ui"
  ? <HorizontalTabNewUI ... />
  : isListing
    ? <HorizontalTab variant="edit_mode" ... />
    : <HorizontalTab ... />          // default
}

// 2. Active panel
{getActivePanel()}

// 3. Footer (Next/Back/Submit) — hidden in listing/read-only mode
{!isListing && <Footer ... />}
```

---

### DefaultFooter Sub-Component

Renders at the bottom of the wizard. Layout varies by variant:

| Variant | Left side | Right side |
|---|---|---|
| `"new_ui"` / `"service-config"` | Cancel + Back | Continue / Submit |
| `"policy-template-wizard"` | Back (if not tab 1) | Continue / Submit |
| Default | Back (if not tab 1) | Continue / Submit |

The Submit button uses the `Button` component with an `isLoading` prop (from `extraProps.isSubmitting`) to show a spinner while saving.

```jsx
{wizardJson.length == selectedTabOrder
  ? <Button isLoading={extraProps?.isSubmitting} onClick={onSubmit}>Submit</Button>
  : <button onClick={moveNext}>Continue</button>
}
```

---

## Data Flow Between Components

```
CdInfo
  │
  ├── Fetches data from API
  │
  ├── Calls panelFetchDataParser()
  │     └── Returns panels: { "1": {...}, "2": {...}, ... }
  │
  └── Renders <StepWizard prev_state={state} onSubmit={onSubmit} extraProps={...}>
        │
        ├── StepWizard holds state.panels internally
        │
        ├── On each tab, renders ActivePanelComponent
        │     ├── receives inherits={childInherits}  ← imperative bridge
        │     ├── receives prev_state={panels[order]} ← restores saved input
        │     └── writes getState(), validateForm() onto childInherits
        │
        ├── On moveNext: childInherits.getState() snapshots panel into state.panels
        │
        └── On final Submit: calls CdInfo.onSubmit(completePanelObject)
              │
              └── CdInfo iterates panels, calls panelPostDataParser per panel
                    └── POSTs merged payload to API
```

---

## Panel-by-Panel Breakdown

### Panel 1 — Access Level
Configures the deployment's network exposure:
- Service/container/deployment names
- Desired replica count
- Image name and pull policy
- Port and ingress rules (guided form, file upload, or git-sourced manifest)
- Service account configuration (name only, file upload, or git-sourced manifest)
- Namespace selection (for multi-namespace environments)

### Panel 2 — Resource Quota
Kubernetes resource requests and limits:
- Memory and CPU requests
- Optional GPU requests (key/value for custom resource names like `nvidia.com/gpu`)
- Optional limit overrides (separate from requests)
- Auto-scaling toggle (presence of `env_cd_scale`)

### Panel 3 — Configuration & Secrets
Volume mounts for ConfigMaps and Secrets:
- Full configmap mount (name → mount path)
- Full secret mount (name → mount path)
- Sub-path configmap mount (individual keys → specific file paths)
- Sub-path secret mount (individual keys → specific file paths)

### Panel 4 — Mount Details
Other volume types:
- EmptyDir volumes (ephemeral in-memory or disk volumes)
- HostPath volumes (mount a node's filesystem path)
- PVC mounts (Persistent Volume Claims)
- Container entrypoint/command override

### Panel 5 — Env Variables
Environment variable injection from multiple sources:
- Raw key/value pairs
- ConfigMap references (entire configmap as env vars, or individual key)
- Secret references (entire secret as env vars, or individual key)
- Field references (`fieldRef` — inject pod metadata like `metadata.name`)

### Panel 6 — Node And Service Affinity
Pod scheduling constraints:
- Node affinity REQUIRED rules (must schedule on matching nodes)
- Node affinity PREFERRED rules (prefer matching nodes, soft constraint)
- Pod affinity (co-locate with specific service pods)
- Pod anti-affinity (avoid co-location with specific service pods)

### Panel 7 — Security Context Details
Container and pod-level Linux security settings:
- Container security context (e.g. `runAsUser`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation`)
- Pod security context (e.g. `fsGroup`, `runAsGroup`, `seccompProfile`)
Both are stored as generic key/value pairs for flexibility.

### Panel 8 — Tolerations And Priorities
Pod scheduling tolerations and priority:
- Tolerations (allow scheduling on tainted nodes)
- Priority class name (references a `PriorityClass` Kubernetes object)

### Panel 9 — Liveness/Readiness
Kubernetes health probes and rollout strategy:
- Liveness probe (HTTP, TCP, or exec — failure restarts the container)
- Readiness probe (HTTP, TCP, or exec — failure removes from service endpoints)
- Deployment strategy (`max_surge`, `max_unavailable`, `minimum_ready`)
- Progress deadline, termination grace period, revision history limit
- Canary release template reference
- Rollback validation toggle
- Server-side apply toggle

### Panel 10 — Labels/Annotations
Kubernetes metadata:
- Labels (used for selection and organisation)
- Label selectors (for service-to-pod targeting)
- Annotations (non-identifying metadata, e.g. for monitoring tools)

### Panel 11 — Additional Manifest
Supplementary Kubernetes YAML to apply alongside the main deployment:
- File upload strategy (`UPLOADED_MANIFEST`)
- Git repository strategy (`GIT_MANIFEST` — repo URL, branch, file path)
- Inline YAML editor strategy (`YAML_MANIFEST` — content base64-encoded)

### Panel 12 — Hooks
Lifecycle automation:
- Pre-deploy hooks (jobs/scripts run before deployment)
- Post-deploy hooks (jobs/scripts run after deployment)
- Queue name (for async deployment queuing)
- Host mapping (IP address → hostname aliases injected into pod `/etc/hosts`)

### Panel 13 — Versioning Details *(conditional)*
Only shown when `VERSIONING_CD_ENABLE = "true"` in system settings:
- Git repository for storing rendered deployment manifests
- Draft branch, main branch, deployed branch
- Versioning file path within the repo
- Auto-refresh toggle

---

## Clone vs Edit vs Create Modes

| Mode | Trigger | Behaviour |
|---|---|---|
| **Create** | No `cd_id`, no `clone_*` params | All panel states start from defaults; POST to `save_cd` |
| **Edit** | `cd_id` present in URL | Panel states pre-filled from existing CD record; PUT to `edit_cd` |
| **Clone from env** | `clone_env_id` in location state | Fetches from source env; nulls out IDs; renames service/deployment fields; POST to `save_cd` |
| **Clone specific deploy** | `clone_env_id` + `clone_deploy_id` | Fetches specific deployment from source env; same null-ID treatment |
| **Versioning edit** | `cd_id` + `VERSIONING_CD_ENABLE=true` | Fetches existing record but nulls all child IDs; POST to `save_versioning` (creates new version) |

---

## Key Design Patterns

### 1. Imperative Handle Pattern (`childInherits`)
Rather than using React's `useImperativeHandle`/`forwardRef`, `StepWizard` passes a plain object (`childInherits`) to each panel as `inherits`. The panel writes `getState` and `validateForm` functions onto this object. The wizard calls these functions when navigating. This is a common pattern in older React codebases that predates hooks being widely adopted.

### 2. Multi-Row State Shape
Many panels use a consistent "multi-row" shape for repeatable fields:
```js
{
  data: {},           // meta
  count: N,           // number of rows
  child_inherits: {}, // for nested wizards
  show_view: false,
  "1": { data: {...} },
  "2": { data: {...} },
  // ...
}
```
Keys `"data"`, `"count"`, `"child_inherits"`, `"show_view"` are always skipped when iterating rows — only numeric string keys are real rows.

### 3. Accumulator Pattern in `panelPostDataParser`
Arrays like `env_cd_configmap` and `env_cd_secret` are threaded through multiple panel parsers and accumulated. This is because configmaps can appear in both Panel 3 (file mounts) and Panel 5 (env var injection), and the final API payload needs a single unified array.

### 4. Default State Factories
Every panel has a `getXxxDefaultState()` factory function. These ensure that even if the API returns no data for a field, the panel always gets a complete, well-shaped state object with no undefined keys.

### 5. Conditional Versioning Behaviour
The `data_post_for_versioing` flag (derived from system settings) acts as a cross-cutting concern: it suppresses IDs throughout `panelFetchDataParser`, changes the stepper panel count, and selects a different API endpoint in `onSubmit`. This allows the same form to serve both regular edit and immutable-version-history workflows.
