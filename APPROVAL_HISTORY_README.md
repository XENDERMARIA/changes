# ApprovalHistory — Complete Detailed Explanation

---

## Table of Contents
1. [Overview](#overview)
2. [File Structure](#file-structure)
3. [ApprovalHistory.js — Deep Dive](#approvalhistoryjs--deep-dive)
   - [Imports](#imports)
   - [Constants](#constants)
   - [State](#state)
   - [useEffects — All 3 Explained](#useeffects--all-3-explained)
   - [buildEndPoint](#buildendpoint)
   - [fetchApprovalHistory](#fetchapprovalhistory)
   - [Filter System](#filter-system)
   - [addFiltersToUrl](#addfilterstourl)
   - [onUpdateHandle](#onupdatehandle)
   - [resetAdvFilter](#resetadvfilter)
4. [ApprovalHistoryTableRow.js — Deep Dive](#approvalhistorytablerowjs--deep-dive)
   - [Field Mapping](#field-mapping)
   - [ACTION_CONFIG](#action_config)
   - [MessageDialog](#messagedialog)
   - [Table Row Rendering](#table-row-rendering)
5. [Complete Data Flow](#complete-data-flow)
6. [URL Filter Flow](#url-filter-flow)
7. [Real World Example](#real-world-example)
8. [Common Questions](#common-questions)

---

## Overview

`ApprovalHistory` is a **full page** component that shows a log of every action performed on a specific pipeline execution — things like who triggered it, who approved it, who re-ran it after failure, etc.

**Real life analogy:**
Think of it like a bank transaction history page. Every time someone does something to the pipeline (trigger, approve, re-run), a record is saved. This page shows all those records with filters to search by action type or who did it.

---

## File Structure

```
ApprovalHistory.js          ← Main page: fetches data, manages filters, renders table
ApprovalHistoryTableRow.js  ← Single row component: displays one action record
```

They work together like a restaurant menu (ApprovalHistory) and a single menu item card (ApprovalHistoryTableRow).

---

## ApprovalHistory.js — Deep Dive

### Imports

```js
import GenerateURL from '../../../../util/APIUrlProvider';
import InvokeApi from '../../../../util/apiInvoker';
```

- **`GenerateURL`** — replaces `${variable}` placeholders in URL templates with real values
  ```js
  // Template: "api/v1/pipeline/pipeline_instance/${pipeline_instance_id}/pipeline_actions/"
  // After:    "api/v1/pipeline/pipeline_instance/27/pipeline_actions/"
  GenerateURL({ pipeline_instance_id: 27 }, properties.api.pipeline_actions)
  ```

- **`InvokeApi`** — makes HTTP requests with authentication token automatically attached

```js
import { useNavigate, useParams } from 'react-router-dom';
```

- **`useParams`** — reads IDs directly from the URL
  ```
  URL: /application/251/pipeline/1136/execution/47801/approval-history
  useParams() gives → { application_id: "251", pipeline_id: "1136", pipeline_instance_id: "47801" }
  ```

- **`useNavigate`** — used to update the URL when filters change

---

### Constants

```js
const ACTION_OPTIONS = [
    { label: 'Approve Stage',          value: 'APPROVE' },
    { label: 'Exceptional Approval',   value: 'EXCEPTIONAL_APPROVAL' },
    { label: 'Re-Run After Failure',   value: 'RE-RUN_AFTER_FAILURE' },
    { label: 'Continue With Failure',  value: 'CONTINUE_WITH_FAILURE' },
    { label: 'Trigger Pipeline',       value: 'TRIGGER' },
    { label: 'Revoke Pipeline',        value: 'REVOKE' },
];
```

This is a **static list** — it never changes, never fetched from API. It's the dropdown options for the Action filter.

- `label` → what the user sees in the dropdown ("Approve Stage")
- `value` → what gets sent to the backend as a query param ("APPROVE")

---

### State

The component has **two separate state objects**:

#### `loadingState`
```js
const [loadingState, setLoadingState] = useState({ listing: false });
```
Only tracks if the table data is loading. Kept separate so changing it doesn't cause unnecessary re-renders of filter-related things.

#### `state` — the main state
```js
const [state, setState] = useState({
    data: [],           // ← the 7 rows shown in the table
    next: null,         // ← URL for next page (from API)
    previous: null,     // ← URL for previous page (from API)
    total_page: 1,      // ← total number of pages
    curr_page: 1,       // ← which page user is on
    count: 0,           // ← total number of records
    error: null,        // ← any API error
    moreAdvFilterList: ['action', 'performed_by'],  // ← which filters are visible
    advFilters: { action: [], performed_by: [] },   // ← currently selected filter values
    resetCount: 0,      // ← incremented to tell filter components to reset themselves
    isFilterApplied: false,  // ← true if any filter is active
});
```

#### `userOptions`
```js
const [userOptions, setUserOptions] = useState([]);
```
Stores the list of users fetched from `api/v1/default/users?is_all=true` to populate the "Performed By" filter dropdown.

---

### useEffects — All 3 Explained

There are **3 useEffects** in this component. Each has a different job.

---

#### useEffect #1 — Fetch Users for Filter

```js
useEffect(() => {
    InvokeApi(
        {
            endPoint: GenerateURL({}, properties.api.user_list_user_portal) + '?is_all=true',
            httpMethod: 'GET',
            httpHeaders: { 'Content-Type': 'application/json' },
        },
        (data) => {
            const list = Array.isArray(data) ? data : (data.results || []);
            const options = list.map(user => ({
                label: user.name,       // "Super Admin" — shown in dropdown
                value: String(user.id), // "1" — sent to backend as ?performed_by=1
            }));
            setUserOptions(options);
        },
        (err) => {
            setUserOptions([]);
        },
    );
}, []); // ← empty array means runs ONCE when component first loads
```

**What it does:**
- Runs once when the page loads
- Hits `api/v1/default/users?is_all=true`
- API returns: `[{ id: 1, name: "Super Admin" }]`
- Transforms it into `[{ label: "Super Admin", value: "1" }]`
- Stores in `userOptions` state
- These options appear in the "Performed By" filter dropdown

**Why `Array.isArray(data) ? data : (data.results || [])`?**
The users API returns the array directly (not wrapped in `{results: []}`), so we check if it's already an array first.

---

#### useEffect #2 — Initial Data Fetch

```js
useEffect(() => {
    if (pipeline_instance_id) {
        fetchApprovalHistory({ page: 1, filters: resetFilterData });
    }
}, [pipeline_instance_id]); // ← runs when pipeline_instance_id changes
```

**What it does:**
- Runs when the page loads (and again if `pipeline_instance_id` changes)
- Calls `fetchApprovalHistory` with page 1 and no filters
- This loads the initial table data

**The `if (pipeline_instance_id)` check** — safety guard. If the ID isn't available yet from URL params, don't make the API call.

---

#### useEffect #3 — Read Filters from URL on Mount

```js
useEffect(() => {
    const urlSearchParams = new URLSearchParams(location.search);
    if (urlSearchParams.size === 0) return; // no filters in URL, do nothing

    const filtersFromUrl = { action: [], performed_by: [] };
    urlSearchParams.forEach((value, key) => {
        if (key in filtersFromUrl && value) {
            filtersFromUrl[key] = value.split(','); // "APPROVE,TRIGGER" → ["APPROVE","TRIGGER"]
        }
    });

    const isApplied = Object.values(filtersFromUrl).some(v => v.length > 0);
    setState(prev => ({
        ...prev,
        advFilters: filtersFromUrl,
        isFilterApplied: isApplied,
    }));
    fetchApprovalHistory({ page: 1, filters: filtersFromUrl });
}, []); // ← runs ONCE on mount
```

**What it does:**
- Runs once when the page loads
- Checks if the URL has filter params like `?action=APPROVE&performed_by=1`
- If yes, restores those filters in state AND re-fetches data with those filters

**Real example:**
```
User bookmarks: /approval-history?action=APPROVE&performed_by=1
User opens bookmark → page loads → this useEffect reads the URL
→ sets advFilters = { action: ["APPROVE"], performed_by: ["1"] }
→ fetches data with those filters applied
→ filter dropdowns show the pre-selected values
```

**Why two useEffects (#2 and #3) that both call `fetchApprovalHistory`?**

- **#2** always runs and loads fresh data (no filters)
- **#3** only runs if URL has filters, and overrides with filtered data

In most cases only #2 runs. #3 only matters when the user arrives with filters already in the URL (bookmarked link, browser back/forward).

---

### buildEndPoint

```js
const buildEndPoint = useCallback((page = 1, filters = resetFilterData) => {
    const base = GenerateURL(
        { pipeline_instance_id },
        properties.api.pipeline_actions,
    );
    // base = "http://127.0.0.1:8000/api/v1/pipeline/pipeline_instance/27/pipeline_actions/"

    const params = new URLSearchParams();

    if (page > 1) {
        params.append('limit', 10);
        params.append('offset', (page - 1) * 10);
        // page 2 → ?limit=10&offset=10
        // page 3 → ?limit=10&offset=20
    }

    if ((filters.action || []).length > 0) {
        params.append('action', filters.action.join(','));
        // ["APPROVE","TRIGGER"] → ?action=APPROVE,TRIGGER
    }

    if ((filters.performed_by || []).length > 0) {
        params.append('performed_by', filters.performed_by.join(','));
        // ["1"] → ?performed_by=1
    }

    const qs = params.toString();
    return qs ? `${base}?${qs}` : base;
    // With filters:    "http://.../pipeline_actions/?action=APPROVE&performed_by=1"
    // Without filters: "http://.../pipeline_actions/"
}, [pipeline_instance_id]);
```

**Think of it like building a search URL:**
- Start with the base address
- Add page number info if not page 1
- Add filter values as query params
- Return the complete URL

---

### fetchApprovalHistory

```js
const fetchApprovalHistory = useCallback(({ page = 1, filters } = {}) => {
    const activeFilters = filters || resetFilterData;
    setLoadingState({ listing: true }); // show skeleton rows

    const endPoint = buildEndPoint(page, activeFilters); // build the URL

    InvokeApi(
        { endPoint, httpMethod: 'GET', httpHeaders: { 'Content-Type': 'application/json' } },
        (data) => {
            // SUCCESS
            const results = data.results || [];
            setState(prev => ({
                ...prev,
                data: results,           // the 7 action records
                next: data.next || null,
                previous: data.previous || null,
                total_page: Math.ceil((data.count || 0) / 10) || 1,
                curr_page: page,
                count: data.count || 0,
                error: null,
            }));
            setLoadingState({ listing: false }); // hide skeleton
        },
        (error) => {
            // FAILURE
            setState(prev => ({ ...prev, error }));
            setLoadingState({ listing: false });
        },
    );
}, [buildEndPoint]);
```

**Flow:**
```
1. setLoadingState(true)  → skeleton rows appear in table
2. buildEndPoint()        → creates the full API URL with filters
3. InvokeApi()            → makes the GET request
4. Success callback       → stores results in state.data
5. setLoadingState(false) → skeleton disappears, real rows appear
```

**API Response structure:**
```json
{
    "count": 2,
    "next": null,
    "previous": null,
    "results": [
        {
            "id": 41,
            "action": "RE-RUN_AFTER_FAILURE",
            "message": "Re-Run Failed job with components [kk]",
            "created_at": "2026-05-21T15:31:18.685022+05:30",
            "pipeline": 8,
            "pipeline_instance": 27,
            "stage_instance": "lesasa",
            "task_instance": "job_1",
            "performed_by": "Super Admin"
        }
    ]
}
```

---

### Filter System

The filter system has 3 parts working together:

#### Part 1: `advanceFilterJson` — Filter Configuration

```js
const advanceFilterJson = useMemo(() => ({
    action: {
        staticList: ACTION_OPTIONS,  // the 6 action options — never fetched from API
        labelName: 'Action',
        uniqueId: 'action_adv_1',    // unique ID to identify this filter
        searchVariable: 'action',    // the URL param name: ?action=...
        getFetchUrl: null,           // null = don't fetch from API
        searchUrl: null,
        filterDataPraseFunction: null,
    },
    performed_by: {
        staticList: userOptions,     // [{label:"Super Admin", value:"1"}] from users API
        labelName: 'Performed By',
        uniqueId: 'performed_by_adv_2',
        searchVariable: 'performed_by',
        getFetchUrl: null,
        searchUrl: null,
        filterDataPraseFunction: null,
    },
}), [userOptions]); // ← re-runs when userOptions changes (after users API responds)
```

**Why `useMemo`?**
Without `useMemo`, this object is recreated on every render. With `useMemo`, it only recreates when `userOptions` actually changes — better performance.

**`staticList` behavior:**
- `ACTION_OPTIONS` (array) → filter shows these options immediately, no API call
- `userOptions` (starts as `[]`) → filter shows empty list first, then populates when users API responds
- `false` → filter tries to fetch from `getFetchUrl` (we avoid this by always passing an array)

---

#### Part 2: `moreFilterData` — Which Filters Are Available

```js
const moreFilterData = [
    { label: 'Action',       value: 'action' },
    { label: 'Performed By', value: 'performed_by' },
];
```

This is the list shown when you click "+ MORE". It lets users add/remove filter pills.

```js
// In state:
moreAdvFilterList: ['action', 'performed_by']
// This means both filters are visible by default
```

---

#### Part 3: How Filters Render

```jsx
{state.moreAdvFilterList?.map(filterName => (
    <AdvanceSearchFilterCombo
        staticList={advanceFilterJson[filterName]?.staticList}
        uniqueId={advanceFilterJson[filterName]?.uniqueId}
        labelName={advanceFilterJson[filterName]?.labelName}
        selectedCheckBoxes={state.advFilters[filterName]}  // currently selected values
        onUpdate={onUpdateHandle}  // called when user selects/deselects
        reset={state.resetCount}   // when this number changes, filter resets itself
        ...
    />
))}
```

`state.moreAdvFilterList` is `['action', 'performed_by']`, so it renders two `AdvanceSearchFilterCombo` components — one for Action, one for Performed By.

---

### addFiltersToUrl

```js
const addFiltersToUrl = (filterName, filterValue) => {
    let urlSearchParams = new URLSearchParams(location.search);
    // Gets current URL params, e.g. "?action=APPROVE"

    if (filterName === 'all_delete') {
        urlSearchParams = new URLSearchParams(); // clear ALL params
    } else {
        if (!filterValue || filterValue.length === 0) {
            urlSearchParams.delete(filterName); // remove this param
        } else {
            if (urlSearchParams.has(filterName)) {
                urlSearchParams.set(filterName, filterValue.join(','));  // update existing
            } else {
                urlSearchParams.append(filterName, filterValue.join(',')); // add new
            }
        }
    }

    navigate({
        pathname: location.pathname,  // keep same path
        search: urlSearchParams.toString(), // update query string
    });
};
```

**Examples:**

| Action | URL Before | URL After |
|--------|-----------|-----------|
| Select APPROVE | `/approval-history` | `/approval-history?action=APPROVE` |
| Also select TRIGGER | `/approval-history?action=APPROVE` | `/approval-history?action=APPROVE,TRIGGER` |
| Deselect all | `/approval-history?action=APPROVE,TRIGGER` | `/approval-history` |
| Select performed_by=1 | `/approval-history?action=APPROVE` | `/approval-history?action=APPROVE&performed_by=1` |
| Clear all filters | `/approval-history?action=APPROVE&performed_by=1` | `/approval-history` |

---

### onUpdateHandle

```js
const onUpdateHandle = (uniqueId, updatedList) => {
    // Called by AdvanceSearchFilterCombo whenever user clicks a checkbox

    if (uniqueId === 'more-button-adv-0') {
        // User clicked the "+ MORE" button
        if (!updatedList?.length) resetAdvFilter();
        else setState(prev => ({ ...prev, moreAdvFilterList: updatedList }));
        return;
    }

    // Map uniqueId back to filter key
    const keyMap = { action_adv_1: 'action', performed_by_adv_2: 'performed_by' };
    const key = keyMap[uniqueId] || '';
    if (!key) return;

    // Normalize: AdvanceSearchFilterCombo sometimes returns [{label, value}] objects
    // We only want the value strings: ["APPROVE", "TRIGGER"]
    const normalizedList = updatedList.map(item =>
        typeof item === 'object' ? item.value : item
    );

    const newFilters = { ...state.advFilters, [key]: normalizedList };
    const isApplied = Object.values(newFilters).some(v => Array.isArray(v) && v.length > 0);

    setState(prev => ({ ...prev, advFilters: newFilters, isFilterApplied: isApplied }));
    addFiltersToUrl(key, normalizedList);  // update URL
    fetchApprovalHistory({ page: 1, filters: newFilters }); // re-fetch data
};
```

**Step by step — user selects "Approve Stage" and "Trigger Pipeline":**

```
1. User clicks "Approve Stage" checkbox
   → AdvanceSearchFilterCombo calls onUpdateHandle("action_adv_1", [{label:"Approve Stage", value:"APPROVE"}])

2. keyMap["action_adv_1"] = "action"

3. normalizedList = ["APPROVE"]  (extracted .value from object)

4. newFilters = { action: ["APPROVE"], performed_by: [] }

5. setState → advFilters updated, isFilterApplied = true

6. addFiltersToUrl("action", ["APPROVE"]) → URL becomes ?action=APPROVE

7. fetchApprovalHistory({ filters: { action: ["APPROVE"], performed_by: [] } })
   → buildEndPoint adds ?action=APPROVE to API URL
   → API called → only APPROVE records returned
   → table updates

8. User also clicks "Trigger Pipeline"
   → onUpdateHandle("action_adv_1", [{label:"Approve Stage", value:"APPROVE"}, {label:"Trigger Pipeline", value:"TRIGGER"}])

9. normalizedList = ["APPROVE", "TRIGGER"]

10. addFiltersToUrl → URL becomes ?action=APPROVE,TRIGGER

11. fetchApprovalHistory → API called with ?action=APPROVE,TRIGGER
    → both APPROVE and TRIGGER records returned
```

---

### resetAdvFilter

```js
const resetAdvFilter = () => {
    addFiltersToUrl('all_delete');   // clear URL params
    setState(prev => ({
        ...prev,
        moreAdvFilterList: defaultFilters,  // show both filters again
        advFilters: { action: [], performed_by: [] }, // clear selections
        resetCount: prev.resetCount + 1,    // tell filter components to visually reset
        isFilterApplied: false,
    }));
    fetchApprovalHistory({ page: 1, filters: resetFilterData }); // fetch all data
};
```

**`resetCount` is clever:** The `AdvanceSearchFilterCombo` component watches the `reset` prop. When the number changes, it clears its internal checkbox state. Without this, the checkboxes would still look checked even after the filter is cleared.

---

## ApprovalHistoryTableRow.js — Deep Dive

### Field Mapping

The API returns this shape per record:
```json
{
    "id": 41,
    "action": "RE-RUN_AFTER_FAILURE",
    "message": "Re-Run Failed job with components [kk]",
    "created_at": "2026-05-21T15:31:18.685022+05:30",
    "pipeline": 8,
    "pipeline_instance": 27,
    "stage_instance": "lesasa",   ← stage name string or null
    "task_instance": "job_1",     ← job code string or null
    "performed_by": "Super Admin" ← name string (used to be an ID)
}
```

The row component maps each field to a table column:

```js
const approverDisplay = row?.performed_by || 'N/A';
// "Super Admin" → shown in Performed By column

const action = row?.action || null;
// "RE-RUN_AFTER_FAILURE" → looked up in ACTION_CONFIG for chip display

const message = row?.message || '';
// "Re-Run Failed job..." → shown truncated with eye icon to expand

// Stage/Job logic:
const isJobLevel   = row?.task_instance != null;   // "job_1" exists → job level
const isStageLevel = !isJobLevel && row?.stage_instance != null; // no job but stage exists

// Result:
// task_instance = "job_1", stage_instance = "lesasa" → isJobLevel = true → show "job_1" with briefcase icon
// task_instance = null,    stage_instance = "lesasa" → isStageLevel = true → show "lesasa" with flag icon
// task_instance = null,    stage_instance = null     → pipeline level → show nothing with merge icon
```

---

### ACTION_CONFIG

```js
const ACTION_CONFIG = {
    'APPROVE':               { label: 'Approve Stage',         chipClass: 'chip chip-green-new' },
    'EXCEPTIONAL_APPROVAL':  { label: 'Exceptional Approval',  chipClass: 'chip chip-yellow' },
    'RE-RUN_AFTER_FAILURE':  { label: 'Re-Run After Failure',  chipClass: 'chip chip-blue-new' },
    'CONTINUE_WITH_FAILURE': { label: 'Continue With Failure', chipClass: 'chip chip-warning-light' },
    'TRIGGER':               { label: 'Trigger Pipeline',      chipClass: 'chip chip-info' },
    'REVOKE':                { label: 'Revoke Pipeline',       chipClass: 'chip chip-error-light' },
};
```

This maps raw backend values to human-readable labels and CSS classes for color-coded chips.

```js
const getActionCfg = (action) =>
    ACTION_CONFIG[action] || { label: action || '—', chipClass: 'chip chip-dark-grey' };
// Fallback: unknown actions show in grey with raw value
```

---

### MessageDialog

A popup that shows the full message when user clicks the eye icon.

```js
const MessageDialog = ({ open, onClose, row }) => {
    const approverDisplay = row?.performed_by_detail?.name
        || (row?.performed_by != null ? `User #${row.performed_by}` : 'N/A');
    // Still has old logic here — should be updated to: row?.performed_by || 'N/A'

    const message = row?.message || '';
    // Full message text shown in dialog

    const action = row?.action || null;
    // Action chip shown in dialog header
```

Opens as a `<Dialog>` (MUI modal) with:
- Header: "Message by Super Admin" + action chip
- Body: full message text in a quote box + timestamp
- Footer: Close button

---

### Table Row Rendering

The row renders 5 columns:

| Column | Data Source | Display |
|--------|------------|---------|
| Performed By | `row.performed_by` | Avatar circle + name |
| Action | `row.action` → `ACTION_CONFIG` | Colored chip |
| Message | `row.message` | Truncated text + eye icon |
| Stage / Job | `row.stage_instance` or `row.task_instance` | Icon + name |
| Time | `row.created_at` | Formatted datetime |

---

## Complete Data Flow

```
Page loads
    │
    ├── useEffect #1 (users)
    │       ↓
    │   GET /api/v1/default/users?is_all=true
    │       ↓
    │   userOptions = [{label:"Super Admin", value:"1"}]
    │       ↓
    │   "Performed By" filter now has options
    │
    ├── useEffect #2 (initial data)
    │       ↓
    │   fetchApprovalHistory({ page:1, filters:{action:[],performed_by:[]} })
    │       ↓
    │   buildEndPoint() → "http://.../pipeline_actions/"
    │       ↓
    │   InvokeApi → GET request
    │       ↓
    │   state.data = [7 records]
    │       ↓
    │   Table renders 7 rows
    │
    └── useEffect #3 (URL filters)
            ↓
        Check URL for ?action=...&performed_by=...
            ↓
        If found → restore filters → re-fetch with filters


User selects filter
    │
    ├── onUpdateHandle called
    │       ↓
    │   normalizedList = extracted values
    │       ↓
    │   setState (update advFilters)
    │       ↓
    │   addFiltersToUrl → URL updated
    │       ↓
    │   fetchApprovalHistory → API called with filter params
    │       ↓
    │   Table updates with filtered results
```

---

## URL Filter Flow

```
Initial:
URL: /approval-history
advFilters: { action: [], performed_by: [] }
API: GET /pipeline_actions/

After selecting Action = APPROVE:
URL: /approval-history?action=APPROVE
advFilters: { action: ["APPROVE"], performed_by: [] }
API: GET /pipeline_actions/?action=APPROVE

After also selecting performed_by = Super Admin:
URL: /approval-history?action=APPROVE&performed_by=1
advFilters: { action: ["APPROVE"], performed_by: ["1"] }
API: GET /pipeline_actions/?action=APPROVE&performed_by=1

After adding TRIGGER to action filter:
URL: /approval-history?action=APPROVE,TRIGGER&performed_by=1
advFilters: { action: ["APPROVE","TRIGGER"], performed_by: ["1"] }
API: GET /pipeline_actions/?action=APPROVE,TRIGGER&performed_by=1

After clicking CLEAR FILTERS:
URL: /approval-history
advFilters: { action: [], performed_by: [] }
API: GET /pipeline_actions/
```

---

## Real World Example

Scenario: Your manager asks you to show only "Approve Stage" actions done by user ID 1.

**What happens step by step:**

1. User opens the Approval History page for pipeline execution 27
2. Page loads → `useEffect #2` fetches all 7 records → table shows all
3. Simultaneously `useEffect #1` fetches users → "Performed By" dropdown gets "Super Admin" option
4. User opens the "Action" filter dropdown → sees 6 options
5. User clicks "Approve Stage" checkbox
6. `AdvanceSearchFilterCombo` calls `onUpdateHandle("action_adv_1", [{label:"Approve Stage", value:"APPROVE"}])`
7. `normalizedList = ["APPROVE"]`
8. `addFiltersToUrl("action", ["APPROVE"])` → URL becomes `?action=APPROVE`
9. `fetchApprovalHistory({ filters: { action:["APPROVE"], performed_by:[] } })`
10. API called: `GET /pipeline_actions/?action=APPROVE`
11. Table now shows only APPROVE records
12. User also opens "Performed By" filter → sees "Super Admin"
13. User selects "Super Admin"
14. `normalizedList = ["1"]` (the id value)
15. URL becomes `?action=APPROVE&performed_by=1`
16. API called: `GET /pipeline_actions/?action=APPROVE&performed_by=1`
17. Table shows only records where action=APPROVE AND performed_by=1

---

## Common Questions

**Q: Why are there two API calls on page load?**
A: One for users (`/api/v1/default/users`) to populate the "Performed By" filter, and one for the actual data (`/pipeline_actions/`). They happen simultaneously.

**Q: Why does the filter URL update even before clicking Apply?**
A: There's no Apply button — filters apply immediately when you click a checkbox. This is the same pattern used across the whole project (see `VmGroupsListing`).

**Q: What happens if I share the URL with filters to someone else?**
A: `useEffect #3` reads the URL params on load and restores the exact same filter state — the other person sees the same filtered view.

**Q: Why `useMemo` for `advanceFilterJson`?**
A: Because it depends on `userOptions` which changes after the users API responds. `useMemo` ensures the filter config updates when `userOptions` changes, but doesn't re-create unnecessarily on every render.

**Q: What is `resetCount` for?**
A: `AdvanceSearchFilterCombo` internally tracks checked state. Just clearing `advFilters` in state doesn't make the checkboxes visually uncheck. Incrementing `resetCount` (passed as `reset` prop) tells the component to clear its internal visual state too.

**Q: Why `performed_by` sends the user ID (`"1"`) but the table shows the name (`"Super Admin"`)?**
A: The filter sends ID to the backend (`value: String(user.id)`), and the backend filters by ID. But the API response returns `performed_by: "Super Admin"` (the name), which the table row displays directly as `row.performed_by`.
