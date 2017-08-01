package org.killbill.billing.plugin.notification.uengine.model;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by uengine on 2017. 1. 25..
 */
public class OneTimeBuy {
    private Long record_id;
    private String bundle_id;
    private String state;
    private String tenant_id;
    private String organization_id;
    private String account_id;
    private String product_id;
    private Long version;
    private String plan_name;
    private String plan_display_name;
    private BigDecimal amount;
    private String currency;
    private String invoice_id;
    private String invoice_item_id;
    private String effective_date;
    private String billing_date;
    private Date created_date;

    public Long getRecord_id() {
        return record_id;
    }

    public void setRecord_id(Long record_id) {
        this.record_id = record_id;
    }

    public String getBundle_id() {
        return bundle_id;
    }

    public void setBundle_id(String bundle_id) {
        this.bundle_id = bundle_id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTenant_id() {
        return tenant_id;
    }

    public void setTenant_id(String tenant_id) {
        this.tenant_id = tenant_id;
    }

    public String getOrganization_id() {
        return organization_id;
    }

    public void setOrganization_id(String organization_id) {
        this.organization_id = organization_id;
    }

    public String getAccount_id() {
        return account_id;
    }

    public void setAccount_id(String account_id) {
        this.account_id = account_id;
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

    public String getPlan_name() {
        return plan_name;
    }

    public void setPlan_name(String plan_name) {
        this.plan_name = plan_name;
    }

    public String getPlan_display_name() {
        return plan_display_name;
    }

    public void setPlan_display_name(String plan_display_name) {
        this.plan_display_name = plan_display_name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getInvoice_id() {
        return invoice_id;
    }

    public void setInvoice_id(String invoice_id) {
        this.invoice_id = invoice_id;
    }

    public String getInvoice_item_id() {
        return invoice_item_id;
    }

    public void setInvoice_item_id(String invoice_item_id) {
        this.invoice_item_id = invoice_item_id;
    }

    public String getEffective_date() {
        return effective_date;
    }

    public void setEffective_date(String effective_date) {
        this.effective_date = effective_date;
    }

    public String getBilling_date() {
        return billing_date;
    }

    public void setBilling_date(String billing_date) {
        this.billing_date = billing_date;
    }

    public Date getCreated_date() {
        return created_date;
    }

    public void setCreated_date(Date created_date) {
        this.created_date = created_date;
    }
}
