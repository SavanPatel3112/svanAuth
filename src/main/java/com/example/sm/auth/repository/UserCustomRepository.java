package com.example.sm.auth.repository;

import com.example.sm.auth.decorator.*;
import com.example.sm.common.decorator.FilterSortRequest;
import com.example.sm.auth.enums.UserSortBy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public interface UserCustomRepository {
    List<UserResponse> findAllUserByFilterAndSortAndPage(UserFilter filter, FilterSortRequest.SortRequest
            <UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException;

    List<UserResponse> getUser(UserFilter userFilter);
    List<UserDetailResponse> getUserResult(UserDetail userDetail);
    List<UserResultResponse> getUserResultBySemester(UserResult userResult);
    List<UserMinMaxMarkSemResponse> getUserResultByMinMaxMark(UserIdsRequest userIdsRequest);
    List<UserResultByDateRespose> getUserResultByDate(UserResultByDate userResultByDate );
    List<UserResultByStatus> getUserResultByStatus(UserIdsRequest userIdsRequest);
    Page<UserResultByStatus> findUserResultStatusByFilterAndSortAndPage(UserIdsRequest userIdsRequest, FilterSortRequest.SortRequest
            <UserSortBy> sort, PageRequest pagination) throws InvocationTargetException, IllegalAccessException;

}


