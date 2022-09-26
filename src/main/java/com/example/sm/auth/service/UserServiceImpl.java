package com.example.sm.auth.service;

import com.amazonaws.services.athena.model.InvalidRequestException;
import com.example.sm.auth.decorator.*;
import com.example.sm.auth.enums.UserSortBy;
import com.example.sm.auth.enums.UserStatus;
import com.example.sm.auth.model.UserModel;
import com.example.sm.auth.rabbitmq.UserPublisher;
import com.example.sm.auth.repository.UserRepository;
import com.example.sm.common.constant.MessageConstant;
import com.example.sm.common.decorator.RequestSession;
import com.example.sm.common.decorator.*;
import com.example.sm.common.enums.PasswordEncryptionType;
import com.example.sm.common.enums.Role;
import com.example.sm.common.exception.AlreadyExistException;
import com.example.sm.common.exception.EmptyException;
import com.example.sm.common.exception.InvaildRequestException;
import com.example.sm.common.exception.NotFoundException;
import com.example.sm.common.model.*;
import com.example.sm.common.repository.ImportedDataRepository;
import com.example.sm.common.repository.UserDataRepository;
import com.example.sm.common.service.AdminConfigurationService;
import com.example.sm.common.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.modelmapper.ModelMapper;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements  UserService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    ImportedDataRepository importedDataRepository;

    @Autowired
    UserDataRepository userDataRepository;

    @Autowired
    NullAwareBeanUtilsBean nullAwareBeanUtilsBean;

    @Autowired
    JwtTokenUtil jwtTokenUtil;

    @Autowired
    PasswordUtils passwordUtils;

    @Autowired
    ExcelUtil excelUtil;

    @Autowired
    AdminConfigurationService adminService;

    @Autowired
    Utils utils;

    @Autowired
    NotificationParser notificationParser;

    @Autowired
    UserPublisher userPublisher;
    @Autowired
    ModelMapper modelMapper;
    @Autowired
    RequestSession requestSession;


    @Override
    public UserResponse addOrUpdateUser(UserAddRequest userAddRequest, String id, Role role) throws InvocationTargetException, IllegalAccessException {
        if (id != null) {
            UserModel userResponse1 = getUserModel(id);
            userResponse1.setAddress(userAddRequest.getAddress());
            userResponse1.setUserName(userAddRequest.getUserName());
            userResponse1.setPassword(userAddRequest.getPassword());
            userResponse1.setFirstName(userAddRequest.getFirstName());
            userResponse1.setMiddleName(userAddRequest.getMiddleName());
            userResponse1.setLastName(userAddRequest.getLastName());
            userResponse1.setEmail(userAddRequest.getEmail());
            userRepository.save(userResponse1);
            UserResponse userResponse = new UserResponse();
            nullAwareBeanUtilsBean.copyProperties(userResponse, userResponse1);
            return userResponse;
        } else {
            if (role == null)//check user role
                throw new InvaildRequestException(MessageConstant.ROLE_NOT_FOUND);
        }
        checkUserDetails(userAddRequest);//chcek empty or not


        //convert birthdate type date to localdate
        Date date = userAddRequest.getBirthDate();
        LocalDateTime dates = Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        System.out.println(dates);
        LocalDate curDate = LocalDate.now();
        System.out.println(curDate);

        //set age from birthdate
        UserModel userModel = new UserModel();
        if (userAddRequest.getBirthDate() != null) {
            int age = Period.between(LocalDate.from(dates), curDate).getYears();
            userModel.setAge(age);
            System.out.println(age);
            userRepository.save(userModel);
        }

        nullAwareBeanUtilsBean.copyProperties(userModel, userAddRequest);
        userModel.setRole(role);//set role in database
        userModel.setFullName();//set fullName
        userModel.setCreatedBy(requestSession.getJwtUser().getId());
        System.out.println(userModel.getFullName());
        userRepository.save(userModel);
        UserResponse userResponse = new UserResponse();
        nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
        return userResponse;
    }

    @Override
    public List<UserResponse> getAllUser() throws InvocationTargetException, IllegalAccessException {
        List<UserModel> userModels = userRepository.findAllBySoftDeleteFalse();
        List<UserResponse> userResponses = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userModels)) {
            for (UserModel userModel : userModels) {
                UserResponse userResponse = new UserResponse();
                nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
                userResponses.add(userResponse);
            }
        }
        return userResponses;
    }

    @Override
    public UserResponse getUser(String id) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        userPublisher.publishToQueue(id);
        UserResponse userResponse = new UserResponse();
        nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);

        //data
        //consumer
        return userResponse;
    }

    @Override
    public void deleteUser(String id) {
        UserModel userModel = getUserModel(id);
        userModel.setSoftDelete(true);
        userRepository.save(userModel);
    }

    @Override
    public List<UserResponse> getAllUserWithFilterAndSort(UserFilter filter, FilterSortRequest.SortRequest
            <UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException {
        return userRepository.findAllUserByFilterAndSortAndPage(filter, sort, pagination);
    }

    @Override
    public UserResponse getToken(String id) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        UserResponse userResponse = new UserResponse();
        userResponse.setRole(userModel.getRole());
        JWTUser jwtUser = new JWTUser(id, Collections.singletonList(userResponse.getRole().toString()));
        String token = jwtTokenUtil.generateToken(jwtUser);
        nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
        userResponse.setToken(token);
        return userResponse;
    }

    @Override
    public String getEncryptPassword(String id) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        UserResponse userResponse = new UserResponse();
        userResponse.setUserName(userModel.getUserName());
        userResponse.setPassword(userModel.getPassword());
        if (userModel.getPassword() != null) {
            String password = passwordUtils.encryptPassword(userModel.getPassword());
            userModel.setPassword(password);
            userResponse.setPassword(password);
            userRepository.save(userModel);
            String passwords = userResponse.getPassword();
            nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
            return passwords;
        } else {
            throw new NotFoundException(MessageConstant.PASSWORD_EMPTY);
        }
    }

    @Override
    public UserResponse checkUserAuthentication(String email, String password) throws NoSuchAlgorithmException, InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserEmail(email);
        UserResponse userResponse = new UserResponse();
        userResponse.setPassword(userModel.getPassword());
        String getPassword = userResponse.getPassword();
        System.out.println(getPassword);
        boolean passwords = passwordUtils.isPasswordAuthenticated(password, getPassword, PasswordEncryptionType.BCRYPT);
        if (passwords) {
            userRepository.save(userModel);
            nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
            return userResponse;
        } else {
            throw new NotFoundException(MessageConstant.PASSWORD_NOT_MATCHED);
        }
    }

    @Override
    public String getIdFromToken(String token) {
        String Id = jwtTokenUtil.getUserIdFromToken(token);
        boolean exists = userRepository.existsByIdAndSoftDeleteFalse(Id);
        if (exists) {
            return Id;
        } else {
            throw new InvaildRequestException(MessageConstant.INVAILD_TOKEN);
        }
    }

    @Override
    public UserResponse getValidityOfToken(String token) throws InvocationTargetException, IllegalAccessException {
        UserResponse userResponse = new UserResponse();
        String validatetoken = getIdFromToken(token);
        UserModel userResponse1 = getUserModel(validatetoken);
        userResponse.setId(validatetoken);
        String tokenid = userResponse.getId();
        JWTUser jwtUser = new JWTUser(tokenid, new ArrayList<>());
        boolean getValidate = jwtTokenUtil.validateToken(token, jwtUser);
        System.out.println(getValidate);
        if (getValidate) {
            nullAwareBeanUtilsBean.copyProperties(userResponse, userResponse1);
            return userResponse;
        } else {
            throw new NotFoundException(MessageConstant.TOKEN_NOT_VAILD);
        }
    }

    @Override
    public void login(String email, String password) throws NoSuchAlgorithmException, InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserEmail(email);
        String userpassword = userModel.getPassword();
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        boolean passwords = passwordUtils.isPasswordAuthenticated(password, userpassword, PasswordEncryptionType.BCRYPT);
        if (passwords) {
            EmailModel emailModel = new EmailModel();
            String otp = generateOtp();
            emailModel.setMessage(otp);
            emailModel.setTo(userModel.getEmail());
            emailModel.setCc(adminConfiguration.getTechAdmins());
            emailModel.setSubject("Otp Verification");
            utils.sendEmailNow(emailModel);
            userModel.setOtp(otp);
            userModel.setLogin(true);
            userModel.setUserStatus(UserStatus.ACTIVE);
            Date date = new Date();
            userModel.setLogoutTime(date);
            userRepository.save(userModel);
        } else {
            throw new NotFoundException(MessageConstant.PASSWORD_NOT_MATCHED);
        }
    }

    @Override
    public UserResponse getOtp(String otp, String id) throws InvocationTargetException, IllegalAccessException {
        boolean exists = userRepository.existsByIdAndOtpAndSoftDeleteFalse(id, otp);
        if (exists) {
            UserModel userResponse1 = getUserModel(id);
            UserResponse userResponse = new UserResponse();
            nullAwareBeanUtilsBean.copyProperties(userResponse, userResponse1);
            return userResponse;
        } else {
            throw new NotFoundException(MessageConstant.INVAILD_OTP);
        }
    }

    //pass email
    //user enter email match to database
    //if it is true then otp send
    @Override
    public void forgotPassword(String email) {
        UserModel userModel = getUserEmail(email);
        EmailModel emailModel = new EmailModel();
        String otp = generateOtp();
        emailModel.setMessage(otp);
        emailModel.setTo("sarthak.j@techroversolutions.com");
        emailModel.setSubject("Otp Verification");
        utils.sendEmailNow(emailModel);
        userModel.setOtp(otp);
        userRepository.save(userModel);
    }


    @Override
    public void setPassword(String password, String confirmPassword, String id) {
        if (password.equals(confirmPassword)) {
            UserModel userModel = getUserModel(id);
            String passwords = passwordUtils.encryptPassword(confirmPassword);
            userModel.setPassword(passwords);
            userRepository.save(userModel);
        } else {
            throw new NotFoundException(MessageConstant.PASSWORD_NOT_MATCHED);
        }
    }

    @Override
    public void otpVerifications(String id, String otp) {
        boolean exists = userRepository.existsByIdAndOtpAndSoftDeleteFalse(id, otp);
        if (!exists) {
            throw new NotFoundException(MessageConstant.INVAILD_OTP);
        }
    }

    @Override
    public void changePassword(String password, String confirmPassword, String newPassword, String id) throws NoSuchAlgorithmException {
        UserModel userModel = getUserModel(id);
        String userPassword = userModel.getPassword();
        boolean passwords = passwordUtils.isPasswordAuthenticated(password, userPassword, PasswordEncryptionType.BCRYPT);
        if (passwords) {
            if (newPassword.equals(confirmPassword)) {
                String confirmPasswords = passwordUtils.encryptPassword(confirmPassword);
                userModel.setPassword(confirmPasswords);
                userRepository.save(userModel);
            } else {
                throw new NotFoundException(MessageConstant.PASSWORD_NOT_MATCHED);
            }
        } else {
            throw new NotFoundException(MessageConstant.INVAILD_PASSWORD);
        }
    }

    @Override
    public void logOut(String id) {
        UserModel userModel = getUserModel(id);
        userModel.setLogin(false);
        Date date = new Date();
        userModel.setLogoutTime(date);
        userRepository.save(userModel);
    }

    @Override
    public List<UserResponse> getUserByRole(UserFilter userFilter) {
        return userRepository.getUser(userFilter);
    }


    @Override
    public UserResponse resultDetail(Result result, String id) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        if (!userModel.getRole().equals(Role.STUDENT)) {//role is student or not?
            throw new NotFoundException(MessageConstant.ROLE_NOT_MATCHED);
        }
        checkResultCond(result);   //common condition  check
        result.setDate(new Date());

        List<Result> results = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userModel.getResults())) {
            results = userModel.getResults();
        }

        double cgpi = 0.0;
        if (CollectionUtils.isEmpty(results)) {
            results.add(result);
            cgpi = result.getSpi();
        } else {
            for (Result result1 : results) {
                if (result1.getSemester() == result.getSemester()) {
                    throw new AlreadyExistException(MessageConstant.SEMESTER_EXISTS);
                }
            }
            results.add(result);

            double total = 0, avg = 0;
            for (Result semester : results) {
                total = total + semester.getSpi();
            }
            if (total > 0) {
                avg = total / results.size();
                DecimalFormat df = new DecimalFormat("0.00");
                cgpi = Double.parseDouble(df.format(avg));
            }
        }
        userModel.setCgpi(cgpi);
        userModel.setResults(results);
        userRepository.save(userModel);
        sendResultEmail(userModel, result);//send email
        UserResponse userResponse = new UserResponse();
        nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);
        return userResponse;
    }

    //create method for mail send to user about result
    private void sendResultEmail(UserModel userModel, Result result) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        ResultEmailRequest resultEmailRequest = modelMapper.map(userModel, ResultEmailRequest.class);
        resultEmailRequest.setSemester(Integer.toString(result.getSemester()));
        resultEmailRequest.setSpi(Double.toString(result.getSpi()));
        resultEmailRequest.setCgpi(Double.toString(userModel.getCgpi()));
        try {
            TemplateModel templateModel = adminConfiguration.getNotificationConfiguration().getResultTemplate();
            EmailModel emailModel = notificationParser.parseEmailNotification(templateModel, resultEmailRequest, userModel.getEmail());
            emailModel.setCc(adminConfiguration.getTechAdmins());
            utils.sendEmailNow(emailModel);
        } catch (Exception e) {
            log.error("Error happened while sending result to user :{}", e.getMessage());
        }
    }

    @Override
    public List<UserDetailResponse> getUserResult(UserDetail userDetail) {
        return userRepository.getUserResult(userDetail);
    }

    @Override
    public List<UserResultResponse> getUserResultBySemester(UserResult userResult) {
        return userRepository.getUserResultBySemester(userResult);
    }

    @Override
    public List<UserMinMaxMarkSemResponse> getUserResultByMinMaxSem(UserIdsRequest userIdsRequest) {
        return userRepository.getUserResultByMinMaxMark(userIdsRequest);
    }

    @Override
    public void deleteUserResult(String id, int semester) {
        UserModel userModel = getUserModel(id);
        List<Result> results1 = new ArrayList<>();
        results1 = userModel.getResults();
        System.out.println("size:" + results1.size());
        System.out.println("data:" + results1);
        if (!CollectionUtils.isEmpty(userModel.getResults())) {//check Result empty or not
            for (Result semesters : results1) {
                if (semesters.getSemester() == semester) {
                    //check  users entered semester equals to  database semester
                    boolean result = results1.remove(semesters);
                    if (result) {
                        userRepository.save(userModel);
                    }
                    break;
                }
            }
        } else {
            throw new NotFoundException(MessageConstant.SEMESTER_NOT_FOUND);
        }

    }

    @Override
    public List<UserResultByDateRespose> getUserResultByDate(UserResultByDate userResultByDate) {
        return userRepository.getUserResultByDate(userResultByDate);
    }

    @Override
    public UserResponse updateUserResult(String id, int semester, Resultupdate result) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        List<Result> results1 = new ArrayList<>();
        results1 = userModel.getResults();
        System.out.println("size:" + results1.size());
        UserResponse userResponse = new UserResponse();
        if (!CollectionUtils.isEmpty(userModel.getResults())) {//check Result empty or not
            for (Result semesters : results1) {
                if (semesters.getSemester() == semester) {
                    semesters.setSemester(semester);
                    if (Double.toString(result.getSpi()) != null) {
                        semesters.setSpi(result.getSpi());
                    }
                    if (Integer.toString(result.getYear()) != null) {
                        semesters.setYear(result.getYear());
                    }
                    semesters.setDate(new Date());
                    nullAwareBeanUtilsBean.copyProperties(userModel, semesters);
                    userRepository.save(userModel);
                    nullAwareBeanUtilsBean.copyProperties(userResponse, userModel);

                }
            }
            return userResponse;
        } else {
            throw new NotFoundException(MessageConstant.RESULT_EMPTY);
        }
    }

    @Override
    public List<UserResultByStatus> getUserResultByStatus(UserIdsRequest userIdsRequest) {
        return userRepository.getUserResultByStatus(userIdsRequest);
    }

    @Override
    public Page<UserResultByStatus> getUserResultStatusByPagination(UserIdsRequest userIdsRequest, FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException {
        return userRepository.findUserResultStatusByFilterAndSortAndPage(userIdsRequest, sort, pagination);
    }

    @Override
    public Workbook getUserByExcel(UserFilter userFilter, FilterSortRequest.SortRequest<UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException {
        List<UserResponse> userResponses = getAllUserWithFilterAndSort(userFilter, sort, pagination);
        List<UserResponseExcel> userResponseExcel = new ArrayList<>();
        for (UserResponse userResponse : userResponses) {//useResponses.for
            UserResponseExcel userResponseExcel1 = new UserResponseExcel();
            nullAwareBeanUtilsBean.copyProperties(userResponseExcel1, userResponse);
            userResponseExcel.add(userResponseExcel1);
            log.info("id" + userResponse.getId());
            log.info("name" + userResponse.getFirstName());
            log.info("ids" + userResponseExcel1.getId());
        }
        return ExcelUtil.createWorkbookFromData(userResponseExcel);
    }

    @Override
    public void resultDetailByEmail(String id) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        //check Result empty or not
        if (!CollectionUtils.isEmpty(userModel.getResults())) {
            sendResultDetails(userModel);
        }
    }

    @Override
    public void userUpdate(String id, Role role, UserAddRequest userAddRequest) throws InvocationTargetException, IllegalAccessException {
        UserModel usermodel = getUserModel(id);
        HashMap<String,String> changedProperties = new HashMap<>();
        boolean userUpdate = false ;
        if (role == Role.ADMIN) {
            userUpdate =true;
        }
        else if (role == Role.DEPARTMENT) {
            if (!usermodel.getRole().equals(Role.ADMIN)) {
                userUpdate =true;
            } else {
                throw new NotFoundException(MessageConstant.ROLE_NOT_MATCHED);
            }
        } else if (role == Role.STUDENT) {
            if (usermodel.getRole().equals(Role.STUDENT)) {
                userUpdate =true;
            } else {
                throw new NotFoundException(MessageConstant.ROLE_NOT_MATCHED);
            }
        }
        if(userUpdate){
            updateUserDetail(id, userAddRequest);
            difference(usermodel, userAddRequest,changedProperties);
            emailSend(changedProperties);
        }
        else {
            throw new InvaildRequestException(MessageConstant.ROLE_NOT_MATCHED);
        }
    }


    @Override
    public void userDelete(String id, Role role) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel = getUserModel(id);
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        if (role == Role.ADMIN) {
            userModel.setSoftDelete(true);
            userRepository.save(userModel);
            try {
                EmailModel emailModel = new EmailModel();
                emailModel.setTo(userModel.getEmail());
                emailModel.setCc(adminConfiguration.getTechAdmins());
                emailModel.setMessage("Delete Successfully...");
                emailModel.setSubject("USer Detail");
                utils.sendEmailNow(emailModel);
            } catch (Exception e) {
                log.error("Error happened while sending result to user :{}", e.getMessage());
            }
        } else {
            throw new NotFoundException(MessageConstant.INVAILD_ROLE);
        }
    }

    @Override
    public String uploadFile(MultipartFile uploadfile) throws IOException {
        if (uploadfile.isEmpty()) {
            throw new EmptyException(MessageConstant.FILE_IS_EMPTY);
        }
        saveUploadedFiles(Arrays.asList(uploadfile));
        log.info( "Successfully uploaded - " + uploadfile.getOriginalFilename());
        return  "Successfully uploaded - " + uploadfile.getOriginalFilename();
    }

    @Override
    public UserImportResponse importUsers(MultipartFile file, String id) throws IOException, InvocationTargetException, IllegalAccessException {
        InputStream is = file.getInputStream();
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        ImportedData data = ImportExcelDataHelper.getDataFromExcel(is,extension);
        UserImportedData importedData = modelMapper.map(data, UserImportedData.class);
        if (!CollectionUtils.isEmpty(importedData.getData())){
            for (Map.Entry<String, List<Object>> map : importedData.getData().entrySet()){
                if (!CollectionUtils.isEmpty(map.getValue())) {
                    if (map.getValue().size()>adminConfiguration.getImportRecordLimit()){
                        throw new InvalidRequestException(MessageConstant.RECORD_SIZE_EXCEED);
                    }
                }
            }
        }

        importedData.setImportDate(new Date());
        importedData = importedDataRepository.save(importedData);
        UserImportResponse importResponse = new UserImportResponse();
        importResponse.setMappingHeaders(adminConfiguration.getUserImportMappingFields());
        importResponse.setExcelHeaders(importedData.getHeaders().stream().map(ImportExcelDataHelper::recoverExcelHeader).collect(Collectors.toList()));
        importResponse.setId(importedData.getId());
        return importResponse;
    }

    @Override
    public List<UserDataModel> importUsersVerify(UserImportVerifyRequest verifyRequest) {
        List<UserDataModel> users;
        //check id in database
        UserImportedData data = importedDataRepository.findById(verifyRequest.getId()).orElseThrow(()->new NotFoundException(MessageConstant.NO_RECORD_FOUND));
        //set import date
        Date importDate = new Date();
        users = getUserFromImportedData(data,verifyRequest,importDate);
//        if (userDataRepository.findAllByImportedIdAndSoftDeleteIsFalse(verifyRequest.getId()).isEmpty()){
        setAndGetDuplicateEmailUsers(users);
        users = userDataRepository.saveAll(users);
        if(!CollectionUtils.isEmpty(users)){
            userDataRepository.saveAll(users);
        }
       /* }else{
            users = userDataRepository.findAllByImportedIdAndSoftDeleteIsFalse(verifyRequest.getId());
        }*/

        return users;
    }

    @Override
    public List<UserResponse> importDataInUser(UserIdsRequest userIdsRequest) throws InvocationTargetException, IllegalAccessException {
       List<UserDataModel> userDataModel= getUserDataModel(userIdsRequest.getUserId());
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
       log.info("--------------start---------------");
       List<UserResponse> userResponses = new ArrayList<>();
       log.info("import data added in list:{}",userDataModel.size());
        for (UserDataModel dataModel : userDataModel) {
            log.info("email:{}",dataModel.getEmail());
            boolean exists = userRepository.existsByEmailAndSoftDeleteFalse(dataModel.getEmail());
            if(exists) {
                throw  new AlreadyExistException(MessageConstant.EMAIL_NAME_EXISTS);
            }
            else{
                UserModel userModel1= modelMapper.map(dataModel,UserModel.class);
                userModel1.setUserStatus(UserStatus.INVITED);
                try {
                    EmailModel emailModel = new EmailModel();
                    emailModel.setTo(dataModel.getEmail());
                    emailModel.setCc(adminConfiguration.getTechAdmins());
                    emailModel.setMessage("your details stored in the system");
                    emailModel.setSubject("User Details");
                    utils.sendEmailNow(emailModel);
                } catch (Exception e) {
                    log.error("Error happened while sending result to user :{}", e.getMessage());
                }
                userRepository.save(userModel1);
                UserResponse userResponse = modelMapper.map(userModel1, UserResponse.class);
                userResponses.add(userResponse);
            }
        }
        return userResponses;
    }

    @Override
    public void deleteUserInXls(String id) {
      UserDataModel userDataModel= getUserData(id);
      userDataModel.setSoftDelete(true);
      userDataRepository.save(userDataModel);
    }

    @Override
    public void getUserPassword(String userName, String password, String confirmPassword) throws InvocationTargetException, IllegalAccessException {
        UserModel userModel= getUserName(userName);
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        if(password.equals(confirmPassword)){
            String passwords = passwordUtils.encryptPassword(confirmPassword);
            log.info("password:{}",passwords);
            userModel.setPassword(passwords);
            userRepository.save(userModel);
            EmailModel emailModel = new EmailModel();
            String otp = generateOtp();
            userModel.setOtp(otp);
            emailModel.setMessage(utils.sendOtp(userModel,confirmPassword));
            emailModel.setTo(userModel.getEmail());
            emailModel.setCc(adminConfiguration.getTechAdmins());
            emailModel.setSubject("Otp Verification");
            utils.sendEmailNow(emailModel);
        }
    }

    @Override
    public void sendMailToInvitedUser(UserStatus userStatus) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        if(userStatus.equals(UserStatus.INVITED)){
            List<UserModel> userModel= userRepository.findByUserStatusAndSoftDeleteIsFalse(userStatus);
            for (UserModel model : userModel) {
                UserModel userModel1 = modelMapper.map(model, UserModel.class);
                try {
                    EmailModel emailModel = new EmailModel();
                    emailModel.setMessage("your details stored in the system");
                    emailModel.setTo(userModel1.getEmail());
                    emailModel.setCc(adminConfiguration.getTechAdmins());
                    emailModel.setSubject("User detail");
                    utils.sendEmailNow(emailModel);
                } catch (Exception e) {
                    log.error("Error happened while sending result to user :{}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void checkUserPublisherId(String id) {

    }

    @Override
    public void getPublishedMessage() {

    }

    @Override
    public String sendMessage(String id) {
        log.info("ID : {}",id);
        return id;
    }

    //common method
    private UserModel getUserModel(String id) {
        return userRepository.findByIdAndSoftDeleteIsFalse(id).orElseThrow(() -> new NotFoundException(MessageConstant.USER_ID_NOT_FOUND));
    }

    private UserModel getUserName(String userName) {
        return userRepository.findByUserNameAndSoftDeleteIsFalse(userName).orElseThrow(() -> new NotFoundException(MessageConstant.USER_ID_NOT_FOUND));
    }

    private List<UserDataModel> getUserDataModel(Set<String> userId){
        return userDataRepository.findByIdInAndSoftDeleteIsFalse(userId);
    }

    private UserDataModel getUserData(String id){
        return userDataRepository.findByIdAndSoftDeleteIsFalse(id).orElseThrow(() -> new NotFoundException(MessageConstant.USER_ID_NOT_FOUND));
    }

    private UserModel getUserEmail(String email) {
        return userRepository.findByEmailAndSoftDeleteIsFalse(email).orElseThrow(() -> new NotFoundException(MessageConstant.EMAIL_NOT_FOUND));
    }

    public String generateOtp() {
        Random rnd = new Random();
        int number = rnd.nextInt(999999);
        // this will convert any number sequence into 6 character.
        String otp = String.format("%06d", number);
        return otp;
    }

    public void checkUserDetails(UserAddRequest userAddRequest) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        if ((StringUtils.isEmpty(userAddRequest.getFirstName()) || (userAddRequest.getFirstName().matches(adminConfiguration.getNameRegex())))) {
            throw new InvaildRequestException(MessageConstant.FIRSTNAME_NOT_EMPTY);
        }
        if ((StringUtils.isEmpty(userAddRequest.getMiddleName()) || (userAddRequest.getMiddleName().matches(adminConfiguration.getNameRegex())))) {
            throw new InvaildRequestException(MessageConstant.MIDDLENAME_NOT_EMPTY);
        }
        if ((StringUtils.isEmpty(userAddRequest.getLastName()) || (userAddRequest.getLastName().matches(adminConfiguration.getNameRegex())))) {
            throw new InvaildRequestException(MessageConstant.LASTNAME_NOT_EMPTY);
        }
        if (StringUtils.isEmpty(userAddRequest.getUserName())) {
            throw new InvaildRequestException(MessageConstant.USERNAME_NOT_EMPTY);
        }
        if (StringUtils.isEmpty(userAddRequest.getEmail())) {
            throw new InvaildRequestException(MessageConstant.EMAIL_NOT_FOUND);
        }
        if (userRepository.existsByEmailAndSoftDeleteFalse(userAddRequest.getEmail())) {
            throw new AlreadyExistException(MessageConstant.EMAIL_NAME_EXISTS);
        }
        if (StringUtils.isEmpty(userAddRequest.getEmail()) &&
                CollectionUtils.isEmpty(adminConfiguration.getRequiredEmailItems())) {
            throw new InvaildRequestException(MessageConstant.EMAIL_EMPTY);
        }
        if (!userAddRequest.getEmail().matches(adminConfiguration.getRegex())) {
            throw new InvaildRequestException(MessageConstant.EMAIL_FORMAT_NOT_VALID);
        }

        if (!userAddRequest.getPassword().matches(adminConfiguration.getPasswordRegex())) {
            throw new InvaildRequestException(MessageConstant.INVAILD_PASSWORD);
        }
        if (!userAddRequest.getMobileNo().matches(adminConfiguration.getMoblieNoRegex())) {
            throw new InvaildRequestException(MessageConstant.INVAILD_MOBILENO);
        }
        if (StringUtils.length(userAddRequest.getAddress().getZipCode()) > 7) {
            throw new InvaildRequestException(MessageConstant.INVAILD_ZIPCODE);
        }
    }

    public void checkResultCond(Result result) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        String sem = String.valueOf(result.getSemester());
        if (!sem.matches(adminConfiguration.getSemesterRegex())) {
            throw new InvaildRequestException(MessageConstant.INVAILD_SEMESTER);
        }
        if (result.getSpi() > 10) {
            throw new InvaildRequestException(MessageConstant.INVAILD_SPI);
        }
        int year = Year.now().getValue();
        System.out.println("year" + year);
        if (result.getYear() > year) {
            throw new InvaildRequestException(MessageConstant.INVAILD_YEAR);
        }
    }


    private void sendResultDetails(UserModel userModel) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        List<Result> results = userModel.getResults();
        List<ResultDetailsResponseByEmail> response = new ArrayList<>();
        ResultDetailsResponseByEmail responseByEmail = new ResultDetailsResponseByEmail();
        for (Result result1 : results) {
            responseByEmail.setCgpi(userModel.getCgpi());
            responseByEmail.setSemester(result1.getSemester());
            responseByEmail.setSpi(result1.getSpi());
            response.add(responseByEmail);
        }
        try {
            EmailModel emailModel = new EmailModel();
            emailModel.setTo(userModel.getEmail());
            emailModel.setCc(adminConfiguration.getTechAdmins());
            emailModel.setMessage(utils.generateReportMessage(userModel.getResults(), userModel.getCgpi()));
            emailModel.setSubject("Result Details");
            utils.sendEmailNow(emailModel);
        } catch (Exception e) {
            log.error("Error happened while sending result to user :{}", e.getMessage());
        }
    }

    public void updateUserDetail(String id, UserAddRequest userAddRequest) throws InvocationTargetException, IllegalAccessException {
        UserModel userResponse1 = getUserModel(id);
        if (userResponse1 != null) {
            if (userAddRequest.getFirstName() != null) {
                userResponse1.setFirstName(userAddRequest.getFirstName());
            }
            if (userAddRequest.getMiddleName() != null) {
                userResponse1.setMiddleName(userAddRequest.getMiddleName());
            }
            if (userAddRequest.getLastName() != null) {
                userResponse1.setLastName(userAddRequest.getLastName());
            }
            if (userAddRequest.getEmail() != null) {
                userResponse1.setEmail(userAddRequest.getEmail());
            }
            if (userAddRequest.getPassword() != null) {
                userResponse1.setPassword(userAddRequest.getPassword());
            }
            if (userAddRequest.getBirthDate() != null) {
                userResponse1.setBirthDate(userAddRequest.getBirthDate());
            }
            if (userAddRequest.getAddress() != null) {
                userResponse1.setAddress(userAddRequest.getAddress());
            }
            if (userAddRequest.getMobileNo() != null) {
                userResponse1.setMobileNo(userAddRequest.getMobileNo());
            }
            userRepository.save(userResponse1);
        }
    }

    public void emailSend(HashMap<String,String> changedProperties) throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = adminService.getConfiguration();
        try {
            EmailModel emailModel = new EmailModel();
            emailModel.setTo("dency05@gmail.com");
            emailModel.setCc(adminConfiguration.getTechAdmins());
            emailModel.setMessage(utils.genearteUpdatedUserDetail(changedProperties));
            emailModel.setSubject("User Details");
            utils.sendEmailNow(emailModel);
            log.info("email send start");
        } catch (Exception e) {
            log.error("Error happened while sending result to user :{}", e.getMessage());
        }
    }

    public HashMap<String,String> difference(UserModel userModel, UserAddRequest userAddRequest,HashMap<String, String> changedProperties) throws IllegalAccessException, InvocationTargetException {
        UserModel userModel1 = new UserModel();
        nullAwareBeanUtilsBean.copyProperties(userModel1, userAddRequest);
        userModel1.setId(userModel.getId());
        for (Field field : userModel.getClass().getDeclaredFields()) {
            // You might want to set modifier to public first (if it is not public yet)
            field.setAccessible(true);
            Object value1 = field.get(userModel);
            Object value2 = field.get(userModel1);
            if (value1 != null && value2 != null) {
                System.out.println(field.getName() + "=" + value1);
                System.out.println(field.getName() + "=" + value2);
                if (!Objects.equals(value1, value2)) {
                    changedProperties.put(field.getName(),value2.toString());
                }
            }
        }
        log.info(changedProperties.toString());
        return changedProperties;
    }

    // Save the uploaded file to this folder
    private static String UPLOADED_FOLDER = "/Downloads";
   public void saveUploadedFiles(List<MultipartFile> files) throws IOException {
        File folder = new File(UPLOADED_FOLDER);
        if (!folder.exists()) {
            folder.mkdir();
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
                // next pls
            }
            byte[] bytes = file.getBytes();
            Path path = Paths.get(UPLOADED_FOLDER + file.getOriginalFilename());
            Files.write(path, bytes);
        }
    }

    private List<UserDataModel> getUserFromImportedData(UserImportedData data, UserImportVerifyRequest verifyRequest, Date importDate) throws IndexOutOfBoundsException{
        //new list
        List<UserDataModel> userDataModels = new ArrayList<>();
        //set
        Set<String> excelKeys = verifyRequest.getMapping().keySet();
        if(CollectionUtils.isEmpty(excelKeys)) {
            return userDataModels;
        }
        List<Object> firstColumnData = data.getData().get(excelKeys.iterator().next());
        for(int i = 0;i< firstColumnData.size();i++){
            Map<String,Object> currentUserData = ImportExcelDataHelper.getMapData(i,data.getData(),excelKeys,verifyRequest.getMapping());
            UserDataModel currentUser = modelMapper.map(currentUserData,UserDataModel.class);
            currentUser.setImportFromExcel(true);
            currentUser.setImportDate(importDate);
            currentUser.setImportedId(verifyRequest.getId());
            //currentUser.setDuplicateEmail(checkDuplicateEmail(currentUser.getEmail()));
            userDataModels.add(currentUser);
        }
        return userDataModels;
    }

    private List<UserDataModel> setAndGetDuplicateEmailUsers(List<UserDataModel> users){
        List<UserDataModel> userList = new LinkedList<>();
        Set<String> uniqueEmails = new HashSet<>();
        Set<String> duplicateEmails = new HashSet<>();
        if(!CollectionUtils.isEmpty(users)) {  //database empty
            for (UserDataModel user : users) { //
                log.info("---------------Start--------------------");
                log.info("Actual Email: {}",user.getEmail());
                user.setDuplicateEmail(checkDuplicateEmail(user.getEmail()));
                log.info("Duplicate Email Status: {}",user.isDuplicateEmail());
                if(!user.isEmptyEmail()){
                    if(!uniqueEmails.contains(user.getEmail().toLowerCase())){
                        log.info("Unique Email: {}",user.getEmail());
                        uniqueEmails.add(user.getEmail().toLowerCase());
                    }else {
                        log.info("Duplicate Email: {}",user.getEmail());
                        duplicateEmails.add(user.getEmail().toLowerCase());
                    }
                }
                log.info("------------------End-----------------");
            }
            for (UserDataModel user : users) {
                log.info("------------Start1---------------");
                if(!user.isEmptyEmail() && !user.isDuplicateEmail()){
                    log.info("User Email Is Not Duplicate: {}",user.getEmail());
                    user.setDuplicateEmail(duplicateEmails.contains(user.getEmail().toLowerCase()));
                    log.info("Check Email Status: {}",user.getEmail());
                }
                if(user.isDuplicateEmail()){
                    log.info("Added Duplicate Email In List: {}",user.getEmail());
                    userList.add(user);
                }
                log.info("------------End1---------------");
            }
        }
        return userList;
    }

    private boolean checkDuplicateEmail(String email) {
        return userDataRepository.existsByEmailAndSoftDeleteFalse(email);
    }


}


