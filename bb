/* eslint-disable eqeqeq */
/* eslint-disable react-hooks/exhaustive-deps */
/* eslint-disable no-unused-vars */
import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Loading } from '../../utils/Loading';
import { ErrorComponent } from '../../utils/Error';
import EditableTable from '../../../components/genericComponents/EditableTable';
import Dialog from '@mui/material/Dialog';
import Slide from '@mui/material/Slide';
import DialogTitle from '@mui/material/DialogTitle';
import Grid from '@mui/material/Grid';
import Tooltip from '@mui/material/Tooltip';
import AddUserForm from './AddUserForm';
import SearchBar, { MULTIPLE } from '../../../components/SearchBar';
import Pagination from '../../../components/Pagination';
import properties from '../../../properties/properties';
import GenerateURL, { GenerateSearchURL } from '../../../util/APIUrlProvider';
import InvokeApi, { PostData } from '../../../util/apiInvoker';
import InviteUserForm from './InviteUserForm';
import PageHeader from '../../../components/PageHeader';
import { useCustomSnackbar } from '../../../contexts/SnackbarContext';
import PageError from '../../../components/genericComponents/Errors/PageError';

const columnNames = [

  {
    label: 'Name',
    width: '20%',
    grey: false,
    checkbox: false,
    name: 'name',
    label_with_initials: true
  },
  {
    label: 'Email',
    width: '20%',
    grey: true,
    name: 'email'
  },
  {
    label: 'Provider',
    grey: false,
    width: '20%',
    name: 'provider',
    alternateName: 'value'
  },
  {
    label: 'User Status',
    grey: true,
    width: '20%',
    name: 'is_active',
  }

]

const filter_list = [
  {
    label: "Email Id",
    name: "email",
  },
  {
    label: "Status",
    name: "user_status",
  }
];

const Transition = React.forwardRef(function Transition(props, ref) {
  return <Slide direction="up" ref={ref} {...props} />;
})


