package com.example.sm.auth.controller;

import com.example.sm.auth.decorator.*;
import com.example.sm.auth.enums.UserSortBy;
import com.example.sm.auth.enums.UserStatus;
import com.example.sm.auth.service.UserService;
import com.example.sm.common.decorator.*;
import com.example.sm.common.enums.Role;
import com.example.sm.common.model.UserDataModel;
import com.example.sm.common.utils.Access;
import com.example.sm.common.utils.ExcelUtil;
import lombok.SneakyThrows;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;


@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    GeneralHelper generalHelper;

    @Autowired
    RequestSession requestSession;


    //1.update
    //pass id, check  if id not null then perform update else add perform
    //check (user entered  id and database id) matched--> setvariable --> save --> copy -->return userResponse
    //2.add
    //pass role
    //copy properties userAddRequest to userModel
    //set role in database
    //save in database
    //copy properties userModel to userResponse
    //return userResponse

    @SneakyThrows
    @RequestMapping(name = "addOrUpdateUser", value = "/addOrUpdate", method = RequestMethod.POST)
    @Access(levels = {Role.ADMIN, Role.STUDENT, Role.DEPARTMENT})
    public DataResponse<UserResponse> addOrUpdateUser(@RequestBody UserAddRequest userAddRequest, @RequestParam(required = false) String id, @RequestParam(required = false) Role role) {
        DataResponse<UserResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.addOrUpdateUser(userAddRequest, id, role));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //1.check All by softDeleteFalse
    //2.check if database is empty or not --> not empty --> copy properties -->add userResponse -->return userRe
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getAllUser", value = "/getAll", method = RequestMethod.GET)
    public ListResponse<UserResponse> getAllUser() {
        ListResponse<UserResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.getAllUser());
        listResponse.setStatus(Response.getSuccessResponse());
        return listResponse;
    }

    //1.pass id
    //2.check user entered id and database id and softDeleteFalse --> matched --> setSoftDelete(true)-->save userModel
    //3.not matched then show error:  user id not found
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getUser", value = "/get{id}", method = RequestMethod.GET)
    public DataResponse<UserResponse> getUser(@RequestParam String id) {
        DataResponse<UserResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.getUser(id));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //1.pass id
    //2.check user entered id and database id and softDeleteFalse --> matched --> setSoftDelete(true)-->save userModel
    //3.not matched then show error:  user id not found
    @SneakyThrows
    @Access(levels = {Role.ADMIN})
    @RequestMapping(name = "deleteUser", value = "/delete{id}", method = RequestMethod.GET)
    public DataResponse<Object> deleteUser(@RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.deleteUser(id);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass FilterSortRequest in (UserFilter,UserSortBy)
    //create UserCustomRepository interface and create method findAllUserByFilterAndSortAndPage
    //UserRepository extend UserCustomRepository
    //UserService create getAllUserWithFilterAndSort method and implement in UserServiceImpl

    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getUserByPagination", value = "/getAll/filter", method = RequestMethod.POST)
    public ListResponse<UserResponse> getUserByPagination(@RequestBody FilterSortRequest<UserFilter, UserSortBy> filterSortRequest) {
        ListResponse<UserResponse> listResponse = new ListResponse<>();
        UserFilter filter = filterSortRequest.getFilter();
        FilterSortRequest.SortRequest<UserSortBy> sort = filterSortRequest.getSort();
        Pagination pagination = filterSortRequest.getPage();
        PageRequest pageRequest = PageRequest.of(pagination.getPage(), pagination.getLimit());
        List<UserResponse> userResponses = userService.getAllUserWithFilterAndSort(filter, sort, pageRequest);
        listResponse.setData(userResponses);
        listResponse.setStatus(Response.getOhkResponse());
        return listResponse;
    }

    //1.pass id
    //2.check user entered id and database id and softDeleteFalse --> matched -->set role
    //JWT token generate
    //response token.
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getToken", value = "/generateToken", method = RequestMethod.GET)
    public TokenResponse<UserResponse> getToken(@RequestParam String id) {
        TokenResponse<UserResponse> tokenResponse = new TokenResponse<>();
        UserResponse userResponse = userService.getToken(id);
        tokenResponse.setData(userResponse);
        tokenResponse.setStatus(Response.getOkResponse());
        tokenResponse.setToken(userResponse.getToken());
        return tokenResponse;
    }


    //id
    //username n password
    //check password is empty or not
    //if not empty then encrypt (passwordutils)
    //if empty hen though error
    //if password is found then store the encrypted password to the provided ID's user.
    //save to database
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = " getEncryptPassword", value = "/getEncryptPassword", method = RequestMethod.GET)
    public DataResponse<String> getEncryptPassword(@RequestParam String id) {
        DataResponse<String> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.getEncryptPassword(id));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //check password is valid or not.
    //STEPS
    //1. pass email,password
    //2. check email is vaild or not
    //3. get password from database
    //4. PasswordUtils is PasswordAuthenticated method call
    //5. if true (Provide User Details)
    // else Error -> Password not matched
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "checkUserAuthentication", value = "/getPasswords", method = RequestMethod.GET)
    public DataResponse<UserResponse> checkUserAuthentication(@RequestParam String email, @RequestParam String password) {
        DataResponse<UserResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.checkUserAuthentication(email, password));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass token
    //getUserIdFromToken() call from jwtTokenUtil
    //check generate id and database id is matched then show user details
    //if not matched then error
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getIdFromToken", value = "/getIdFromToken", method = RequestMethod.GET)
    public TokenResponse<String> getIdFromToken(@RequestParam String token) {
        TokenResponse<String> tokenResponse = new TokenResponse<>();
        tokenResponse.setData(userService.getIdFromToken(token));
        tokenResponse.setStatus(Response.getOkResponse());
        tokenResponse.setToken(token);
        return tokenResponse;
    }

    //pass token
    //method call getIdFromToken()
    //getid from getIdFromToken()
    //Database id set getid
    //validateToken() method call from jwtTokenUtil
    //check if true than show userdetails else error show
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "getValidityOfToken", value = "/validate/token", method = RequestMethod.GET)
    public TokenResponse<UserResponse> getValidityOfToken(@RequestParam String token) {
        TokenResponse<UserResponse> tokenResponse = new TokenResponse<>();
        tokenResponse.setData(userService.getValidityOfToken(token));
        tokenResponse.setStatus(Response.getTokensucessResponse());
        tokenResponse.setToken(token);
        return tokenResponse;
    }

    //pass email, password
    //check username n password
    //generate random otp and set into emailmodel
    //set message and To into emailmodel
    //call the method sendEmailNow from utils
    //Otp save in userModel
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "uerLogin", value = "/login", method = RequestMethod.GET)
    public DataResponse<Object> userLogin(@RequestParam String email, String password) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.login(email, password);
        dataResponse.setStatus(Response.getLoginResponse());
        return dataResponse;
    }

    //pass id,otp
    //check existsBy(id,otp)
    //if exists then show all details
    //else error
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "otpVerification", value = "/verification/Otp", method = RequestMethod.GET)
    public DataResponse<UserResponse> otpVerification(@RequestParam String otp, @RequestParam String id) {
        DataResponse<UserResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.getOtp(otp, id));
        dataResponse.setStatus(Response.getOtpResponse());
        return dataResponse;
    }

    //10/8/22
    //pass email
    //user enter email match to database
    //if it is true then otp send
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "forgotPassword", value = "/forgotPassword", method = RequestMethod.GET)
    public DataResponse<Object> forgotPassword(@RequestParam String email) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.forgotPassword(email);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass otp
    //check user entered otp to database otp
    //if matched then setPassword api call
    //else generate error

    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "otpVerifications", value = "/otp/verification", method = RequestMethod.GET)
    public DataResponse<Object> otpVerifications(@RequestParam String otp, @RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.otpVerifications(id, otp);
        dataResponse.setStatus(Response.getOtpResponses());
        return dataResponse;
    }


    //pass password, confirm password
    //match password and confirm password is equal or not
    //if match then call login api
    //not match then show error
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "setPassword", value = "/setPassword", method = RequestMethod.GET)
    public DataResponse<Object> setPassword(@RequestParam String password, @RequestParam String confirmPassword, @RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.setPassword(password, confirmPassword, id);
        dataResponse.setStatus(Response.getPasswordResponse());
        return dataResponse;
    }

    //12 aug
    //pass password
    //check to database password
    //new password , confirm password
    //match --> set to database  else---> error
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "changePassword", value = "/changePassword", method = RequestMethod.GET)
    public DataResponse<Object> changePassword(@RequestParam String password, @RequestParam String confirmPassword, @RequestParam String newPassword, @RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.changePassword(password, confirmPassword, newPassword, id);
        dataResponse.setStatus(Response.getPasswordResponse());
        return dataResponse;
    }

    //pass id
    //check to databse id
    //match then set login false
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "logOut", value = "/logOut/{id}", method = RequestMethod.GET)
    public DataResponse<Object> logOut(@RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.logOut(id);
        dataResponse.setStatus(Response.logOutResponse());
        return dataResponse;
    }

    //if user pass role as student then
    //show only student data
    @RequestMapping(name = "getUserByRole", value = "getUser", method = RequestMethod.POST)
    @Access(levels = Role.ANONYMOUS)
    public ListResponse<UserResponse> getUserByRole(@RequestBody UserFilter userFilter) {
        ListResponse<UserResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserByRole(userFilter));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //22/8/22
    //pass id & check with database
    //check user role is Student or not if student then add result to this id
    //check result is empty or not
    //IF empty then add result to new list
    //set cgpa(AVG OF SPI)
    //else result get to database & add result to this
    // set cgpa(AVG OF SPI)
    // check semseter  exists or not if exists than throw error
    @SneakyThrows
    @Access(levels = {Role.ADMIN, Role.STUDENT})
    @RequestMapping(name = "addResult", value = "add/Result", method = RequestMethod.POST)
    public DataResponse<UserResponse> addResult(@RequestBody Result result, @RequestParam String id) {
        DataResponse<UserResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.resultDetail(result, id));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //user pass multiple id and semester
    //show user detail that id
    @SneakyThrows
    @RequestMapping(name = "getUserResult", value = "result", method = RequestMethod.POST)
    public ListResponse<UserDetailResponse> getUserResult(@RequestBody UserDetail userDetail) {
        ListResponse<UserDetailResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserResult(userDetail));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //user pass multiple id and semester
    //match in database, unwind result , and find this result total marks and average // group all of data
    //find total marks and average to this user
    @SneakyThrows
    @RequestMapping(name = "getUserResultBySemester", value = "resultBySemester", method = RequestMethod.POST)
    public ListResponse<UserResultResponse> getUserResultBySemester(@RequestBody UserResult userResult) {
        ListResponse<UserResultResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserResultBySemester(userResult));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //user pass mutiple ids
    //match in database, unwind result, find minimum and maximum spi
    @SneakyThrows
    @RequestMapping(name = "getUserResultByMinMaxMark", value = "resultByMinMaxMark", method = RequestMethod.POST)
    public ListResponse<UserMinMaxMarkSemResponse> getUserResultByMinMaxMark(@RequestBody UserIdsRequest userIdsRequest) {
        ListResponse<UserMinMaxMarkSemResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserResultByMinMaxSem(userIdsRequest));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //user pass id and semester
    //check in database
    //remove in database
    @SneakyThrows
    @Access(levels = {Role.ADMIN, Role.DEPARTMENT})
    @RequestMapping(name = "deleteUserResult", value = "/delete/result", method = RequestMethod.GET)
    public DataResponse<Object> deleteUserResult(@RequestParam String id, @RequestParam int semester) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.deleteUserResult(id, semester);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //user pass date
    //match in database
       //found then show all result of that date

    @SneakyThrows
    @RequestMapping(name = "getUserResultByDate", value = "resultByDate", method = RequestMethod.POST)
    public ListResponse<UserResultByDateRespose> getUserResultByDate(@RequestBody UserResultByDate userResultByDate) {
        ListResponse<UserResultByDateRespose> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserResultByDate(userResultByDate));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //pass id n semester which semester you want to update
    //update result
    @SneakyThrows
    @Access(levels = {Role.ADMIN, Role.DEPARTMENT})
    @RequestMapping(name = "updateUserResult", value = "/update/result", method = RequestMethod.POST)
    public ResultResponse<UserResponse> updateUserResult(@RequestParam String id, @RequestParam int semester, @RequestBody Resultupdate result) {
        ResultResponse<UserResponse> resultResponse = new ResultResponse<>();
        resultResponse.setData(userService.updateUserResult(id, semester, result));
        resultResponse.setStatus(Response.getOkResponse());
        resultResponse.setResult(result);
        return resultResponse;
    }

    //pass mutiple id
    //aggregation operation(branch switch case)
    //set the user result status like(firstClass, secondClass, fourth,fifth)
    @SneakyThrows
    @RequestMapping(name = "getUserResultByStatus", value = "resultByStatus", method = RequestMethod.POST)
    public ListResponse<UserResultByStatus> getUserResultByStatus(@RequestBody UserIdsRequest userIdsRequest) {
        ListResponse<UserResultByStatus> listResponse = new ListResponse<>();
        listResponse.setData(userService.getUserResultByStatus(userIdsRequest));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }


    @SneakyThrows
    @RequestMapping(name = "getUserResultStatusByPagination", value = "/get/filter/userResultStatus", method = RequestMethod.POST)
    public PageResponse<UserResultByStatus> getUserResultStatusByPagination(@RequestBody FilterSortRequest<UserIdsRequest, UserSortBy> filterSortRequest) {
        PageResponse<UserResultByStatus> pageResponse = new PageResponse<>();
        UserIdsRequest userIdsRequest = filterSortRequest.getFilter();
        Page<UserResultByStatus> userResponse = userService.getUserResultStatusByPagination(userIdsRequest,
                filterSortRequest.getSort(),
                generalHelper.getPagination(filterSortRequest.getPage().getPage(), filterSortRequest.getPage().getLimit()));
        pageResponse.setData(userResponse);
        pageResponse.setStatus(Response.getOhkResponse());
        return pageResponse;
    }

    @SneakyThrows
    @RequestMapping(name = "getUserByExcel", value = "/get/user/excel", method = RequestMethod.POST)
    @Access(levels = {Role.ADMIN})
    public ResponseEntity<Resource> getUserByExcel(@RequestBody FilterSortRequest<UserFilter, UserSortBy> filterSortRequest) {
        UserFilter userFilter = filterSortRequest.getFilter();
        Workbook workbook = userService.getUserByExcel(userFilter,
                filterSortRequest.getSort(),
                generalHelper.getPagination(filterSortRequest.getPage().getPage(), filterSortRequest.getPage().getLimit()));
        assert workbook != null;

        ByteArrayResource resource = ExcelUtil.getBiteResourceFromWorkbook(workbook);
        return ResponseEntity.ok()
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + "exported_data_xlsx" + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(resource);
    }


    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "sendUserResultEmail", value = "/send/result/email", method = RequestMethod.GET)
    public DataResponse<Object> sendUserResultEmail(@RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.resultDetailByEmail(id);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }


    //update user detail
    //admin can all user update
    //student only update student user
    //department only update department and student user
    //send mail to user that which item user is updated
    @SneakyThrows
    @Access(levels = Role.ANONYMOUS)
    @RequestMapping(name = "userUpdate", value = "/get{id}/role", method = RequestMethod.POST)
    public DataResponse<Object> userUpdate(@RequestParam String id, @RequestParam Role role, @RequestBody UserAddRequest userAddRequest) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.userUpdate(id, role, userAddRequest);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //only admin can delete user
    @SneakyThrows
    @Access(levels = Role.ADMIN)
    @RequestMapping(name = "userDelete", value = "/delete{id}/role", method = RequestMethod.GET)
    public DataResponse<Object> userDelete(@RequestParam String id, @RequestParam Role role) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.userDelete(id, role);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }


    // Single File upload
    @RequestMapping(name = "uploadFile", value = "/rest/upload", method = RequestMethod.POST)
    @Access(levels = Role.ANONYMOUS)
    public DataResponse<String> uploadFile(@RequestParam("file") MultipartFile uploadfile) throws IOException {
        DataResponse<String> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.uploadFile(uploadfile));
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass excel file
    //excel file data import into database
    @SneakyThrows
    @RequestMapping(name = "importUsers", value = {"/import"}, method = RequestMethod.POST, consumes = {"multipart/form-data"})
    @Access(levels = {Role.ADMIN})
    public DataResponse<UserImportResponse> importUsers(@RequestParam(value = "file") MultipartFile file) {
        DataResponse<UserImportResponse> dataResponse = new DataResponse<>();
        dataResponse.setData(userService.importUsers(file, requestSession.getJwtUser().getId()));
        dataResponse.setStatus(Response.getSuccessResponse());
        return dataResponse;
    }


    //pass FirstName: firstname, as it all header and pass id
    //check  id in importdataUser
    //set the value in database
    //check email is exists or not in imported xlxs data if email is same then throw error
    //add all data in user_imported_data collection(json formt)
    @RequestMapping(name = "importUsersVerify", value = {"/import/user/verify"}, method = RequestMethod.POST)
    @Access(levels = {Role.ADMIN})
    public ListResponse<UserDataModel> importUsersVerify(@RequestBody UserImportVerifyRequest verifyRequest) {
        ListResponse<UserDataModel> listResponse = new ListResponse<>();
        listResponse.setData(userService.importUsersVerify(verifyRequest));
        listResponse.setStatus(Response.getOkResponse());
        return listResponse;
    }

    //pass mutiple id
    //check userDataRepository ma--> true

    @SneakyThrows
    @Access(levels = Role.ADMIN)
    @RequestMapping(name = "importDataInUser", value = "/import/data/user", method = RequestMethod.POST)
    public ListResponse<UserResponse> importDataInUser(@RequestBody UserIdsRequest userIdsRequest) {
        ListResponse<UserResponse> listResponse = new ListResponse<>();
        listResponse.setData(userService.importDataInUser(userIdsRequest));
        listResponse.setStatus(Response.getOtpResponse());
        return listResponse;
    }

    @SneakyThrows
    @Access(levels = {Role.ADMIN})
    @RequestMapping(name = "deleteUserInXls", value = "/delete/user/xls", method = RequestMethod.GET)
    public DataResponse<Object> deleteUserInXls(@RequestParam String id) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.deleteUserInXls(id);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass and check username, password, confirmPassword
    //match send otp in email to user
    @SneakyThrows
    @Access(levels = {Role.ADMIN})
    @RequestMapping(name = "getUserPassword", value = "/username/password", method = RequestMethod.GET)
    public DataResponse<Object> getUserPassword(@RequestParam String userName, @RequestParam String password, @RequestParam String confirmPassword) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.getUserPassword(userName, password, confirmPassword);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    //pass role
    //if role is invited then send mail to all invited user
    @SneakyThrows
    @Access(levels = {Role.ADMIN})
    @RequestMapping(name = "sendMailToInvitedUser", value = "/sendMail", method = RequestMethod.GET)
    public DataResponse<Object> sendMailToInvitedUser(UserStatus userStatus) {
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.sendMailToInvitedUser(userStatus);
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }

    @SneakyThrows
    @Access(levels = {Role.ADMIN})
    @RequestMapping(name = "getPublishedMessage", value = "/message", method = RequestMethod.GET)
    public DataResponse<Object>getPublishedMessage(){
        DataResponse<Object> dataResponse = new DataResponse<>();
        userService.getPublishedMessage();
        dataResponse.setStatus(Response.getOkResponse());
        return dataResponse;
    }
}
