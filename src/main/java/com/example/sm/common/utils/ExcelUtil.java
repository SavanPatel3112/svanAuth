package com.example.sm.common.utils;

import com.example.sm.auth.decorator.ExcelField;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.apache.poi.xssf.usermodel.XSSFWorkbookFactory.createWorkbook;

@Data
@Slf4j
@Component
public class ExcelUtil {
    public static <T> Workbook createWorkbookFromData(List<T> data) {
        // Create new Workbook
        Workbook workbook = createWorkbook();
        // Create A Sheet
        Sheet sheet = workbook.createSheet("Sheet1");
        log.info("Sheet -> {}", sheet);
        // If no data then return no work needed
        if (data.size() == 0) {
            return workbook;
        }

        Row topRow = sheet.createRow(0);
        Row headerRow = sheet.createRow(1);
        //logger.info("Sheet -> row[0] -> {}",headerRow);
    /*Font font = workbook.createFont();
    font.setFontHeightInPoints((short) 15);
    font.setBoldweight(XSSFFont.BOLDWEIGHT_BOLD);*/
        topRow.createCell(0).setCellValue("Exported Data");
        List<Method> methods = setHeaders(headerRow, data.get(0).getClass());
        // This is start with one because we need to skip first element ...
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, methods.size()));
        topRow.getCell(0).getCellStyle().setAlignment(HorizontalAlignment.CENTER);
        //topRow.getCell(0).getCellStyle().setFont(font);
        int i = 2;
        for (T record : data) {
            Row row = sheet.createRow(i++);
            setData(row, methods, record, i - 2);
        }

        for (int column = 0; column <= methods.size(); column++) {
            try {
                System.out.println(column);
                //sheet.autoSizeColumn(column);
            } catch (Exception ignored) {
            }
        }

        return workbook;
    }

    private static List<Method> setHeaders(Row row, Class c) {
        // This is the list of method to be called on object in order to get data...
        List<Method> methods = new ArrayList<>();
        Method[] fields = c.getMethods();
        for (Method m : fields) {
            ExcelField excelField = m.getAnnotation(ExcelField.class);
            if (excelField != null) {
                methods.add(m);
            }
        }

        methods.sort(Comparator.comparingInt(o -> o.getAnnotation(ExcelField.class).position()));
        int i = 0;
        Cell cell = row.createCell(i++);
        cell.setCellValue("#");
        for (Method m : methods) {
            ExcelField excelField = m.getAnnotation(ExcelField.class);
            //logger.info("Index Problem Index -> {}",i);
            //logger.info("Index Problem Row  -> {}",row);
            //logger.info("Index Problem Method -> {}",methods);
            cell = row.createCell(i++);
            cell.setCellValue(excelField.excelHeader());
            //logger.info("Cell Value -> {}",cell.getStringCellValue());
        }
        return methods;
    }

    private static void setData(Row row,List<Method> methods,Object o,int position){
        int index  = 0;
        Cell cell = row.createCell(index++);
        cell.setCellValue(position);
        for (Method method : methods) {
            cell = row.createCell(index++);
            try {
                Object cellValue = method.invoke(o);
                if(cellValue == null){
                    cellValue = "";
                }
                cell.setCellValue(cellValue.toString().replaceAll("null",""));
            }catch (Exception ignored){
            }
        }
    }

    public static ByteArrayResource getBiteResourceFromWorkbook(Workbook workbook) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return new  ByteArrayResource(outputStream.toByteArray());
    }

}
