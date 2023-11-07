package com.gl.ceir.service;

import com.gl.ceir.builder.*;
import com.gl.ceir.dto.RuleEngineDto;
import com.gl.ceir.enums.Rules;
import com.gl.ceir.model.app.*;
import com.gl.ceir.model.audit.ModulesAuditTrail;
import com.gl.ceir.repository.app.*;
import com.gl.ceir.repository.audit.ModulesAuditTrailRepository;
import com.gl.ceir.rules.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

@Service
public class ValidateRules implements Runnable{
    private final Logger log = LogManager.getLogger(getClass());
    private volatile long lastProgressTime = System.currentTimeMillis();
    @Value("${whitelist.batch.size}")
    private Integer batchSize;
    @Autowired
    RuleEngineMappingRepository ruleEngineMappingRepository;
    @Autowired
    ActiveUniqueImeiRepository activeUniqueImeiRepository;
    @Autowired
    NationalWhitelistService nationalWhitelistService;
    @Autowired
    ExceptionListService exceptionListService;
    @Autowired
    ActiveImeiWithDifferentMsisdnRepository activeImeiWithDifferentMsisdnRepository;
    @Autowired
    SystemConfigurationDbRepository systemConfigurationDbRepository;
    @Autowired
    ActiveForeignImeiWithDifferentMsisdnRepository activeForeignImeiWithDifferentMsisdnRepository;
    @Autowired
    ActiveUniqueForeignImeiRepository activeUniqueForeignImeiRepository;
    @Autowired
    GenericRepository genericRepository;
    @Autowired
    CfgFeatureAlertRepository cfgFeatureAlertRepository;
    @Autowired
    ModulesAuditTrailRepository modulesAuditTrailRepository;
    @Autowired
    ForeignExceptionListService foreignExceptionListService;
    @Autowired
    ForeignWhitelistService foreignWhitelistService;
    @Autowired
    VALID_TAC validTac;
    @Autowired
    IMEI_NULL imeiNull;
    @Autowired
    IMEI_TEST imeiTest;
    @Autowired
    IMEI_LENGTH imeiLength;
    @Autowired
    IMEI_ALPHANUMERIC imeiAlphanumeric;

