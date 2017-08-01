/*
 * Copyright 2015-2016 Groupon, Inc
 * Copyright 2015-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.plugin.notification.generator;

import com.google.common.base.Strings;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatter;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.plugin.notification.email.EmailContent;
import org.killbill.billing.plugin.notification.generator.formatters.DefaultInvoiceFormatter;
import org.killbill.billing.plugin.notification.generator.formatters.PaymentFormatter;
import org.killbill.billing.plugin.notification.templates.TemplateEngine;
import org.killbill.billing.plugin.notification.templates.TemplateType;
import org.killbill.billing.plugin.notification.uengine.model.NotificationConfig;
import org.killbill.billing.plugin.notification.uengine.util.JsonUtils;
import org.killbill.billing.plugin.notification.util.IOUtils;
import org.killbill.billing.plugin.notification.util.LocaleUtils;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.TenantContext;
import org.osgi.service.log.LogService;
import org.killbill.billing.plugin.notification.uengine.model.NotificationType;
import org.killbill.billing.plugin.notification.uengine.model.Organization;
import org.killbill.billing.plugin.notification.uengine.model.Template;
import org.killbill.billing.plugin.notification.uengine.service.InvoiceExtService;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class TemplateRenderer {

    private final String DEFAULT_TEMPLATE_PATH_PREFIX = "org/killbill/billing/plugin/notification/templates/";

    private final TemplateEngine templateEngine;
    private final ResourceBundleFactory bundleFactory;
    private final TenantUserApi tenantApi;
    private final LogService logService;

    public TemplateRenderer(final TemplateEngine templateEngine,
                            final ResourceBundleFactory bundleFactory,
                            final TenantUserApi tenantApi,
                            final LogService logService) {
        this.templateEngine = templateEngine;
        this.bundleFactory = bundleFactory;
        this.tenantApi = tenantApi;
        this.logService = logService;
    }


    public EmailContent generateEmailForUpComingInvoice(final Account account, final Invoice invoice, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.INVOICE, TemplateType.UPCOMING_INVOICE, account, null, invoice, null, context);
    }

    public EmailContent generateEmailForSuccessfulPayment(final Account account, final Invoice invoice, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.SUCCESSFUL_PAYMENT, TemplateType.SUCCESSFUL_PAYMENT, account, null, invoice, null, context);
    }

    public EmailContent generateEmailForFailedPayment(final Account account, final Invoice invoice, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.FAILED_PAYMENT, TemplateType.FAILED_PAYMENT, account, null, invoice, null, context);
    }

    public EmailContent generateEmailForPaymentRefund(final Account account, final PaymentTransaction paymentTransaction, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.PAYMENT_REFUND, TemplateType.PAYMENT_REFUND, account, null, null, paymentTransaction, context);
    }

    public EmailContent generateEmailForSubscriptionCancellationRequested(final Account account, final Subscription subscription, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.SUBSCRIPTION_CANCELLATION_REQUESTED, TemplateType.SUBSCRIPTION_CANCELLATION_REQUESTED, account, subscription, null, null, context);
    }

    public EmailContent generateEmailForSubscriptionCancellationEffective(final Account account, final Subscription subscription, final TenantContext context) throws IOException, TenantApiException {
        return getEmailContent(NotificationType.SUBSCRIPTION_CANCELLATION_EFFECTIVE, TemplateType.SUBSCRIPTION_CANCELLATION_EFFECTIVE, account, subscription, null, null, context);
    }

    private EmailContent getEmailContent(final NotificationType notificationType, final TemplateType templateType, final Account account, @Nullable Subscription subscription, @Nullable final Invoice invoice, @Nullable final PaymentTransaction paymentTransaction, final TenantContext context) throws IOException, TenantApiException {

        final String accountLocale = Strings.emptyToNull(account.getLocale());
        final Locale locale = accountLocale == null ? Locale.getDefault() : LocaleUtils.toLocale(accountLocale);

        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("account", account);
        if (subscription != null) {
            data.put("subscription", subscription);
        }
        if (invoice != null) {
            final InvoiceFormatter formattedInvoice = new DefaultInvoiceFormatter(new HashMap<String, String>(), invoice, locale);
            data.put("invoice", formattedInvoice);
        }
        if (paymentTransaction != null) {
            final PaymentFormatter formattedPayment = new PaymentFormatter(paymentTransaction, locale);
            data.put("payment", formattedPayment);
        }

        InvoiceExtService invoiceExtService = new InvoiceExtService();
        Organization organization = invoiceExtService.selectOrganizationFromAccountId(account.getId().toString());
        Template currentTemplate = null;
        if (organization != null) {

            NotificationConfig notificationConfig = invoiceExtService.selectConfigByOrgId(organization.getId());
            String configuration = notificationConfig.getConfiguration();
            Map configMap = JsonUtils.unmarshal(configuration);

            //organization 에 인보이스 발송 설정이 금지일 경우 보내지 않는다.
            final boolean send = (Boolean) configMap.get(notificationType.toString());
            if (!send) {
                return null;
            }

            List<Template> templates = invoiceExtService.selectByOrgIdAndType(organization.getId(), notificationType.toString());

            //템플릿 중, 어카운트의 로케일과 같은 것을 찾는다.
            for (final Template template : templates) {
                if (template.getLocale().equals(account.getLocale())) {
                    currentTemplate = template;
                }
            }
            //템플릿이 없다면, 디폴트 템플릿을 찾는다.
            if (currentTemplate == null) {
                for (final Template template : templates) {
                    if ("Y".equals(template.getIs_default())) {
                        currentTemplate = template;
                    }
                }
            }
        }

        //조직이 없거나, currentTemplate 을 찾을 수 없는 경우 원래 로직대로 처리한다.
        if (organization == null || currentTemplate == null) {
            final Map<String, String> text = getTranslationMap(accountLocale, ResourceBundleFactory.ResourceBundleType.TEMPLATE_TRANSLATION, context);
            data.put("text", text);

            final String templateText = getTemplateText(locale, templateType, context);
            final String body = templateEngine.executeTemplateText(templateText, data);
            final String subject = new StringBuffer((String) text.get("merchantName")).append(": ").append(text.get(templateType.getSubjectKeyName())).toString();
            return new EmailContent(subject, body);
        }


        // 1. 인보이스 제목을 템플릿의 subject 에서 가져온다.
        // 2. 인보이스 바디를 템플릿의 body 에서 가져온다.
        else {
            //조직 데이터 추가
            data.put("organization", organization);
            final String subject = templateEngine.executeTemplateText(currentTemplate.getSubject(), data);
            final String body = templateEngine.executeTemplateText(currentTemplate.getBody(), data);
            return new EmailContent(subject, body);
        }

    }

    private Map<String, String> getTranslationMap(final String accountLocale, final ResourceBundleFactory.ResourceBundleType bundleType, final TenantContext context) throws TenantApiException {
        final ResourceBundle translationBundle = accountLocale != null ?
                bundleFactory.createBundle(LocaleUtils.toLocale(accountLocale), bundleType, context) : null;

        final Map<String, String> text = new HashMap<String, String>();
        Enumeration<String> keys = translationBundle.getKeys();
        while (keys.hasMoreElements()) {
            final String key = keys.nextElement();
            text.put(key, translationBundle.getString(key));
        }
        return text;
    }


    private String getTemplateText(final Locale locale, final TemplateType templateType, final TenantContext context) throws IOException, TenantApiException {

        final String defaultTemplateName = DEFAULT_TEMPLATE_PATH_PREFIX + templateType.getDefaultTemplateName();
        if (context.getTenantId() == null) {
            return getDefaultTemplate(defaultTemplateName);
        }

        // TODO Caching strategy
//        final String templateTenantKey = LocaleUtils.localeString(locale, templateType.getTemplateKey());
        final String templateTenantKey = templateType.getTemplateKey();
        final List<String> result = tenantApi.getTenantValuesForKey(templateTenantKey, context);
        if (result.size() == 1) {

            return result.get(0);
        }
        return getDefaultTemplate(defaultTemplateName);

    }

    private String getDefaultTemplate(final String templateName) throws IOException {
        final InputStream templateStream = this.getClass().getClassLoader().getResource(templateName).openStream();
        return IOUtils.toString(templateStream);
    }

}
