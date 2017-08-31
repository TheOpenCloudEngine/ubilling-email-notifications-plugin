/*
 * Copyright 2015-2015 Groupon, Inc
 * Copyright 2015-2015 The Billing Project, LLC
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

package org.killbill.billing.plugin.notification.setup;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.samskivert.mustache.MustacheException;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.EmailException;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.*;
import org.killbill.billing.invoice.api.*;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.osgi.libs.killbill.OSGIConfigPropertiesService;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillLogService;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.plugin.notification.email.EmailContent;
import org.killbill.billing.plugin.notification.email.EmailSender;
import org.killbill.billing.plugin.notification.generator.ResourceBundleFactory;
import org.killbill.billing.plugin.notification.generator.TemplateRenderer;
import org.killbill.billing.plugin.notification.templates.MustacheTemplateEngine;
import org.killbill.billing.plugin.notification.uengine.model.*;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Phase;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Plan;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Usage;
import org.killbill.billing.plugin.notification.uengine.repository.InvoiceExtRepository;
import org.killbill.billing.plugin.notification.uengine.service.InvoiceExtService;
import org.killbill.billing.plugin.notification.uengine.util.JsonUtils;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.util.callcontext.TenantContext;
import org.osgi.service.log.LogService;
import org.skife.config.TimeSpan;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class EmailNotificationListener implements OSGIKillbillEventDispatcher.OSGIKillbillEventHandler {

    private static final String INVOICE_DRY_RUN_TIME_PROPERTY = "org.killbill.invoice.dryRunNotificationSchedule";

    private static final NullDryRunArguments NULL_DRY_RUN_ARGUMENTS = new NullDryRunArguments();

    private final LogService logService;
    private final OSGIKillbillAPI osgiKillbillAPI;
    private final TemplateRenderer templateRenderer;
    private final OSGIConfigPropertiesService configProperties;
    private final EmailSender emailSender;
    private final OSGIKillbillClock clock;


    /**
     * 수용해야 할 이벤트
     * INVOICE_CREATION : 수익 분배율을 추가한다.
     * INVOICE_ADJUSTMENT : 수익 분배율을 차감한다.
     * INVOICE_PAYMENT_SUCCESS: 결제 성공 이메일
     * INVOICE_PAYMENT_FAILED : 결제 실패 이메일
     * SUBSCRIPTION_CANCEL : 구독 취소 이메일
     */
    private final ImmutableList<ExtBusEventType> EVENTS_TO_CONSIDER = new ImmutableList.Builder()
            .add(ExtBusEventType.INVOICE_CREATION)
            .add(ExtBusEventType.INVOICE_ADJUSTMENT)
            .add(ExtBusEventType.INVOICE_PAYMENT_SUCCESS)
            .add(ExtBusEventType.INVOICE_PAYMENT_FAILED)
            .add(ExtBusEventType.SUBSCRIPTION_CANCEL)
            .build();


    public EmailNotificationListener(final OSGIKillbillClock clock, final OSGIKillbillLogService logService, final OSGIKillbillAPI killbillAPI, final OSGIConfigPropertiesService configProperties) {
        this.logService = logService;
        this.osgiKillbillAPI = killbillAPI;
        this.configProperties = configProperties;
        this.clock = clock;
        this.emailSender = new EmailSender(configProperties, logService);
        this.templateRenderer = new TemplateRenderer(new MustacheTemplateEngine(), new ResourceBundleFactory(killbillAPI.getTenantUserApi(), logService), killbillAPI.getTenantUserApi(), logService);
    }

    @Override
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {


//        //TODO. 결제 성공했을 때 이폼서비스에 새로운 알람을 주고싶다.
//        ExtBusEventType eventType = killbillEvent.getEventType();
//        if (eventType.equals(ExtBusEventType.PAYMENT_SUCCESS)) {
//            UUID objectId = killbillEvent.getObjectId();
//
//
//            try {
//                Payment payment = osgiKillbillAPI.getPaymentApi().getPayment(
//                        objectId,
//                        false,
//                        false,
//                        null,
//                        new EmailNotificationContext(killbillEvent.getTenantId()));
//
//                //어카운트 아이디로 이폼서비스의 사용자를 찾음.
//                UUID accountId = payment.getAccountId();
//
//
//                //결제 금액 얻기.
//                BigDecimal purchasedAmount = payment.getPurchasedAmount();
//
//                //결제 통화 얻기.
//                Currency currency = payment.getCurrency();
//
//                //TODO 이폼 서비스 사용에게 결제 금액, 통화 포함하여 푸시 및 이메일을 날리는 코드를 작성하면 됨.
//            } catch (Exception ex) {
//
//            }
//        }


        if (!EVENTS_TO_CONSIDER.contains(killbillEvent.getEventType())) {
            return;
        }

        // TODO see https://github.com/killbill/killbill-platform/issues/5
        final ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

        try {
            final Account account = osgiKillbillAPI.getAccountUserApi().getAccountById(killbillEvent.getAccountId(), new EmailNotificationContext(killbillEvent.getTenantId()));
            final String to = account.getEmail();
            if (to == null) {
                logService.log(LogService.LOG_INFO, "Account " + account.getId() + " does not have an email address configured, skip...");
                return;
            }

            final EmailNotificationContext context = new EmailNotificationContext(killbillEvent.getTenantId());
            switch (killbillEvent.getEventType()) {

                case INVOICE_ADJUSTMENT:
                    reduceProductDistributionHistory(account, killbillEvent, context);
                    break;

                case INVOICE_CREATION:
                    addProductDistributionHistory(account, killbillEvent, context);
                    break;

                case INVOICE_PAYMENT_SUCCESS:
                case INVOICE_PAYMENT_FAILED:
                    sendEmailForPayment(account, killbillEvent, context);
                    break;

                case SUBSCRIPTION_CANCEL:
                    sendEmailForCancelledSubscription(account, killbillEvent, context);
                    break;

                default:
                    break;
            }

            logService.log(LogService.LOG_INFO, String.format("Received event %s for object type = %s, id = %s",
                    killbillEvent.getEventType(), killbillEvent.getObjectType(), killbillEvent.getObjectId()));

        } catch (final AccountApiException e) {
            logService.log(LogService.LOG_WARNING, String.format("Unable to find account: %s", killbillEvent.getAccountId()), e);
        } catch (InvoiceApiException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to retrieve invoice for account %s", killbillEvent.getAccountId()), e);
        } catch (SubscriptionApiException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to retrieve subscription for account %s", killbillEvent.getAccountId()), e);
        } catch (PaymentApiException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to send email for account %s", killbillEvent.getAccountId()), e);
        } catch (EmailException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to send email for account %s", killbillEvent.getAccountId()), e);
        } catch (IOException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to send email for account %s", killbillEvent.getAccountId()), e);
        } catch (TenantApiException e) {
            logService.log(LogService.LOG_WARNING, String.format("Fail to send email for account %s", killbillEvent.getAccountId()), e);
        } catch (IllegalArgumentException e) {
            logService.log(LogService.LOG_WARNING, e.getMessage(), e);
        } catch (MustacheException e) {
            logService.log(LogService.LOG_WARNING, e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }

    /**
     * Adjustment 된 인보이스를 바탕으로 판매 이력에서 차감한다.
     *
     * @param account
     * @param killbillEvent
     * @param context
     * @throws InvoiceApiException
     * @throws IOException
     * @throws EmailException
     * @throws TenantApiException
     */
    private void reduceProductDistributionHistory(final Account account, final ExtBusEvent killbillEvent, final TenantContext context) throws InvoiceApiException, IOException, EmailException, TenantApiException {
        Preconditions.checkArgument(killbillEvent.getEventType() == ExtBusEventType.INVOICE_ADJUSTMENT, String.format("Unexpected event %s", killbillEvent.getEventType()));

        final UUID invoiceId = killbillEvent.getObjectId();
        final Invoice invoice = osgiKillbillAPI.getInvoiceUserApi().getInvoice(invoiceId, context);
        List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        InvoiceExtService invoiceExtService = new InvoiceExtService();
        Organization organization = invoiceExtService.selectOrganizationFromAccountId(account.getId().toString());
        if (organization == null) {
            return;
        }

        //킬빌의 현재시각
        Date currentDate = clock.getClock().getUTCNow().toDate();

        for (InvoiceItem invoiceItem : invoiceItems) {

            //REPAIR_ADJ 또는 ITEM_ADJ 만 해당된다.
            InvoiceItemType invoiceItemType = invoiceItem.getInvoiceItemType();
            if (!invoiceItemType.toString().equals(InvoiceItemType.REPAIR_ADJ.toString()) &&
                    !invoiceItemType.toString().equals(InvoiceItemType.ITEM_ADJ.toString())) {
                continue;
            }

            //링크 아이템이 없는 경우 해당되지 않는다.
            if (invoiceItem.getLinkedItemId() == null) {
                continue;
            }

            //해당 이력이 저장되었는지 확인하기. 해당 이력이 있을 경우 해당되지 않는다.
            List<ProductDistributionHistory> existHistories = invoiceExtService.selectHistoryByItemId(invoiceItem.getId().toString());
            if (existHistories != null && existHistories.size() > 0) {
                continue;
            }

            //원본 인보이스 아이템 트랜잭션 가져오기.
            List<ProductDistributionHistory> originalItems = new ArrayList<ProductDistributionHistory>();
            List<ProductDistributionHistory> histories = invoiceExtService.selectHistoryByItemId(invoiceItem.getLinkedItemId().toString());
            if (histories != null) {
                for (ProductDistributionHistory history : histories) {
                    if (DistributionTransactionType.CREATION.toString().equals(history.getTransaction_type())) {
                        originalItems.add(history);
                    }
                }
            }
            //원본 인보이스 아이템이 없을 경우 해당되지 않는다.
            if (originalItems.isEmpty()) {
                continue;
            }

            //원본 내용대로 트랜잭션 저장하기.
            for (ProductDistributionHistory originalItem : originalItems) {

                //인보이스 아이템의 가격 조정된 가격 = original_amount
                //인보이스 아이템의 가격 조정된 가격 * 구매시 적용되었던 ratio * 0.01 = amount
                BigDecimal original_amount = invoiceItem.getAmount();
                BigDecimal ratio = originalItem.getRatio();
                BigDecimal amount = original_amount.multiply(ratio).multiply(new BigDecimal("0.01"));

                originalItem.setRecord_id(null);
                originalItem.setAmount(amount);
                originalItem.setOriginal_amount(original_amount);
                originalItem.setInvoice_id(invoice.getId().toString());
                originalItem.setInvoice_item_id(invoiceItem.getId().toString());
                originalItem.setLinked_invoice_item_id(invoiceItem.getLinkedItemId().toString());
                originalItem.setInvoice_item_type(invoiceItem.getInvoiceItemType().toString());
                originalItem.setTransaction_type(DistributionTransactionType.ADJUSTMENT.toString());

                SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
                String formatDate = format1.format(currentDate);
                originalItem.setFormat_date(formatDate);
                originalItem.setCreated_date(currentDate);

                //이제 저장하면 된다.
                invoiceExtService.insertHistory(originalItem);
            }
        }
    }

    /**
     * 발급된 인보이스를 바탕으로 판매 이력을 추가한다.
     *
     * @param account
     * @param killbillEvent
     * @param context
     * @throws InvoiceApiException
     * @throws IOException
     * @throws EmailException
     * @throws TenantApiException
     */
    private void addProductDistributionHistory(final Account account, final ExtBusEvent killbillEvent, final TenantContext context) throws InvoiceApiException, IOException, EmailException, TenantApiException {
        Preconditions.checkArgument(killbillEvent.getEventType() == ExtBusEventType.INVOICE_CREATION, String.format("Unexpected event %s", killbillEvent.getEventType()));

        final UUID invoiceId = killbillEvent.getObjectId();
        final Invoice invoice = osgiKillbillAPI.getInvoiceUserApi().getInvoice(invoiceId, context);
        List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        InvoiceExtService invoiceExtService = new InvoiceExtService();
        Organization organization = invoiceExtService.selectOrganizationFromAccountId(account.getId().toString());
        if (organization == null) {
            return;
        }

        //킬빌의 현재시각
        Date currentDate = clock.getClock().getUTCNow().toDate();

//        인보이스 이벤트 리스너에서 원타임 구매 기록에 대한 벤더분배율을 기록하기.
//
//                아이템 중에 서브스크립션 아이디가 없고 플랜 네임만 있는 경우 원타임 페이먼트 이다.
//                해당 구매일에 맞는 버젼의 가격을 찾아, 분배율을 기록하도록 한다.

        //원타임 구매 모델의 분배 규칙을 수행한다.
        for (InvoiceItem invoiceItem : invoiceItems) {
            //서브스크립션 아이디가 없고, 플랜네임이 있으며, 익스터널 차지 아이템 ==> 원타임 구매 아이템이다.
            boolean isOneTimeItem = false;
            if (invoiceItem.getSubscriptionId() == null && !StringUtils.isEmpty(invoiceItem.getPlanName())
                    && invoiceItem.getInvoiceItemType().equals(InvoiceItemType.EXTERNAL_CHARGE)) {
                isOneTimeItem = true;
            }

            if (!isOneTimeItem) {
                continue;
            }

            try {
                //해당 인보이스 아이템 아이디의 원타임 구매 기록을 가져온다.
                OneTimeBuy oneTimeBuy = invoiceExtService.selectOneTimeBuyByItemId(invoiceItem.getId().toString());
                if (oneTimeBuy == null) {
                    continue;
                }

                //구매 기록의 프로덕트, 플랜, 버젼으로 플랜과 프로덕트를 획득한다.
                String plan_name = oneTimeBuy.getPlan_name();
                Long version = oneTimeBuy.getVersion();
                String product_id = oneTimeBuy.getProduct_id();

                Product product = invoiceExtService.selectProductById(product_id);
                ProductVersion productVersion = invoiceExtService.selectProductVersionByVersion(product_id, version);

                List<Plan> plans = productVersion.getPlans();
                Plan plan = null;
                for (Plan plan_ : plans) {
                    if (plan_.getName().equals(plan_name)) {
                        plan = plan_;
                    }
                }
                if (plan == null) {
                    continue;
                }

                //최종 결정된 프로덕트, 플랜 벤더 분배율로 최종 벤더 분베율을 구한다.
                List<Vendor> vendors = new ArrayList<Vendor>();
                if (plan.getOverwriteVendors() != null) {
                    vendors = plan.getOverwriteVendors();
                } else if (product.getVendors() != null) {
                    vendors = product.getVendors();
                }

                //벤더의 합이 100 을 넘으면 안된다.
                BigDecimal organization_ratio = null;
                BigDecimal total = new BigDecimal("0");
                for (Vendor vendor : vendors) {
                    BigDecimal ratio = vendor.getRatio();
                    total = total.add(ratio);
                }

                //벤더 분배율 합이 100 이 넘을 경우, vendor 리스트를 비우고, organization 비율을 100 으로 설정한다.
                if (total.compareTo(new BigDecimal("100")) == 1) {
                    vendors = new ArrayList<Vendor>();
                    organization_ratio = new BigDecimal("100");
                    Vendor vendor = new Vendor();
                    vendor.setAccount_id("organization");
                    vendor.setRatio(organization_ratio);
                    vendors.add(vendor);
                }
                //그 외의 경우 100 에서 벤더 분배율을 뺀 비율이 organization 몫이다.
                else {
                    organization_ratio = new BigDecimal("100").subtract(total);
                    Vendor vendor = new Vendor();
                    vendor.setAccount_id("organization");
                    vendor.setRatio(organization_ratio);
                    vendors.add(vendor);
                }

                //각 ratio 에 따라 인서트를 수행한다.
                for (Vendor vendor : vendors) {
                    BigDecimal original_amount = invoiceItem.getAmount();
                    BigDecimal ratio = vendor.getRatio();
                    BigDecimal amount = original_amount.multiply(ratio).multiply(new BigDecimal("0.01"));

                    ProductDistributionHistory history = new ProductDistributionHistory();
                    history.setSubscription_id(null);
                    history.setTenant_id(organization.getTenant_id());
                    history.setOrganization_id(organization.getId());
                    history.setBuyer_id(account.getId().toString());
                    history.setVendor_id(vendor.getAccount_id());
                    history.setProduct_id(product_id);
                    history.setVersion(version);
                    history.setPlan_name(plan_name);
                    history.setUsage_name(null);
                    history.setRatio(vendor.getRatio());
                    history.setAmount(amount);
                    history.setOriginal_amount(original_amount);
                    history.setCurrency(invoiceItem.getCurrency().toString());
                    history.setInvoice_id(invoice.getId().toString());
                    history.setInvoice_item_id(invoiceItem.getId().toString());
                    history.setInvoice_item_type(invoiceItem.getInvoiceItemType().toString());
                    history.setPrice_type("ONE_TIME");
                    history.setTransaction_type(DistributionTransactionType.CREATION.toString());

                    SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
                    String formatDate = format1.format(currentDate);
                    history.setFormat_date(formatDate);
                    history.setCreated_date(currentDate);

                    //이제 저장하면 된다.
                    invoiceExtService.insertHistory(history);
                }
            } catch (Exception ex) {

            }
        }


        //서브스크립션 모델의 프로덕트 분배 규칙을 수행한다.
        for (InvoiceItem invoiceItem : invoiceItems) {
            String planName = invoiceItem.getPlanName();
            String usageName = invoiceItem.getUsageName();

            UUID subscriptionId = invoiceItem.getSubscriptionId();
            if (subscriptionId == null) {
                continue;
            }
            try {
                Subscription subscription = osgiKillbillAPI.getSubscriptionApi().getSubscriptionForEntitlementId(subscriptionId, context);

                //=== 서브스크립션 적용 날짜 구하기 ====
                //서브스크립션 이벤트 중, 현재 시각보다 이전인 구독 변경 이력 중 가장 나중인 것을 찾는다.
                Date effectiveVersionDate = null;
                List<SubscriptionEvent> subscriptionEvents = subscription.getSubscriptionEvents();
                for (SubscriptionEvent subscriptionEvent : subscriptionEvents) {
                    SubscriptionEventType eventType = subscriptionEvent.getSubscriptionEventType();

                    //변경 이력의 이벤트타입이며, nextPlan 명이 인보이스 아이템 플랜명과 같을 경우
                    if (SubscriptionEventType.CHANGE.toString().equals(eventType.name())
                            && planName.equals(subscriptionEvent.getNextPlan().getName())) {
                        LocalDate changeDate = subscriptionEvent.getEffectiveDate();

                        //킬빌의 시각보다 이전 시각이라면
                        if (changeDate.toDate().getTime() < currentDate.getTime()) {

                            //가장 나중일 경우에만 치환한다.
                            if (effectiveVersionDate == null) {
                                effectiveVersionDate = changeDate.toDate();
                            } else {
                                if (effectiveVersionDate.getTime() < changeDate.toDate().getTime()) {
                                    effectiveVersionDate = changeDate.toDate();
                                }
                            }
                        }
                    }
                }
                //해당 하는 구독 변경 이력이 없다면, 서브스크립션 시작일로 지정한다.
                if (effectiveVersionDate == null) {
                    for (SubscriptionEvent subscriptionEvent : subscriptionEvents) {
                        SubscriptionEventType eventType = subscriptionEvent.getSubscriptionEventType();
                        if (SubscriptionEventType.START_ENTITLEMENT.toString().equals(eventType.name())) {
                            effectiveVersionDate = subscriptionEvent.getEffectiveDate().toDate();
                        }
                    }
                }
                //최종 적용 날짜를 구하지 못하였다면, 현재시각으로 대체한다.
                if (effectiveVersionDate == null) {
                    effectiveVersionDate = currentDate;
                }


                //== 적용 플랜 구하기 ==
                //플랜명으로부터 프로덕트 아이디를 얻어온 후, 프로덕트와 해당 프로덕트의 모든 버젼을 불러온다.
                String product_id = planName.substring(0, 14);
                Product product = invoiceExtService.selectProductById(product_id);
                List<ProductVersion> productVersions = invoiceExtService.selectVersionByProductId(product_id);


                //이펙티브 데이트에 해당하는 플랜을 선정한다.
                //플랜을 포함한 버젼을 선출한다. 유서지네임이 있는 경우, 유서지네임을 포함한 플랜을 선출한다.
                ProductVersion maxTimeVersion = null;
                Plan maxTimePlan = null;
                Usage maxTimeUsage = null;
                for (ProductVersion productVersion : productVersions) {
                    List<Plan> plans = productVersion.getPlans();
                    Plan matchPlan = null;
                    Usage matchUsage = null;

                    for (Plan plan : plans) {
                        //버젼에 해당 플랜이 있다면
                        if (planName.equals(plan.getName())) {
                            //유서지가 없는 경우는 matchPlan 에 등록.
                            if (usageName == null) {
                                matchPlan = plan;
                            }
                            //유서지가 있는 경우는 플랜이 유서지를 포함해야 matchPlan 과 matchUsage 등록.
                            else {
                                List<Phase> initialPhases = plan.getInitialPhases();
                                if (initialPhases != null) {
                                    for (Phase initialPhase : initialPhases) {
                                        if (initialPhase.getUsages() != null) {
                                            for (Usage usage : initialPhase.getUsages()) {
                                                if (usageName.equals(usage.getName())) {
                                                    matchPlan = plan;
                                                    matchUsage = usage;
                                                }
                                            }
                                        }
                                    }
                                }
                                Phase finalPhase = plan.getFinalPhase();
                                if (finalPhase.getUsages() != null) {
                                    for (Usage usage : finalPhase.getUsages()) {
                                        if (usageName.equals(usage.getName())) {
                                            matchPlan = plan;
                                            matchUsage = usage;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    //플랜 또는 유서지를 가지고 있는 프로덕트 버젼의 effective 날짜와 , 서브스크립션의 적용 날짜를 비교해 적용 버젼을 찾는다.
                    if (matchPlan != null) {
                        if (productVersion.getEffective_date().getTime() < effectiveVersionDate.getTime()) {
                            if (maxTimeVersion == null) {
                                maxTimeVersion = productVersion;
                                maxTimePlan = matchPlan;
                                maxTimeUsage = matchUsage;
                            } else {
                                if (maxTimeVersion.getEffective_date().getTime() < productVersion.getEffective_date().getTime()) {
                                    maxTimeVersion = productVersion;
                                    maxTimePlan = matchPlan;
                                    maxTimeUsage = matchUsage;
                                }
                            }
                        }
                    }
                }

                //최종 결정된 프로덕트,플랜,유서지의 벤더 분배율로 최종 벤더 분베율을 구한다.
                List<Vendor> vendors = new ArrayList<Vendor>();
                if (maxTimeUsage != null && maxTimeUsage.getOverwriteVendors() != null) {
                    vendors = maxTimeUsage.getOverwriteVendors();
                } else if (maxTimePlan != null && maxTimePlan.getOverwriteVendors() != null) {
                    vendors = maxTimePlan.getOverwriteVendors();
                } else if (product.getVendors() != null) {
                    vendors = product.getVendors();
                }

                //벤더의 합이 100 을 넘으면 안된다.
                BigDecimal organization_ratio = null;
                BigDecimal total = new BigDecimal("0");
                for (Vendor vendor : vendors) {
                    BigDecimal ratio = vendor.getRatio();
                    total = total.add(ratio);
                }
                //벤더 분배율 합이 100 이 넘을 경우, vendor 리스트를 비우고, organization 비율을 100 으로 설정한다.
                if (total.compareTo(new BigDecimal("100")) == 1) {
                    vendors = new ArrayList<Vendor>();
                    organization_ratio = new BigDecimal("100");
                    Vendor vendor = new Vendor();
                    vendor.setAccount_id("organization");
                    vendor.setRatio(organization_ratio);
                    vendors.add(vendor);
                }
                //그 외의 경우 100 에서 벤더 분배율을 뺀 비율이 organization 몫이다.
                else {
                    organization_ratio = new BigDecimal("100").subtract(total);
                    Vendor vendor = new Vendor();
                    vendor.setAccount_id("organization");
                    vendor.setRatio(organization_ratio);
                    vendors.add(vendor);
                }

                //각 ratio 에 따라 인서트를 수행한다.
                for (Vendor vendor : vendors) {
                    BigDecimal original_amount = invoiceItem.getAmount();
                    BigDecimal ratio = vendor.getRatio();
                    BigDecimal amount = original_amount.multiply(ratio).multiply(new BigDecimal("0.01"));

                    ProductDistributionHistory history = new ProductDistributionHistory();
                    history.setSubscription_id(subscriptionId.toString());
                    history.setTenant_id(organization.getTenant_id());
                    history.setOrganization_id(organization.getId());
                    history.setBuyer_id(account.getId().toString());
                    history.setVendor_id(vendor.getAccount_id());
                    history.setProduct_id(product_id);
                    history.setVersion(maxTimeVersion.getVersion());
                    history.setPlan_name(planName);
                    history.setUsage_name(usageName);
                    history.setRatio(vendor.getRatio());
                    history.setAmount(amount);
                    history.setOriginal_amount(original_amount);
                    history.setCurrency(invoiceItem.getCurrency().toString());
                    history.setInvoice_id(invoice.getId().toString());
                    history.setInvoice_item_id(invoiceItem.getId().toString());
                    history.setInvoice_item_type(invoiceItem.getInvoiceItemType().toString());
                    history.setPrice_type(invoiceItem.getInvoiceItemType().toString());
                    history.setTransaction_type(DistributionTransactionType.CREATION.toString());

                    SimpleDateFormat format1 = new SimpleDateFormat("yyyy-MM-dd");
                    String formatDate = format1.format(currentDate);
                    history.setFormat_date(formatDate);
                    history.setCreated_date(currentDate);

                    //이제 저장하면 된다.
                    invoiceExtService.insertHistory(history);
                }
            } catch (Exception ex) {

            }
        }

    }

    private void sendEmailForUpComingInvoice(final Account account, final ExtBusEvent killbillEvent, final TenantContext context) throws IOException, InvoiceApiException, EmailException, TenantApiException {

        Preconditions.checkArgument(killbillEvent.getEventType() == ExtBusEventType.INVOICE_NOTIFICATION, String.format("Unexpected event %s", killbillEvent.getEventType()));

        final String dryRunTimePropValue = configProperties.getString(INVOICE_DRY_RUN_TIME_PROPERTY);
        Preconditions.checkArgument(dryRunTimePropValue != null, String.format("Cannot find property %s", INVOICE_DRY_RUN_TIME_PROPERTY));

        final TimeSpan span = new TimeSpan(dryRunTimePropValue);

        final DateTime now = clock.getClock().getUTCNow();
        final DateTime targetDateTime = now.plus(span.getMillis());

        final PluginCallContext callContext = new PluginCallContext(EmailNotificationActivator.PLUGIN_NAME, now, context.getTenantId());
        final Invoice invoice = osgiKillbillAPI.getInvoiceUserApi().triggerInvoiceGeneration(account.getId(), new LocalDate(targetDateTime, account.getTimeZone()), NULL_DRY_RUN_ARGUMENTS, callContext);
        if (invoice != null) {
            final EmailContent emailContent = templateRenderer.generateEmailForUpComingInvoice(account, invoice, context);
            sendEmail(account, emailContent, context);
        }
    }

    private void sendEmailForCancelledSubscription(final Account account, final ExtBusEvent killbillEvent, final TenantContext context) throws SubscriptionApiException, IOException, EmailException, TenantApiException {
        Preconditions.checkArgument(killbillEvent.getEventType() == ExtBusEventType.SUBSCRIPTION_CANCEL, String.format("Unexpected event %s", killbillEvent.getEventType()));
        final UUID subscriptionId = killbillEvent.getObjectId();

        final Subscription subscription = osgiKillbillAPI.getSubscriptionApi().getSubscriptionForEntitlementId(subscriptionId, context);
        if (subscription != null) {
            final EmailContent emailContent = subscription.getState() == Entitlement.EntitlementState.CANCELLED ?
                    templateRenderer.generateEmailForSubscriptionCancellationEffective(account, subscription, context) :
                    templateRenderer.generateEmailForSubscriptionCancellationRequested(account, subscription, context);
            sendEmail(account, emailContent, context);
        }
    }


    private void sendEmailForPayment(final Account account, final ExtBusEvent killbillEvent, final TenantContext context) throws InvoiceApiException, IOException, EmailException, PaymentApiException, TenantApiException {
        final UUID invoiceId = killbillEvent.getObjectId();
        if (invoiceId == null) {
            return;
        }

        Preconditions.checkArgument(killbillEvent.getEventType() == ExtBusEventType.INVOICE_PAYMENT_FAILED || killbillEvent.getEventType() == ExtBusEventType.INVOICE_PAYMENT_SUCCESS, String.format("Unexpected event %s", killbillEvent.getEventType()));

        final Invoice invoice = osgiKillbillAPI.getInvoiceUserApi().getInvoice(invoiceId, context);
        if (invoice.getNumberOfPayments() == 0) {
            // Aborted payment? Maybe no default payment method...
            return;
        }

        final InvoicePayment invoicePayment = invoice.getPayments().get(invoice.getNumberOfPayments() - 1);

        final Payment payment = osgiKillbillAPI.getPaymentApi().getPayment(invoicePayment.getPaymentId(), false, false, ImmutableList.<PluginProperty>of(), context);
        final PaymentTransaction lastTransaction = payment.getTransactions().get(payment.getTransactions().size() - 1);

        if (lastTransaction.getTransactionType() != TransactionType.PURCHASE &&
                lastTransaction.getTransactionType() != TransactionType.REFUND) {
            // Ignore for now, but this is easy to add...
            return;
        }

        EmailContent emailContent = null;
        if (lastTransaction.getTransactionType() == TransactionType.REFUND && lastTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
            emailContent = templateRenderer.generateEmailForPaymentRefund(account, lastTransaction, context);
        } else {
            if (lastTransaction.getTransactionType() == TransactionType.PURCHASE && lastTransaction.getTransactionStatus() == TransactionStatus.SUCCESS) {
                emailContent = templateRenderer.generateEmailForSuccessfulPayment(account, invoice, context);
            } else if (lastTransaction.getTransactionType() == TransactionType.PURCHASE && lastTransaction.getTransactionStatus() == TransactionStatus.PAYMENT_FAILURE) {
                emailContent = templateRenderer.generateEmailForFailedPayment(account, invoice, context);
            }
        }
        if (emailContent != null) {
            sendEmail(account, emailContent, context);
        }
    }

    private void sendEmail(final Account account, final EmailContent emailContent, final TenantContext context) throws IOException, EmailException {

        //organization 의 이메일 설정이 전송 금지일 경우 중지.
        if (emailContent == null) {
            return;
        }

        final Iterable<String> cc = Iterables.transform(osgiKillbillAPI.getAccountUserApi().getEmails(account.getId(), context), new Function<AccountEmail, String>() {
            @Nullable
            @Override
            public String apply(AccountEmail input) {
                return input.getEmail();
            }
        });
        //emailSender.sendPlainTextEmail(ImmutableList.of(account.getEmail()), ImmutableList.copyOf(cc), emailContent.getSubject(), emailContent.getBody());
        emailSender.sendHTMLEmail(ImmutableList.of(account.getEmail()), ImmutableList.copyOf(cc), emailContent.getSubject(), emailContent.getBody());
    }

    private static final class EmailNotificationContext implements TenantContext {

        private final UUID tenantId;

        private EmailNotificationContext(final UUID tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public UUID getTenantId() {
            return tenantId;
        }
    }

    private final static class NullDryRunArguments implements DryRunArguments {
        @Override
        public DryRunType getDryRunType() {
            return null;
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
            return null;
        }

        @Override
        public SubscriptionEventType getAction() {
            return null;
        }

        @Override
        public UUID getSubscriptionId() {
            return null;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return null;
        }

        @Override
        public UUID getBundleId() {
            return null;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return null;
        }

        @Override
        public List<PlanPhasePriceOverride> getPlanPhasePriceOverrides() {
            return null;
        }
    }
}
