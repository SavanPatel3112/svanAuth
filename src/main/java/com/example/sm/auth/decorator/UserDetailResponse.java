package com.example.sm.auth.decorator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailResponse {
    String firstName;
    String middleName;
    String lastName;
    String email;
    String mobileNo;

    double cgpa;
    //List<Result> results;
    Result results;
}
