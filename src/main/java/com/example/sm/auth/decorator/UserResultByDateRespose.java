package com.example.sm.auth.decorator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResultByDateRespose {
     String  id;
     Double resultOfDate;
     String resultDate;
   List<Result> result;
}
