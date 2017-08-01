package org.killbill.billing.plugin.notification.uengine.model;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by uengine on 2017. 1. 25..
 */
public class ProductDistributionHistory {
    private Long record_id;
    private String subscription_id;
    private String tenant_id;
    private String organization_id;
    private String buyer_id;
    private String vendor_id;
    private String product_id;
    private Long version;
    private String plan_name;
    private String usage_name;
    private BigDecimal ratio;
    private BigDecimal amount;
    private BigDecimal original_amount;
    private String currency;
    private String invoice_id;
    private String invoice_item_id;
    private String linked_invoice_item_id;
    private String invoice_item_type;
    private String price_type;
    private String transaction_type;
    private String format_date;
    private Date created_date;
    private String notes;

    public Long getRecord_id() {
        return record_id;
    }

    public void setRecord_id(Long record_id) {
        this.record_id = record_id;
    }

    public String getSubscription_id() {
        return subscription_id;
    }

    public void setSubscription_id(String subscription_id) {
        this.subscription_id = subscription_id;
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

    public String getBuyer_id() {
        return buyer_id;
    }

    public void setBuyer_id(String buyer_id) {
        this.buyer_id = buyer_id;
    }

    public String getVendor_id() {
        return vendor_id;
    }

    public void setVendor_id(String vendor_id) {
        this.vendor_id = vendor_id;
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

    public String getUsage_name() {
        return usage_name;
    }

    public void setUsage_name(String usage_name) {
        this.usage_name = usage_name;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public void setRatio(BigDecimal ratio) {
        this.ratio = ratio;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getOriginal_amount() {
        return original_amount;
    }

    public void setOriginal_amount(BigDecimal original_amount) {
        this.original_amount = original_amount;
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

    public String getLinked_invoice_item_id() {
        return linked_invoice_item_id;
    }

    public void setLinked_invoice_item_id(String linked_invoice_item_id) {
        this.linked_invoice_item_id = linked_invoice_item_id;
    }

    public String getInvoice_item_type() {
        return invoice_item_type;
    }

    public void setInvoice_item_type(String invoice_item_type) {
        this.invoice_item_type = invoice_item_type;
    }

    public String getPrice_type() {
        return price_type;
    }

    public void setPrice_type(String price_type) {
        this.price_type = price_type;
    }

    public String getTransaction_type() {
        return transaction_type;
    }

    public void setTransaction_type(String transaction_type) {
        this.transaction_type = transaction_type;
    }

    public String getFormat_date() {
        return format_date;
    }

    public void setFormat_date(String format_date) {
        this.format_date = format_date;
    }

    public Date getCreated_date() {
        return created_date;
    }

    public void setCreated_date(Date created_date) {
        this.created_date = created_date;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
