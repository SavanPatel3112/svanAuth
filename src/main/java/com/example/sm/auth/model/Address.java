package com.example.sm.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@AllArgsConstructor
public class Address {
   String address;
   String address2;
   String address3;
   String city;
   String state;
   String zipCode;
}
