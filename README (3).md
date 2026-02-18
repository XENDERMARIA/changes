# CommonListingPage — Complete Developer Reference

## Table of Contents

1. [Architecture overview](#1-architecture-overview)
2. [File map](#2-file-map)
3. [useListingController — the data hook](#3-uselistingcontroller)
4. [CommonListingPage — the shell component](#4-commonlistingpage)
   - [Section 1 · Page Header](#section-1--page-header)
   - [Section 2 · Toolbar](#section-2--toolbar)
   - [Section 3 · Body (Loading / Error / Data / Empty)](#section-3--body)
   - [Section 4 · Pagination](#section-4--pagination)
5. [Complete usage examples](#5-complete-usage-examples)
6. [Migrating an existing listing page](#6-migrating-an-existing-listing-page)
7. [Props reference tables](#7-props-reference-tables)

---

## 1. Architecture overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  Feature listing page  (e.g. VmGroupsListing.js)                     │
│                                                                      │
│  const controller = useListingController({ endpoint, urlParams })    │
│                                                                      │
│  return (                                                            │
│    <CommonListingPage                                                │
│      title="…"   headerButtons={…}                                  │
│      toolbar={…}                                                     │
│      controller={controller}                                         │
│      renderContent={(data) => <YourTable data={data} />}            │
│      emptyState={…}                                                  │
│    />                                                                │
│  )                                                                   │
└──────────────────────────────────────────────────────────────────────┘
          │                          │
          ▼                          ▼
  useListingController         CommonListingPage renders
  (owns ALL data concerns)     five fixed sections in order:

  state: loading, error,       ┌──────────────────────────┐
         data, count,          │  1.  PageHeader           │
         curr_page,            ├──────────────────────────┤
         total_page,           │  2.  Toolbar              │
         next, previous        │      SearchBar            │
                               │      AdvanceFilters       │
  actions:                     │      extra slot           │
    fetchData(params, url)     ├──────────────────────────┤
    fetchNext()                │  3.  Body                 │
    fetchPrev()                │      Loading              │
    fetchPage(n)               │      PageError            │
                               │      renderContent(data)  │
                               │      BlankPage            │
                               ├──────────────────────────┤
                               │  4.  PaginationTwo        │
                               └──────────────────────────┘
```

### Design principles

| Principle | Explanation |
|-----------|-------------|
| **Separation of concerns** | The hook owns data, the shell owns layout. Your page component owns only what is *unique* to it. |
| **Opt-in everything** | Omit any prop and that section is simply not rendered. Nothing is forced. |
| **No hidden side-effects** | `useListingController` never starts a fetch on its own. You call `fetchData()` in your own `useEffect`. This keeps you in control of *when* and *how* fetching starts. |
| **Search-params preserved across pages** | The hook stores the last search params in a ref. `fetchPage(n)` automatically re-applies them so filters don't reset when the user navigates pages. |

---

## 2. File map

```
src/
├── hooks/
│   └── useListingController.js       ← data hook (copy here)
│
└── components/
    └── CommonListingPage.js          ← shell component (copy here)
```

Your feature listings live wherever they already live. They just import the two files above.

---

## 3. useListingController

### What it replaces

Every listing page currently has **3–5 almost-identical fetch functions**:

```js
// BEFORE  (inside VmGroupsListing, JobTemplatesListing, etc.)
function fetchAllVMGroups(data, url) { … }        // initial load
function fetchNextInfo(data, url) { … }           // next page
function fetchPrevInfo(data, url) { … }           // prev page
function fetchPageInfo(enteredPageNumber) { … }   // jump to page
```

All four are collapsed into one hook and four clean actions.

### Setup

```js
import useListingController from '../hooks/useListingController';

const controller = useListingController({
  endpoint:   properties.api.create_vm_group,   // REQUIRED – the API endpoint template string
  urlParams:  { application_id },               // OPTIONAL – path params for GenerateURL
  pageSize:   10,                               // OPTIONAL – default 10
});
```

### Start the initial fetch

The hook **never auto-fetches**. You decide when:

```js
useEffect(() => {
  controller.fetchData();
}, []);
```

### Actions

| Method | When to call | Notes |
|--------|-------------|-------|
| `fetchData(searchParams?, directUrl?)` | Initial load, search, clear-search | `searchParams` is a `{ key: value }` object forwarded to `GenerateSearchURL`. `directUrl` overrides the endpoint entirely (rare). |
| `fetchNext()` | Next-page button | Reads `state.next` from the last API response. No-ops if `next` is null. |
| `fetchPrev()` | Prev-page button | Reads `state.previous`. No-ops if null. |
| `fetchPage(n)` | Page-number jump | Computes `?limit=10&offset=…`. Re-applies the last search params automatically. |

### State exposed

```js
controller.loading      // boolean  – true while a request is in-flight
controller.error        // any      – error object/string from InvokeApi, or null
controller.statusCode   // number   – HTTP status code from the failed request, or null
controller.data         // array    – data.results from the last successful response
controller.count        // number   – data.count (total items)
controller.total_page   // number   – Math.ceil(count / pageSize)
controller.curr_page    // number   – 1-indexed current page
controller.next         // string|null
controller.previous     // string|null
```

---

## 4. CommonListingPage

Pass `controller` plus the five section props. Every section is optional except `controller` and `renderContent`.

### Section 1 · Page Header

Wraps the existing `<PageHeader>` component. Supports up to **three** buttons via `headerButtons`.

```jsx
<CommonListingPage
  title="Virtual Machine Groups"
  subHeading="All connected and disconnected VM groups."
  icon="ri-cpu-line"
  backgroundColor="#0086FF14"          // icon background colour
  headerButtons={{
    primary: {
      actionType: 'open-dialog',
      action: () => openDialog(),
      icon: <span className="ri-add-line" />,
      text: 'Add VM Group',
      buttonClass: 'btn-primary',
      isPermitted: hasPermission('POST', url),   // grey-out + tooltip when false
    },
    secondary: {
      actionType: 'link',
      action: '/vm-groups/export',
      text: 'Export',
      buttonClass: 'btn-outline-primary',
    },
    tertiary: {
      actionType: 'open-dialog',
      action: () => openFilter(),
      text: 'Filters',
      buttonClass: 'btn-outline-default',
    },
  }}
  …
/>
```

**Button `actionType` values**

| Value | Behaviour |
|-------|-----------|
| `'link'` | Wraps in `<Link to={action}>` |
| `'open-dialog'` | Calls `action()` onClick — use for dialogs, drawers, any JS handler |

**`isPermitted`** (optional, default `true`): when `false` the button is disabled and shows a "You are not allowed…" tooltip. Wire it to your permission system.

---

### Section 2 · Toolbar

Configured via a single `toolbar` prop object. It has a `type` field that controls which sub-components render.

#### `type: 'search'` — only SearchBar

```jsx
toolbar={{
  type: 'search',
  searchConfig: {
    searchData: searchState,                  // controlled value { name: '' }
    defaultFilter: { name: 'name', label: 'Search by name' },
    onSearch: (value) => {
      // value is the string if realTime=true, or the full state object on Enter
      controller.fetchData({ name: value });
    },
    onClear: () => controller.fetchData(),    // optional – defaults to fetchData()
    realTime: false,                          // true = fires onSearch on every keystroke
    width: '37%',                             // optional – default '37%'
  },
}}
```

#### `type: 'advance'` — only AdvanceSearchFilterCombo chips

```jsx
toolbar={{
  type: 'advance',
  advanceFilterConfig: {
    filters: advState.moreAdvFilterList,   // array of currently active filter keys
    filterJson: {                          // one entry per possible filter key
      virtual_group_name: {
        uniqueId: 'name_adv_1',
        labelName: 'Name',
        searchVariable: 'virtual_group_name',
        staticList: false,
        getFetchUrl: properties.api.create_vm_group,
        searchUrl: properties.api.create_vm_group,
        filterDataPraseFunction: (data) =>
          data?.map(i => ({ label: i.virtual_group_name, value: i.virtual_group_name })),
      },
      environment_master_type: {
        uniqueId: 'env_adv_2',
        labelName: 'Restricted Envs',
        searchVariable: 'environment_master_type',
        staticList: false,
        getFetchUrl: properties.api.create_vm_group,
        filterDataPraseFunction: parseEnvs,
      },
    },
    moreFilterData: [                      // optional – drives the "+ More" button
      { label: 'Name', value: 'virtual_group_name' },
      { label: 'Restricted Envs', value: 'environment_master_type' },
    ],
    advFilters: advState.advFilters,       // { virtual_group_name: [], environment_master_type: [] }
    resetCount: advState.resetCount,       // increment to reset all checkboxes
    onUpdate: onUpdateHandle,             // called by every AdvanceSearchFilterCombo on change
    onReset: resetAdvFilter,              // called when the user clicks "Reset"
    showReset: true,                      // optional – default true
  },
}}
```

#### `type: 'both'` — SearchBar on the left, chips on the right

```jsx
toolbar={{
  type: 'both',
  searchConfig: { … },
  advanceFilterConfig: { … },
}}
```

#### `extra` — arbitrary JSX beside any toolbar type

Use `extra` for anything that doesn't fit the above: a "Set as Default" dialog button, a date-range picker, an export button, etc.

```jsx
toolbar={{
  type: 'search',
  searchConfig: { … },
  extra: (
    <SetAsDefaultDialog
      varient="jobtemplatelisting"
      onCreateDefaultTemplate={createDefaultTemplate}
      …
    />
  ),
}}
```

`extra` is always placed with `margin-left: auto` so it floats to the right of the toolbar.

---

### Section 3 · Body

The body renders exactly **one** of four states in priority order:

```
loading  →  error  →  data (hasData)  →  empty
```

#### Loading

Default: renders `<Loading varient="default" />` (full-overlay spinner).

```jsx
// Light variant (inline spinner, no overlay)
loadingVariant="light"

// Custom skeleton instead of the spinner
renderSkeleton={() => (
  <div style={{ display: 'grid', gap: 16 }}>
    {Array.from({ length: 4 }).map((_, i) => (
      <GenericSkeleton key={i} variant="rect" height={60} style={{ borderRadius: 6 }} />
    ))}
  </div>
)}
```

#### Error

Default: renders `<PageError>` with a "Go to home" link.

```jsx
// Show a Refresh button instead
errorVariant="REFRESH"

// Show a Close button instead
errorVariant="CLOSE"
```

`handleRefresh` is automatically wired to `controller.fetchData()`.

#### Data — `renderContent`

Called only when `data.length > 0`. Receives the current page's array.

```jsx
renderContent={(data) => (
  <div className="table-view">
    {data.map(item => <MyRow key={item.id} item={item} />)}
  </div>
)}
```

#### Empty state — `emptyState`

Rendered when `data.length === 0` and not loading/erroring.

```jsx
emptyState={{
  text: 'No VM groups found',
  subHeading: "Click 'Add VM Group' to get started",
  icon: <span className="ri-cpu-line font-28 color-key-gray" />,
  backgroundColor: '#F4F4F4',
  primaryButton: {
    actionType: 'open-dialog',
    action: () => openDialog(),
    text: 'Add VM Group',
    buttonClass: 'btn-primary',
  },
  // 'default'       – centered, full viewport height (default)
  // 'inside-table'  – compact, designed to sit inside a table grid
  variant: 'default',
}}
```

`'inside-table'` variant with additional styles — useful when you want the table headers to stay visible:

```jsx
emptyState={{
  variant: 'inside-table',
  text: 'No results',
  additionalStyles: {
    padding: '55px 0',
    textAlign: 'center',
    height: 'auto',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gridColumn: '1 / -1',   // ← spans all grid columns when inside a CSS grid table
  },
}}
```

---

### Section 4 · Pagination

`PaginationTwo` is automatically rendered below `renderContent` when `data.length > 0`. All wiring is done internally — you don't touch it.

To **hide** the "Go to page" input:

```jsx
// This is a prop on PaginationTwo itself.
// The CommonListingPage always passes disable_go_to_page_number={false}.
// If you need to hide it, pass a custom renderContent that wraps PaginationTwo manually
// and passes disable_go_to_page_number={true}.
```

---

## 5. Complete usage examples

### Minimal (no search, single primary button)

```jsx
import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import properties from '../../properties/properties';
import useListingController from '../../hooks/useListingController';
import CommonListingPage from '../../components/CommonListingPage';
import MyCard from './components/MyCard';

const MyFeatureListing = () => {
  const controller = useListingController({
    endpoint: properties.api.my_feature,
  });

  useEffect(() => { controller.fetchData(); }, []);

  return (
    <CommonListingPage
      title="My Feature"
      subHeading="All configured items."
      icon="ri-settings-line"
      headerButtons={{
        primary: {
          actionType: 'link',
          action: '/my-feature/add',
          text: 'Add New',
          buttonClass: 'btn-primary',
        },
      }}
      controller={controller}
      renderContent={(data) => (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          {data.map(item => <MyCard key={item.id} data={item} />)}
        </div>
      )}
      emptyState={{
        text: 'No items yet',
        icon: <span className="ri-settings-line font-28 color-key-gray" />,
        primaryButton: {
          actionType: 'link',
          action: '/my-feature/add',
          text: 'Add New',
          buttonClass: 'btn-primary',
        },
      }}
    />
  );
};

export default MyFeatureListing;
```

---

### With simple search + secondary button

```jsx
const [searchData, setSearchData] = useState({ name: '' });

const handleSearch = (value) => {
  const params = { name: typeof value === 'string' ? value : value.name };
  setSearchData(params);
  controller.fetchData(params);
};

<CommonListingPage
  title="Policy Templates"
  icon="ri-shield-line"
  headerButtons={{
    primary: {
      actionType: 'link',
      action: '/policy-templates/add',
      text: 'Add Template',
      buttonClass: 'btn-primary',
    },
    secondary: {
      actionType: 'open-dialog',
      action: () => setExportOpen(true),
      text: 'Export',
      buttonClass: 'btn-outline-primary',
    },
  }}
  toolbar={{
    type: 'search',
    searchConfig: {
      searchData,
      defaultFilter: { name: 'name', label: 'Search templates…' },
      onSearch: handleSearch,
    },
  }}
  controller={controller}
  renderContent={(data) => <PolicyTable data={data} />}
  emptyState={{ text: 'No policy templates', variant: 'default' }}
/>
```

---

### With advanced filters + "More" button + Reset (VM Groups pattern)

```jsx
const defaultFilters = ['virtual_group_name', 'environment_master_type'];
const [advState, setAdvState] = useState({
  moreAdvFilterList: defaultFilters,
  advFilters: { virtual_group_name: [], environment_master_type: [] },
  resetCount: 0,
});

const onUpdateHandle = (uniqueId, updatedList) => {
  if (uniqueId === 'more-button-adv-0') {
    setAdvState(s => ({ ...s, moreAdvFilterList: updatedList }));
    return;
  }
  const keyMap = { name_adv_1: 'virtual_group_name', env_adv_2: 'environment_master_type' };
  const key = keyMap[uniqueId];
  const values = updatedList.map(i => (typeof i === 'object' ? i.value : i));
  setAdvState(s => {
    const next = { ...s.advFilters, [key]: values };
    controller.fetchData(next);
    return { ...s, advFilters: next };
  });
};

const resetAdvFilter = () => {
  setAdvState(s => ({
    ...s,
    moreAdvFilterList: defaultFilters,
    advFilters: { virtual_group_name: [], environment_master_type: [] },
    resetCount: s.resetCount + 1,
  }));
  controller.fetchData();
};

<CommonListingPage
  title="Virtual Machine Groups"
  icon="ri-cpu-line"
  headerButtons={{
    primary: {
      actionType: 'open-dialog',
      action: () => setDialogOpen(true),
      text: 'Add VM Group',
      buttonClass: 'btn-primary',
      icon: <span className="ri-add-line" />,
    },
  }}
  toolbar={{
    type: 'advance',
    advanceFilterConfig: {
      filters: advState.moreAdvFilterList,
      filterJson: advanceFilterJson,       // your existing filterJson config
      moreFilterData: moreFilterData,
      advFilters: advState.advFilters,
      resetCount: advState.resetCount,
      onUpdate: onUpdateHandle,
      onReset: resetAdvFilter,
    },
  }}
  controller={controller}
  renderContent={(data) => <VmTable data={data} />}
  emptyState={{
    text: 'No VM groups added',
    subHeading: "Click 'Add VM Group' to get started",
    icon: <span className="ri-cpu-line font-28 color-key-gray" />,
    primaryButton: { actionType: 'open-dialog', action: () => setDialogOpen(true), text: 'Add VM Group', buttonClass: 'btn-primary' },
  }}
/>
```

---

### With both search and advance filters + extra slot (Job Templates pattern)

```jsx
toolbar={{
  type: 'both',
  searchConfig: {
    searchData: filter,
    defaultFilter: { name: 'name', label: 'Search for job template' },
    onSearch: handleSearchChange,
    onClear: handleSearchClear,
    width: '35%',
  },
  advanceFilterConfig: { … },
  extra: (
    <SetAsDefaultDialog
      varient="jobtemplatelisting"
      onCreateDefaultTemplate={createDefaultTemplate}
      changeDefaultJobTemplate={ValidateAndSave}
      …
    />
  ),
}}
```

---

### Custom skeleton instead of spinner

```jsx
<CommonListingPage
  …
  renderSkeleton={() => (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginTop: 20 }}>
      {Array.from({ length: 4 }).map((_, i) => (
        <GenericSkeleton key={i} variant="rect" height={120} style={{ borderRadius: 8 }} />
      ))}
    </div>
  )}
/>
```

---

### Permission-gated primary button

```jsx
const { hasPermission } = usePermissions();
const canCreate = hasPermission('POST', GenerateEndpointURL({}, properties.api.create_vm_group));

headerButtons={{
  primary: {
    actionType: 'open-dialog',
    action: () => setOpen(true),
    text: 'Add VM Group',
    buttonClass: 'btn-primary',
    isPermitted: canCreate,   // false → button disabled + tooltip
  },
}}
```

---

## 6. Migrating an existing listing page

### Step-by-step

**Step 1 — Replace state with the hook**

```js
// REMOVE all of this:
const [state, setState] = useState({ loading: true, all_templates: [], total_page: '', curr_page: '', next: null, previous: null, count: 0, error: null });

// ADD this:
const controller = useListingController({ endpoint: properties.api.create_vm_group });
```

**Step 2 — Replace the four fetch functions**

```js
// REMOVE: fetchAllVMGroups, fetchNextInfo, fetchPrevInfo, fetchPageInfo
// REMOVE: fetchAllVMGroupsDataSuccess, fetchAllVMGroupsDataFailed
// REMOVE: handleWorkflowNextDataSuccessApiHit, handleWorkflowPrevDataSuccessApiHit

// KEEP: only the business-logic that is unique to your page
//       (URL-sync for advanced filters, dialog open/close, etc.)
```

**Step 3 — Trigger initial load**

```js
useEffect(() => {
  controller.fetchData();
}, []);
```

**Step 4 — Wrap the JSX**

```jsx
// REMOVE: your manual loading/error/empty ternaries
// REPLACE your entire return() with:

return (
  <CommonListingPage
    title="…"
    …
    controller={controller}
    renderContent={(data) => <YourExistingTable data={data} />}
    …
  />
);
```

### Migration checklist per page

| Page | Search type | Unique concerns |
|------|-------------|-----------------|
| `VmGroupsListing` | `'advance'` | URL sync, advanced filter state, Add/Edit dialog |
| `JobTemplatesListing` | `'both'` | PermissionGate, SetAsDefaultDialog, URL query-string pre-filter |
| `CanaryTemplatesListing` | `'advance'` | URL sync, advanced filter state |
| `PolicyTemplateListing` | `'search'` | Permission check before first fetch, AddPolicyTemplate drawer |

---

## 7. Props reference tables

### `useListingController` options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `endpoint` | `string` | **required** | `properties.api.*` value passed to `GenerateURL` |
| `urlParams` | `object` | `{}` | Path params, e.g. `{ application_id }` |
| `pageSize` | `number` | `10` | Items per page for offset calculation |

---

### `<CommonListingPage>` props

| Prop | Type | Required | Description |
|------|------|----------|-------------|
| `title` | `string` | ✅ | Page heading |
| `subHeading` | `string \| node` | | Page sub-heading text or JSX |
| `icon` | `string` | | Remixicon class, e.g. `'ri-cpu-line'` |
| `imgIcon` | `string` | | Image path — alternative to icon |
| `backgroundColor` | `string` | | Icon avatar background colour |
| `headerButtons` | `object` | | `{ primary, secondary, tertiary }` |
| `toolbar` | `object` | | See Toolbar shape below |
| `controller` | `object` | ✅ | Returned by `useListingController` |
| `renderContent` | `(data) => node` | ✅ | Renders when `data.length > 0` |
| `renderSkeleton` | `() => node` | | Custom skeleton; replaces `<Loading>` |
| `loadingVariant` | `'default' \| 'light'` | | Default `'default'` |
| `emptyState` | `object` | | See EmptyState shape below |
| `errorVariant` | `'REFRESH' \| 'CLOSE' \| null` | | Controls PageError action button |
| `containerClass` | `string` | | CSS class on the outer div |
| `containerStyle` | `object` | | Inline style on the outer div |

---

### `headerButtons` shape (each button)

| Key | Type | Description |
|-----|------|-------------|
| `actionType` | `'link' \| 'open-dialog'` | Navigation vs JS handler |
| `action` | `string \| func` | URL path or click handler |
| `text` | `string` | Button label |
| `icon` | `node` | Icon node rendered before text |
| `buttonClass` | `string` | CSS class(es), e.g. `'btn-primary'` |
| `isPermitted` | `bool` | Default `true`. `false` disables + shows tooltip |

---

### `toolbar` shape

| Key | Type | Description |
|-----|------|-------------|
| `type` | `'search' \| 'advance' \| 'both' \| 'none'` | Controls which sub-components render |
| `searchConfig` | `object` | Config for `<SearchBar>` |
| `advanceFilterConfig` | `object` | Config for `<AdvanceSearchFilterCombo>` chips |
| `extra` | `node` | Arbitrary JSX, floated to the right |

### `searchConfig` shape

| Key | Type | Description |
|-----|------|-------------|
| `searchData` | `object` | Controlled state, e.g. `{ name: '' }` |
| `defaultFilter` | `{ name, label }` | Field name and placeholder |
| `onSearch` | `func` | Called on Enter (or every keystroke if `realTime: true`) |
| `onClear` | `func` | Called when × is clicked. Defaults to `fetchData()` |
| `realTime` | `bool` | Default `false` |
| `width` | `string` | CSS width. Default `'37%'` |
| `backgroundColor` | `string` | SearchBar background |
| `border` | `string` | SearchBar border |

### `advanceFilterConfig` shape

| Key | Type | Description |
|-----|------|-------------|
| `filters` | `string[]` | Active filter keys — controls which chips render |
| `filterJson` | `object` | Map of key → `AdvanceSearchFilterCombo` props |
| `moreFilterData` | `array` | Static list for the "+ More" button. Omit to hide it. |
| `advFilters` | `object` | Current selections, e.g. `{ name: [], env: [] }` |
| `resetCount` | `number` | Increment to reset all checkboxes |
| `onUpdate` | `func` | Called by every chip on selection change |
| `onReset` | `func` | Called when "Reset" is clicked |
| `showReset` | `bool` | Default `true`. Set `false` to hide Reset link. |

### `emptyState` shape

| Key | Type | Description |
|-----|------|-------------|
| `text` | `string` | Primary empty-state message |
| `subHeading` | `string` | Secondary message below the heading |
| `icon` | `node` | Icon node |
| `primaryButton` | `object` | Same shape as header button |
| `additionalComponent` | `node` | Replaces primaryButton when provided |
| `backgroundColor` | `string` | Icon avatar background |
| `variant` | `'default' \| 'inside-table'` | `'inside-table'` is compact, for use inside CSS grid tables |
| `additionalStyles` | `object` | Extra styles on the BlankPage wrapper |
| `type` | `string` | `'deployment-freeze'` changes the height on the default variant |
