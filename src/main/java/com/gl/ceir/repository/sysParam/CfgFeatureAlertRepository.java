package com.gl.ceir.repository.sysParam;

import com.gl.ceir.model.sysParam.CfgFeatureAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CfgFeatureAlertRepository extends JpaRepository<CfgFeatureAlert,Long>, JpaSpecificationExecutor<CfgFeatureAlert> {
    Optional<CfgFeatureAlert> findByAlertId(String alertId);
}
