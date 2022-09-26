package com.example.sm.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Component
public class EmailModel {
    String to;
    String subject;
    String Message;
String templateName;
   Set<String> bcc;
   Set<String> cc;
}