const ManageUsers = (props) => {
  const { showSnackbar } = useCustomSnackbar();
  const [open, setOpen] = useState(false);
  const [downloadOpen, setDownloadOpen] = useState(false);
  const [source, setSource] = useState('');
  const [get_super_admin, setGetSuperAdmin] = useState({});
  const [policyList, setPolicyList] = useState([]);
  const [groupList, setGroupList] = useState([]);
  const [state, setState] = useState({ data: {}, error: '', total_page: '', curr_page: '', user_list: [], })
  const [modelOpen, setModelOpen] = useState({ open: false, mode: '' })
  console.log(state.current, "fdjksan")

  const downloadOptionsRef = useRef(null);

  useEffect(() => {
    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, []);

  const handleClickOutside = (event) => {
    if (downloadOptionsRef.current && !downloadOptionsRef.current.contains(event.target)) {
      setDownloadOpen(false);
    }
  };

  const closeModel = () => {
    setModelOpen({ open: false, mode: '' })
  }

  const openModel = (mode) => {
    setModelOpen({ open: true, mode: 'edit' })
  }

  const editClicked = (mode, data) => {

    setModelOpen({ open: true, mode: mode })
  }

  useEffect(() => {
    managePolicyListData();
  }, [])

  function fetchAllUsers(data, url) {
    console.log(data, "fkjdsakj")
    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.user_list),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }
    if (data) {
      requestInfo.endPoint = GenerateSearchURL(data, requestInfo.endPoint);
    }


    if (url) {
      requestInfo.endPoint = url;
    }


    InvokeApi(requestInfo, handleFetchUserDataSuccessApiHit, handleFetchUserDataFailedApiHit);
    setState(new_state => ({
      ...new_state,
      search_data: data,
      loading: true,
      current: requestInfo.endPoint,
      search_query_name: data ? data.name ? data.name : "" : "",
      search_query_email: data ? (data.email ? data.email : "") : "",
      search_query_status: data ? (data.user_status ? data.user_status : "") : ""
    }));

  }
  function handleFetchUserDataSuccessApiHit(data) {
   

    setState(new_state => ({
      ...new_state,
      user_list: data.results,
      count: data.count,
      loading: false,
      next: data.next ? properties.api.baseURL + data.next : null,
      previous: data.previous ? properties.api.baseURL + data.previous : null,
      total_page: Number.isInteger(Number(data.count) / 10) ? (Number(data.count) / 10).toFixed(0) : (Number(data.count) / 10 + 1).toFixed(0) > (Number(data.count) / 10 + 1) ? (Number(data.count) / 10 + 1).toFixed(0) - 1 : (Number(data.count) / 10 + 1).toFixed(0),
      curr_page: 1
    }));

  }

  function handleFetchUserDataFailedApiHit(error,statusCode) {
    setState(new_state => ({
      ...new_state,
      error: error,
      loading: false,
      statusCode:statusCode
    }))
  }

  /* fetch policy list data */
  const managePolicyListData = () => {
    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.clone_policies_listing) ,
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }

    InvokeApi(requestInfo, policyListFetchSuccess, policyListFetchFailure);

    setState(new_state => ({
      ...new_state,
      loading: true,
    }));
  }
  var policy_List;
  const policyListFetchSuccess = data => {
    manageGroupListData();
    policy_List = data.map(item => item.name);

    console.log('line 89', policy_List);
    setPolicyList(policy_List);
  }
  const policyListFetchFailure = (error, exception) => {
    setState(new_state => ({
      ...new_state,
      loading: false,
      error_msg: error,
    }));
  }

  /* fetch group list data */
  const manageGroupListData = () => {
    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.get_role_list) + "?all=true&no_mapping=true",
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }

    InvokeApi(requestInfo, groupListFetchSuccess, groupListFetchFailure);

    setState(new_state => ({
      ...new_state,
      loading: true,
    }));
  }

  var role_List;
  const groupListFetchSuccess = data => {
    fetchAllUsers();
    role_List = data.results.map(item => item.name);
    setGroupList(role_List);
  }
  const groupListFetchFailure = (error, exception) => {
    setState(new_state => ({
      ...new_state,
      loading: false,
      error_msg: error,
    }));
  }


  function fetchAssociatedRoleAndProject(data) {
    var requestInfo = {
      endPoint: GenerateURL({ id: data.id }, properties.api.user_roles_and_projects),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }
    InvokeApi(requestInfo, handleSuccessAssociatedRoleAndProject, handleFailureAssociatedRoleAndProject);
    setState(new_state => ({
      ...new_state,
      show_loading_icon: true,
    }));

  }
  const fetchAppNamesFromData = (data) => {

    if (data && data.project_role) {
      if (data.project_role.project && data.project_role.role_master) {
        console.log(data.project_role, "fdasfasd>>")
        return {
          projects: data.project_role.project.name,
          roles: data.project_role.role_master.name
        }
      }
    }
  }
  const handleSuccessAssociatedRoleAndProject = (response) => {
    console.log(response, "response>>")
    state.user_list.forEach(single_user => {
      if (response.id === single_user.id) {
        single_user.permission_url = response.user_project && response.user_project.length > 0 ?
          response.user_project.map(item => {
            return fetchAppNamesFromData(item).projects
          }) : null
        single_user.roles = response.user_project && response.user_project.length > 0 ?
          response.user_project.map(item => {
            return fetchAppNamesFromData(item).roles
          }) : null
      }
    })
    setState(new_state => ({
      ...new_state,
      user_list: [...state.user_list]
    }));
  }
  console.log(state.user_list, "response>>")
  const handleFailureAssociatedRoleAndProject = (error) => {
    console.log(error, "response>>")
  }


  function fetchPrevFetchUserDataInfo(data, url) {

    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.add_jira_ids),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }

    if (data) {
      requestInfo.endPoint = GenerateSearchURL(data, requestInfo.endPoint);
    }


    if (url) {
      requestInfo.endPoint = url;
    }
    setState(new_state => ({
      ...new_state,
      search_data: data,
      current: requestInfo.endPoint,
      loading: true
    }));
    InvokeApi(requestInfo, FetchUserPrevInfoFetchSuccess, FetchUserPrevInfoFetchFailure);

  }

  function FetchUserPrevInfoFetchSuccess(data) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      user_list: data.results,
      count: data.count,
      next: data.next ? properties.api.baseURL + data.next : null,
      previous: data.previous ? properties.api.baseURL + data.previous : null,
      total_page: Number.isInteger(Number(data.count) / 10) ? (Number(data.count) / 10).toFixed(0) : (Number(data.count) / 10 + 1).toFixed(0) > (Number(data.count) / 10 + 1) ? (Number(data.count) / 10 + 1).toFixed(0) - 1 : (Number(data.count) / 10 + 1).toFixed(0),
      curr_page: new_state.curr_page - 1
    }));
  }
  function FetchUserPrevInfoFetchFailure(error, statusCode) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      error: error,
      statusCode:statusCode
    }));
  }

  function FetchUserNextDataInfo(data, url) {

    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.user_list),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }

    if (data) {
      requestInfo.endPoint = GenerateSearchURL(data, requestInfo.endPoint);
    }


    if (url) {
      requestInfo.endPoint = url;
    }
    setState(new_state => ({
      ...new_state,
      search_data: data,
      current: requestInfo.endPoint,
      loading: true,
    }));
    InvokeApi(requestInfo, FetchUserDataNextInfoFetchSuccess, FetchUserDataNextInfoFetchFailure);

  }

  function FetchUserDataNextInfoFetchSuccess(data) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      user_list: data.results,
      count: data.count,
      next: data.next ? properties.api.baseURL + data.next : null,
      previous: data.previous ? properties.api.baseURL + data.previous : null,
      total_page: Number.isInteger(Number(data.count) / 10) ? (Number(data.count) / 10).toFixed(0) : (Number(data.count) / 10 + 1).toFixed(0) > (Number(data.count) / 10 + 1) ? (Number(data.count) / 10 + 1).toFixed(0) - 1 : (Number(data.count) / 10 + 1).toFixed(0),
      curr_page: new_state.curr_page + 1
    }));
  }
  function FetchUserDataNextInfoFetchFailure(error, statusCode) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      error: error,
      statusCode:statusCode
    }));
  }


  function FetchUserPageDataInfo(enteredPageNumber) {

    var requestInfo = {
      endPoint: GenerateURL({}, properties.api.user_list),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    }

    if (enteredPageNumber > 1) {
            requestInfo.endPoint =
                requestInfo.endPoint +
                "?limit=10&offset=" +
                (enteredPageNumber - 1) * 10;
    }
    var current_page = enteredPageNumber;
    setState(new_state => ({
      ...new_state,
      search_data: data,
      current: requestInfo.endPoint,
      loading: true,
    }));
    InvokeApi(requestInfo,(response) => FetchUserDataPageInfoFetchSuccess(response, current_page), FetchUserDataPageInfoFetchFailure);

  }

  function FetchUserDataPageInfoFetchSuccess(data,current_page) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      user_list: data.results,
      count: data.count,
      next: data.next ? properties.api.baseURL + data.next : null,
      previous: data.previous ? properties.api.baseURL + data.previous : null,
      total_page: Number.isInteger(Number(data.count) / 10) ? (Number(data.count) / 10).toFixed(0) : (Number(data.count) / 10 + 1).toFixed(0) > (Number(data.count) / 10 + 1) ? (Number(data.count) / 10 + 1).toFixed(0) - 1 : (Number(data.count) / 10 + 1).toFixed(0),
      curr_page: Number(current_page)
    }));
  }
  function FetchUserDataPageInfoFetchFailure(error, statusCode) {
    setState(new_state => ({
      ...new_state,
      loading: false,
      error: error,
      statusCode:statusCode
    }));
  }

  const invokeSearch = (data) => {
    fetchAllUsers(data);
  }
  const apiUrlForUpdate = properties.api.inactive_user;
  const inactiveUserEntry = (id, status) => {
    console.log(status, "Fdsadfsafsa")
    if (!status) {
      var post_url = GenerateURL({ id: id }, properties.api.active_user);
    } else {
      var post_url = GenerateURL({ id: id }, apiUrlForUpdate);
    }
    showSnackbar("info", "Marking the user as inactive...");
    PostData(post_url, {}, postDeleteSuccess, postDeleteFail);
    setState(new_state => ({
      ...new_state,
      show_loading_icon: true,
      post_request: "SENDING"
    }))
  }
  const postDeleteSuccess = (response) => {
    showSnackbar("success", "User has been marked as inactive.");
    console.log(response, "responseresponse")
    setState(new_state => ({
      ...new_state,
      show_loading_icon: false,
      post_request: "SUCCESS"
    }))
    fetchAllUsers(null, state.current);
  }
  const postDeleteFail = (error) => {
    showSnackbar("error", "Failed to mark the user as inactive.");
    console.log(error, "responseresponse")
    setState(new_state => ({
      ...new_state,
      show_loading_icon: false,
      post_request: "FAILED"
    }))
  }

  const [clear_child_search, set_clear_child_state] = useState({
    clear_state: false,
  });
  const clear_search_params = () => {
    set_clear_child_state({
      ...clear_child_search,
      clear_state: true,
    });
  };
  const handleClose = () => {
    setState(new_state => ({
      ...new_state,
      post_request: null
    }))
  }

  var data = [
    ['John Doe', 'example@example.com', 'sample-policy-1|sample-policy-2', 'dev-user-group|dev-user-group2', 'BUILDPIPER', '0', '0']
  ];


  function download_csv() {
    var csv = 'Name,Email,Policies,User Group,Provider,Active,SuperUser\n';

    data.forEach(function (row) {
      csv += row.join(',');
      csv += "\n";
    });

    var hiddenElement = document.createElement('a');
    hiddenElement.href = 'data:text/csv;charset=utf-8,' + encodeURI(csv);
    hiddenElement.target = '_blank';
    hiddenElement.download = 'sample-data.csv';
    hiddenElement.click();
  }

  const downloadPolicyDump = () => {
    const requestInfo = {
      endPoint: GenerateURL({}, properties.api.all_user_list+"&unmasking=true"),
      httpMethod: "GET",
      httpHeaders: { "Content-Type": "application/json" }
    };

    InvokeApi(requestInfo, handlePolicyDumpSuccess, handlePolicyDumpFailure);
  };

  function convertArrayToString(array) {
    return array.join('/');
  }

  // Function to handle successful policy dump API call
  const handlePolicyDumpSuccess = (data) => {

    let csv = 'Name,Email,Policies,User Group,Provider,Active,SuperUser\n';

    data.results.forEach((user) => {
      let policy_updated = convertArrayToString(user.policy)
      let user_group_updated = convertArrayToString(user.user_group)
      csv += `${user.name},${user.email},${policy_updated},${user_group_updated},${user.provider},${user.is_active},${user.is_superuser}\n`;
    });

    const hiddenElement = document.createElement('a');
    hiddenElement.href = 'data:text/csv;charset=utf-8,' + encodeURI(csv);
    hiddenElement.target = '_blank';
    hiddenElement.download = 'policy_dump.csv';
    hiddenElement.click();
  };

  // Function to handle failed policy dump API call
  const handlePolicyDumpFailure = (error) => {
    console.error("Error fetching policy dump:", error);
    // Handle error accordingly
  };


  const handleAddUserForm = () => {
    setOpen(true);
    setSource('new user');
    setGetSuperAdmin({});
  };

  const checkCallingState = (user, item) => {
    setSource(user);
    setOpen(true);
    setGetSuperAdmin(item);
  }

  const openDownloadPopup = () => {
    setDownloadOpen(!downloadOpen)
  }

  return (
    <div className="pd-20" style={{ backgroundColor: '#fff', height: 'calc(100vh - 70px)' }}>
      <div className="head d-flex align-center space-between mb-20" >
        <PageHeader
          heading='Manage Users'
          subHeading='Displaying overall summary of Users'
          icon='ri-user-settings-line'
          primaryButton={null}
        />

        <div className="btn-group btn-icon-group bg-white" style={{ position: "relative" }}>
          <button className="btn btn-default" onClick={openDownloadPopup}>{/* btn btn-default */}
            <span className="material-icons material-symbols-outlined icon-color">
              file_download
            </span>
          </button>
          {
            downloadOpen &&
            <div className='download-options' ref={downloadOptionsRef}>
              <div className='download-menu' onClick={download_csv} role='button' tabIndex={0} onKeyDown={() => { }}>
                <span className='ri-download-2-line' style={{ color: "#5C5C5C", height: "16px", width: "16px", lineHeight: "1" }}></span>
                <p className='option-text'>Download CSV Sample file</p>
              </div>
              <div className='download-menu' style={{ marginTop: "8px" }} onClick={downloadPolicyDump} role='button' tabIndex={0} onKeyDown={() => { }}>
                <span className='ri-download-2-line' style={{ color: "#5C5C5C", height: "16px", width: "16px", lineHeight: "1" }}></span>
                <p className='option-text'>Download User Dump</p>
              </div>
            </div>
          }
          <Tooltip title="Upload User Sheet" >
            <Link className="btn btn-default" to={"/upload-users"}>
              <span className="material-icons material-symbols-outlined icon-color">
                upload_file
              </span>
            </Link>
          </Tooltip>
          <Tooltip title="Add a user">
            <Link className="btn btn-default" onClick={handleAddUserForm}>
              <span className="material-icons material-symbols-outlined  icon-color">
                person_add
              </span>
            </Link>
          </Tooltip>
        </div>
      </div>

      {/* Invite user form component */}
      {open && <InviteUserForm
        open={open}
        setOpen={setOpen}
        source={source}
        get_super_admin={get_super_admin}
        policyList={policyList}
        groupList={groupList}
        checkCallingState={checkCallingState}
        refreshFn={fetchAllUsers} />}

      <Grid container alignItems="center" spacing="2" style={{ marginBottom: '15px' }}>
        <Grid item lg={9} style={{ marginTop: '8px' }} >
          <SearchBar
            search_data={state.search_data}
            default_filter={{ name: "name", label: "Name" }}
            search_call_back={invokeSearch}
            clear_search_callback={fetchAllUsers}
            prev_state={clear_child_search}
            varient={MULTIPLE}
            params_list={filter_list}
          />
        </Grid>

      </Grid>
      <div className={state.loading ? "card d-flex br-8" : "card d-grid br-8"}>
        {
          state.loading ? 
            <div className="m-auto" style={{ alignSelf: 'center', justifySelf: 'center', height: 500 }}>
              <Loading varient="light" />
            </div>
            :
            (state.error || state.error_msg) ? <PageError error={state.error_msg} statusCode={state?.statusCode} /> :

              <>

                <div>

                  {
                    state.user_list.length > 0 ?
                      <EditableTable columnNames={columnNames}
                        actionButton={true}
                        tableActionHandler={editClicked}
                        edit_button_enabled={false}
                        apiUrl={apiUrlForUpdate}
                        variant={"only_post"}
                        apiFetchedData={state.user_list}
                        postRequestOnly={inactiveUserEntry}
                        hitApiOnClick={fetchAssociatedRoleAndProject}
                        checkCallingState={checkCallingState}
                        userInvite={true}
                      />
                      :
                      state.search_query_name || state.search_query_email || state.search_query_status ?
                        <div className='d-flex align-center' style={{ width: '100%', height: "300px" }}>
                          <div className="svg" style={{ margin: 'auto' }}>
                            <div className="servicePanel">
                              <div className="blank-div">
                                <div className="blank-div-text">
                                  No User found with this.
                                  {
                                    state.search_query_name ? " name: " + state.search_query_name :
                                      state.search_query_email ? " email: " + state.search_query_email :
                                        " Status: " + state.search_query_status}
                                </div>
                                <button
                                  className="btn btn-submit"
                                  onClick={() => {
                                    clear_search_params();
                                  }}
                                >
                                  Refresh
                                </button>
                              </div>
                            </div>
                          </div>
                        </div>
                        :
                        <div>
                          No user added yet
                        </div>
                  }

                  {
                    modelOpen.open &&
                    <Dialog
                      fullWidth
                      maxWidth='md'
                      open={modelOpen.open}
                      onClose={closeModel}
                      aria-labelledby="max-width-dialog-title" TransitionComponent={Transition}>

                      <div className='pop-heading' style={{ display: 'flex', background: '#124d9b', fontSize: '20px', justifyContent: 'space-between', paddingRight: '20px' }}>
                        <DialogTitle id="max-width-dialog-title" className='heading'>
                          Add User
                        </DialogTitle>
                        <button className="btn btn-transparent" onClick={() => closeModel()} aria-label="close" style={{ color: '#fff' }}>
                          <span className='ri-close-line font-24'></span>
                        </button>
                      </div>
                      <AddUserForm closeModel={closeModel} />
                    </Dialog>
                  }


                </div>
                <Grid container>
                  <Grid item lg={8}></Grid>
                  <Grid item lg={4}>
                    <Pagination
                      current_count={state.user_list ? `Page ${state.curr_page}` : 0}
                      total_count={state.total_page}
                      current_page_count={state.curr_page}
                      next={state.next}
                      previous={state.previous}
                      on_previous_click={() => { fetchPrevFetchUserDataInfo(null, state.previous) }}
                      on_next_click={() => { FetchUserNextDataInfo(null, state.next) }}
                      on_pageNumber_click={(pageNumber) => { FetchUserPageDataInfo(pageNumber) }}
                    />
                  </Grid>
                </Grid>
              </>

        }
      </div>
  
    </div>
  )
}

export default ManageUsers

