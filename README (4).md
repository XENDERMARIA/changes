# CommonListingPage — Deep Technical Reference

## Table of Contents

1. [What is this component?](#1-what-is-this-component)
2. [Two modes of operation](#2-two-modes-of-operation)
3. [Imports — what each one does](#3-imports--what-each-one-does)
4. [Props — every single one explained](#4-props--every-single-one-explained)
5. [Internal state — what it holds and why](#5-internal-state--what-it-holds-and-why)
6. [useRef — lastSearchRef explained](#6-useref--lastsearchref-explained)
7. [isSelfManaged — the mode switch](#7-isselfmanaged--the-mode-switch)
8. [invokeApi — the core API caller](#8-invokeapi--the-core-api-caller)
9. [buildBaseUrl — building the endpoint](#9-buildbaseurl--building-the-endpoint)
10. [fetchData — initial load and search](#10-fetchdata--initial-load-and-search)
11. [fetchNext, fetchPrev, fetchPage — pagination](#11-fetchnext-fetchprev-fetchpage--pagination)
12. [useEffect — auto fetch on mount](#12-useeffect--auto-fetch-on-mount)
13. [Resolve block — picking the right values](#13-resolve-block--picking-the-right-values)
14. [JSX — the five sections](#14-jsx--the-five-sections)
15. [AdvanceFilterBar — sub-component deep dive](#15-advancefilterbar--sub-component-deep-dive)
16. [children as a function pattern](#16-children-as-a-function-pattern)
17. [PropTypes and defaultProps](#17-proptypes-and-defaultprops)
18. [Full usage examples](#18-full-usage-examples)
19. [Common mistakes and how to avoid them](#19-common-mistakes-and-how-to-avoid-them)

---

## 1. What is this component?

`CommonListingPage` is a **reusable shell component** that handles everything that every listing page in the app shares:

- Page header with buttons
- Search bar and/or advance filters
- Loading skeleton while data is fetching
- Error page if the API fails
- The actual data content
- Empty state if there is no data
- Pagination controls

Without this component, every listing page (`VmGroupsListing`, `JobTemplatesListing`, `CanaryTemplatesListing`, etc.) was copy-pasting the same 200+ lines. This component puts all of that in one place.

**What it does NOT do:**
- It does not decide how your rows/cards look — that is your job via `children`
- It does not manage your dialog open/close state
- It does not handle advanced filter state (which filters are active, what values are selected) — that stays in your feature component

---

## 2. Two modes of operation

### MODE 1 — Self-managed (`endpoint` prop)

You pass an `endpoint` and the component handles **everything** — fetching, loading state, error state, pagination, and re-fetching on search/filter.

```jsx
<CommonListingPage
    heading="VM Groups"
    endpoint={properties.api.create_vm_group}
    pageSize={10}
>
    {(data) => data.map(item => <Row key={item.id} item={item} />)}
</CommonListingPage>
```

Use this when:
- You don't need complex custom fetch logic
- The API follows the standard `{ results, count, next, previous }` shape
- You want the least amount of code in your feature component

### MODE 2 — External state (flat props)

You manage your own state as you always did, and just pass `state.x` as props.

```jsx
<CommonListingPage
    heading="VM Groups"
    loading={state.loading}
    error={state.error}
    data={state.all_templates}
    count={state.count}
    total_page={state.total_page}
    curr_page={state.curr_page}
    next={state.next}
    previous={state.previous}
    on_next_click={() => fetchNextInfo(null, state.next)}
    on_previous_click={() => fetchPrevInfo(null, state.previous)}
    on_pageNumber_click={(page) => fetchPageInfo(page)}
    onRefresh={fetchAllVMGroups}
>
    {state.all_templates.map(item => <Row key={item.id} item={item} />)}
</CommonListingPage>
```

Use this when:
- You have complex fetch logic (URL sync, permission checks, transformations)
- You need access to `data` outside this component (e.g. for a dialog)
- You already have working fetch functions you don't want to rewrite

---

## 3. Imports — what each one does

```js
import React, { useState, useEffect, useCallback, useRef } from "react";
```
- `useState` — holds internal state in MODE 1
- `useEffect` — triggers the initial fetch in MODE 1
- `useCallback` — memoizes fetch functions so they don't get recreated on every render
- `useRef` — stores last search params without causing re-renders

```js
import GenerateURL, { GenerateSearchURL } from "../../../util/APIUrlProvider";
```
- `GenerateURL(params, template)` — replaces path params in a URL template.
  Example: `GenerateURL({ application_id: 5 }, '/app/:application_id/templates')` → `'/app/5/templates'`
- `GenerateSearchURL(searchObj, baseUrl)` — appends query params.
  Example: `GenerateSearchURL({ name: 'foo' }, '/api/templates/')` → `'/api/templates/?name=foo'`

```js
import InvokeApi from "../../../util/apiInvoker";
```
The app's standard API caller. Takes a request config object, a success callback, and a failure callback. Internally handles auth headers, base URL, etc.

```js
import properties from "../../../properties/properties";
```
Used to access `properties.api.baseURL` — the API server base URL which is prepended to `next`/`previous` cursor URLs returned by the API.

---

## 4. Props — every single one explained

### Header props

| Prop | Type | What it does |
|------|------|--------------|
| `heading` | `string` **required** | The page title shown in the header |
| `subHeading` | `string \| node` | Smaller text below the heading. Can be a string or JSX (e.g. a link) |
| `icon` | `string` | Remixicon class name e.g. `"ri-cpu-line"`. Shown as an icon avatar |
| `imgIcon` | `string` | Image path — alternative to `icon` when you want an image instead |
| `backgroundColor` | `string` | Background colour of the icon avatar box e.g. `"#0086FF14"` |
| `primaryButton` | `object` | The main action button — e.g. "Add VM Group". See button shape below |
| `secondaryButton` | `object` | Second button in the header — e.g. "Export" |
| `tertiaryButton` | `object` | Third button — e.g. "Filters" |

**Button object shape:**
```js
{
    actionType: 'link',         // 'link' = <Link to={action}>, 'open-dialog' = onClick handler
    action: '/vm-group/add',    // route path (if link) OR function (if open-dialog)
    text: 'Add VM Group',       // button label
    icon: <span className="ri-add-line" />,  // optional icon node before text
    buttonClass: 'btn-primary', // CSS class
    isPermitted: true,          // false = disabled button + tooltip
}
```

---

### Toolbar props

| Prop | Type | What it does |
|------|------|--------------|
| `searchBar` | `object` | Pass this to show a search bar. Omit to hide it. |
| `advanceFilter` | `object` | Pass this to show advance filter chips. Omit to hide them. |

**`searchBar` object shape:**
```js
{
    searchData: { name: '' },                      // controlled value — your state
    defaultFilter: { name: 'name', label: 'Search...' }, // field name + placeholder
    onSearch: (value) => fetchData({ name: value }),     // called on Enter / keystroke
    onClear: () => fetchData(),                    // called when × is clicked
    realTime: false,       // true = fires onSearch on every single keystroke
    width: '37%',          // CSS width of the search bar wrapper div
    backgroundColor: '#fff',
    border: '1px solid #e6e6e6',
}
```

**`advanceFilter` object shape:**
```js
{
    filters: ['virtual_group_name', 'env'],  // which filter chips are currently active
    advanceFilterJson: {                     // config for each possible filter
        virtual_group_name: {
            uniqueId: 'name_adv_1',          // MUST be unique on the page
            labelName: 'Name',               // text shown on the chip button
            searchVariable: 'virtual_group_name', // query param key
            staticList: false,               // true = pass a static array, false = fetch from API
            getFetchUrl: properties.api.create_vm_group,
            searchUrl: properties.api.create_vm_group,
            filterDataPraseFunction: (data) => // transform API response into { label, value } array
                data?.map(i => ({ label: i.virtual_group_name, value: i.virtual_group_name })),
        },
    },
    moreFilterData: [            // static list for the "+ More" button. Omit to hide the button.
        { label: 'Name', value: 'virtual_group_name' },
    ],
    advFilters: { virtual_group_name: [] },  // current selected values per filter key
    resetCount: 0,               // increment this number to reset all checkboxes
    onUpdate: onUpdateHandle,    // called when any filter checkbox changes
    onReset: resetAdvFilter,     // called when Reset is clicked
    showReset: true,             // false to hide the Reset link
}
```

---

### MODE 1 props (self-managed fetch)

| Prop | Type | What it does |
|------|------|--------------|
| `endpoint` | `string` | The API endpoint template. Presence of this activates MODE 1. |
| `urlParams` | `object` | Path params for the endpoint e.g. `{ application_id: 5 }`. Default `{}` |
| `pageSize` | `number` | Items per page for offset calculation. Default `10` |

---

### MODE 2 props (external state)

These are only read when `endpoint` is NOT provided (MODE 2).

| Prop | Type | What it does |
|------|------|--------------|
| `loading` | `bool` | Pass `state.loading`. Shows skeleton while true. |
| `error` | `any` | Pass `state.error`. Shows PageError when truthy. |
| `statusCode` | `any` | Pass `state.statusCode`. Forwarded to PageError. |
| `data` | `array` | Pass `state.all_templates` or `state.data`. Shows content when length > 0. |
| `count` | `number` | Total items across all pages. Shown in pagination. |
| `total_page` | `number` | Total number of pages. `Math.ceil(count / 10)` |
| `curr_page` | `number` | Currently active page number (1-indexed) |
| `next` | `string \| null` | Next page cursor URL from API response |
| `previous` | `string \| null` | Previous page cursor URL from API response |
| `on_next_click` | `func` | Called when next arrow is clicked |
| `on_previous_click` | `func` | Called when prev arrow is clicked |
| `on_pageNumber_click` | `func` | Called with page number when a page number is clicked or entered |
| `onRefresh` | `func` | Called when error page Refresh button is clicked, also default for search clear |

---

### Display props

| Prop | Type | What it does |
|------|------|--------------|
| `skeleton` | `object` | Config for the loading skeleton. `{ count, height, variant, style, rootStyle }` |
| `emptyState` | `object` | Config for the blank/empty state. See shape below. |
| `errorVariant` | `string \| null` | `"REFRESH"` = show Refresh button, `"CLOSE"` = show Close button, `null` = show Go Home link |
| `children` | `node \| func` | Your table/cards content. Can be JSX or a function `(data) => JSX` |

**`skeleton` object shape:**
```js
{
    count: 4,             // how many skeleton rows to show
    height: '60px',       // height of each row
    variant: 'rect',      // 'rect' | 'text' | 'circle'
    style: { borderRadius: '8px' },  // extra style on each skeleton item
    rootStyle: { gap: '16px' },      // extra style on the wrapper div
}
```

**`emptyState` object shape:**
```js
{
    text: 'No VM groups added',
    subHeading: "Click 'Add VM Group' to get started",
    icon: <span className="ri-cpu-line font-28 color-key-gray" />,
    backgroundColor: '#F4F4F4',
    primaryButton: {           // same shape as header buttons
        actionType: 'open-dialog',
        action: () => setOpen(true),
        text: 'Add VM Group',
        buttonClass: 'btn-primary',
    },
    additionalComponent: null,     // replaces primaryButton when provided
    variant: 'default',            // 'default' = full height, 'inside-table' = compact
    additionalStyles: {},          // extra styles on the BlankPage wrapper
    type: null,                    // 'deployment-freeze' changes height behaviour
}
```

---

## 5. Internal state — what it holds and why

```js
const [state, setState] = useState({
    loading: false,
    error: null,
    statusCode: null,
    data: [],
    count: 0,
    total_page: 0,
    curr_page: 1,
    next: null,
    previous: null,
});
```

This state is **only used in MODE 1** (when `endpoint` is provided). In MODE 2 it exists in memory but is never read — all values are taken from external props instead.

Each field:
- `loading` — true while `InvokeApi` is in flight. Triggers the skeleton.
- `error` — the error object/string from a failed API call. Triggers PageError.
- `statusCode` — HTTP status code from a failed call (403, 500 etc.). Shown in PageError.
- `data` — the `results` array from the API response. Your table maps over this.
- `count` — total number of items across all pages from `response.count`.
- `total_page` — `Math.ceil(count / pageSize)`. How many pages exist. Used by PaginationTwo.
- `curr_page` — which page the user is currently on. Used by PaginationTwo to highlight the active page.
- `next` — cursor URL for the next page (from `response.next`). PaginationTwo uses this.
- `previous` — cursor URL for the previous page (from `response.previous`).

---

## 6. useRef — lastSearchRef explained

```js
const lastSearchRef = useRef(null);
```

**Problem it solves:** When a user searches for "foo" and then clicks page 3, the page-jump fetch needs to include `?name=foo` in the URL. But `fetchPage` doesn't receive the search params — it only knows the page number.

**How `useRef` solves it:**
- `useRef` creates a mutable container that persists across renders
- Unlike `useState`, updating a ref does NOT cause a re-render
- Every time `fetchData` is called with search params, it stores them: `lastSearchRef.current = searchParams`
- When `fetchPage` runs, it reads `lastSearchRef.current` and re-applies those params to the URL

```js
// In fetchData — save the params
const fetchData = useCallback((searchParams = null) => {
    lastSearchRef.current = searchParams;   // ← save here
    ...
});

// In fetchPage — re-apply the saved params
const fetchPage = useCallback((pageNumber) => {
    let url = buildBaseUrl();
    if (lastSearchRef.current && Object.keys(lastSearchRef.current).length > 0)
        url = GenerateSearchURL(lastSearchRef.current, url);  // ← re-apply here
    ...
});
```

If the user clears search and calls `fetchData(null)`, `lastSearchRef.current` becomes `null` and page jumps will not include any search params — correct behaviour.

---

## 7. isSelfManaged — the mode switch

```js
const isSelfManaged = Boolean(endpoint);
```

This is the single decision point that separates MODE 1 from MODE 2.

- If `endpoint` is passed → `isSelfManaged = true` → use internal `state.*`
- If `endpoint` is not passed → `isSelfManaged = false` → use external props

`Boolean(endpoint)` converts the string to `true`, and `Boolean(null)` (the default) to `false`.

This value is used in two places:
1. The resolve block (deciding which values to use)
2. Passing `fetchData` to `AdvanceFilterBar` and `SearchBar` (only needed in MODE 1)

---

## 8. invokeApi — the core API caller

```js
const invokeApi = useCallback((url, pageNumber) => {
    setState(prev => ({ ...prev, loading: true, error: null }));

    InvokeApi(
        { endPoint: url, httpMethod: "GET", httpHeaders: { "Content-Type": "application/json" } },
        (res) => {
            setState({
                loading: false,
                error: null,
                statusCode: null,
                data: res.results ?? [],
                count: res.count ?? 0,
                next: res.next ? properties.api.baseURL + res.next : null,
                previous: res.previous ? properties.api.baseURL + res.previous : null,
                total_page: Math.ceil((res.count ?? 0) / limit),
                curr_page: pageNumber,
            });
        },
        (error, statusCode) => {
            setState(prev => ({ ...prev, loading: false, error, statusCode }));
        }
    );
}, [limit]);
```

**What it does step by step:**

1. Immediately sets `loading: true` and clears any previous error.

2. Calls `InvokeApi` with the URL and a GET request.

3. **On success** — sets the full state in one `setState` call:
   - `data: res.results ?? []` — the `??` means "if null/undefined, use empty array". Prevents crashes if the API returns no results key.
   - `next: res.next ? properties.api.baseURL + res.next : null` — the API returns a relative URL like `/api/vm-groups/?limit=10&offset=10`. We prepend the base URL to make it absolute so `InvokeApi` can call it directly.
   - `total_page: Math.ceil((res.count ?? 0) / limit)` — calculates total pages. `Math.ceil` ensures we round up (9 items / 10 per page = 1 page, not 0.9).
   - `curr_page: pageNumber` — the page number is passed in from the caller (fetchNext, fetchPrev, fetchPage) so we know which page we landed on.

4. **On failure** — sets `error` and `statusCode` while turning off loading.

**Why `useCallback`?**
Without it, `invokeApi` would be a new function reference on every render. Since `fetchData`, `fetchNext`, `fetchPrev`, and `fetchPage` all depend on `invokeApi`, they would also be recreated every render, causing potential infinite loops in `useEffect`. `useCallback` with `[limit]` means invokeApi only changes if `pageSize` prop changes.

---

## 9. buildBaseUrl — building the endpoint

```js
const buildBaseUrl = useCallback(() => {
    return GenerateURL(urlParams ?? {}, endpoint);
}, [endpoint, urlParams]);
```

`GenerateURL` replaces placeholder tokens in the endpoint string with real values from `urlParams`.

Example:
```js
// endpoint = '/api/app/:application_id/job-templates/'
// urlParams = { application_id: 42 }
// result   = '/api/app/42/job-templates/'
GenerateURL({ application_id: 42 }, '/api/app/:application_id/job-templates/')
```

For endpoints with no path params (like VM Groups):
```js
GenerateURL({}, '/api/vm-groups/')  →  '/api/vm-groups/'
```

`urlParams ?? {}` protects against cases where `urlParams` prop is not passed — `GenerateURL` might crash if it receives `undefined` instead of an object.

---

## 10. fetchData — initial load and search

```js
const fetchData = useCallback((searchParams = null) => {
    lastSearchRef.current = searchParams;
    let url = buildBaseUrl();
    if (searchParams) url = GenerateSearchURL(searchParams, url);
    invokeApi(url, 1);
}, [buildBaseUrl, invokeApi]);
```

`fetchData` is the primary fetch function. It is called in three situations:

**1. Initial page load** (via `useEffect`):
```js
fetchData()          // searchParams = null, loads page 1 with no filters
```

**2. When user searches:**
```js
fetchData({ name: 'foo' })
// → lastSearchRef.current = { name: 'foo' }
// → url = '/api/vm-groups/?name=foo'
// → invokes, sets curr_page = 1
```

**3. When search is cleared:**
```js
fetchData(null)
// → lastSearchRef.current = null
// → url = '/api/vm-groups/'
// → loads fresh unfiltered page 1
```

`= null` as the default parameter means calling `fetchData()` with nothing is the same as `fetchData(null)` — both load page 1 with no filters.

Always resets to page 1 (`invokeApi(url, 1)`) because searching or changing filters should always bring you back to the first page of results.

---

## 11. fetchNext, fetchPrev, fetchPage — pagination

### fetchNext

```js
const fetchNext = useCallback(() => {
    if (!state.next) return;
    invokeApi(state.next, state.curr_page + 1);
}, [state.next, state.curr_page, invokeApi]);
```

Uses the cursor URL stored in `state.next` (which came from `response.next`). If `state.next` is null (last page), does nothing. Increments `curr_page` by 1 so pagination highlights the correct page.

### fetchPrev

```js
const fetchPrev = useCallback(() => {
    if (!state.previous) return;
    invokeApi(state.previous, state.curr_page - 1);
}, [state.previous, state.curr_page, invokeApi]);
```

Same idea but backwards. Uses `state.previous` cursor URL. Decrements `curr_page` by 1.

### fetchPage

```js
const fetchPage = useCallback((pageNumber) => {
    if (pageNumber < 1 || pageNumber > state.total_page) return;
    let url = buildBaseUrl();
    if (lastSearchRef.current && Object.keys(lastSearchRef.current).length > 0)
        url = GenerateSearchURL(lastSearchRef.current, url);
    const sep = url.includes("?") ? "&" : "?";
    if (pageNumber > 1) url += `${sep}limit=${limit}&offset=${(pageNumber - 1) * limit}`;
    invokeApi(url, pageNumber);
}, [buildBaseUrl, invokeApi, limit, state.total_page]);
```

Called when user types a page number or clicks a specific page chip. More complex because it builds the URL from scratch:

1. Validates the page number is in range. If not, returns early.
2. Builds the base URL fresh (doesn't use cursor URLs).
3. Re-applies last search params if any exist.
4. Appends pagination offset: `?limit=10&offset=20` for page 3.
   - `sep` — checks if URL already has `?` (from search params). If yes, uses `&`. If no, uses `?`. This prevents double `?` in the URL.
   - `offset = (pageNumber - 1) * limit` — page 1 = offset 0 (no query appended), page 2 = offset 10, page 3 = offset 20, etc.

---

## 12. useEffect — auto fetch on mount

```js
useEffect(() => {
    if (isSelfManaged) fetchData();
}, [endpoint]);
```

Runs once when the component mounts. Only calls `fetchData()` if we are in self-managed mode.

The dependency array is `[endpoint]` — this means if the `endpoint` prop ever changes (rare, but possible in tabbed UIs), the component will re-fetch for the new endpoint automatically.

**Why not `[]`?** — Using `[]` would work for initial load but would miss endpoint changes. `[endpoint]` covers both.

**Why not include `fetchData` in the dependency array?** — `fetchData` is a `useCallback` that itself depends on `buildBaseUrl` and `invokeApi`. Adding it would cause infinite loops in some cases. The intent is clear: re-fetch when the endpoint changes.

---

## 13. Resolve block — picking the right values

```js
const loading    = isSelfManaged ? state.loading    : loadingProp;
const error      = isSelfManaged ? state.error      : errorProp;
// ... etc
const handleNext     = isSelfManaged ? fetchNext  : on_next_click;
const handlePrev     = isSelfManaged ? fetchPrev  : on_previous_click;
const handlePage     = isSelfManaged ? fetchPage  : on_pageNumber_click;
const handleRefresh  = isSelfManaged ? fetchData  : onRefresh;
```

This block creates a unified set of variables that the JSX always uses — regardless of which mode is active.

- In MODE 1: everything comes from `state.*` (internal) and `fetch*` functions
- In MODE 2: everything comes from the props you passed (`loadingProp`, `on_next_click`, etc.)

The JSX below this block just uses `loading`, `data`, `handleNext` etc. — it never needs to know which mode it's in. This is the key design decision that makes both modes possible with one render function.

---

## 14. JSX — the five sections

### Section 1 — PageHeader

```jsx
<PageHeader
    heading={heading}
    subHeading={subHeading}
    icon={icon}
    ...
    primaryButton={primaryButton}
    secondaryButton={secondaryButton}
    tertiaryButton={tertiaryButton}
    commonDivMargin={false}
/>
```

`commonDivMargin={false}` prevents the header from adding a bottom margin — the toolbar or content below manages its own spacing.

### Section 2 — Toolbar

```jsx
{(searchBar || advanceFilter) && (
    <div ...>
        {searchBar && <SearchBar ... />}
        {advanceFilter && <AdvanceFilterBar config={advanceFilter} fetchData={isSelfManaged ? fetchData : null} />}
    </div>
)}
```

The outer condition `(searchBar || advanceFilter)` means the toolbar div only renders if at least one of them is provided. The inner conditions are separate — you can have just search, just advance filters, or both.

`fetchData={isSelfManaged ? fetchData : null}` — passes the internal fetch function to `AdvanceFilterBar` only in MODE 1. In MODE 2, `null` is passed and your own `onUpdate` in the config handles fetching.

### Section 3 — Body (priority chain)

```jsx
{loading ? (
    <GenericSkeleton ... />
) : error ? (
    <PageError ... />
) : data?.length > 0 ? (
    <>{children} <PaginationTwo ... /></>
) : (
    <BlankPage ... />
)}
```

This is a **priority chain** — React evaluates left to right, top to bottom, and renders the first truthy condition:

1. `loading` is checked first — even if there's an error, we show skeleton while loading
2. `error` is checked second — if not loading but errored, show error page
3. `data?.length > 0` — if not loading and no error and data exists, show content
4. Final `else` — not loading, no error, no data → show empty state

`data?.length` uses optional chaining — if `data` is somehow undefined (shouldn't happen with defaultProps but safe practice), `undefined > 0` is `false` rather than throwing.

### Section 4 — Pagination (inside the data branch)

```jsx
<PaginationTwo
    current_page_count={curr_page}
    total_count={total_page}
    count={count}
    next={next}
    previous={previous}
    on_previous_click={handlePrev}
    on_next_click={handleNext}
    on_pageNumber_click={handlePage}
/>
```

Only renders when `data?.length > 0`. Receives the resolved handlers — which are either the internal fetch functions (MODE 1) or your external callbacks (MODE 2).

---

## 15. AdvanceFilterBar — sub-component deep dive

```js
const AdvanceFilterBar = ({ config, fetchData }) => {
    const { filters, advanceFilterJson, moreFilterData, advFilters, resetCount, onUpdate, onReset, showReset } = config;

    const handleUpdate = (uniqueId, list) => {
        onUpdate?.(uniqueId, list, fetchData);
    };
    ...
}
```

### Why is it a separate sub-component?

To keep the main component's JSX readable. `AdvanceFilterBar` is only used once (inside `CommonListingPage`) so it's defined in the same file rather than a separate file.

### The handleUpdate wrapper — critical for filters working

This is the fix for filters not working. The original `onUpdate` in your config is called directly, but it needs access to `fetchData` (the internal fetch function) in self-managed mode.

```js
const handleUpdate = (uniqueId, list) => {
    onUpdate?.(uniqueId, list, fetchData);
};
```

`?.(` is optional chaining for function calls — if `onUpdate` is undefined, nothing happens instead of throwing.

Your `onUpdate` in the config receives three arguments:
1. `uniqueId` — which filter chip triggered the change (e.g. `"name_adv_1"`)
2. `list` — the new list of selected values
3. `fetchData` — the internal fetch function (MODE 1) OR `null` (MODE 2)

This lets your handler work in both modes:
```js
// Works in MODE 1 (fetchData is the internal function)
const onUpdateHandle = (uniqueId, list, fetchData) => {
    const params = buildParams(uniqueId, list);
    fetchData?.(params);
};

// Works in MODE 2 (fetchData is null, ignore it)
const onUpdateHandle = (uniqueId, list) => {
    fetchAllVMGroups(builtParams);
};
```

### filters vs advanceFilterJson

```js
filters: ['virtual_group_name', 'environment_master_type']
```
This array controls **which chips are currently shown**. It changes dynamically — when the user clicks "+ More" and adds a filter, you add its key to this array.

```js
advanceFilterJson: {
    virtual_group_name: { ... },
    environment_master_type: { ... }
}
```
This object is **static** — it defines the full config for every possible filter. Only the keys present in `filters` array will actually be rendered.

This separation means you can have 10 defined filters but only show 2 at a time.

### resetCount

```js
resetCount: advState.resetCount
```

`AdvanceSearchFilterCombo` watches this value in a `useEffect`. When it changes (you increment it), the component resets all its checkboxes. To reset all filters: `setAdvState(s => ({ ...s, resetCount: s.resetCount + 1 }))`.

---

## 16. children as a function pattern

```jsx
{typeof children === "function" ? children(data) : children}
```

`children` supports two patterns:

**Pattern A — Regular JSX children** (data already in scope from your state):
```jsx
<CommonListingPage data={state.all_templates} ...>
    {state.all_templates.map(item => <Row key={item.id} item={item} />)}
</CommonListingPage>
```

**Pattern B — Render prop / children as function** (useful in MODE 1 where you don't have data in scope):
```jsx
<CommonListingPage endpoint={properties.api.create_vm_group} ...>
    {(data) => data.map(item => <Row key={item.id} item={item} />)}
</CommonListingPage>
```

In MODE 1 your feature component has no `state.data` — the data lives inside `CommonListingPage`. The only way to access it for rendering is through the children-as-function pattern. The component calls `children(data)` and passes the internal `state.data` array.

`typeof children === "function"` checks which pattern is being used, then calls accordingly.

---

## 17. PropTypes and defaultProps

```js
CommonListingPage.propTypes = {
    heading: PropTypes.string.isRequired,  // only 'required' one
    loading: PropTypes.bool,
    error:   PropTypes.any,
    data:    PropTypes.array,
    children: PropTypes.node,
    errorVariant: PropTypes.oneOf(["REFRESH", "CLOSE", null]),
    // everything else is PropTypes.object / PropTypes.string / PropTypes.func
    // because child components validate their own props
    ...
};
```

**Rule applied:** Only `heading`, `loading`, `error`, `data`, `children`, and `errorVariant` are typed strictly — these are values `CommonListingPage` itself reads and acts on. Everything else (`primaryButton`, `searchBar`, `advanceFilter`, etc.) is `PropTypes.object` because it is passed straight through to a child component that already validates it.

```js
CommonListingPage.defaultProps = {
    loading:      false,   // don't show skeleton by default
    error:        null,    // don't show error by default
    data:         [],      // prevents data?.length crashing if not passed
    skeleton:     {},      // prevents skeleton?.count crashing
    emptyState:   {},      // prevents emptyState?.text crashing
    errorVariant: null,    // show "Go home" link by default
    children:     null,
    endpoint:     null,    // MODE 2 is default
    urlParams:    {},
    pageSize:     10,
};
```

`skeleton: {}` and `emptyState: {}` default to empty objects so the optional chaining (`skeleton?.count`, `emptyState?.text`) inside the JSX returns `undefined` rather than throwing — and the `??` fallbacks kick in.

---

## 18. Full usage examples

### Example 1 — MODE 1: Self-managed, simplest possible

```jsx
const PolicyTemplateListing = () => {
    return (
        <div className={classes.root}>
            <CommonListingPage
                heading="Policy Templates"
                subHeading="Manage all policy templates."
                icon="ri-shield-line"
                endpoint={properties.api.add_policy_templates}
                primaryButton={{
                    actionType: 'link',
                    action: '/policy-template/add',
                    text: 'Add Template',
                    buttonClass: 'btn-primary',
                }}
                emptyState={{
                    text: 'No policy templates yet',
                    icon: <span className="ri-shield-line font-28 color-key-gray" />,
                    primaryButton: {
                        actionType: 'link',
                        action: '/policy-template/add',
                        text: 'Add Template',
                        buttonClass: 'btn-primary',
                    },
                }}
            >
                {(data) => (
                    <div className="grid-2-col">
                        {data.map(item => <PolicyCard key={item.id} data={item} />)}
                    </div>
                )}
            </CommonListingPage>
        </div>
    );
};
```

### Example 2 — MODE 2: External state, with search

```jsx
const JobTemplatesListing = () => {
    const [state, setState] = useState({ loading: false, error: null, job_template_listing: [], count: 0, total_page: 0, curr_page: 1, next: null, previous: null });
    const [filter, setFilter] = useState({ name: '' });

    useEffect(() => { fetchJobTemplatesInfo(); }, []);

    const handleSearch = (value) => {
        const params = { name: typeof value === 'string' ? value : value.name };
        setFilter(params);
        fetchJobTemplatesInfo(params);
    };

    return (
        <div className={classes.root}>
            <CommonListingPage
                heading="Job Templates"
                icon="ri-file-paper-line"
                loading={state.loading}
                error={state.error}
                data={state.job_template_listing}
                count={state.count}
                total_page={state.total_page}
                curr_page={state.curr_page}
                next={state.next}
                previous={state.previous}
                on_next_click={() => fetchNextJobTemplateInfo(null, state.next)}
                on_previous_click={() => fetchPrevJobTemplateInfo(null, state.previous)}
                on_pageNumber_click={(page) => fetchPageJobTemplateInfo(page)}
                onRefresh={fetchJobTemplatesInfo}
                searchBar={{
                    searchData: filter,
                    defaultFilter: { name: 'name', label: 'Search job templates...' },
                    onSearch: handleSearch,
                    onClear: () => { setFilter({ name: '' }); fetchJobTemplatesInfo(); },
                }}
                emptyState={{ text: 'No job templates found', variant: 'default' }}
            >
                {state.job_template_listing.map(item => (
                    <JobListingCardNew key={item.id} data={item} />
                ))}
            </CommonListingPage>
        </div>
    );
};
```

### Example 3 — MODE 2: With advance filters (VM Groups pattern)

```jsx
const onUpdateHandle = (uniqueId, list) => {
    const keyMap = { name_adv_1: 'virtual_group_name', env_adv_2: 'environment_master_type' };
    if (uniqueId === 'more-button-adv-0') {
        setAdvState(s => ({ ...s, moreAdvFilterList: list }));
        return;
    }
    const key = keyMap[uniqueId];
    const values = list.map(i => typeof i === 'object' ? i.value : i);
    setAdvState(s => {
        const updated = { ...s.advFilters, [key]: values };
        fetchAllVMGroups(updated);   // ← your own fetch
        return { ...s, advFilters: updated };
    });
};

<CommonListingPage
    heading="Virtual Machine Groups"
    ...
    advanceFilter={{
        filters: advState.moreAdvFilterList,
        advanceFilterJson: advanceFilterJson,
        moreFilterData: moreFilterData,
        advFilters: advState.advFilters,
        resetCount: advState.resetCount,
        onUpdate: onUpdateHandle,
        onReset: resetAdvFilter,
    }}
    ...
>
    {state.all_templates.map(item => <VmRow key={item.id} item={item} />)}
</CommonListingPage>
```

---

## 19. Common mistakes and how to avoid them

### 1. Passing both `endpoint` and `loading`/`data`

```jsx
// ❌ WRONG — isSelfManaged = true so loading/data props are ignored
<CommonListingPage endpoint={properties.api.vm_group} loading={state.loading} data={state.data} />

// ✅ Use only one mode
<CommonListingPage endpoint={properties.api.vm_group} />          // MODE 1
<CommonListingPage loading={state.loading} data={state.data} />   // MODE 2
```

### 2. Forgetting children-as-function in MODE 1

```jsx
// ❌ WRONG — state.data doesn't exist in your component in MODE 1
<CommonListingPage endpoint={...}>
    {state.data.map(item => <Row item={item} />)}
</CommonListingPage>

// ✅ Use the function pattern — data is passed from internal state
<CommonListingPage endpoint={...}>
    {(data) => data.map(item => <Row item={item} />)}
</CommonListingPage>
```

### 3. Not incrementing resetCount to reset filters

```jsx
// ❌ WRONG — checkboxes won't reset
setState(s => ({ ...s, advFilters: { name: [], env: [] } }));

// ✅ Also increment resetCount
setState(s => ({
    ...s,
    advFilters: { name: [], env: [] },
    resetCount: s.resetCount + 1,   // ← this triggers the reset in AdvanceSearchFilterCombo
}));
```

### 4. Using the wrong `actionType` for buttons

```jsx
// ❌ WRONG — 'onClick' is not a valid actionType
primaryButton={{ actionType: 'onClick', action: () => setOpen(true) }}

// ✅ Use 'open-dialog' for JS handlers
primaryButton={{ actionType: 'open-dialog', action: () => setOpen(true) }}

// ✅ Use 'link' for route navigation
primaryButton={{ actionType: 'link', action: '/vm-group/add' }}
```

### 5. Forgetting to set `unique` uniqueId on each filter

Each `AdvanceSearchFilterCombo` uses its `uniqueId` to identify itself in click-outside event listeners. If two filters on the same page share a `uniqueId`, clicking one will close the other.

```js
// ❌ WRONG
virtual_group_name: { uniqueId: 'filter_1', ... }
environment_master_type: { uniqueId: 'filter_1', ... }   // same ID!

// ✅ Always unique per page
virtual_group_name: { uniqueId: 'name_adv_1', ... }
environment_master_type: { uniqueId: 'env_adv_2', ... }
```
