package top.egon.mario.rbac.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.rbac.converter.jpa.ApiMatcherTypeConverter;
import top.egon.mario.rbac.converter.jpa.ApiRiskLevelConverter;
import top.egon.mario.rbac.po.enums.ApiMatcherType;
import top.egon.mario.rbac.po.enums.ApiRiskLevel;

import java.time.Instant;

/**
 * API-specific resource details used by dynamic request authorization.
 */
@Getter
@Setter
@Entity
@Table(name = "sys_api", uniqueConstraints = {
        @UniqueConstraint(name = "uk_api_method_pattern_type", columnNames = {"http_method", "url_pattern", "matcher_type"})
})
public class ApiPo {

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "url_pattern", nullable = false, length = 512)
    private String urlPattern;

    @Convert(converter = ApiMatcherTypeConverter.class)
    @Column(name = "matcher_type", nullable = false)
    private ApiMatcherType matcherType = ApiMatcherType.EXACT;

    @Column(name = "public_flag", nullable = false)
    private boolean publicFlag;

    @Column(name = "service_tag", length = 128)
    private String serviceTag;

    @Column(name = "operation_name", length = 128)
    private String operationName;

    @Convert(converter = ApiRiskLevelConverter.class)
    @Column(name = "risk_level", nullable = false)
    private ApiRiskLevel riskLevel = ApiRiskLevel.LOW;

    @Column(name = "last_scanned_at")
    private Instant lastScannedAt;

}
