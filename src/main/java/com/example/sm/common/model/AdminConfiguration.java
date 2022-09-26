package com.example.sm.common.model;

import com.example.sm.common.decorator.NotificationConfiguration;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Component;


import java.util.*;

@Document(collection= "admin_config")
@Data
@AllArgsConstructor
@Component
public class AdminConfiguration{

    @Id
    String id;
    String from;
    String username;
    String password;
    Set<String> requiredEmailItems = getRequiredItems();
    String host;
    String port;
    boolean smptAuth;
    boolean starttls;
    String nameRegex;
    String emailRegex;
    String regex;
    String semesterRegex;
    String spiRegex;
    String passwordRegex;
    String moblieNoRegex;
    String CreatedBy;
    String UpdatedBy;
    Date Created;
    Date Updated;
    int importRecordLimit = 100;
    Set<String> extensions = getExtensionsData();
    Set<String> techAdmins = gettechadminEmails();
    NotificationConfiguration notificationConfiguration;
    Map<String,String> userImportMappingFields = new LinkedHashMap<>();



    private Set<String> getRequiredItems() {
        Set<String> requiredEmailItems = new HashSet<>();
        requiredEmailItems.add("@");
        return requiredEmailItems;
    }

    private Set<String> getExtensionsData() {
        Set<String> extensions = new HashSet<>();
        extensions.add("gmail.com");
        extensions.add("yahoo.com");
        return extensions;
    }
    private Set<String> gettechadminEmails() {
        Set<String> emails = new HashSet<>();
        emails.add("dency.g@techroversolutions.com");
        //emails.add("sarthak.j@techroversolutions.com");
        emails.add("savan.p@techroversolutions.com");
        return emails;
    }

    public AdminConfiguration(){
        this.from = "developer@techroversolutions.com";
        this.username = "developer@techroversolutions.com";
        this.password = "Techrover@2022";
        this.host = "smtp.office365.com";
        this.port="587";
        this.smptAuth = true;
        this.starttls = true;
        this.extensions = getExtensionsData();
        this.emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        this.regex= "^(?=.{1,64}@)[a-z0-9_-]+(\\.[a-z0-9_-]+)*@"
                + "[^-][a-z0-9-]+(\\.[a-z0-9-]+)*(\\.[a-z]{2,})$";
        this.semesterRegex = "^[0-8]{1}$";
        this.spiRegex = "^[0-10]{2}$";
        this.passwordRegex ="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[#$@!%&*?])[A-Za-z\\d#$@!%&*?]{8,15}$";
        this.moblieNoRegex="^[0-9]{10}$";
        this.nameRegex ="^[0-9#$@!%&*?.-_=]{1,15}$";
        this.notificationConfiguration= new NotificationConfiguration();

        userImportMappingFields.put("First Name","firstName");
        userImportMappingFields.put("Last Name","lastName");
        userImportMappingFields.put("Middle Name","middleName");
        userImportMappingFields.put("Address","Address");
        userImportMappingFields.put("City","city");
        userImportMappingFields.put("State","state");
        userImportMappingFields.put("Email","email");
        userImportMappingFields.put("UserName","userName");
        userImportMappingFields.put("MobileNo","mobileNo");
    }
}
