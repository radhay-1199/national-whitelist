package com.gl.ceir.builder;

import com.gl.ceir.model.app.ActiveUniqueForeignImei;
import com.gl.ceir.model.app.ForeignWhitelist;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ForeignWhitelistBuilder {
    public static List<ForeignWhitelist> fromActiveUniqueImei(List<ActiveUniqueForeignImei> activeUniqueImeiList) {
        List<ForeignWhitelist> nationalWhitelistList = new ArrayList<>();

        for (ActiveUniqueForeignImei activeUniqueImei : activeUniqueImeiList) {
            ForeignWhitelist nationalWhitelist = new ForeignWhitelist();

            nationalWhitelist.setCreatedOn(activeUniqueImei.getCreatedOn());
            nationalWhitelist.setModifiedOn(activeUniqueImei.getModifiedOn());
            nationalWhitelist.setForeignRule(activeUniqueImei.getForeginRule());
            nationalWhitelist.setTac(activeUniqueImei.getTac());
            nationalWhitelist.setMobileOperator(activeUniqueImei.getMobileOperator());
            nationalWhitelist.setCreatedFilename(activeUniqueImei.getCreateFilename());
            nationalWhitelist.setUpdatedFilename(activeUniqueImei.getUpdateFilename());
            nationalWhitelist.setUpdatedOn(activeUniqueImei.getUpdatedOn());
            nationalWhitelist.setSystemType(activeUniqueImei.getSystemType());
            nationalWhitelist.setAction(activeUniqueImei.getAction());
            nationalWhitelist.setPeriod(activeUniqueImei.getPeriod());
            nationalWhitelist.setFailedRuleDate(activeUniqueImei.getFailedRuleDate());
            nationalWhitelist.setFailedRuleId(activeUniqueImei.getFailedRuleId());
            nationalWhitelist.setFailedRuleName(activeUniqueImei.getFailedRuleName());
            nationalWhitelist.setTaxPaid(Integer.toString(Optional.ofNullable(activeUniqueImei.getTaxPaid()).orElse(0)));
            nationalWhitelist.setFeatureName(activeUniqueImei.getFeatureName());
            nationalWhitelist.setRecordTime(activeUniqueImei.getRecordTime());
            nationalWhitelist.setActualImei(activeUniqueImei.getActualImei());
            nationalWhitelist.setRecordType(activeUniqueImei.getRecordType());
            nationalWhitelist.setImei(activeUniqueImei.getImei());
            nationalWhitelist.setRawCdrFileName(activeUniqueImei.getRawCdrFileName());
            nationalWhitelist.setImeiArrivalTime(activeUniqueImei.getImeiArrivalTime());
            nationalWhitelist.setSource(activeUniqueImei.getSource());
            nationalWhitelist.setUpdateRawCdrFileName(activeUniqueImei.getUpdateRawCdrFileName());
            nationalWhitelist.setUpdateImeiArrivalTime(activeUniqueImei.getUpdateImeiArrivalTime());
            nationalWhitelist.setUpdateSource(activeUniqueImei.getUpdateSource());
            nationalWhitelist.setServerOrigin(activeUniqueImei.getServerOrigin());
            nationalWhitelist.setMsisdn(activeUniqueImei.getMsisdn());
            nationalWhitelist.setImsi(activeUniqueImei.getImsi());
            nationalWhitelist.setCreatedOnDate(convertLocalDate(activeUniqueImei.getCreatedOn()));
            nationalWhitelist.setActualOperator(activeUniqueImei.getActualOperator());
            nationalWhitelist.setIsTestImei(activeUniqueImei.getTestImei());
            nationalWhitelist.setValidityFlag(activeUniqueImei.isValidityFlag());
            nationalWhitelist.setListType("active_unique_foreign_imei");
            nationalWhitelist.setReasonForInvalidImei(activeUniqueImei.getReason());
            nationalWhitelist.setForeignWhiteListCreatedDate(LocalDateTime.now());
            nationalWhitelist.setIsUsedDeviceImei(activeUniqueImei.getIsUsed());
            nationalWhitelist.setForeignRule(activeUniqueImei.getForeginRule());

            nationalWhitelistList.add(nationalWhitelist);
        }

        return nationalWhitelistList;
    }

    public static ForeignWhitelist fromActiveUniqueImei(ActiveUniqueForeignImei activeUniqueImei) {

            ForeignWhitelist nationalWhitelist = new ForeignWhitelist();
            nationalWhitelist.setCreatedOn(activeUniqueImei.getCreatedOn());
            nationalWhitelist.setModifiedOn(activeUniqueImei.getModifiedOn());
            nationalWhitelist.setForeignRule(activeUniqueImei.getForeginRule());
            nationalWhitelist.setTac(activeUniqueImei.getTac());
            nationalWhitelist.setMobileOperator(activeUniqueImei.getMobileOperator());
            nationalWhitelist.setCreatedFilename(activeUniqueImei.getCreateFilename());
            nationalWhitelist.setUpdatedFilename(activeUniqueImei.getUpdateFilename());
            nationalWhitelist.setUpdatedOn(activeUniqueImei.getUpdatedOn());
            nationalWhitelist.setSystemType(activeUniqueImei.getSystemType());
            nationalWhitelist.setAction(activeUniqueImei.getAction());
            nationalWhitelist.setPeriod(activeUniqueImei.getPeriod());
            nationalWhitelist.setFailedRuleDate(activeUniqueImei.getFailedRuleDate());
            nationalWhitelist.setFailedRuleId(activeUniqueImei.getFailedRuleId());
            nationalWhitelist.setFailedRuleName(activeUniqueImei.getFailedRuleName());
            nationalWhitelist.setTaxPaid(Integer.toString(Optional.ofNullable(activeUniqueImei.getTaxPaid()).orElse(0)));
            nationalWhitelist.setFeatureName(activeUniqueImei.getFeatureName());
            nationalWhitelist.setRecordTime(activeUniqueImei.getRecordTime());
            nationalWhitelist.setActualImei(activeUniqueImei.getActualImei());
            nationalWhitelist.setRecordType(activeUniqueImei.getRecordType());
            nationalWhitelist.setImei(activeUniqueImei.getImei());
            nationalWhitelist.setRawCdrFileName(activeUniqueImei.getRawCdrFileName());
            nationalWhitelist.setImeiArrivalTime(activeUniqueImei.getImeiArrivalTime());
            nationalWhitelist.setSource(activeUniqueImei.getSource());
            nationalWhitelist.setUpdateRawCdrFileName(activeUniqueImei.getUpdateRawCdrFileName());
            nationalWhitelist.setUpdateImeiArrivalTime(activeUniqueImei.getUpdateImeiArrivalTime());
            nationalWhitelist.setUpdateSource(activeUniqueImei.getUpdateSource());
            nationalWhitelist.setServerOrigin(activeUniqueImei.getServerOrigin());
            nationalWhitelist.setMsisdn(activeUniqueImei.getMsisdn());
            nationalWhitelist.setImsi(activeUniqueImei.getImsi());
            nationalWhitelist.setCreatedOnDate(convertLocalDate(activeUniqueImei.getCreatedOn()));
            nationalWhitelist.setActualOperator(activeUniqueImei.getActualOperator());
            nationalWhitelist.setIsTestImei(activeUniqueImei.getTestImei());
            nationalWhitelist.setValidityFlag(activeUniqueImei.isValidityFlag());
            nationalWhitelist.setListType("active_unique_foreign_imei");
            nationalWhitelist.setReasonForInvalidImei(activeUniqueImei.getReason());
            nationalWhitelist.setForeignWhiteListCreatedDate(LocalDateTime.now());
            nationalWhitelist.setIsUsedDeviceImei(activeUniqueImei.getIsUsed());
            nationalWhitelist.setForeignRule(activeUniqueImei.getForeginRule());

        return nationalWhitelist;
    }

    public static LocalDate convertLocalDate(LocalDateTime inputDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = inputDate.format(formatter);
        return LocalDate.parse(formattedDate, formatter);
    }
}
