package com.example.sm.common.service;

import com.example.sm.common.repository.AdminRepository;
import com.example.sm.common.decorator.AdminResponse;
import com.example.sm.common.decorator.NullAwareBeanUtilsBean;
import com.example.sm.common.model.AdminConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.InvocationTargetException;

@Service
public class AdminConfigurationServiceImpl implements AdminConfigurationService {

    @Autowired
    AdminRepository adminRepository;
    @Autowired
    NullAwareBeanUtilsBean nullAwareBeanUtilsBean;

    @Override
    public AdminResponse addConfiguration() throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration = new AdminConfiguration();
        if (!CollectionUtils.isEmpty(adminRepository.findAll())){
           adminConfiguration = adminRepository.findAll().get(0);
        }else{
           adminConfiguration = adminRepository.save(adminConfiguration);
        }
        AdminResponse adminResponse= new AdminResponse();
        nullAwareBeanUtilsBean.copyProperties(adminResponse,adminConfiguration);
        return adminResponse;
    }

    @Override
    public AdminConfiguration getConfiguration() throws InvocationTargetException, IllegalAccessException {
        AdminConfiguration adminConfiguration=new AdminConfiguration();
        if (adminRepository.findAll().isEmpty()){
            adminRepository.save(adminConfiguration);
        }else {
            AdminConfiguration adminConfiguration1 = adminRepository.findAll().get(0);
            nullAwareBeanUtilsBean.copyProperties(adminConfiguration1,adminConfiguration);
            adminRepository.save(adminConfiguration1);
        }
        return adminConfiguration;
    }
}
