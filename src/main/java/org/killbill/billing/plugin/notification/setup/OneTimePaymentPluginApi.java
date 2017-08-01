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
import org.joda.time.format.DateTimeFormat;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.catalog.api.*;
import org.killbill.billing.entitlement.api.*;
import org.killbill.billing.invoice.api.*;
import org.killbill.billing.invoice.api.formatters.InvoiceItemFormatter;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.osgi.libs.killbill.*;
import org.killbill.billing.payment.api.*;
import org.killbill.billing.plugin.api.invoice.PluginInvoiceItem;
import org.killbill.billing.plugin.notification.email.EmailContent;
import org.killbill.billing.plugin.notification.email.EmailSender;
import org.killbill.billing.plugin.notification.generator.ResourceBundleFactory;
import org.killbill.billing.plugin.notification.generator.TemplateRenderer;
import org.killbill.billing.plugin.notification.templates.MustacheTemplateEngine;
import org.killbill.billing.plugin.notification.uengine.model.*;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Phase;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Plan;
import org.killbill.billing.plugin.notification.uengine.model.catalog.Usage;
import org.killbill.billing.plugin.notification.uengine.service.InvoiceExtService;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.osgi.service.log.LogService;
import org.skife.config.TimeSpan;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class OneTimePaymentPluginApi implements InvoicePluginApi {

    private final LogService logService;
    private final OSGIKillbillAPI osgiKillbillAPI;
    private final OSGIConfigPropertiesService configProperties;
    private final OSGIKillbillClock clock;


    public OneTimePaymentPluginApi(final OSGIKillbillClock clock, final OSGIKillbillLogService logService, final OSGIKillbillAPI killbillAPI, final OSGIConfigPropertiesService configProperties) {
        this.logService = logService;
        this.osgiKillbillAPI = killbillAPI;
        this.configProperties = configProperties;
        this.clock = clock;
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(Invoice invoice, boolean dryRun, Iterable<PluginProperty> properties, CallContext context) {
//
//        플러그인에서 BUNDLE 인보이스 생성시, 아래를 조회하되, 현재 날짜가 billing_date 보다 같거나 이후날짜일 경우 인보이스에 합산처리함. 인보이스에는 effective_date 로 지정.
//        각 아이템의 서브스크립션 중 카테고리가 BASE 인 것을 추리고, 번들 아이디를 얻음.
//        인보이스 플러그인 호출시 드라이런일 경우 원타임 플랜 구매 기록의 번들아이디가 같은 건을 조회하여 인보이스 아이템을 추가하고, 구매 기록의 인보이스아이디, 아이템아이디, 스테이터스는 INVOICED 로 업데이트 함.
//        인보이스 플러그인 호출시 드라이런이 아닐 경우 원타임 플랜 구매 기록의 번들아이디가 같은 건을 조회하여 인보이스 아이템을 추가하고, 구매 기록은 변동하지 않음.

        //각 아이템의 서브스크립션 중 카테고리가 BASE 인 것의 번들 아이디를 가져온다.
        List<String> baseSubscriptionBundleIds = new ArrayList<String>();
        List<InvoiceItem> invoiceItems = invoice.getInvoiceItems();
        for (InvoiceItem invoiceItem : invoiceItems) {
            UUID subscriptionId = invoiceItem.getSubscriptionId();
            if (subscriptionId != null) {
                try {
                    Subscription subscription = osgiKillbillAPI.getSubscriptionApi().getSubscriptionForEntitlementId(subscriptionId, context);
                    ProductCategory category = subscription.getLastActiveProductCategory();
                    if (ProductCategory.BASE.equals(category)) {
                        baseSubscriptionBundleIds.add(subscription.getBundleId().toString());
                    }
                } catch (Exception ex) {
                    //Nothing to do
                }
            }
        }

        //추가할 아이템 리스트
        List list = new ArrayList();

        //각 번들 아이디마다 펜딩상태에 있는 일회성 구매 이력을 가져온다.
        //빌링 데이트는 인보이스의 대상 날짜(target date) 이다.
        Date billingDate = invoice.getTargetDate().toDate();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        String billing_date = format.format(billingDate);
        InvoiceExtService invoiceExtService = new InvoiceExtService();
        for (String bundleId : baseSubscriptionBundleIds) {

            //가져온 일회성 구매 기록을 인보이스 아이템으로 생성하여 반환한다.
            List<OneTimeBuy> oneTimeBuys = invoiceExtService.selectBundlePendingBuys(bundleId, billing_date);

            for (OneTimeBuy oneTimeBuy : oneTimeBuys) {

                //인보이스 아이템 생성
                UUID invoiceItemId = UUID.randomUUID();
                PluginInvoiceItem item = new PluginInvoiceItem(
                        invoiceItemId,
                        InvoiceItemType.EXTERNAL_CHARGE,
                        invoice.getId(),
                        invoice.getAccountId(),
                        null,
                        new LocalDate(oneTimeBuy.getEffective_date()),
                        null,
                        oneTimeBuy.getAmount(),
                        Currency.valueOf(oneTimeBuy.getCurrency()),
                        oneTimeBuy.getPlan_display_name(),
                        null,
                        UUID.fromString(bundleId),
                        oneTimeBuy.getPlan_name(),
                        null,
                        null,
                        null,
                        null,
                        clock.getClock().getUTCNow(),
                        clock.getClock().getUTCNow());
                list.add(item);

                //드라이런이 아닐 경우, DB 업데이트
                if (!dryRun) {
                    oneTimeBuy.setInvoice_id(invoice.getId().toString());
                    oneTimeBuy.setInvoice_item_id(invoiceItemId.toString());
                    oneTimeBuy.setBilling_date(billing_date);
                    oneTimeBuy.setState(OneTimeBuyState.INVOICED.toString());
                    invoiceExtService.updateOneTimeBuy(oneTimeBuy);
                }
            }
        }

        return list;
    }
}