    @Override
    public void run() {
        int executionStartTime = Math.toIntExact(System.currentTimeMillis() / 1000);
        log.info("Starting national whitelist process");
        int moduleAudiTrailId = 0;
        int foreignModuleTrailId = 0;
        int nationaWhitelistCount = 0;
        int exceptionListCount = 0;
        int foreignWhitelistCount = 0;
        int foreignExceptionListCount = 0;
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        TimeZone serverTimeZone = TimeZone.getDefault();
        System.out.println("Server Time Zone ID: " + serverTimeZone.getID());
        System.out.println("Server Time Zone Display Name: " + serverTimeZone.getDisplayName());
        if(batchSize == null) {
            batchSize = 1000000;
        }
        LocalDateTime startTime = LocalDateTime.now();
        try {
            ModulesAuditTrail startAudit = ModulesAuditTrailBuilder.forInsert(201, "created", "NA", "National Whitelist", "INSERT", 0,"Started National Process", "national_whitelist", startTime);
            startAudit = modulesAuditTrailRepository.save(startAudit);
            moduleAudiTrailId = startAudit.getId();
            Optional<SystemConfigurationDb> activeUniqueImeiDate = Optional.ofNullable(systemConfigurationDbRepository.getByTag("nw_unique_imei_last_run_time"));
            String cdrProcessingTimestamp = Optional.ofNullable(genericRepository.getCdrProcessingTimestamp()).orElseThrow(() -> new Exception("CDR Not Processed Yet"));
            String activeUniqueImeisLastRunDate = "";
            String activeUniqueImeisLastRunEndDate = "";
            if(activeUniqueImeiDate.isPresent()) {
                    activeUniqueImeisLastRunDate = formatDateString(activeUniqueImeiDate.get().getValue());
                    int compareDate = compareDates(activeUniqueImeisLastRunDate, cdrProcessingTimestamp);
                    if (compareDate > 0) {
                        if (compareDates(addOneDayToDate(activeUniqueImeisLastRunDate), cdrProcessingTimestamp) > 0) {
                            SystemConfigurationDb activeUniqueImeiLatestDate = activeUniqueImeiDate.get();
                            activeUniqueImeiLatestDate.setValue(addOneDayToDate(activeUniqueImeisLastRunDate));
                            systemConfigurationDbRepository.save(activeUniqueImeiLatestDate);
                            activeUniqueImeisLastRunEndDate = addOneDayToDate(activeUniqueImeisLastRunDate);
                        } else {
                            SystemConfigurationDb activeUniqueImeiLatestDate = activeUniqueImeiDate.get();
                            activeUniqueImeiLatestDate.setValue(cdrProcessingTimestamp);
                            systemConfigurationDbRepository.save(activeUniqueImeiLatestDate);
                            activeUniqueImeisLastRunEndDate = cdrProcessingTimestamp;
                        }
                    }
            } else {
                activeUniqueImeisLastRunDate = formatDateString(activeUniqueImeiRepository.getEarliestActiveTimestamp());
                if (compareDates(addOneDayToDate(activeUniqueImeisLastRunDate), cdrProcessingTimestamp) > 0) {
                    activeUniqueImeisLastRunEndDate = addOneDayToDate(activeUniqueImeisLastRunDate);
                } else {
                    activeUniqueImeisLastRunEndDate = cdrProcessingTimestamp;
                }
                systemConfigurationDbRepository.save(new SystemConfigurationDb("nw_unique_imei_last_run_time", activeUniqueImeisLastRunEndDate, "latest date when national whitelist process for unique imei ran"));
            }
            List<RuleEngineMapping> rules = ruleEngineMappingRepository.getByFeatureAndUserTypeOrderByRuleOrder("national_whitelist", "default", "Enabled");
            int compareDatesActiveUniqueImei = compareDates(activeUniqueImeisLastRunDate, cdrProcessingTimestamp);
            if (compareDatesActiveUniqueImei > 0) {
                Pageable pageable = PageRequest.of(0, batchSize);
                Page<ActiveUniqueImei> activeUniqueImeis;
                while (true) {
                    activeUniqueImeis = activeUniqueImeiRepository.findAllLatestUniqueImeiInLastXDays(activeUniqueImeisLastRunDate, activeUniqueImeisLastRunEndDate, pageable);
                    if (activeUniqueImeis.isEmpty()) {
                        break;
                    }
                    RuleEngineDto<ActiveUniqueImei, ExceptionList> activeUniqueImeiDto = new RuleEngineDto(activeUniqueImeis.getContent(), new ArrayList<>());
                    if (!rules.isEmpty()) {
                        if (!activeUniqueImeis.isEmpty()) {
                            log.info("Starting validation for " + activeUniqueImeis.getTotalElements() + " active unique imei between startDate: {}, endDate: {}, pageNo: {}, totalPages: {}", activeUniqueImeisLastRunDate, activeUniqueImeisLastRunEndDate, pageable.getPageNumber(), activeUniqueImeis.getTotalPages());
                            for (RuleEngineMapping rule : rules) {
                                switch (Rules.valueOf(rule.getName().trim())) {
                                    case VALID_TAC:
                                        ModulesAuditTrail tacAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking invalid tac for unique imei", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(tacAudit);
                                        activeUniqueImeiDto = validTac.validateActiveUniqueImei(activeUniqueImeiDto);
                                        break;
                                    case IMEI_NULL:
                                        ModulesAuditTrail imeiAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei is null for unique imei", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(imeiAudit);
                                        activeUniqueImeiDto = imeiNull.validateActiveUniqueImei(activeUniqueImeiDto);
                                        break;
                                    case IMEI_TEST:
                                        ModulesAuditTrail testAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if test imei for unique imei", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(testAudit);
                                        activeUniqueImeiDto = imeiTest.validateActiveUniqueImei(activeUniqueImeiDto);
                                        break;
                                    case IMEI_LENGTH_NATIONAL_WHITELIST:
                                        ModulesAuditTrail lengthAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking imei length for unique imei", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(lengthAudit);
                                        activeUniqueImeiDto = imeiLength.validateActiveUniqueImei(activeUniqueImeiDto);
                                        break;
                                    case IMEI_ALPHANUMERIC:
                                        ModulesAuditTrail alphanumericAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei alphanumeric for unique imei", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(alphanumericAudit);
                                        activeUniqueImeiDto = imeiAlphanumeric.validateActiveUniqueImei(activeUniqueImeiDto);
                                        break;
                                }
                            }
                            lastProgressTime = System.currentTimeMillis();
                            log.info("ActiveUniqueImeis: Count National Whitelist=> "+activeUniqueImeiDto.getNationalWhitelistAccepted().size()+ ", Exception List=> "+activeUniqueImeiDto.getExceptionList().size());
                            if (!activeUniqueImeiDto.getNationalWhitelistAccepted().isEmpty()) {
                                List<NationalWhitelist> nationalWhitelists = NationalWhitelistBuilder.fromActiveUniqueImei(activeUniqueImeiDto.getNationalWhitelistAccepted());
                                nationalWhitelistService.saveNationalWhitelists(nationalWhitelists);
                                nationaWhitelistCount = nationaWhitelistCount + nationalWhitelists.size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                            if (!activeUniqueImeiDto.getExceptionList().isEmpty()) {
                                exceptionListService.saveExceptionLists(activeUniqueImeiDto.getExceptionList());
                                exceptionListCount = exceptionListCount + activeUniqueImeiDto.getExceptionList().size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                        } else {
                            log.info("No active unique imei found for " + activeUniqueImeisLastRunDate);
                        }
                    }
                    pageable = pageable.next();
                }
            }
                ModulesAuditTrail completedAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running", "NA", "National Whitelist", "UPDATE", "Process completed for unique imei", "national_whitelist", executionStartTime, startTime);
                modulesAuditTrailRepository.save(completedAudit);

                    Optional<SystemConfigurationDb> activeImeiWithDifferentMsisdnDate = Optional.ofNullable(systemConfigurationDbRepository.getByTag("nw_unique_imei_diff_msisdn_last_run_time"));
                    String activeImeisDifferentMsisdnLastRunDate = "";
                    String activeImeisDifferentMsisdnLastRunEndDate = "";
                if (activeImeiWithDifferentMsisdnDate.isPresent()) {
                    activeImeisDifferentMsisdnLastRunDate = formatDateString(activeImeiWithDifferentMsisdnDate.get().getValue());
                    int compareDate = compareDates(activeImeisDifferentMsisdnLastRunDate, cdrProcessingTimestamp);
                    if (compareDate > 0) {
                        if (compareDates(addOneDayToDate(activeImeisDifferentMsisdnLastRunDate), cdrProcessingTimestamp) > 0) {
                            SystemConfigurationDb activeImeiWithDifferentMsisdnLatestDate = activeImeiWithDifferentMsisdnDate.get();
                            activeImeiWithDifferentMsisdnLatestDate.setValue(addOneDayToDate(activeImeisDifferentMsisdnLastRunDate));
                            systemConfigurationDbRepository.save(activeImeiWithDifferentMsisdnLatestDate);
                            activeImeisDifferentMsisdnLastRunEndDate = addOneDayToDate(activeImeisDifferentMsisdnLastRunDate);
                        } else {
                            SystemConfigurationDb activeImeiWithDifferentMsisdnLatestDate = activeImeiWithDifferentMsisdnDate.get();
                            activeImeiWithDifferentMsisdnLatestDate.setValue(cdrProcessingTimestamp);
                            systemConfigurationDbRepository.save(activeImeiWithDifferentMsisdnLatestDate);
                            activeImeisDifferentMsisdnLastRunEndDate = cdrProcessingTimestamp;
                        }
                    }
                } else {
                    activeImeisDifferentMsisdnLastRunDate = formatDateString(activeImeiWithDifferentMsisdnRepository.getEarliestActiveTimestamp());
                    if (compareDates(addOneDayToDate(activeImeisDifferentMsisdnLastRunDate), cdrProcessingTimestamp) > 0) {
                        activeImeisDifferentMsisdnLastRunEndDate = addOneDayToDate(activeImeisDifferentMsisdnLastRunDate);
                    } else {
                        activeImeisDifferentMsisdnLastRunEndDate = cdrProcessingTimestamp;
                    }
                    systemConfigurationDbRepository.save(new SystemConfigurationDb("nw_unique_imei_diff_msisdn_last_run_time", activeImeisDifferentMsisdnLastRunEndDate, "latest date when national whitelist process for imei with different msisdn ran"));
                }
            int compareDatesActiveImeiWithDifferentMsisdnDate = compareDates(activeImeisDifferentMsisdnLastRunDate, cdrProcessingTimestamp);
            if (compareDatesActiveImeiWithDifferentMsisdnDate > 0) {
                Pageable pageable = PageRequest.of(0, batchSize);
                Page<ActiveImeiWithDifferentMsisdn> activeImeiWithDifferentMsisdns;
                while (true) {
                    activeImeiWithDifferentMsisdns = activeImeiWithDifferentMsisdnRepository.findAllLatestDifferentImeiInLastXDays(activeImeisDifferentMsisdnLastRunDate, activeImeisDifferentMsisdnLastRunEndDate, pageable);

                    if (activeImeiWithDifferentMsisdns.isEmpty()) {
                        break;
                    }
                    RuleEngineDto<ActiveImeiWithDifferentMsisdn, ExceptionList> activeUniqueImeiWithDifferentMsisdnDto = new RuleEngineDto(activeImeiWithDifferentMsisdns.getContent(), new ArrayList<>());
                    if (!rules.isEmpty()) {
                        if (!activeImeiWithDifferentMsisdns.isEmpty()) {
                            log.info("Starting validation for " + activeImeiWithDifferentMsisdns.getTotalElements() + " active imei with different msisdn between startDate: {}, endDate: {}, pageNo: {}, totalPages: {}", activeImeisDifferentMsisdnLastRunDate, activeImeisDifferentMsisdnLastRunEndDate, pageable.getPageNumber(), activeImeiWithDifferentMsisdns.getTotalPages());
                            for (RuleEngineMapping rule : rules) {
                                switch (Rules.valueOf(rule.getName().trim())) {
                                    case VALID_TAC:
                                        ModulesAuditTrail diffMsisdnTacAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking invalid tac for imei with different msisdn", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnTacAudit);
                                        activeUniqueImeiWithDifferentMsisdnDto = validTac.validateActiveImeiWithDifferentMsisdn(activeUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_NULL:
                                        ModulesAuditTrail diffMsisdnNullAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei is null  for imei with different msisdn", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnNullAudit);
                                        activeUniqueImeiWithDifferentMsisdnDto = imeiNull.validateActiveImeiWithDifferentMsisdn(activeUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_TEST:
                                        ModulesAuditTrail diffMsisdnTestAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if test imei  for imei with different msisdn", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnTestAudit);
                                        activeUniqueImeiWithDifferentMsisdnDto = imeiTest.validateActiveImeiWithDifferentMsisdn(activeUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_LENGTH_NATIONAL_WHITELIST:
                                        ModulesAuditTrail diffMsisdnLengthAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking imei length  for imei with different msisdn", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnLengthAudit);
                                        activeUniqueImeiWithDifferentMsisdnDto = imeiLength.validateActiveImeiWithDifferentMsisdn(activeUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_ALPHANUMERIC:
                                        ModulesAuditTrail diffMsisdnAlphanumericAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei alphanumeric  for imei with different msisdn", "national_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnAlphanumericAudit);
                                        activeUniqueImeiWithDifferentMsisdnDto = imeiAlphanumeric.validateActiveImeiWithDifferentMsisdn(activeUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                }
                            }
                            lastProgressTime = System.currentTimeMillis();
                            log.info("ActiveImeiWithDifferentMsisdns: Count National Whitelist=> "+activeUniqueImeiWithDifferentMsisdnDto.getNationalWhitelistAccepted().size()+ ", Exception List=> "+activeUniqueImeiWithDifferentMsisdnDto.getExceptionList().size());
                            if (!activeUniqueImeiWithDifferentMsisdnDto.getExceptionList().isEmpty()) {
                                exceptionListService.saveExceptionLists(activeUniqueImeiWithDifferentMsisdnDto.getExceptionList());
                                exceptionListCount = exceptionListCount + activeUniqueImeiWithDifferentMsisdnDto.getExceptionList().size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                        } else {
                            log.info("No active imei with different msisdn found for " + activeImeisDifferentMsisdnLastRunDate);
                        }
                    }
                    pageable = pageable.next();
                }
            }
                    ModulesAuditTrail diffMsisdnCompletedAudit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 200, "completed", "NA", "National Whitelist", "UPDATE", "Process completed national whitelist", "national_whitelist", nationaWhitelistCount, executionStartTime, startTime);
                    modulesAuditTrailRepository.save(diffMsisdnCompletedAudit);
                    ModulesAuditTrail exceptionListAudit = ModulesAuditTrailBuilder.forInsert(200, "completed", "NA", "National Whitelist", "INSERT", exceptionListCount,"Process completed for exception list", "exception_list", startTime);
                    modulesAuditTrailRepository.save(exceptionListAudit);
                // For foreign tables

                ModulesAuditTrail foreignModuleTrail = ModulesAuditTrailBuilder.forInsert(201, "created", "NA", "National Whitelist", "INSERT", 0,"Started Foreign Whitelist Process", "foreign_whitelist", startTime);
                foreignModuleTrail = modulesAuditTrailRepository.save(foreignModuleTrail);
                foreignModuleTrailId = foreignModuleTrail.getId();

                Optional<SystemConfigurationDb> acitveUniqueForeignImeiDate =Optional.ofNullable(systemConfigurationDbRepository.getByTag("nw_unique_foreign_imei_last_run_time"));
                String activeUniqueForeignImeisLastRunDate = "";
                String activeUniqueForeignImeisLastRunEndDate = "";
                if(acitveUniqueForeignImeiDate.isPresent()) {
                    activeUniqueForeignImeisLastRunDate = formatDateString(acitveUniqueForeignImeiDate.get().getValue());
                    int compareDates = compareDates(activeUniqueForeignImeisLastRunDate, cdrProcessingTimestamp);
                    if (compareDates > 0) {
                        if (compareDates(addOneDayToDate(activeUniqueForeignImeisLastRunDate), cdrProcessingTimestamp) > 0) {
                            SystemConfigurationDb acitveUniqueForeignImeiLatestDate = acitveUniqueForeignImeiDate.get();
                            acitveUniqueForeignImeiLatestDate.setValue(addOneDayToDate(activeUniqueForeignImeisLastRunDate));
                            systemConfigurationDbRepository.save(acitveUniqueForeignImeiLatestDate);
                            activeUniqueForeignImeisLastRunEndDate = addOneDayToDate(activeUniqueForeignImeisLastRunDate);
                        } else {
                            SystemConfigurationDb acitveUniqueForeignImeiLatestDate = acitveUniqueForeignImeiDate.get();
                            acitveUniqueForeignImeiLatestDate.setValue(cdrProcessingTimestamp);
                            systemConfigurationDbRepository.save(acitveUniqueForeignImeiLatestDate);
                            activeUniqueForeignImeisLastRunEndDate = cdrProcessingTimestamp;
                        }
                    }
                } else {
                    activeUniqueForeignImeisLastRunDate = formatDateString(activeUniqueForeignImeiRepository.getEarliestActiveTimestamp());
                    if (compareDates(addOneDayToDate(activeUniqueForeignImeisLastRunDate), cdrProcessingTimestamp) > 0) {
                        activeUniqueForeignImeisLastRunEndDate = addOneDayToDate(activeUniqueForeignImeisLastRunDate);
                    } else {
                        activeUniqueForeignImeisLastRunEndDate = cdrProcessingTimestamp;
                    }
                    systemConfigurationDbRepository.save(new SystemConfigurationDb("nw_unique_foreign_imei_last_run_time", activeUniqueForeignImeisLastRunEndDate, "latest date when foreign whitelist process for unique imei ran"));
                }
            List<RuleEngineMapping> foreignRules = ruleEngineMappingRepository.getByFeatureAndUserTypeOrderByRuleOrder("foreign_whitelist", "default", "Enabled");
            int compareDatesActiveUniqueForeignImeis = compareDates(activeUniqueForeignImeisLastRunDate, cdrProcessingTimestamp);
            if (compareDatesActiveUniqueForeignImeis > 0) {
                Pageable pageable = PageRequest.of(0, batchSize);
                Page<ActiveUniqueForeignImei> activeUniqueForeignImeis;
                while (true) {
                    activeUniqueForeignImeis = activeUniqueForeignImeiRepository.findAllLatestUniqueImeiInLastXDays(activeUniqueForeignImeisLastRunDate, activeUniqueForeignImeisLastRunEndDate, pageable);

                    if (activeUniqueForeignImeis.isEmpty()) {
                        break;
                    }
                    RuleEngineDto<ActiveUniqueForeignImei, ForeignExceptionList> activeUniqueForeignImeiDto = new RuleEngineDto(activeUniqueForeignImeis.getContent(), new ArrayList<>());
                    if (!foreignRules.isEmpty()) {
                        if (!activeUniqueForeignImeis.isEmpty()) {
                            log.info("Starting validation for " + activeUniqueForeignImeis.getTotalElements() + " active unique foreign imei between startDate: {}, endDate: {}, pageNo: {}, totalPages: {}", activeUniqueForeignImeisLastRunDate, activeUniqueForeignImeisLastRunEndDate, pageable.getPageNumber(), activeUniqueForeignImeis.getTotalPages());
                            for (RuleEngineMapping rule : foreignRules) {
                                switch (Rules.valueOf(rule.getName().trim())) {
                                    case VALID_TAC:
                                        ModulesAuditTrail foreignTacAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking invalid tac for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(foreignTacAudit);
                                        activeUniqueForeignImeiDto = validTac.validateActiveUniqueForeignImei(activeUniqueForeignImeiDto);
                                        break;
                                    case IMEI_NULL:
                                        ModulesAuditTrail foreignNullAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei is null for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(foreignNullAudit);
                                        activeUniqueForeignImeiDto = imeiNull.validateActiveUniqueForeignImei(activeUniqueForeignImeiDto);
                                        break;
                                    case IMEI_TEST:
                                        ModulesAuditTrail foreignTestAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if test imei for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(foreignTestAudit);
                                        activeUniqueForeignImeiDto = imeiTest.validateActiveUniqueForeignImei(activeUniqueForeignImeiDto);
                                        break;
                                    case IMEI_LENGTH_NATIONAL_WHITELIST:
                                        ModulesAuditTrail foreignLengthAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking imei length for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(foreignLengthAudit);
                                        activeUniqueForeignImeiDto = imeiLength.validateActiveUniqueForeignImei(activeUniqueForeignImeiDto);
                                        break;
                                    case IMEI_ALPHANUMERIC:
                                        ModulesAuditTrail foreignAlphanumericAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei alphanumeric for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(foreignAlphanumericAudit);
                                        activeUniqueForeignImeiDto = imeiAlphanumeric.validateActiveUniqueForeignImei(activeUniqueForeignImeiDto);
                                        break;
                                }
                            }
                            lastProgressTime = System.currentTimeMillis();
                            log.info("ActiveUniqueForeignImeis: Count National Whitelist=> "+activeUniqueForeignImeiDto.getNationalWhitelistAccepted().size()+ ", Exception List=> "+activeUniqueForeignImeiDto.getExceptionList().size());
                            if (!activeUniqueForeignImeiDto.getNationalWhitelistAccepted().isEmpty()) {
                                List<ForeignWhitelist> nationalWhitelists = ForeignWhitelistBuilder.fromActiveUniqueImei(activeUniqueForeignImeiDto.getNationalWhitelistAccepted());
                                foreignWhitelistService.saveNationalWhitelists(nationalWhitelists);
                                foreignWhitelistCount = foreignWhitelistCount + nationalWhitelists.size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                            if (!activeUniqueForeignImeiDto.getExceptionList().isEmpty()) {
                                foreignExceptionListService.saveExceptionLists(activeUniqueForeignImeiDto.getExceptionList());
                                foreignExceptionListCount = foreignExceptionListCount + activeUniqueForeignImeiDto.getExceptionList().size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                        } else {
                            log.info("No active unique foreign imei found for " + activeUniqueForeignImeisLastRunDate);
                        }
                    }
                    pageable = pageable.next();
                }
            }
                    ModulesAuditTrail foreignCompletedAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running", "NA", "National Whitelist", "UPDATE", "Process completed for foreign unique imei", "foreign_whitelist", executionStartTime, startTime);
                    modulesAuditTrailRepository.save(foreignCompletedAudit);

                    Optional<SystemConfigurationDb> activeForeignImeisDifferentMsisdnDate = Optional.ofNullable(systemConfigurationDbRepository.getByTag("nw_foreign_unique_imei_diff_msisdn_last_run_time"));
                    String activeForeignImeisDifferentMsisdnLastRunDate = "";
                    String activeForeignImeisDifferentMsisdnLastRunEndDate = "";
                if (activeForeignImeisDifferentMsisdnDate.isPresent()) {
                    activeForeignImeisDifferentMsisdnLastRunDate = formatDateString(activeForeignImeisDifferentMsisdnDate.get().getValue());
                    int compareDates = compareDates(activeForeignImeisDifferentMsisdnLastRunDate, cdrProcessingTimestamp);
                    if (compareDates > 0) {
                        if (compareDates(addOneDayToDate(activeForeignImeisDifferentMsisdnLastRunDate), cdrProcessingTimestamp) > 0) {
                            SystemConfigurationDb activeForeignImeisDifferentMsisdnLatestDate = activeForeignImeisDifferentMsisdnDate.get();
                            activeForeignImeisDifferentMsisdnLatestDate.setValue(addOneDayToDate(activeForeignImeisDifferentMsisdnLastRunDate));
                            systemConfigurationDbRepository.save(activeForeignImeisDifferentMsisdnLatestDate);
                            activeForeignImeisDifferentMsisdnLastRunEndDate = addOneDayToDate(activeForeignImeisDifferentMsisdnLastRunDate);
                        } else {
                            SystemConfigurationDb activeForeignImeisDifferentMsisdnLatestDate = activeForeignImeisDifferentMsisdnDate.get();
                            activeForeignImeisDifferentMsisdnLatestDate.setValue(cdrProcessingTimestamp);
                            systemConfigurationDbRepository.save(activeForeignImeisDifferentMsisdnLatestDate);
                            activeForeignImeisDifferentMsisdnLastRunEndDate = cdrProcessingTimestamp;
                        }
                    }
                } else {
                    activeForeignImeisDifferentMsisdnLastRunDate = formatDateString(activeForeignImeiWithDifferentMsisdnRepository.getEarliestActiveTimestamp());
                    if (compareDates(addOneDayToDate(activeForeignImeisDifferentMsisdnLastRunDate), cdrProcessingTimestamp) > 0) {
                        activeForeignImeisDifferentMsisdnLastRunEndDate = addOneDayToDate(activeForeignImeisDifferentMsisdnLastRunDate);
                    } else {
                        activeForeignImeisDifferentMsisdnLastRunEndDate = cdrProcessingTimestamp;
                    }
                    systemConfigurationDbRepository.save(new SystemConfigurationDb("nw_foreign_unique_imei_diff_msisdn_last_run_time", activeForeignImeisDifferentMsisdnLastRunEndDate, "latest date when foreign whitelist process for imei with different msisdn ran"));
                }
            int compareDatesActiveForeignImeisDifferentMsisdn = compareDates(activeForeignImeisDifferentMsisdnLastRunDate, cdrProcessingTimestamp);
            if (compareDatesActiveForeignImeisDifferentMsisdn > 0) {
                Pageable pageable = PageRequest.of(0, batchSize);
                Page<ActiveForeignImeiWithDifferentMsisdn> activeForeignImeiWithDifferentMsisdns;
                while (true) {
                    activeForeignImeiWithDifferentMsisdns = activeForeignImeiWithDifferentMsisdnRepository.findAllLatestDifferentImeiInLastXDays(activeForeignImeisDifferentMsisdnLastRunDate, activeForeignImeisDifferentMsisdnLastRunEndDate, pageable);

                    if (activeForeignImeiWithDifferentMsisdns.isEmpty()) {
                        break;
                    }
                    RuleEngineDto<ActiveForeignImeiWithDifferentMsisdn, ForeignExceptionList> activeForeignUniqueImeiWithDifferentMsisdnDto = new RuleEngineDto(activeForeignImeiWithDifferentMsisdns.getContent(), new ArrayList<>());
                    if (!foreignRules.isEmpty()) {
                        if (!activeForeignImeiWithDifferentMsisdns.isEmpty()) {
                            log.info("Starting validation for " + activeForeignImeiWithDifferentMsisdns.getTotalElements() + " active foreign imei with different msisdn between startDate: {}, endDate: {}, pageNo: {}, totalPages", activeForeignImeisDifferentMsisdnLastRunDate, activeForeignImeisDifferentMsisdnLastRunEndDate, pageable.getPageNumber(), activeForeignImeiWithDifferentMsisdns.getTotalPages());
                            for (RuleEngineMapping rule : rules) {
                                switch (Rules.valueOf(rule.getName().trim())) {
                                    case VALID_TAC:
                                        ModulesAuditTrail diffMsisdnForeignTacAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking invalid tac for foreign imei with different msisdn", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnForeignTacAudit);
                                        activeForeignUniqueImeiWithDifferentMsisdnDto = validTac.validateActiveForeignImeiWithDifferentMsisdn(activeForeignUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_NULL:
                                        ModulesAuditTrail diffMsisdnForeignNullAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei is null for foreign imei with different msisdn", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnForeignNullAudit);
                                        activeForeignUniqueImeiWithDifferentMsisdnDto = imeiNull.validateActiveForeignImeiWithDifferentMsisdn(activeForeignUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_TEST:
                                        ModulesAuditTrail diffMsisdnForeignTestAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if test imei for foreign imei with different msisdn", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnForeignTestAudit);
                                        activeForeignUniqueImeiWithDifferentMsisdnDto = imeiTest.validateActiveForeignImeiWithDifferentMsisdn(activeForeignUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_LENGTH_NATIONAL_WHITELIST:
                                        ModulesAuditTrail diffMsisdnForeignLengthAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking imei length for foreign imei with different msisdn", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnForeignLengthAudit);
                                        activeForeignUniqueImeiWithDifferentMsisdnDto = imeiLength.validateActiveForeignImeiWithDifferentMsisdn(activeForeignUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                    case IMEI_ALPHANUMERIC:
                                        ModulesAuditTrail diffMsisdnForeignAlphanumericAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 201, "running rule-" + rule.getRuleOrder(), "NA", "National Whitelist", "UPDATE", "Checking if imei alphanumeric for foreign imei with different msisdn", "foreign_whitelist", executionStartTime, startTime);
                                        modulesAuditTrailRepository.save(diffMsisdnForeignAlphanumericAudit);
                                        activeForeignUniqueImeiWithDifferentMsisdnDto = imeiAlphanumeric.validateActiveForeignImeiWithDifferentMsisdn(activeForeignUniqueImeiWithDifferentMsisdnDto);
                                        break;
                                }
                            }
                            lastProgressTime = System.currentTimeMillis();
                            log.info("ActiveForeignImeiWithDifferentMsisdns: Count National Whitelist=> "+activeForeignUniqueImeiWithDifferentMsisdnDto.getNationalWhitelistAccepted().size()+ ", Exception List=> "+activeForeignUniqueImeiWithDifferentMsisdnDto.getExceptionList().size());
                            if (!activeForeignUniqueImeiWithDifferentMsisdnDto.getExceptionList().isEmpty()) {
                                foreignExceptionListService.saveExceptionLists(activeForeignUniqueImeiWithDifferentMsisdnDto.getExceptionList());
                                foreignExceptionListCount = foreignExceptionListCount + activeForeignUniqueImeiWithDifferentMsisdnDto.getExceptionList().size();
                            }
                            lastProgressTime = System.currentTimeMillis();
                        } else {
                            log.info("No active foreign imei with different msisdn found for " + activeForeignImeisDifferentMsisdnLastRunDate);
                        }
                    }
                    pageable = pageable.next();
                }
            }
                    ModulesAuditTrail diffMsisdnForeignCompletedAudit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 200, "completed", "NA", "National Whitelist", "UPDATE", "Process completed for foreign whitelist", "foreign_whitelist", foreignWhitelistCount, executionStartTime, startTime);
                    modulesAuditTrailRepository.save(diffMsisdnForeignCompletedAudit);
                    ModulesAuditTrail foreignExceptionModuleTrail = ModulesAuditTrailBuilder.forInsert(200, "completed", "NA", "National Whitelist", "INSERT", foreignExceptionListCount,"Process completed for foreign exception list", "foreign_whitelist", startTime);
                    modulesAuditTrailRepository.save(foreignExceptionModuleTrail);

        } catch (DataAccessException e) {
            log.error("DB Exception: Raising alert016 "+e);
            String msg = e.getMessage().length() <= 200?e.getMessage(): e.getMessage().substring(0, 200);
            e.printStackTrace();
            Optional<CfgFeatureAlert> alert = cfgFeatureAlertRepository.findByAlertId("alert016");
            log.error("raising alert016");
            System.out.println("raising alert016");
            if (alert.isPresent()) {
                raiseAnAlert(alert.get().getAlertId(), msg, "national_whitelist", 0);
//                RunningAlertDb alertDb = new RunningAlertDb(alert.get().getAlertId(), alert.get().getDescription().replace("<ERROR>", msg), 0);
//                runningAlertDbRepo.save(alertDb);
            }
            if (moduleAudiTrailId == 0) {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forInsert(501, "failed", msg, "National Whitelist", "INSERT", 0,"Exception during national whitelist process", "national_whitelist", startTime);
                modulesAuditTrailRepository.save(audit);
            } else {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 501, "failed", msg, "National Whitelist", "UPDATE", "Exception during national whitelist process", "national_whitelist", executionStartTime, startTime);
                modulesAuditTrailRepository.save(audit);
            }
            if (foreignModuleTrailId == 0) {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forInsert(501, "failed", msg, "National Whitelist", "INSERT", 0,"Exception during exception whitelist process", "foreign_whitelist", startTime);
                modulesAuditTrailRepository.save(audit);
            } else {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 501, "failed", msg, "National Whitelist", "UPDATE", "Exception during exception whitelist process", "foreign_whitelist", executionStartTime, startTime);
                modulesAuditTrailRepository.save(audit);
            }
        } catch (Exception ex) {
            log.error("Exception: Raising alert1209 "+ ex);
            String msg = ex.getMessage().length() <= 200?ex.getMessage(): ex.getMessage().substring(0, 200);
            ex.printStackTrace();
            if (moduleAudiTrailId == 0) {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forInsert(501, "failed", msg, "National Whitelist", "INSERT", 0,"Exception during national whitelist process", "national_whitelist", startTime);
                modulesAuditTrailRepository.save(audit);
            } else {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forUpdate(moduleAudiTrailId, 501, "failed", msg, "National Whitelist", "UPDATE", "Exception during national whitelist process", "national_whitelist", executionStartTime, startTime);
                modulesAuditTrailRepository.save(audit);
            }
            if (foreignModuleTrailId == 0) {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forInsert(501, "failed", msg, "National Whitelist", "INSERT", 0,"Exception during exception whitelist process", "foreign_whitelist", startTime);
                modulesAuditTrailRepository.save(audit);
            } else {
                ModulesAuditTrail audit = ModulesAuditTrailBuilder.forUpdate(foreignModuleTrailId, 501, "failed", msg, "National Whitelist", "UPDATE", "Exception during exception whitelist process", "foreign_whitelist", executionStartTime, startTime);
                modulesAuditTrailRepository.save(audit);
            }
            Optional<CfgFeatureAlert> alert = cfgFeatureAlertRepository.findByAlertId("alert1209");
            log.error("raising alert1209");
            System.out.println("raising alert1209");
            if (alert.isPresent()) {
                raiseAnAlert(alert.get().getAlertId(), msg, "national_whitelist", 0);
//                RunningAlertDb alertDb = new RunningAlertDb(alert.get().getAlertId(), alert.get().getDescription().replace("<ERROR>", msg), 0);
//                runningAlertDbRepo.save(alertDb);
            }
        } finally {
            log.info("Process Completed");
            System.out.println("Process Completed");
        }
    }

    public String getDateTimeNow() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return currentDateTime.format(formatter);
    }

    public int compareDates(String timestamp1, String timestamp2) {
        DateTimeFormatter formatterWithoutMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime dateTime1 = LocalDateTime.parse(timestamp1, formatterWithoutMilliseconds);
        LocalDateTime dateTime2 = LocalDateTime.parse(timestamp2, formatterWithoutMilliseconds);

        OffsetDateTime offsetDateTime1 = dateTime1.atOffset(ZoneOffset.UTC);
        OffsetDateTime offsetDateTime2 = dateTime2.atOffset(ZoneOffset.UTC);

        return offsetDateTime2.compareTo(offsetDateTime1);
    }

    public static String addOneDayToDate(String timestamp) {
        DateTimeFormatter formatterWithoutMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        LocalDateTime dateTime = LocalDateTime.parse(timestamp, formatterWithoutMilliseconds);
        LocalDateTime modifiedDateTime = dateTime.plusDays(1);

        OffsetDateTime offsetModifiedDateTime = modifiedDateTime.atOffset(ZoneOffset.UTC);

        return offsetModifiedDateTime.format(formatterWithoutMilliseconds);
    }

    public static String formatDateString(String inputDateString) {
        DateTimeFormatter formatterWithMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
        DateTimeFormatter formatterWithoutMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(inputDateString, formatterWithMilliseconds);
            OffsetDateTime offsetDateTime = localDateTime.atOffset(ZoneOffset.UTC);
            return offsetDateTime.format(formatterWithoutMilliseconds);
        } catch (Exception e) {
            LocalDateTime localDateTime = LocalDateTime.parse(inputDateString, formatterWithoutMilliseconds);
            OffsetDateTime offsetDateTime = localDateTime.atOffset(ZoneOffset.UTC);
            return offsetDateTime.format(formatterWithoutMilliseconds);
        }
    }

    public void raiseAnAlert(String alertCode, String alertMessage, String alertProcess, int userId) {
        try {   // <e>  alertMessage    //      <process_name> alertProcess
            String path = System.getenv("APP_HOME") + "alert/start.sh";
            ProcessBuilder pb = new ProcessBuilder(path, alertCode, alertMessage, alertProcess, String.valueOf(userId));
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = null;
            String response = null;
            while ((line = reader.readLine()) != null) {
                response += line;
            }
            log.info("Alert is generated :response " + response);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("Not able to execute Alert mgnt jar "+ ex.getLocalizedMessage() + " ::: " + ex.getMessage());
        }
    }

    public boolean madeProgressSince(long timestamp) {
        return lastProgressTime >= timestamp;
    }

}
