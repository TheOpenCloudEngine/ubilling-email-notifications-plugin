package org.killbill.billing.plugin.notification.uengine.model;

import java.sql.Date;

/**
 * Created by uengine on 2017. 1. 25..
 */
public class ProductDaoVersion {

    private Long id;
    private String product_id;
    private Long version;
    private Date effective_date;
    private String organization_id;
    private String tenant_id;
    private String plans;
    private Date reg_dt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProduct_id() {
        return product_id;
    }

    public void setProduct_id(String product_id) {
        this.product_id = product_id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Date getEffective_date() {
        return effective_date;
    }

    public void setEffective_date(Date effective_date) {
        this.effective_date = effective_date;
    }

    public String getOrganization_id() {
        return organization_id;
    }

    public void setOrganization_id(String organization_id) {
        this.organization_id = organization_id;
    }

    public String getTenant_id() {
        return tenant_id;
    }

    public void setTenant_id(String tenant_id) {
        this.tenant_id = tenant_id;
    }

    public String getPlans() {
        return plans;
    }

    public void setPlans(String plans) {
        this.plans = plans;
    }

    public Date getReg_dt() {
        return reg_dt;
    }

    public void setReg_dt(Date reg_dt) {
        this.reg_dt = reg_dt;
    }
}
