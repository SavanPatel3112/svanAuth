package com.example.sm.common.decorator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserImportVerifyRequest {
    String id;
    Map<String,String> mapping;
}
