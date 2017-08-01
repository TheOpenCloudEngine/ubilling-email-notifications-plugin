package org.killbill.billing.plugin.notification.uengine.model;

import java.sql.Date;

/**
 * Created by uengine on 2017. 4. 28..
 */
public class ProductDao {

    private Long record_id;
    private String id;
    private String name;
    private String category;
    private String owner_account_id;
    private String description;
    private String redirect_url;
    private String organization_id;
    private String tenant_id;
    private String is_active;
    private Long plan_seq;
    private Long usage_seq;
    private String vendors;
    private Date reg_dt;

    public Long getRecord_id() {
        return record_id;
    }

    public void setRecord_id(Long record_id) {
        this.record_id = record_id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getOwner_account_id() {
        return owner_account_id;
    }

    public void setOwner_account_id(String owner_account_id) {
        this.owner_account_id = owner_account_id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRedirect_url() {
        return redirect_url;
    }

    public void setRedirect_url(String redirect_url) {
        this.redirect_url = redirect_url;
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

    public String getIs_active() {
        return is_active;
    }

    public void setIs_active(String is_active) {
        this.is_active = is_active;
    }

    public Long getPlan_seq() {
        return plan_seq;
    }

    public void setPlan_seq(Long plan_seq) {
        this.plan_seq = plan_seq;
    }

    public Long getUsage_seq() {
        return usage_seq;
    }

    public void setUsage_seq(Long usage_seq) {
        this.usage_seq = usage_seq;
    }

    public String getVendors() {
        return vendors;
    }

    public void setVendors(String vendors) {
        this.vendors = vendors;
    }

    public Date getReg_dt() {
        return reg_dt;
    }

    public void setReg_dt(Date reg_dt) {
        this.reg_dt = reg_dt;
    }
}
